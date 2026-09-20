# 수강 마감 기한 자동 만료

2026-09-20 구현. 단일 애플리케이션 인스턴스에서 동작하는 내부 정기 작업이다.
외부 수동 만료 API, 분산 잠금, 알림, 기한 연장, 재수강 초기화는 제공하지 않는다.

## 대상 및 날짜 정책

- `status IN (ASSIGNED, IN_PROGRESS) AND dueDate < today`
- `today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")))`
- 기존 UTC `Clock` Bean을 재사용하며 실행 시작 시 서울 날짜를 한 번만 계산한다.
- `dueDate` 당일에는 유효하다. 2026-09-20 마감이면 서울 2026-09-21 00:00부터 만료 대상이다.
- Entity와 기존 SQL 모두 `due_date DATE NOT NULL`이므로 null 제외 정책이나 기본값을 추가하지 않았다.
- Course 상태, 회원 상태, 배정 출처는 만료 조건에 포함하지 않는다. CLOSED 과정도 처리한다.

## 상태 및 시각 필드

| 기존 상태 | 마감일 경과 시 | startedAt | completedAt |
| --- | --- | --- | --- |
| ASSIGNED | EXPIRED | null 유지 | null 유지 |
| IN_PROGRESS | EXPIRED | 최초 시작 시각 유지 | null 유지 |
| COMPLETED | 변경 없음 | 유지 | 기존 수료 시각 유지 |
| FAILED | 변경 없음 | 유지 | null 유지 |
| EXPIRED | 변경 없음 | 유지 | null 유지 |

`Enrollment.expireIfOverdue(today)`가 상태와 마감일을 함께 재검증하고 변경 여부를 반환한다.
assignedAt, dueDate, 배정 출처/규칙은 변경하지 않는다. 기존 감사 필드와 @Version은 JPA가 관리한다.
expiredAt을 추가하지 않았으며 completedAt을 만료 시각으로 사용하지 않는다.

## 실행 및 트랜잭션

1. `EnrollmentExpirationScheduler`가 `@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")`로 서비스를 호출한다.
2. `EnrollmentExpirationService.expireOverdueEnrollments()`는 `NOT_SUPPORTED`로 전체 작업을 하나의 트랜잭션으로 묶지 않는다.
3. `EnrollmentRepository.findOverdueIds(statuses, today, afterId, pageable)`는 readOnly 조회로 후보 ID를 가져온다.
4. ID 오름차순으로 최대 500개씩 조회한다. 매번 offset=0, `id > afterId`를 사용하므로 앞선 상태 변경으로 후보가 줄어도 offset 방식의 누락이 생기지 않는다.
5. 별도 Bean인 `EnrollmentExpirationProcessor.expire(id, today)`를 호출한다. `REQUIRES_NEW` 트랜잭션 안에서 기존 findById로 다시 읽고 상태·날짜를 재검증한다.
6. 변경된 관리 엔티티를 flush하고 트랜잭션을 커밋한다. 서비스는 커밋까지 성공한 건만 expiredCount에 포함한다.
7. 후보가 없어질 때까지 반복하고 `ExpirationResult(expiredCount, conflictCount)`를 반환한다. 스케줄러는 개인정보 없이 집계 로그 한 건만 남긴다.

Processor 분리는 새로운 도메인 추상화가 아니라 Spring 프록시를 통한 건별 독립 트랜잭션을 위한 것이다.
같은 Bean 내부 호출로 `REQUIRES_NEW`가 무시되는 문제와 실패 후 rollback-only 트랜잭션을 재사용하는 문제를 피한다.
조회용 ID 묶음과 건별 엔티티만 유지하며 전체 수강 엔티티를 메모리에 적재하지 않는다.

## 동시성 및 실패 정책

- 기존 Enrollment의 `@Version`을 그대로 사용한다. 직접 bulk update나 version 우회 저장은 없다.
- 후보 조회 후 이미 COMPLETED/FAILED/EXPIRED가 된 경우 건별 재조회와 도메인 검사에서 건너뛴다.
- 재조회 이후 다른 트랜잭션이 수강을 변경하면 버전 조건으로 충돌을 감지한다. 오래된 상태로 수료/실패 결과를 덮어쓰지 않는다.
- 기존 진도 수정의 `OPTIMISTIC_FORCE_INCREMENT`, 시험 변경의 `PESSIMISTIC_FORCE_INCREMENT` 정책은 유지한다.
- flush 또는 commit 시 발생한 Spring `ConcurrencyFailureException` 계열 예외는 건별 트랜잭션 종료 후 서비스가 처리한다. 낙관적 충돌뿐 아니라 Spring이 변환한 잠금 경합도 건너뛴다.
- 충돌 건은 conflictCount로 집계하고 같은 실행에서 재시도하지 않는다. 다른 후보는 계속 처리하며 충돌 건은 다음 스케줄에서 다시 평가한다.
- 동시성 이외의 예상하지 못한 오류는 숨기지 않고 스케줄러로 전파한다. 해당 실행은 중단되며 이미 성공한 건은 커밋된 채 유지된다. 이후 정기 실행에서 남은 대상을 재조회한다.
- HTTP 요청의 기존 `CONCURRENT_MODIFICATION` 409 응답 정책은 변경하지 않았다. 스케줄러는 HTTP 예외 처리기를 호출하지 않는다.
- 새로운 ErrorCode는 없다.

## catch-up, 멱등성 및 운영 경계

어제 마감 건만 조회하지 않고 `dueDate < today`인 모든 미처리 건을 조회한다.
따라서 9월 21일 실행을 놓쳤더라도 9월 23일 스케줄이 실행되면 9월 20일 마감 건도 처리한다.
서버 재시작 직후 즉시 실행하는 별도 트리거는 없다. 재시작 후 다음 서울 자정 정기 실행에서 catch-up한다.

EXPIRED는 다음 후보 조회에서 제외되며, 중복 후보가 전달되어도 도메인 메서드가 false를 반환한다.
COMPLETED/FAILED 역시 변경하지 않아 반복 실행은 멱등적이다.

**서울 자정은 만료 대상이 되는 시점이지 모든 건의 저장이 동시에 끝나는 시점은 아니다.**
처리 지연, 서버 중단, 충돌 후 다음 실행 대기 동안에는 아직 ASSIGNED/IN_PROGRESS인 수강 요청이 허용될 수 있다.
이는 이번 범위에서 요청 시점 dueDate 검사를 도입하지 않는 정책에 따른 것이다.
현재 실행 도중 새로 생기거나 변경된 후보 중 이미 지나간 ID 범위의 건도 다음 정기 실행에서 평가한다.
진행 중인 요청과 만료가 경합하면 DB 잠금·버전 정책에 따라 먼저 확정된 결과가 유지되며 요청 시작 시각 우선권은 없다.
충돌 뒤 수료/실패가 확정된 건은 다음 실행에서 만료하지 않는다.

다중 인스턴스의 스케줄 실행 조정은 제공하지 않는다. 배포 구성이 바뀌면 별도 운영 설계가 필요하다.

## 기존 기능과의 관계

- ContentProgressService의 terminal 상태 검사로 EXPIRED 이후 진도 변경이 차단된다.
- ExamAttemptService의 기존 검사로 EXPIRED 이후 시험 시작, 답안 저장, 제출이 차단된다.
- 해당 서비스에 dueDate 검사를 복사하지 않았으며 조회 API와 Security 설정도 변경하지 않았다.
- AutoAssignmentService는 기존 `findExistingForAssignment(memberId, courseId)`에서 상태와 무관하게 기존 수강을 인정한다.
- EXPIRED 수강이 있으면 회원 변경·과정 OPEN·규칙 재평가 트리거 모두 새 수강을 만들거나 상태/출처/규칙을 초기화하지 않는다.
- `UNIQUE(member_id, course_id)`는 그대로 유지한다. 재교육은 새로운 Course로 배정한다.

## 테스트 및 실행 결과

추가/보강 테스트:

- `EnrollmentExpirationServiceTest`: 24건. 날짜 4개 경계, 서울 자정 직전/직후, Clock 시간대 독립성, 전체 수강 상태, Course 상태 독립성, 시각 보존, catch-up, 반복 실행, 후보 재검증, 누락 행 건너뛰기, ID 기반 다음 묶음 조회, flush/commit 충돌, 예상 밖 오류 전파.
- `EnrollmentExpirationSchedulerTest`: 2건. 서비스 단일 위임, cron/zone 및 EnableScheduling 설정.
- `AutoAssignmentServiceTest`: 기존 21건에 수동/자동 출처별 EXPIRED 이력 보존 2건 추가. 세 종류 자동배정 트리거 모두 확인.
- 기존 ContentProgressServiceTest / ExamAttemptServiceTest: EXPIRED 학습 및 시험 변경 차단 회귀 검증.
- 기존 EntityMappingTest: 실제 DB 연결 없이 전체 엔티티 MySQL DDL 및 새 Repository JPQL 경로/타입 검증.

Java 21에서 Maven `-B -ntp -Dtest=*Test test` 실행 결과:

- 전체 500건, 통과 497건, 실패 0건, 오류 0건, 건너뜀 3건, BUILD SUCCESS.
- 건너뜀 3건은 기존 Redis 연동 테스트의 실행 조건에 따른 것이다.
- 위 실행 당시 DB가 필요했던 `BeApplicationTests`는 `*Test` 선택 범위에서 제외했다. 현재는 외부 저장소를 대체한 부팅 스모크 테스트로 기본 `mvn test`에 포함되며 최신 결과는 STABILIZATION_REVIEW.md를 참고한다.
- 새 충돌 테스트는 실제 Spring 트랜잭션 프록시와 모의 TransactionManager/Repository를 사용한다. rollback 후 다음 건 시작과 commit 실패 시 성공 집계 제외를 검증한다.
- 실제 MySQL에서 두 트랜잭션을 병렬 실행하는 경쟁 테스트는 수행하지 않았다. 모의 테스트는 DB의 격리/잠금 동작 자체를 검증하지 않는다.
- 자정 대기를 위한 sleep은 사용하지 않았다. SQL 파일을 실행하지 않았다.

## 파일 변경 목록

생성:

- `src/main/java/com/be/enrollment/service/EnrollmentExpirationService.java`
- `src/main/java/com/be/enrollment/service/EnrollmentExpirationProcessor.java`
- `src/main/java/com/be/enrollment/scheduler/EnrollmentExpirationScheduler.java`
- `src/main/java/com/be/global/config/SchedulingConfig.java`
- `src/test/java/com/be/enrollment/EnrollmentExpirationServiceTest.java`
- `src/test/java/com/be/enrollment/EnrollmentExpirationSchedulerTest.java`
- `ENROLLMENT_EXPIRATION.md`

수정:

- `src/main/java/com/be/enrollment/entity/Enrollment.java`: expireIfOverdue 메서드만 추가. 필드/매핑 불변.
- `src/main/java/com/be/enrollment/repository/EnrollmentRepository.java`: readOnly 후보 ID 조회 추가.
- `src/test/java/com/be/assignment/AutoAssignmentServiceTest.java`: EXPIRED 자동배정 이력 회귀 테스트 추가.
- `EXAM_ATTEMPT_API.md`: 자동 만료 구현 현황과 운영 문서 연결 갱신.

DB Schema, Enum, Entity 필드, 기존 Service/DTO, ErrorCode, API, Security 및 의존성은 변경하지 않았다.
기존 설계와 충돌하는 규칙 변경은 없으며 요청 시점 강제 만료가 아니라는 운영 경계는 위에 명시했다.
