# 교육 배정 규칙 / Enrollment API

## 범위 및 기존 스키마

이번 단계는 규칙 관리와 ASSIGNED Enrollment 생성·조회까지만 제공한다.
학습 시작·진도·시험·완료·만료 스케줄러·자율 수강·배정 삭제·재수강 초기화는 구현하지 않는다.

기존 AssignmentRule/Enrollment/Member/Department/JobPosition/Course 엔티티와 SQL을 확인했으며
필드·nullable·FK·UNIQUE·CHECK·Enum·@Version·DB 스키마는 변경하지 않았다.

- AssignmentRule.newEmployeeDays는 Short/SMALLINT이므로 1~32767일만 허용한다.
- Enrollment.dueDate는 필수이며 배정 당시 Course.endDate를 복사한다.
- Enrollment에는 기존 @Version Long version=0L이 있다. 저장 응답은 saveAndFlush 반환 엔티티를 사용한다.
  [Spring Data JPA의 버전 기반 신규 엔티티 판정](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html)에 따라
  merge가 반환한 관리 엔티티와 입력 객체가 다를 수 있기 때문이다.
- AssignmentRule에는 create/updateTarget/changeActive, Enrollment에는 manual/automatic 생성 메서드만 추가했다.
- Enrollment의 기존 출처·규칙·상태·마감일을 변경하는 메서드나 API는 추가하지 않았다.

## 권한

| 경로 | 권한 |
|---|---|
| /api/courses/{courseId}/assignment-rules 및 하위 경로 | ADMIN |
| /api/courses/{courseId}/enrollments | ADMIN |
| GET /api/enrollments/{enrollmentId} | ADMIN |
| GET /api/enrollments/me | EMPLOYEE |

모든 실제 Member는 EMPLOYEE 기본 역할을 보유하므로 ADMIN/INSTRUCTOR도 자신의 목록을 볼 수 있다.
INSTRUCTOR만으로는 규칙·수강생 목록·관리 상세에 접근할 수 없다.
관리 하위 경로를 기존 Course GET의 INSTRUCTOR 허용 규칙보다 먼저 검사한다.
기존 Course/콘텐츠 조회 권한과 JWT 정책은 유지한다.
본인 목록의 memberId는 인증된 MemberPrincipal에서만 가져오며 쿼리 파라미터로 변경할 수 없다.

## AssignmentRule API

기본 경로: `/api/courses/{courseId}/assignment-rules`

| Method | 추가 경로 | 기능 | 성공 |
|---|---|---|---|
| POST | 없음 | 규칙 생성 | 201 + Location |
| GET | 없음 | ID 오름차순 전체 목록 | 200 |
| GET | /{ruleId} | 단건 조회 | 200 |
| PATCH | /{ruleId} | 조건 전체 교체 | 200 |
| PATCH | /{ruleId}/status | 활성 여부 변경 | 200 |

생성 예:

```json
{
  "ruleType": "NEW_EMPLOYEE",
  "departmentId": null,
  "jobPositionId": null,
  "newEmployeeDays": 90,
  "active": true
}
```

생성 시 active 생략/null은 true다. false이면 규칙만 저장한다.
조건 PATCH는 ruleType 필수이며 조건 집합 전체를 교체한다. 생략된 nullable 대상 필드는 null로 처리한다.
조건 PATCH로 활성 여부를 변경하지 않으며 별도 status API를 사용한다.

```json
{"active":false}
```

규칙은 DRAFT/OPEN/CLOSED 과정에서 구성할 수 있지만 신규 배정은 OPEN에서만 수행한다.
courseId + ruleId로 소속을 검증하며 다른 과정의 규칙도 없는 규칙과 동일하게 404다.
규칙 DELETE API는 없다.

## 규칙별 조건 및 대상

모든 자동 대상은 ACTIVE 회원이어야 한다. ON_LEAVE/RESIGNED는 제외한다.

| 유형 | 필수 조건 | 반드시 null | 대상 |
|---|---|---|---|
| ALL_EMPLOYEES | 없음 | 부서·직무·신입 기간 | ACTIVE 전체 |
| DEPARTMENT | departmentId | 직무·신입 기간 | 해당 부서에 직접 소속 |
| JOB_POSITION | jobPositionId | 부서·신입 기간 | 해당 직무가 정확히 일치 |
| NEW_EMPLOYEE | newEmployeeDays > 0 | 부서·직무 | 0 ≤ 경과일수 < 기간 |

DEPARTMENT는 자식 부서를 포함하지 않는다.
대상 부서/직무는 생성·조건 수정·활성화 시 존재 및 활성 여부를 검사한다.
비활성화 요청은 대상 조직의 현재 활성 여부와 관계없이 허용한다.

NEW_EMPLOYEE의 오늘은 주입한 Clock을 Asia/Seoul로 변환해 트리거당 한 번 계산한다.
기간이 90이면 입사일 범위는 오늘-89일 ≤ hireDate ≤ 오늘이다.
입사 당일·89일째 포함, 정확히 90일째·미래 입사일 제외다.
assignedAt은 기존 설계대로 UTC LocalDateTime이다.

## 자동 배정 트리거

자동 판단은 AutoAssignmentService 한 곳에서 수행한다.

| 호출 지점 | 평가 범위 |
|---|---|
| 활성 Rule 생성 | 해당 규칙의 현재 대상자 |
| Rule 활성화(true 재요청 포함) | 해당 규칙의 현재 대상자 |
| 활성 Rule 조건 수정 | 변경된 조건의 현재 대상자 |
| Course DRAFT → OPEN | 해당 과정의 활성 규칙 전체 |
| Member 생성 | 해당 직원과 OPEN 과정의 활성 규칙 |
| Member 부서 변경 요청 | 동일 |
| Member 직무 변경 요청 | 동일 |
| Member hireDate 실제 변경(update/patch) | 동일 |

부서/직무에 같은 값을 다시 요청해도 재평가는 멱등적으로 동작한다.
이메일/이름만 수정하거나 상태·역할만 변경하면 자동 배정을 호출하지 않는다.
특히 ON_LEAVE → ACTIVE 복귀 자체를 새 트리거로 추가하지 않았다.

CourseService의 기존 OPEN 검증·상태 전이는 유지하고 flush 성공 뒤 평가한다.
MemberService도 기존 검증·저장/flush 뒤 평가하며 내부에 규칙 판단 코드를 복제하지 않는다.
RuleService는 저장/수정/활성화 후 평가한다. 비활성 규칙은 호출하지 않는다.

여러 활성 규칙을 한 번에 평가하면 ID 오름차순으로 처리한다.
한 직원이 여러 규칙에 일치해도 가장 먼저 생성된 Enrollment 하나만 남는다.
이미 있던 MANUAL/AUTOMATIC 또는 완료/실패/만료 배정도 모두 그대로 유지한다.

## Enrollment API 및 생성 규칙

| Method | 경로 | 기능 | 성공 |
|---|---|---|---|
| POST | /api/courses/{courseId}/enrollments | 수동 배정 | 201 + Location |
| GET | /api/courses/{courseId}/enrollments | 과정별 목록 | 200 |
| GET | /api/enrollments/{enrollmentId} | 관리 상세 | 200 |
| GET | /api/enrollments/me | 본인 목록 | 200 |

수동 요청은 대상 회원만 전달한다:

```json
{"memberId":15}
```

| 항목 | 수동 | 자동 |
|---|---|---|
| Course | OPEN만 | OPEN만 |
| Member | ACTIVE/ON_LEAVE | ACTIVE만 |
| 최초 상태 | ASSIGNED | ASSIGNED |
| assignmentSource | MANUAL | AUTOMATIC |
| assignmentRule | null | 최초 배정 규칙 필수 |
| 기존 배정 존재 | DUPLICATE_ENROLLMENT 409 | 변경 없이 skip |
| dueDate | 배정 당시 Course.endDate | 동일 |

startedAt/completedAt은 null이다. dueDate가 지나도 이번 단계에서는 상태를 바꾸지 않는다.
Course 종료일이 나중에 바뀌어도 기존 Enrollment의 dueDate는 바뀌지 않는다.
OPEN이지만 달력상 기간이 지나거나 아직 시작 전인 과정의 배정을 별도로 막는 정책은 추가하지 않았다.

두 목록 API는 page(기본 0), size(기본 20, 최대 100), status(선택)를 지원하며 ID 오름차순이다.
기존 PageResponse의 content/page/size/totalElements/totalPages를 사용한다.
각 응답은 enrollmentId/memberId/memberName/courseId/courseTitle/status/assignmentSource/
assignmentRuleId/assignedAt/startedAt/completedAt/dueDate를 포함한다.
MANUAL 응답의 assignmentRuleId=null도 정상 반환한다.
학습 시작/완료·배정 삭제 API는 제공하지 않는다.

## 멱등성·동시성·트랜잭션

- 수동/자동 배정과 규칙 변경은 부모 Course에 PESSIMISTIC_WRITE 잠금을 사용한다.
- 회원 변경 평가도 OPEN 과정을 ID 순서로 잠근다. 현재 MVP에서는 평가 중 이 과정 잠금을 유지한다.
- 대량 평가 대상 회원은 ACTIVE/직속 소속/입사일 범위로 현재 읽기 및 잠금 조회한다.
  같은 영속성 컨텍스트에 강사로 미리 로드된 회원도 refresh 후 최신 상태를 판단한다.
- 중복 Enrollment 확인도 잠금 현재 읽기를 사용한다.
  이는 MySQL 기본 REPEATABLE READ에서 대기 이전 스냅샷으로 중복을 놓치지 않기 위함이다.
  [MySQL 잠금 읽기 문서](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html).
- DB UNIQUE(member_id, course_id)는 최종 보호장치로 유지한다.
- 중복을 발견한 자동 배정은 기존 값을 변경하지 않고 반환한다.
  실제 UNIQUE 오류를 catch해서 성공으로 위장하지 않으며 전체 트랜잭션 실패로 전파한다.
- Member 변경/Course OPEN/Rule 변경과 자동 배정은 동기 호출 및 같은 REQUIRED 트랜잭션이다.
  REQUIRES_NEW, 비동기 이벤트, DB Trigger/Stored Procedure를 추가하지 않았다.
- 기존 회원 변경은 Member부터 잠그고, 과정 전체 배정은 Course부터 잠그므로 동시 요청 시
  교착/잠금 충돌 가능성은 남는다. Spring의 ConcurrencyFailureException은
  CONCURRENT_MODIFICATION(409)로 반환하고 전체 요청을 롤백한다. 클라이언트는 다시 시도할 수 있다.
  실패 트랜잭션 내부에서 재시도하거나 일부만 저장하지 않는다.
  [MySQL 교착 처리 문서](https://dev.mysql.com/doc/refman/8.0/en/innodb-deadlocks-handling.html).
- 현재 방식은 MVP의 단순성과 일관성을 우선한다. 대량 직원 환경에서는 동기 평가 시간 및 잠금 경합을
  측정한 뒤 배치 처리나 별도 재처리 설계를 검토해야 한다. 이번 단계에는 추가하지 않았다.

## 비활성화 및 애매한 정책의 처리

- 규칙 비활성화는 isActive=false만 변경하고 과거 배정은 유지한다.
- 재활성화하면 현재 조건을 다시 평가하여 미배정 직원만 추가한다.
- 부서/직무 이동, 신입 기간 초과, 휴직/퇴사로 조건을 벗어나도 기존 배정은 삭제·취소하지 않는다.
- 조직 자체가 나중에 비활성화되어도 기존 활성 규칙을 자동 비활성화하지 않는다.
  기존 규칙 평가는 현재 정의된 소속 조건을 따른다. 신규 생성/조건 수정/재활성화 때는 활성 조직만 허용한다.
- 규칙 조건을 수정해도 Enrollment.assignmentRuleId는 그대로다. 현재 스키마에는 규칙 조건의
  과거 버전 스냅샷이 없으므로 과거 조건까지 보존하는 기능은 임의로 추가하지 않았다.
- 미래 입사일이 도래하는 것 자체나 하루 경과는 트리거가 아니다. 날짜 기반 주기 실행을 추가하지 않았다.
- 모든 신규 수강 상태는 ASSIGNED이며 기존 상태 전이·수료·만료 처리는 이번 범위 밖이다.

## ErrorCode 및 HTTP

추가 업무 코드:

| Code | HTTP |
|---|---|
| ASSIGNMENT_RULE_NOT_FOUND | 404 |
| ENROLLMENT_NOT_FOUND | 404 |
| INVALID_ASSIGNMENT_RULE_TARGET | 400 |
| INVALID_NEW_EMPLOYEE_DAYS | 400 |
| DUPLICATE_ENROLLMENT | 409 |
| COURSE_NOT_OPEN_FOR_ASSIGNMENT | 409 |
| RESIGNED_MEMBER_ASSIGNMENT | 409 |

COURSE/MEMBER/DEPARTMENT/JOB_POSITION_NOT_FOUND(404), INACTIVE_DEPARTMENT/INACTIVE_JOB_POSITION(409)는 재사용한다.
DTO 검증 실패는 기존 VALIDATION_ERROR/INVALID_REQUEST(400)다.
DB 잠금 경합용 공통 응답 code=CONCURRENT_MODIFICATION(409)도 추가했다.
모든 오류는 기존 code/message/errors JSON 형식을 유지한다.

## 생성/수정 파일

생성:

- assignment/controller/AssignmentRuleController
- assignment/dto/AssignmentRuleCreateRequest, AssignmentRuleUpdateRequest, AssignmentRuleStatusRequest, AssignmentRuleResponse
- assignment/repository/AssignmentRuleRepository
- assignment/service/AssignmentRuleService, AutoAssignmentService
- enrollment/controller/EnrollmentController
- enrollment/dto/ManualEnrollmentRequest, EnrollmentSearchRequest, EnrollmentResponse
- enrollment/repository/EnrollmentRepository
- enrollment/service/EnrollmentService
- 테스트 assignment/AssignmentFixtures, AssignmentRuleServiceTest, AutoAssignmentServiceTest,
  EnrollmentServiceTest, AssignmentTriggerTest, AssignmentApiTest, AssignmentWorkflowTest
- 문서 ASSIGNMENT_API.md

수정:

- AssignmentRule, Enrollment: 필드 변경 없이 최소 생성/조건·활성 변경 메서드 추가
- MemberRepository, CourseRepository: 대상자 및 현재 상태 잠금 조회 추가
- MemberService, CourseService: 자동 배정 호출 의존성 및 확정된 트리거 연결
- SecurityConfig: 관리 하위 경로 우선 제한 및 /me 허용
- ErrorCode, GlobalExceptionHandler, UniqueConstraintErrors: 업무/동시성 오류 매핑
- MemberServiceTest, CourseServiceTest, ApiServiceExtensionTest: 새 의존성 및 트리거 검증
- ServiceValidationTest: 새 Service 입력 경계 검증
- UniqueConstraintErrorsTest: Enrollment UNIQUE 코드 변환
- EntityMappingTest: 새 JPQL 파싱 검증

## 테스트

실행: `mvn -Dtest=*Test test`.

- 규칙 유형·조합·기간·활성 조직·과정 소속·비활성화/재활성화
- ACTIVE 전용 자동 배정, 직속 부서/직무, 서울 날짜 기반 신입 경계
- DRAFT/OPEN/CLOSED 처리, 반복/겹치는 규칙의 중복 방지 및 기존 MANUAL 보존
- 수동 배정의 ACTIVE/ON_LEAVE 허용·RESIGNED 거부, 상태/출처/마감일/UTC 시각
- 실제 Service 연결의 초안 규칙 → OPEN → 비활성화 → 재활성화 흐름
- 기존 Member/Course 트리거 호출 시점 및 상태·역할 변경 트리거 미추가
- ADMIN/EMPLOYEE/INSTRUCTOR/미인증 접근 정책, 본인 조회 ID 조작 방어
- DTO/Service Validation, HTTP 오류, MySQL DDL 생성 및 JPQL 파싱

DB는 테스트에서 대체하며, 실제 MySQL SQL 실행·잠금 경합·DB 롤백 검증은 별도 환경이 필요하다.
BeApplicationTests 전체 앱 기동은 포함하지 않는다. 기존 Redis 통합 테스트는 활성화 옵션이 없으면 건너뛴다.
