# 학습 진도 API

> 2026-09-20 업데이트: 공통 수료 판정은 이제 Exam이 있어도 제출된 합격 Attempt가 있으면
> 콘텐츠 조건과 함께 COMPLETED를 판정한다. 시험 먼저/콘텐츠 먼저 모두 지원한다.
> 현재 시험 정책은 EXAM_ATTEMPT_API.md, 자동 만료는 ENROLLMENT_EXPIRATION.md를 참고한다.

## 범위

직원 본인의 콘텐츠 진도 기록, 학습 시작, 필수 콘텐츠 평균 계산, 시험 없는 과정의 수료를 구현한다.
시험 응시·제출·채점·실패 및 만료 배치는 별도 서비스에 구현되어 있다.
영상/문서 추적, 알림, 프론트엔드는 이 API에 포함하지 않는다.
기존 15개 테이블과 Entity 필드·매핑·인덱스·CHECK·@Version은 변경하지 않았다.

## API 및 권한

| Method | Endpoint | 권한 | 성공 |
|---|---|---|---|
| GET | /api/enrollments/{enrollmentId}/progress | 본인 EMPLOYEE 또는 ADMIN | 200 |
| PATCH | /api/enrollments/{enrollmentId}/contents/{contentId}/progress | 본인 EMPLOYEE | 200 |

PATCH 입력:

```json
{"progressRate": 35.25}
```

두 API는 동일한 EnrollmentProgressResponse 구조를 반환한다.

```json
{
  "enrollmentId": 10,
  "courseId": 3,
  "status": "IN_PROGRESS",
  "progressRate": 62.50,
  "passingProgressRate": 80.00,
  "contentConditionSatisfied": false,
  "startedAt": "2026-09-19T09:00:00",
  "completedAt": null,
  "contents": [
    {"contentId": 1, "title": "보안 영상", "contentType": "VIDEO", "required": true,
      "sortOrder": 1, "progressRate": 100.00, "completedAt": "2026-09-19T09:30:00"},
    {"contentId": 2, "title": "보안 가이드", "contentType": "DOCUMENT", "required": true,
      "sortOrder": 2, "progressRate": 25.00, "completedAt": null}
  ]
}
```

시간은 기존 DTO와 같이 UTC LocalDateTime의 ISO 문자열이다. 클라이언트 시간대를 적용한 시간이 아니다.
회원 식별자는 요청 파라미터가 아닌 SecurityContext의 MemberPrincipal.memberId에서만 가져온다.
Service에서도 Role 및 Enrollment.member.id를 검증한다.
ADMIN+EMPLOYEE는 자신의 진도만 수정할 수 있으며 다른 직원 수정은 403이다.
INSTRUCTOR 단독 역할에 별도 학생 진도 접근 권한은 없다.
Security의 진도 경로 matcher는 기존 ADMIN 전용 수강 경로보다 먼저 선언한다.

## 저장과 상태 전이

- (enrollmentId, courseContentId)로 찾고 없으면 생성, 있으면 동일 row 갱신한다.
- 기존 UNIQUE(enrollment_id, course_content_id)를 최종 방어선으로 유지한다.
- 콘텐츠는 findByIdAndCourseId로 수강 과정 소속까지 확인한다.
- 다른 과정 콘텐츠도 기존 scoped 조회 정책대로 COURSE_CONTENT_NOT_FOUND / 404다.
- BigDecimal / DECIMAL(5,2)를 유지한다. null·음수·100 초과·소수 셋째 자리 입력은 400이다.
- 진행 중에는 진도 감소를 허용한다. 100 미만이면 콘텐츠 completedAt은 null이다.
- 최초 100 도달 시 서버 Clock의 UTC 시각을 기록한다. 100 → 100은 기존 시각을 유지한다.
- 100 → 100 미만 → 100이면 다시 도달한 시각을 기록한다.
- ASSIGNED + 양수 진도 → IN_PROGRESS, 최초 startedAt 기록.
- 0만 저장하면 ASSIGNED 유지. 이미 시작한 수강을 0으로 낮춰도 IN_PROGRESS와 startedAt을 유지한다.
- COMPLETED / FAILED / EXPIRED에서는 신규 생성과 기존 진도 수정 모두 409로 거부한다.
- 진도 변경과 수강 시작·완료는 하나의 @Transactional에서 처리한다.
- version 기본값 0L 때문에 신규 엔티티가 merge될 수 있으므로 저장 후 전체 진도를 다시 조회하여 관리 상태 값으로 응답한다.
- 조회는 readOnly 트랜잭션이며 누락된 진도를 DB에 생성하지 않는다.

## 평균과 수료

전체 콘텐츠를 sortOrder 순서로 조회하고, 해당 수강 진도를 일괄 조회하여 합친다.
row가 없는 콘텐츠도 응답에 progressRate=0.00, completedAt=null로 포함한다.
콘텐츠별 추가 조회를 반복하지 않는다.

```text
필수 평균 = 필수 콘텐츠 진도 합계 / 필수 콘텐츠 개수
정확한 충족 판정 = 필수 진도 합계 >= passingProgressRate × 필수 콘텐츠 개수
```

- OPTIONAL 콘텐츠는 응답에 표시하지만 합계와 분모에서는 제외한다.
- 응답 평균만 소수 둘째 자리까지 내림한다. 판정에는 반올림/나눗셈을 사용하지 않는다.
- 필수 콘텐츠 0개는 조건 충족으로 취급하며 평균 응답은 100.00이다.
- EnrollmentCompletionService가 계산 및 수료 판정을 담당한다.
- IN_PROGRESS이고 콘텐츠 조건 충족 + Exam 없음이면 COMPLETED 및 completedAt을 기록한다.
- Exam이 있으면 콘텐츠 조건과 제출된 합격 Attempt가 모두 충족될 때 COMPLETED로 전환한다.
- 시험 먼저 합격한 뒤 콘텐츠를 완료해도 동일하게 수료한다.
- 시험 제출과 진도 변경은 같은 EnrollmentCompletionService를 재사용한다.
- evaluate는 MANDATORY 트랜잭션이며 호출자는 동일 수강의 버전을 잠그고 진도 요약을 계산한다.

### 충돌하거나 애매한 규칙의 해석

필수 콘텐츠 0개 또는 passingProgressRate=0이라도 0% 요청만으로 ASSIGNED를 바로 COMPLETED로 전환하지 않는다.
이는 기존 ASSIGNED → IN_PROGRESS → COMPLETED 및 startedAt CHECK를 보존하기 위함이다.
양수 진도가 들어오면 시작과 수료가 같은 트랜잭션에서 일어날 수 있다.
콘텐츠 자체가 없는 과정은 진도 API만으로 시작·수료시키지 않는다. Exam-only 과정은 시험 시작·합격으로 수료한다.
배정 생성이나 GET은 완료 판정 트리거가 아니다.

dueDate 당일까지 학습 가능, 다음 날 서울 00:00부터 만료 대상이라는 기존 정의를 유지한다.
진도 요청에서 날짜 경과만으로 즉시 만료시키지는 않는다. 서울 자정 스케줄러가 상태를 EXPIRED로 저장한 뒤 차단한다.
Course 상태/기간에 따른 별도 학습 차단도 추가하지 않았다.
관리자가 콘텐츠나 기준을 바꿔도 이미 COMPLETED인 수강을 재개하거나 자동 재평가하지 않는다.
수료 전 조회·판정에는 현재 과정 콘텐츠와 기준을 사용하며 이력 스냅샷 필드는 추가하지 않았다.

## 동시성

기존 ContentProgress와 Enrollment의 @Version을 유지한다.
쓰기용 수강 조회에 OPTIMISTIC_FORCE_INCREMENT를 사용하여 수강 상태가 변하지 않는 진도 요청도 버전을 증가시킨다.
서로 다른 콘텐츠를 동시에 갱신하여 각각 오래된 합계로 수료를 놓치는 경우도 동일 수강 버전 충돌로 감지한다.
버전 증가/충돌은 커밋 단계에 발생할 수도 있으며, 실패 시 진도와 수강 변경 전체를 롤백한다.
낙관적 잠금은 클라이언트의 오래된 순차 요청 자체를 거절하는 ETag/If-Match 기능은 아니다.

동시 최초 생성은 UNIQUE 충돌이 먼저 발생할 수도 있다. 해당 요청은 409로 전체 롤백한다.
실패 트랜잭션 안에서 재조회·덮어쓰기·자동 재시도하지 않는다. 클라이언트는 최신 상태를 조회한 뒤 재시도할 수 있다.
분산 락과 새 비관적 잠금은 도입하지 않았다.

잠금 의미 참고: [Hibernate 공식 안내](https://docs.hibernate.org/orm/7.2/introduction/pdf/Hibernate_Introduction.pdf).

## 오류

기존 {code, message, errors} 구조를 유지한다.

| HTTP | code | 설명 |
|---|---|---|
| 400 | VALIDATION_ERROR | @Valid 입력/정밀도 검증 실패 |
| 400 | INVALID_PROGRESS_RATE | Entity 진도 값 방어 검증 실패, 신규 ErrorCode |
| 401 | UNAUTHORIZED | 인증 없음/실패 |
| 403 | FORBIDDEN | 보안 체인 역할 제한 |
| 403 | ENROLLMENT_PROGRESS_ACCESS_DENIED | 소유권/Service 권한 위반, 신규 ErrorCode |
| 404 | ENROLLMENT_NOT_FOUND | 수강 없음 |
| 404 | COURSE_CONTENT_NOT_FOUND | 해당 과정에 콘텐츠 없음 |
| 409 | ENROLLMENT_PROGRESS_NOT_EDITABLE | 최종 상태 진도 변경, 신규 ErrorCode |
| 409 | CONCURRENT_MODIFICATION | 기존 낙관적 잠금 등 동시성 오류 응답 |
| 409 | DATA_CONFLICT | 동시 신규 진도 생성의 UNIQUE 등 무결성 충돌 |

## 파일 목록

생성 (프로덕션 8개, 테스트 4개, 문서 1개):

- src/main/java/com/be/enrollment/controller/ContentProgressController.java
- src/main/java/com/be/enrollment/dto/ContentProgressUpdateRequest.java
- src/main/java/com/be/enrollment/dto/ContentProgressResponse.java
- src/main/java/com/be/enrollment/dto/EnrollmentProgressResponse.java
- src/main/java/com/be/enrollment/repository/ContentProgressRepository.java
- src/main/java/com/be/enrollment/service/ContentProgressService.java
- src/main/java/com/be/enrollment/service/EnrollmentCompletionService.java
- src/main/java/com/be/exam/repository/ExamRepository.java
- src/test/java/com/be/enrollment/ProgressFixtures.java
- src/test/java/com/be/enrollment/ContentProgressServiceTest.java
- src/test/java/com/be/enrollment/EnrollmentCompletionServiceTest.java
- src/test/java/com/be/enrollment/ContentProgressApiTest.java
- PROGRESS_API.md

수정:

- src/main/java/com/be/enrollment/entity/ContentProgress.java: 생성 및 진도/완료 시각 갱신 메서드만 추가
- src/main/java/com/be/enrollment/entity/Enrollment.java: 수정 가능 상태 검사, 시작·수료 메서드만 추가
- src/main/java/com/be/enrollment/repository/EnrollmentRepository.java: 버전 증가 잠금 조회 추가
- src/main/java/com/be/global/config/SecurityConfig.java: 진도 GET/PATCH 권한 matcher 추가
- src/main/java/com/be/global/exception/ErrorCode.java: 업무 오류 3개 추가
- src/main/java/com/be/global/exception/GlobalExceptionHandler.java: 신규 오류의 HTTP 매핑 추가
- src/test/java/com/be/validation/ServiceValidationTest.java: 진도 Service 입력 검증 추가

기존 조직/회원/교육/배정 Service와 DTO는 변경하지 않았다. Entity 필드 추가·삭제 및 DB 변경도 없다.

## 테스트 결과

2026-09-19, Java 21 / Maven 3.9.16, `mvn -B -ntp -Dtest=*Test test`:

- 총 365개: 성공 362개 / 실패 0 / 오류 0 / 건너뜀 3. BUILD SUCCESS.
- ContentProgressServiceTest 23개: 생성/수정/동일 row, 0·100·범위·정밀도, 완료 시각 보존/초기화,
  시작 시각, terminal 차단, 소속 검증, 소유권, 누락 콘텐츠, 선택 콘텐츠, 시험 유무, 마감 경과,
  낙관적 충돌/무결성 충돌 전파.
- EnrollmentCompletionServiceTest 7개: 필수 평균, 정확한 기준 경계, 나눗셈 오차 방지,
  필수 0개, ASSIGNED 직접 완료 금지, 최초 완료 시각 보존.
- ContentProgressApiTest 18개: 본인 조회/수정, 타인 차단, ADMIN 조회 전용, 복수 역할,
  INSTRUCTOR 제한, 401/400/404/409, 서버 완료 시각 관리.
- ServiceValidationTest에 진도 검증 1개 추가 (총 8개). 이번 추가 검증은 총 49개.
- 기존 전체 Entity 매핑/MySQL DDL 생성/Repository JPQL 검증도 통과했다.
- Redis 통합 테스트 3개는 기존 활성화 설정이 없어 건너뛰었다.
- 위 실행 당시 BeApplicationTests는 `*Test` 선택 범위에서 제외했다. 현재는 외부 저장소를 대체한 부팅 스모크 테스트로 기본 `mvn test`에 포함되며 최신 결과는 STABILIZATION_REVIEW.md를 참고한다.
- 실제 MySQL 동시 트랜잭션의 @Version/UNIQUE 경합 및 롤백은 검증하지 않았다.
  Mockito 예외 전파/API 응답 테스트를 실제 DB 동시성 검증으로 간주하지 않는다.
