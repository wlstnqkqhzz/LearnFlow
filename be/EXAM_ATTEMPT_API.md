# 시험 응시·자동 채점 API

## 범위와 기본 정책

직원 본인의 시험 시작, 문제 조회, 답안 저장, 제출·채점, 결과·이력 조회를 구현한다.
Entity 필드, DB 컬럼, UNIQUE/FK/CHECK는 변경하지 않았다.
시간 제한, 자동 제출, 랜덤 출제, 부분점수, 해설, 재교육 초기화는 제공하지 않는다.
수강 자동 만료는 [수강 마감 기한 자동 만료](ENROLLMENT_EXPIRATION.md) 문서의 별도 내부 스케줄러로 처리한다.

## API

| Method | Endpoint | 성공 | 접근 |
|---|---|---|---|
| POST | /api/enrollments/{enrollmentId}/exam-attempts | 신규 201 + Location / 미제출 재사용 200 | 본인 EMPLOYEE |
| GET | /api/exam-attempts/{attemptId} | 200 | 본인 EMPLOYEE |
| PUT | /api/exam-attempts/{attemptId}/answers/{questionId} | 200 | 본인 EMPLOYEE |
| POST | /api/exam-attempts/{attemptId}/submit | 200 | 본인 EMPLOYEE |
| GET | /api/exam-attempts/{attemptId}/result | 200 | 본인 EMPLOYEE 또는 ADMIN |
| GET | /api/enrollments/{enrollmentId}/exam-attempts | 200 | 본인 EMPLOYEE 또는 ADMIN |

인증은 기존 JWT를 사용한다. 요청에 memberId를 받지 않고 MemberPrincipal의 ID/역할을 Service에서 검증한다.
ADMIN+EMPLOYEE도 다른 직원 대신 시작·답안 저장·제출할 수 없다.
ADMIN 단독 역할은 이력·결과 조회만 허용한다. INSTRUCTOR 단독에는 별도 학생 ACL이 없다.
직원은 기존 관리자용 시험 API에 접근할 수 없다.

## 시험 시작

본문 없는 POST다. Course/Enrollment를 잠근 뒤 소유권, 상태, 과정 시험 존재, 구성 유효성을 검사한다.
ASSIGNED/IN_PROGRESS만 허용한다. 시작 시 ASSIGNED → IN_PROGRESS, 최초 startedAt은 UTC Clock으로 기록한다.
기존 시작 시각은 보존한다. 콘텐츠 진도는 시작 조건이 아니다.

이미 제출된 합격 Attempt가 하나라도 있으면 409 EXAM_ALREADY_PASSED로 거절한다.
미제출 Attempt가 있으면 그 Attempt를 그대로 반환하여 네트워크 재요청으로 횟수를 소비하지 않는다.
없으면 최대 attemptNumber + 1로 생성한다. 처음은 1이며 maxAttempts 초과를 차단한다.
기존 UNIQUE(enrollment_id, exam_id, attempt_number)는 최종 방어선이다.
제출된 실패 횟수만큼만 재응시할 수 있으며, 미제출 시도가 있는 동안 새 시도는 만들지 않는다.

```json
{
  "attemptId":30,"attemptNumber":1,"score":null,"passed":null,
  "startedAt":"2026-09-20T07:00:00","submittedAt":null,
  "maxAttempts":3,"remainingAttempts":2
}
```

remainingAttempts는 maxAttempts - 생성된 Attempt 수이며 미제출 시도도 이미 사용 중인 1회로 센다.
합격 후에도 숫자상 잔여 횟수가 표시될 수 있지만, 합격 여부에 의해 새 응시는 차단된다.
새 응시의 Location은 /api/exam-attempts/{attemptId}다.
시간은 기존 프로젝트처럼 UTC LocalDateTime ISO 문자열이다.

## 문제 조회와 정답 비노출

```json
{
  "attemptId":30,"title":"보안 시험",
  "questions":[{
    "questionId":1,"questionType":"SINGLE_CHOICE","questionText":"문제 내용",
    "score":10.00,"sortOrder":1,
    "choices":[
      {"choiceId":100,"choiceText":"A","sortOrder":1},
      {"choiceId":101,"choiceText":"B","sortOrder":2}
    ],
    "selectedChoiceIds":[101]
  }]
}
```

문항/선택지는 설정 순서대로 반환하고 selectedChoiceIds는 본인이 임시 저장한 값이다.
관리용 DTO를 사용하지 않는다. correct/isCorrect, 정답 ID/개수, earnedScore 등 채점 정보를 포함하지 않는다.
제출 이후에도 본인 문제/선택값을 조회할 수 있지만 정답은 공개하지 않는다.
문제 목록·선택지·저장 답안은 일괄 조회한다. 답안의 selectedChoices는 EntityGraph로 가져온다.

## 답안 저장

```json
{"selectedChoiceIds":[100,102]}
```

응답:

```json
{"questionId":1,"selectedChoiceIds":[100,102]}
```

- Question은 Attempt.exam 소속, Choice는 해당 Question 소속이어야 한다.
- ID 중복은 400이다. null/음수/0 ID도 입력 검증으로 거절한다.
- SINGLE_CHOICE와 TRUE_FALSE는 정확히 1개 선택이어야 한다.
- MULTIPLE_CHOICE는 빈 집합도 허용하여 저장한 선택을 비울 수 있다.
- SINGLE_CHOICE/TRUE_FALSE의 빈 목록은 400이다. 아직 답하지 않은 문제는 답안 row 없이 남길 수 있다.
- 동일 Attempt/Question이면 기존 ExamAnswer를 갱신하고 연결 테이블의 선택 집합을 현재 값으로 교체한다.
- 임시 저장 시 isCorrect/earnedScore는 null이다. 채점은 제출에서만 수행한다.
- 답안 DELETE API는 없다. submittedAt이 있으면 저장/재제출 모두 409다.
- COMPLETED/FAILED/EXPIRED 수강에서는 새 답안 생성 및 변경을 차단한다.

## 제출과 채점

본문 없는 POST다. 수강/응시 잠금, 본인 및 미제출/상태 확인 후 구성을 다시 검증한다.
모든 문항 및 선택지, 기존 답안과 선택 집합을 일괄 조회한다.

모든 문제 유형은 제출 Choice ID 집합이 정답 Choice ID 집합과 **정확히 같을 때만** 만점을 부여한다.
MULTIPLE_CHOICE는 순서를 무시하지만 정답 일부만 선택하거나 오답이 추가되면 0점이다.
정답이면 ExamAnswer.earnedScore = Question.score, 오답이면 0으로 저장한다.
isCorrect도 함께 확정한다. earnedScore는 정규화 점수가 아닌 해당 문항의 원점수다.
미응답 문제는 획득 0점이며 전체 배점 합계에 포함한다. 불필요한 답안 row는 만들지 않는다.

```text
ExamAttempt.score = 획득 원점수 합계 × 100 / 전체 문항 원점수 합계
소수 둘째 자리, RoundingMode.HALF_UP
passed = 정규화된 score >= Exam.passingScore
```

BigDecimal만 사용한다. 예: 원점수 10+20+30에서 10+20 획득 → 50.00점.
83.335는 83.34로 반올림한다. 79.99/80은 불합격, 80.00/80은 합격이다.
구성 검증으로 문항 1개 이상·양수 배점을 확인하고 채점기에서도 분모 0을 차단한다.

제출 응답/결과 응답은 AttemptResponse 요약이다. 문항별 정답·획득 점수·해설은 노출하지 않는다.
제출 전 result 요청은 409 EXAM_ATTEMPT_NOT_SUBMITTED다.
이력은 attemptNumber 오름차순이며 제출 전 score/passed/submittedAt은 null이다.

## 수강 상태 연동

- 합격하면 기존 EnrollmentCompletionService를 호출한다.
- 필수 콘텐츠 조건 + 제출된 합격 Attempt 존재 → COMPLETED 및 최초 completedAt 기록.
- 콘텐츠 미완료면 IN_PROGRESS 유지. 이미 시험 합격했으므로 새 응시는 거절한다.
- ContentProgress 변경도 같은 판정기를 사용하므로 시험 먼저 합격한 뒤 콘텐츠를 완료해도 수료한다.
- 필수 콘텐츠 0개는 조건 충족: Exam-only 과정도 시작 및 합격 수료가 가능하다.
- 시험 없는 과정의 기존 콘텐츠 기반 수료는 유지한다.
- 불합격이고 제출 횟수 < maxAttempts이면 IN_PROGRESS 유지.
- 불합격이고 제출 횟수 >= maxAttempts이며 과거 합격도 없으면 FAILED.
- FAILED는 startedAt을 보존하고 completedAt을 설정하지 않는다. 이후 진도/시험 활동은 차단한다.

채점·답안 갱신·Attempt 제출 확정·수강 후속 처리는 하나의 트랜잭션이다.
수료 판정 예외를 삼키지 않으며 중간 결과만 커밋하지 않는다.

## 동시성

응시 쓰기 메서드는 READ_COMMITTED 트랜잭션을 사용한다. 최초 경로 식별 조회가 만든 오래된
MySQL REPEATABLE READ 스냅샷을 잠금 대기 이후 재사용하지 않도록 범위를 제한한 설정이다.

- 시작: Course PESSIMISTIC_WRITE → Enrollment PESSIMISTIC_FORCE_INCREMENT.
- 저장/제출: Enrollment PESSIMISTIC_FORCE_INCREMENT → Attempt PESSIMISTIC_WRITE.
- 관리자 구성 변경은 기존 Course 잠금 아래 Attempt 존재를 검사한다.
- 동시 시작은 잠금 이후 최신 이력에서 미제출 Attempt를 재사용한다.
- 동시 답안 저장은 최신 row를 잠근 후 순차 처리하며, 제출과 경쟁할 때 제출 이후 저장은 실패한다.
- 동시 제출 중 첫 요청만 확정하고 후속 요청은 이미 제출됨 409를 반환한다.
- 수강 버전 증가로 기존 ContentProgress의 낙관적 잠금도 시험 변경을 감지한다.
  시험 합격과 콘텐츠 변경이 경쟁해 한쪽의 오래된 판단이 확정되지 않도록 한다.
- DB UNIQUE는 응시 번호/답안 중복 생성의 최종 방어선이다.
- 잠금/낙관적 충돌은 기존 CONCURRENT_MODIFICATION/409, 무결성 충돌은 DATA_CONFLICT/409.
- 실패 트랜잭션 내 재시도, 분산 락, 버전 강제 덮어쓰기는 사용하지 않는다.

행 잠금은 서버 트랜잭션을 직렬화하는 정책이다. 클라이언트의 순차적인 오래된 편집까지 감지하는
ETag/If-Match는 구현하지 않았다. 각 응답을 확인하고 충돌 시 최신 상태 조회 후 재시도해야 한다.

## 시험 구성 동결 및 기존 기능 변경 이유

첫 Attempt가 생성되면 시험 제목·passingScore·maxAttempts, 문항 내용·유형·배점·순서,
선택지 내용·정답·순서와 추가를 모두 EXAM_CONFIGURATION_LOCKED/409로 차단한다.
삭제는 기존 EXAM_HISTORY_DELETE_CONFLICT/409 정책으로 계속 차단한다.
문구도 문제 의미를 바꿀 수 있고 스냅샷이 없으므로, 채점 수치뿐 아니라 전체 구성을 동결했다.
첫 응시 전에는 Course가 OPEN/CLOSED여도 기존 관리 기능을 사용할 수 있다.

ExamService에 동결 검사를 추가하고 QuestionService의 각 수정 경로에서 공통으로 호출한다.
Exam/Question/QuestionChoice 컬럼이나 snapshot/version Entity를 새로 만들지 않았다.
기존 Course 수료 기준/콘텐츠 관리 정책 자체를 동결하는 기능은 이번 범위에 추가하지 않았다.
이미 COMPLETED인 수강은 시험 추가나 기준 변경으로 재개하지 않는다.
시험 요청 시점에 dueDate를 검사하여 즉시 만료시키지는 않는다.
별도 스케줄러가 매일 서울 자정에 dueDate가 지난 미종료 수강을 EXPIRED로 전환한다.
처리 지연이나 충돌로 상태 전환 전인 건은 기존 요청 정책을 따르며, EXPIRED 저장 후에는 terminal 검사로 차단된다.

## 신규 ErrorCode

| HTTP | code |
|---|---|
| 404 | EXAM_ATTEMPT_NOT_FOUND |
| 403 | EXAM_ACCESS_DENIED |
| 400 | INVALID_EXAM_ANSWER |
| 409 | EXAM_CONFIGURATION_LOCKED, ENROLLMENT_EXAM_NOT_EDITABLE, EXAM_ALREADY_PASSED, EXAM_ATTEMPTS_EXHAUSTED, EXAM_ATTEMPT_ALREADY_SUBMITTED, EXAM_ATTEMPT_NOT_SUBMITTED |

기존 리소스/Validation/인증/권한/동시성 오류와 {code,message,errors} JSON 형식을 재사용한다.

## 파일 목록

생성:

```text
src/main/java/com/be/exam/controller/ExamAttemptController.java
src/main/java/com/be/exam/dto/AnswerSaveRequest.java
src/main/java/com/be/exam/dto/AnswerResponse.java
src/main/java/com/be/exam/dto/AttemptResponse.java
src/main/java/com/be/exam/dto/AttemptPaperResponse.java
src/main/java/com/be/exam/service/ExamAttemptService.java
src/main/java/com/be/exam/service/ExamGradingService.java
src/test/java/com/be/exam/ExamAttemptServiceTest.java
src/test/java/com/be/exam/ExamGradingServiceTest.java
src/test/java/com/be/exam/ExamAttemptApiTest.java
EXAM_ATTEMPT_API.md
```

수정:

```text
src/main/java/com/be/enrollment/entity/Enrollment.java
src/main/java/com/be/enrollment/repository/EnrollmentRepository.java
src/main/java/com/be/enrollment/service/EnrollmentCompletionService.java
src/main/java/com/be/exam/entity/ExamAttempt.java
src/main/java/com/be/exam/entity/ExamAnswer.java
src/main/java/com/be/exam/repository/ExamAttemptRepository.java
src/main/java/com/be/exam/repository/ExamAnswerRepository.java
src/main/java/com/be/exam/service/ExamService.java
src/main/java/com/be/exam/service/QuestionService.java
src/main/java/com/be/global/config/SecurityConfig.java
src/main/java/com/be/global/exception/ErrorCode.java
src/main/java/com/be/global/exception/GlobalExceptionHandler.java
src/test/java/com/be/enrollment/ContentProgressApiTest.java
src/test/java/com/be/enrollment/ContentProgressServiceTest.java
src/test/java/com/be/enrollment/EnrollmentCompletionServiceTest.java
src/test/java/com/be/exam/ExamManagementServiceTest.java
src/test/java/com/be/mapping/EntityMappingTest.java
src/test/java/com/be/validation/ServiceValidationTest.java
EXAM_API.md
PROGRESS_API.md
```

Entity 변경은 ExamAttempt 시작·제출, ExamAnswer 생성·선택 교체·채점, Enrollment 실패 전이 메서드뿐이다.
기존 nullable 의미, @Version, 연결 테이블, 모든 DB 제약은 유지한다.
기존 테스트 수정은 Completion/ExamService 의존성 확장, 동결 검증, JPQL 매핑 검증을 위한 것이다.

## 테스트 결과

2026-09-20, Java 21 / Maven 3.9.16, `mvn -B -ntp -Dtest=*Test test`:

- 전체 472개: 성공 469, 실패 0, 오류 0, 건너뜀 3. BUILD SUCCESS.
- ExamAttemptServiceTest 22개: 시작/재사용/번호/횟수/합격 재응시 차단, 소유권, terminal,
  선택 구조·교체·미제출 null, 시험 전후 콘텐츠 수료, Exam-only, 불합격 소진,
  결과 불변, 실제 직원 DTO 직렬화, 잠금 예외 및 수료 오류 전파.
- ExamGradingServiceTest 9개: 세 유형 정오답, 복수 집합 순서/일부/추가 선택, 미응답 분모,
  원점수와 정규화 점수 구분, HALF_UP 반올림.
- ExamAttemptApiTest 9개: 시작 201/200, 문제/저장/제출/결과/이력, 비노출 응답,
  인증 주체 전달, ADMIN 조회 전용, INSTRUCTOR 제한, 401/400/403/404/409.
- 기존 ExamManagementServiceTest에 전체 구성 동결 검증 1개 추가. 신규 검증 총 41개.
- 기존 매핑 테스트에서 새 잠금 조회/응시 식별 JPQL도 검증했다.
- Redis 통합 테스트 3개는 활성화되지 않아 건너뜀. BeApplicationTests는 *Test 패턴에서 제외.
- 실제 MySQL 동시 요청, 잠금 순서, UNIQUE 경합, 커밋/롤백 통합 검증은 미실행이다.
  Mock 기반 순차 워크플로와 예외 전파 검증을 실제 DB 동시성 검증으로 간주하지 않는다.
