# 시험·문항·선택지 관리 API

> 2026-09-20 업데이트: 실제 응시·제출·채점은 EXAM_ATTEMPT_API.md에 구현되어 있다.
> 아래의 '미구현/후속 결정' 설명은 관리 기능 구현 당시의 기록이다.
> 현재는 첫 Attempt 이후 시험 제목·설정·문항·선택지·순서를 모두 동결한다.
> 기존 삭제 보호 정책은 그대로 유지한다. 현재 응시 정책은 새 문서를 우선한다.

## 구현 범위

ADMIN이 Course별 시험, 문항, 선택지를 구성하는 단계다. Course당 Exam은 0..1개다.
응시 시작, ExamAttempt/ExamAnswer 생성, 답안 저장·제출, 채점, 시험 결과,
Enrollment FAILED/시험 합격 수료 처리는 구현하지 않았다.
기존 DB 15개 테이블과 Entity 필드·관계·UNIQUE·FK RESTRICT는 변경하지 않았다.

## Endpoint

모든 경로는 ADMIN 전용이다. EMPLOYEE/INSTRUCTOR 단독 역할은 403, 미인증은 401이다.
INSTRUCTOR에게 허용된 기존 Course GET보다 시험 경로 matcher를 먼저 적용한다.
ADMIN과 다른 역할을 함께 가진 회원은 ADMIN 자격으로 사용할 수 있다.

| Method | 경로 | 성공 | 설명 |
|---|---|---|---|
| POST | /api/courses/{courseId}/exam | 201 + Location | 시험 생성 |
| GET | /api/courses/{courseId}/exam | 200 | 시험 설정 및 문항 수/배점 합계 |
| PATCH | /api/courses/{courseId}/exam | 200 | 시험 설정 부분 수정 |
| GET | /api/courses/{courseId}/exam/validation | 200 | 구성 검증 성공 시 시험 요약, 무효 구성은 400 |
| POST | /api/courses/{courseId}/exam/questions | 201 + Location | 선택지를 포함한 문항 생성 |
| GET | /api/courses/{courseId}/exam/questions | 200 | 문항/선택지 순서대로 조회 |
| GET | /api/courses/{courseId}/exam/questions/{questionId} | 200 | 문항 단건 |
| PATCH | /api/courses/{courseId}/exam/questions/{questionId} | 200 | 문항 및 정답 집합 수정 |
| DELETE | /api/courses/{courseId}/exam/questions/{questionId} | 204 | 이력 없는 문항 삭제 |
| PATCH | /api/courses/{courseId}/exam/questions/order | 200 | 전체 문항 순서 변경 |
| POST | /api/courses/{courseId}/exam/questions/{questionId}/choices | 201 + Location | 선택지 추가 |
| GET | /api/courses/{courseId}/exam/questions/{questionId}/choices | 200 | 선택지 목록 |
| GET | /api/courses/{courseId}/exam/questions/{questionId}/choices/{choiceId} | 200 | 선택지 단건 |
| PATCH | /api/courses/{courseId}/exam/questions/{questionId}/choices/{choiceId} | 200 | 선택지 부분 수정 |
| DELETE | /api/courses/{courseId}/exam/questions/{questionId}/choices/{choiceId} | 204 | 삭제 후 정답 구성까지 검증 |
| PATCH | /api/courses/{courseId}/exam/questions/{questionId}/choices/order | 200 | 전체 선택지 순서 변경 |

Exam DELETE API는 없다 (ADMIN 요청 시 405).
Course → Exam → Question → Choice 소속을 각 단계에서 검증한다.
다른 시험의 문항/다른 문항의 선택지는 해당 소속에 없는 리소스로 보아 404를 반환한다.

## 시험 요청·응답

생성:

```json
{"title":"보안 시험","passingScore":80.00,"maxAttempts":3}
```

- title: 필수, 공백 불가, 최대 200자.
- passingScore: 필수, 0~100, 소수 둘째 자리까지. 정규화 최종 점수의 합격 기준.
- maxAttempts: 필수, 1 이상 정수.
- Course 없음은 404, 같은 Course의 두 번째 시험 생성은 409.
- 생성 시 문항 0개인 시험을 허용하지만 구성 검증에서는 응시 불가능으로 판정한다.

PATCH는 title/passingScore/maxAttempts 중 변경할 필드만 전달한다.
생략은 유지, 명시적 null은 400이며 course 변경은 지원하지 않는다.
Course의 OPEN/CLOSED 여부에 따른 새로운 수정 제한은 없다.

```json
{
  "examId":10,"courseId":1,"title":"보안 시험","passingScore":80.00,
  "maxAttempts":3,"questionCount":2,"totalQuestionScore":30.00
}
```

totalQuestionScore는 Question.score 합계이며 별도 DB 컬럼에 저장하지 않는다.

## 문항 생성과 수정

문항과 선택지를 같은 트랜잭션으로 생성한다. 정답 없는 문항을 먼저 저장하는 흐름은 제공하지 않는다.

```json
{
  "questionText":"보안 정책에 부합하는 행동은?",
  "questionType":"SINGLE_CHOICE",
  "score":10.00,
  "sortOrder":1,
  "choices":[
    {"choiceText":"업무 계정을 공유하지 않는다","correct":true,"sortOrder":1},
    {"choiceText":"비밀번호를 공개한다","correct":false,"sortOrder":2}
  ]
}
```

- questionText: 공백 불가.
- score: DECIMAL(7,2)의 양수 원점수 (최소 0.01, 최대 99999.99). 합계가 100일 필요는 없다.
- sortOrder: 양수. 같은 시험에서 중복 불가.
- choices: 1개 이상, 각 choiceText 공백 불가/최대 1000자, correct 필수, sortOrder 양수·문항 내 유일.

관리 응답:

```json
{
  "questionId":1,"questionType":"SINGLE_CHOICE","questionText":"문제",
  "score":10.00,"sortOrder":1,
  "choices":[
    {"choiceId":100,"choiceText":"A","correct":true,"sortOrder":1},
    {"choiceId":101,"choiceText":"B","correct":false,"sortOrder":2}
  ]
}
```

QuestionAdminResponse/ChoiceAdminResponse는 정답을 포함하는 관리자 전용 DTO다.
향후 EMPLOYEE 응시용 DTO에는 correct를 포함하지 말고 별도 정의해야 한다.

문항 PATCH는 questionText/questionType/score/sortOrder를 부분 수정한다.
correctChoiceIds를 함께 전달하면 기존 선택지 ID를 유지한 채 정답 집합을 원자적으로 교체한다.

```json
{"questionType":"SINGLE_CHOICE","correctChoiceIds":[101]}
```

correctChoiceIds 생략은 유지, 중복/외부 ID는 400, 빈 집합도 유형별 정답 최소 조건에 의해 400이다.
유형 변경은 허용하지만 최종 선택지 구성이 새 유형과 일치해야 한다.
모든 PATCH 필드의 명시적 null은 거절한다. 빈 객체 PATCH는 기존 값을 유지한다.

## 유형별 정답 규칙

| 유형 | 관리 시 검증 |
|---|---|
| SINGLE_CHOICE | 선택지 존재 + 정답 정확히 1개 |
| MULTIPLE_CHOICE | 선택지 존재 + 정답 최소 1개 (2개 이상 강제하지 않음) |
| TRUE_FALSE | choiceText가 각각 TRUE/FALSE인 선택지 정확히 2개 + 정답 정확히 1개 |

TRUE_FALSE는 별도 Boolean answer 필드 없이 기존 QuestionChoice를 사용한다.
선택지 앞뒤 공백은 제거하지만 TRUE/FALSE 대문자 리터럴을 사용해야 한다.

선택지 단독 POST/PATCH/DELETE도 최종 문항 전체 구성을 검증한다.
따라서 SINGLE_CHOICE의 유일한 정답을 지우거나 두 번째 정답을 추가하는 요청은 실패한다.
정답 교체는 문항 PATCH의 correctChoiceIds로 처리한다.
TRUE_FALSE의 선택지를 하나만 추가/삭제하여 총 개수를 1개/3개로 만드는 요청 역시 실패한다.
TRUE_FALSE 정답 변경은 correctChoiceIds를 사용하고, 문항 전체를 제거하려면 문항 DELETE를 사용한다.

### 향후 채점 규칙 (이번 단계 미구현)

- SINGLE_CHOICE/TRUE_FALSE: 제출 선택지가 정답과 일치하면 원점수 전부, 아니면 0점.
- MULTIPLE_CHOICE: 제출 Choice 집합이 정답 집합과 정확히 같으면 원점수 전부, 아니면 0점.
- 부분점수 없음.
- 최종 점수 = 획득 배점 합계 / 전체 문제 배점 합계 × 100.
- 소수 둘째 자리 HALF_UP 정규화 후 Exam.passingScore 이상이면 합격.

## 전체 순서 관리

```json
{"questionIds":[3,1,2]}
```

```json
{"choiceIds":[101,100]}
```

해당 부모의 전체 ID 집합을 정확히 전달해야 한다. 중복/누락/외부/존재하지 않는 ID는 400이다.
기존 CourseContent 패턴대로 Course 행을 먼저 잠그고,
현재 최대 순서보다 큰 임시 양수 영역으로 모두 이동 → flush → 최종 1..N 부여 → flush 한다.
MySQL의 즉시 UNIQUE 검사 중 순서 교환 충돌을 피하며 INT 상한을 넘으면 400이다.
문항이 0개인 시험은 빈 questionIds를 허용한다. 단건 PATCH 순서는 비어 있는 양수 위치만 가능하다.

## 시험 구성 검사 및 재사용

ExamConfigurationValidator.validate는 시험 설정, 최소 1개 문항, 양수 배점,
각 문항의 유형별 정답 구성, 양수 총 원점수를 검증한다.
ExamService.validateConfiguration은 Course/Exam 존재 확인 후 문항과 선택지를 일괄 조회하여 검증한다.
조회는 readOnly 트랜잭션이고 어떤 응시/답안/수강 상태도 변경하지 않는다.

다음 ExamAttempt 단계에서는 같은 Validator를 시작 직전에 재사용할 수 있다.
검증 후 시작 사이의 구성 변경을 막으려면 향후 응시 시작도 같은 Course 잠금 규약을 따라야 한다.
삭제 보호용 ExamAttemptRepository/ExamAnswerRepository는 exists/참조 확인만 제공하며 save 메서드는 없다.

## 중복·동시성·삭제

- 모든 관리 쓰기는 Course 행의 PESSIMISTIC_WRITE 잠금을 먼저 획득한다.
- 시험 생성은 Service exists 검사 + DB uk_exams_course로 0..1 관계를 보장한다.
- 문항/선택지 순서 UNIQUE 사전 검사와 DB 제약을 유지한다.
- 조회는 readOnly, 생성/수정/삭제/순서 변경은 하나의 쓰기 트랜잭션이다.
- 문항 삭제는 해당 시험에 응시 이력이 있거나 문항/선택지가 답안에 참조되면 409다.
- 선택지 삭제도 해당 시험에 응시 이력이 있거나 해당 선택지가 답안에 참조되면 409다.
- 응시 이력이 없고 참조되지 않은 문항은 선택지를 먼저 명시적으로 삭제한 후 문항을 삭제한다.
- 선택지 단독 삭제는 삭제 후 남은 정답 구성까지 검사한다.
- FK RESTRICT는 최종 방어선으로 유지한다. 동시 FK 경합 등 DB 무결성 실패는 기존 DATA_CONFLICT/409다.
- 추가 버전/soft-delete/cascade 필드는 없다. 실패 트랜잭션 내 자동 재시도도 없다.

## 설계 경계 및 후속 결정

관리 상태의 유효성을 보장하기 위해 문항 생성에 선택지 목록을 필수로 받는다.
개별 Choice CRUD만으로 SINGLE_CHOICE 정답을 교체할 수 없는 모순은 correctChoiceIds 원자적 변경으로 해결했다.
문항 0개인 시험은 구성 중인 상태로 허용하지만 응시 가능한 구성은 아니다. 새 Exam 상태 Enum은 추가하지 않았다.

요청 범위에 따라 응시 이력 존재 시 **삭제**는 제한하지만, 설정/문항/선택지 **수정** 자체는 새로 금지하지 않았다.
현재 스키마에는 시험 구성 스냅샷/버전이 없으므로 응시 기능을 도입하기 전
진행·제출 이력에 대한 수정 잠금 또는 스냅샷 정책을 확정해야 한다.
현재 API를 이력 보존용 시험 버전 관리로 간주하면 안 된다.

OPEN/CLOSED 상태에 대한 새 편집 제한은 없다. 이미 완료된 수강은 시험 추가로 다시 열리지 않는다.
학습 진도 요청과 시험 생성 사이를 직렬화하는 새 잠금도 추가하지 않았다.
운영 중 시험 추가와 학습 완료 판정의 경합 정책은 응시 운영 정책과 함께 후속 확정이 필요하다.

## 오류 코드

기존 {code, message, errors} JSON 형식을 유지한다. 추가된 ErrorCode는 12개다.

| HTTP | 신규 코드 |
|---|---|
| 404 | EXAM_NOT_FOUND, QUESTION_NOT_FOUND, QUESTION_CHOICE_NOT_FOUND |
| 409 | DUPLICATE_EXAM, DUPLICATE_QUESTION_SORT_ORDER, DUPLICATE_CHOICE_SORT_ORDER, EXAM_HISTORY_DELETE_CONFLICT |
| 400 | INVALID_EXAM_CONFIGURATION, INVALID_QUESTION_CONFIGURATION, INVALID_QUESTION_ORDER, INVALID_CHOICE_ORDER, EXAM_ORDER_LIMIT_EXCEEDED |

기존 COURSE_NOT_FOUND/404, VALIDATION_ERROR 및 INVALID_REQUEST/400, UNAUTHORIZED/401,
FORBIDDEN/403, DATA_CONFLICT 및 CONCURRENT_MODIFICATION/409도 재사용한다.
새 UNIQUE 이름 3개를 UniqueConstraintErrors의 변환 대상으로 추가했다.

## 파일 변경 목록

생성: 프로덕션 20개, 테스트 4개, 문서 1개.

```text
src/main/java/com/be/exam/
  controller/ExamController.java
  controller/QuestionController.java
  dto/ExamCreateRequest.java
  dto/ExamPatchRequest.java
  dto/ExamResponse.java
  dto/QuestionCreateRequest.java
  dto/QuestionPatchRequest.java
  dto/QuestionOrderRequest.java
  dto/QuestionAdminResponse.java
  dto/ChoiceCreateRequest.java
  dto/ChoicePatchRequest.java
  dto/ChoiceOrderRequest.java
  dto/ChoiceAdminResponse.java
  repository/QuestionRepository.java
  repository/QuestionChoiceRepository.java
  repository/ExamAttemptRepository.java
  repository/ExamAnswerRepository.java
  service/ExamService.java
  service/QuestionService.java
  service/ExamConfigurationValidator.java
src/test/java/com/be/exam/
  ExamFixtures.java
  ExamConfigurationValidatorTest.java
  ExamManagementServiceTest.java
  ExamManagementApiTest.java
EXAM_API.md
```

수정 11개:

```text
src/main/java/com/be/exam/entity/Exam.java
src/main/java/com/be/exam/entity/Question.java
src/main/java/com/be/exam/entity/QuestionChoice.java
src/main/java/com/be/exam/repository/ExamRepository.java
src/main/java/com/be/global/config/SecurityConfig.java
src/main/java/com/be/global/exception/ErrorCode.java
src/main/java/com/be/global/exception/GlobalExceptionHandler.java
src/main/java/com/be/global/exception/UniqueConstraintErrors.java
src/test/java/com/be/global/exception/UniqueConstraintErrorsTest.java
src/test/java/com/be/mapping/EntityMappingTest.java
src/test/java/com/be/validation/ServiceValidationTest.java
```

Entity 변경 이유: 기존 필드에 대한 생성/설정 수정/정답 변경/순서 변경을 캡슐화하는 메서드만 추가했다.
ExamRepository는 기존 existsByCourseId를 유지하면서 관리 CRUD/소속 조회를 확장했다.
기존 Course/Enrollment/ContentProgress Service와 Entity, SQL, 의존성은 변경하지 않았다.

## 테스트

2026-09-19, Java 21 / Maven 3.9.16, `mvn -B -ntp -Dtest=*Test test`:

- 전체 431개: 성공 428, 실패 0, 오류 0, 건너뜀 3. BUILD SUCCESS.
- ExamConfigurationValidatorTest 14개: 유형별 정답 수/선택지, TRUE_FALSE 리터럴, 빈 시험,
  설정 범위, 양수 배점, 모든 문항 유효성.
- ExamManagementServiceTest 32개: 시험 생성·조회·수정/중복/합계, Course 상태별 수정 허용,
  세 유형 생성, 문항/선택지 소속, 원자적 정답·유형 변경, 이력 삭제 보호,
  2단계 순서 교환, 중복/누락/외부 ID, INT 상한.
- ExamManagementApiTest 16개: ADMIN 관리 경로, 생성 Location/삭제 204,
  EMPLOYEE/INSTRUCTOR 차단, 401/400/404/409, null/정밀도/중첩 검증, Exam DELETE 부재.
- 기존 UniqueConstraintErrorsTest에 3개, ServiceValidationTest에 1개 추가. 총 신규 검증 66개.
- EntityMappingTest에서 답안 선택지 참조 JPQL도 추가 검증했다. 스키마는 여전히 15개 테이블이다.
- 기존 Redis 통합 테스트 3개는 활성화 설정이 없어 건너뜀.
- 실제 DB 연결용 BeApplicationTests는 *Test 패턴에서 제외된다.
- 실제 MySQL 경합/UNIQUE 순서 교환/트랜잭션 롤백은 실행하지 않았다.
  단위 테스트의 flush 단계 검증 및 Hibernate 매핑 검증을 실 DB 검증으로 간주하지 않는다.
