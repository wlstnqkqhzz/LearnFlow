# LearnFlow 백엔드 통합 점검 및 안정화

점검일: 2026-09-20. Java 21 / Spring Boot 4.1.1 / Maven 3.9.16.
실제 서비스 연결 테스트, MVC/Security 회귀, 부팅 스모크, 소스·설정·문서 점검을 수행했다.
실제 MySQL/Redis를 사용한 end-to-end 및 병렬 트랜잭션 검증을 수행했다는 의미는 아니다.

## 1. 수정 전 전체 테스트 결과

제외 패턴 없는 `mvn -B -ntp test` 실행:

| 전체 | 통과 | assertion 실패 | 오류 | 건너뜀 | 빌드 |
| --- | --- | --- | --- | --- | --- |
| 501 | 497 | 0 | 1 | 3 | BUILD FAILURE |

기존 오류는 `BeApplicationTests.contextLoads` 한 건이다.
별도 테스트 설정이 없는 @SpringBootTest가 `${app.datasource.url}`을 해석하지 못해 DataSource/EntityManagerFactory 생성에 실패했다.
Redis 연동 3건은 기존 명시적 활성화 옵션 부재로 건너뛰었다. 최초 sandbox Maven 실행은 네트워크 제한으로 의존성 확인부터 실패하여, 권한 승인 후 재실행한 결과를 기준으로 기록했다.

## 2. 전체 코드 점검 결과 요약

Controller, DTO, Service, Repository, Entity, Security/인증, 예외, 설정 및 각 API 문서를 검토했다.
조직/회원 → 과정/규칙 → 자동배정 → 진도/시험 → COMPLETED/FAILED/EXPIRED 흐름이 연결된다.
공통 EnrollmentCompletionService, 기존 역할·소유권 검사, terminal 상태 보호, 시험 구성 동결을 유지했다.
새 업무 기능, URI 변경, 권한 확대, DB 스키마 변경은 없다.

## 3. 발견한 문제와 수정

| 문제 | 영향 | 원인 | 수정 |
| --- | --- | --- | --- |
| 부팅 테스트가 로컬 DB 설정에 의존 | 기본 전체 테스트 실패, 실행 환경별 결과 불일치 | 테스트 전용 인프라 경계가 없는 contextLoads | DataSource/LettuceConnectionFactory만 mock, 실제 JPA 매핑/Repository/서비스/Security 컨텍스트 구성 확인. 테스트 전용 JWT 키는 실행 중 생성 |
| 회원 페이지의 roles N+1 | 페이지 회원 수에 따라 역할 SELECT 증가 | 지연 역할 컬렉션을 각 MemberResponse 변환에서 접근 | 기존 페이지를 먼저 조회하고 해당 ID들의 역할을 EntityGraph로 한 번에 로딩. 페이지 자체에 컬렉션 fetch를 적용하지 않음 |
| 관리자 선택지 DTO toString에 정답 포함 | 중첩 문항 DTO를 문자열로 기록하면 선택지 정답 정보가 함께 출력될 위험 | Java record 기본 toString | ChoiceCreateRequest/ChoiceAdminResponse의 문자열 표현만 REDACTED. 관리자 JSON 계약은 그대로 유지 |
| 단계별 API 문서가 현재 구현과 충돌 | 자동배정/수료/시험 동결·권한을 잘못 이해할 수 있음 | 과거 단계의 미구현 설명이 본문에 남음 | 현재 구현으로 본문을 갱신하고 과거 테스트 결과는 당시 기록임을 명시 |

정답 정보가 실제 운영 로그에서 유출되었다는 증거를 발견한 것은 아니다. 문자열 변환으로 노출 가능한 코드 경로를 제거한 것이다.
회원 역할 N+1은 매핑·호출 경로로 확인했으며 실제 MySQL 쿼리 수/응답시간 벤치마크는 하지 않았다.

## 4. 생성한 파일

- `src/test/java/com/be/integration/LearningWorkflowIntegrationTest.java`
- `src/test/java/com/be/exam/ExamDtoRedactionTest.java`
- `STABILIZATION_REVIEW.md`

## 5. 수정한 파일

프로덕션:

- `src/main/java/com/be/member/repository/MemberRepository.java`: findWithRolesByIdIn 추가.
- `src/main/java/com/be/member/service/MemberService.java`: 검색 페이지의 역할 일괄 초기화.
- `src/main/java/com/be/exam/dto/ChoiceCreateRequest.java`: toString 비노출.
- `src/main/java/com/be/exam/dto/ChoiceAdminResponse.java`: toString 비노출.

테스트:

- `src/test/java/com/be/BeApplicationTests.java`: 외부 저장소와 격리한 부팅 스모크 테스트.
- `src/test/java/com/be/api/ApiServiceExtensionTest.java`: 역할 일괄 조회·빈 페이지·순서 보존.

문서:

- `API.md`, `AUTH.md`, `COURSE_API.md`, `ASSIGNMENT_API.md`
- `PROGRESS_API.md`, `EXAM_API.md`, `EXAM_ATTEMPT_API.md`, `ENROLLMENT_EXPIRATION.md`

## 6. 삭제한 파일

없음.

## 7. 추가·보강 테스트

- LearningWorkflowIntegrationTest: 11건 신규. 실제 Service/Entity/PasswordEncoder/채점·수료 로직을 사용한다. Repository는 테스트별 메모리 저장소로 대체한다. 실행 순서·실제 DB ID에 의존하지 않는다.
- ExamDtoRedactionTest: 2건 신규. 중첩 요청/응답 toString 비노출 및 관리자 JSON 정답 계약 유지.
- ApiServiceExtensionTest: 1건 신규, 기존 2건 보강. 반환된 페이지 ID만 일괄 조회, 빈 페이지 조회 생략, 기존 순서/역할/페이지 정보 보존.
- BeApplicationTests: 기존 1건 수정. 실제 Bean 연결·13개 Entity 메타모델·Repository 생성 및 외부 연결 미호출을 검증한다. 테스트 제외나 production 규칙 완화로 성공시킨 것이 아니다.

총 테스트 수는 14건 증가했다. 스모크 테스트는 SQL 실행/DB 제약 검증을 대체하지 않는다.
기존 잠금 예외·트랜잭션 프록시 테스트와 단위 테스트는 그대로 실행했다.

## 8. Happy Path 결과

조직·직무 생성 → INSTRUCTOR/EMPLOYEE 생성 → DRAFT 과정 → 필수 콘텐츠·세 문제 유형 시험 구성 → 규칙 생성 → OPEN → 자동 ASSIGNED → 콘텐츠 진도 → 응시/답안/제출 → 100.00점 합격 → COMPLETED를 통과했다.
최초 startedAt 및 completedAt, 원점수 earnedScore 저장을 확인했다.
콘텐츠 먼저/시험 먼저 두 순서 모두 같은 CompletionService로 수료했다.

## 9. 시험 없는 과정 결과

강사 없는 과정도 OPEN·자동배정이 가능하고 콘텐츠 완료만으로 COMPLETED가 된다.
별도 응시 이력은 생성하지 않는다. Exam-only 과정도 필수 콘텐츠 0개에서 나눗셈 오류 없이 시험 합격으로 수료했다.

## 10. FAILED 결과

실제 응시 서비스를 통해 1/2/3회 불합격을 제출했다.
1/2회는 IN_PROGRESS, 3회는 FAILED, 이력 3개 보존, 추가 응시/진도 수정 거부, completedAt null을 확인했다.

## 11. EXPIRED 결과

실제 배정 후 ASSIGNED/IN_PROGRESS 각각에 만료 서비스를 호출하여 EXPIRED 및 시각 보존을 확인했다.
동일 실행 재요청은 0건, 이후 학습·시험 시작 차단과 자동배정 재평가 후 기존 수강 유지를 검증했다.
기존 만료 테스트 24건/스케줄러 2건에서 dueDate 당일 제외, 서울 날짜, 과거 미처리 건, terminal 상태 보존 및 충돌 처리도 통과했다.
요청마다 날짜로 즉시 차단하는 정책은 추가하지 않았다.

## 12. ON_LEAVE / RESIGNED 결과

기존 진도가 있는 직원을 실제 MemberService로 휴직/퇴사 처리한 후 새 과정 공개와 회원 재평가를 수행했다.
둘 다 신규 자동배정에서 제외하고 기존 수강/진도를 보존했다.
실제 PasswordEncoder·JWT 발급을 사용하는 AuthService에서 ON_LEAVE 로그인 성공, RESIGNED 실패를 확인했다.
기존 로그인·Access·Refresh 회귀 테스트도 통과했다.

## 13. 규칙 / 중복 배정

기존 테스트에서 ACTIVE만 자동배정, 직속 부서, 정확한 직무, 서울 날짜 기반 신입 기간 및 미래 입사자 제외를 확인했다.
Member 생성/부서/직무/입사일, Rule 생성/활성화/조건 수정, Course OPEN 트리거가 연결되어 있다.
수동·자동 출처와 terminal 상태를 불문하고 기존 (member, course) 수강은 그대로 보존한다.
상태·역할 변경 자체는 새 자동배정 트리거가 아니다. ON_LEAVE → ACTIVE 복귀 트리거를 임의로 추가하지 않았다.

## 14. Security 권한

기존 SecurityFilterChain은 명시적 허용 후 denyAll이며 STATELESS다.
조직/회원·과정 변경·규칙/수동배정·시험 구성은 ADMIN이다.
Course/콘텐츠 GET은 ADMIN 또는 INSTRUCTOR이며 강사의 담당 과정 한정 ACL은 현재 정책에 없다.
직원 학습·시험 쓰기는 EMPLOYEE이고 Service가 소유권을 검사한다.
ADMIN 예외는 타인 진도·시험 결과·이력 조회뿐이며 대신 학습/제출할 수 없다.
미인증 401 / 인증 후 권한 부족 403 회귀가 통과했다. 권한 설정은 변경하지 않았다.

## 15. IDOR / 소유권

본인 수강 목록은 MemberPrincipal.memberId로 고정된다. 관리 상세는 ADMIN 전용이다.
새 통합 테스트에서 타인 진도 읽기/변경, 응시 시작/문제/답안/제출/결과/이력을 실제 Service가 모두 거부했다.
CourseContent, Question, Choice는 부모 소속을 포함한 조회로 검증한다.

## 16. 정답 비노출

직원 시작/문제/저장/제출/결과/이력 응답을 실제 Service에서 만들고 JSON으로 직렬화했다.
correct, isCorrect, correctChoiceIds, correctCount, earnedScore가 없고 제출 전 score/passed는 null이다.
관리 응답과 직원 응답은 별도 DTO이며 Entity 직접 반환이 없다.
관리 선택지 DTO의 문자열 노출만 보완했다.

## 17. JWT / Refresh

기존 테스트에서 ACTIVE/ON_LEAVE 로그인, RESIGNED 차단, 토큰 종류 혼용 차단, 만료 경계, HS256, Base64 오류/최소 키 길이, 양수 TTL 검증을 확인했다.
최신 회원 상태·email·roles 반영, refresh 재사용 차단, 회전 경쟁 실패, logout 제거도 통과했다.
Redis Lua 비교·교체 계약은 유지했다. 실제 Redis 3건은 명시적 옵션 부재로 건너뛰었으므로 이번 점검에서 Lua의 실서버 동작까지 재검증한 것은 아니다.

## 18. Transaction

Rule 생성/활성화·Course OPEN·Member 변경과 자동배정은 같은 REQUIRED 트랜잭션이다.
진도 변경/수료 및 시험 채점/제출 확정/수강 상태 변경은 각 하나의 트랜잭션이다.
CompletionService는 MANDATORY이며 만료만 기존 건별 REQUIRES_NEW를 유지한다.
조회는 기존 readOnly 정책을 유지한다. 실패 예외를 삼켜 일부 저장하는 경로를 추가하지 않았다.

## 19. 동시성

- Enrollment/ContentProgress @Version 유지.
- 진도는 Enrollment OPTIMISTIC_FORCE_INCREMENT.
- 응시 시작은 Course → Enrollment 잠금, 저장/제출은 Enrollment → Attempt 잠금.
- 응시 쓰기는 READ_COMMITTED에서 최신 이력을 읽으며 응시 번호/답안 UNIQUE가 최종 보호장치다.
- 시험 첫 응시와 관리 쓰기는 Course 잠금을 공유하고 첫 응시 이후 전체 시험 구성을 동결한다.
- 만료는 상태 재조회·버전 검사 및 건별 롤백/다음 실행 재평가를 유지한다.
- 기존 자동배정의 Member → Course와 과정 일괄배정의 Course → Member 잠금 순서 차이로 교착 가능성은 남는다. 기존 409/롤백 정책이며 새 잠금 체계를 도입하지 않았다.

실제 MySQL 병렬 경쟁을 실행하지 않았다. 단위 테스트·어노테이션·호출 순서·모의 충돌 검증을 DB 동시성 증명으로 주장하지 않는다.

## 20. 쿼리 점검

회원 페이지의 역할 로딩을 개선했다. 이전에는 페이지 조회 뒤 회원 수 N에 비례하는 roles 로딩이 가능했다.
현재는 페이지/필요한 count 조회 뒤 반환 ID의 roles를 추가 한 번 조회한다. 컬렉션 fetch pagination으로 메모리 페이징을 유발하지 않는다.
같은 readOnly 영속성 컨텍스트의 회원을 초기화하므로 기존 DTO/페이지 응답은 그대로다.

Course 검색의 instructor, Enrollment 목록의 member/course, 진도 목록의 content, 답안 목록의 question/selectedChoices는 기존 EntityGraph/일괄 조회를 사용한다.
시험 이력은 한 Enrollment의 단일 Exam을 공유한다. 자동배정의 건별 잠금·refresh·중복 확인 및 관리 삭제의 참조 검사는 기존 보호 정책을 유지했다.
실제 DB 쿼리 수 계측·대량 부하 테스트는 미실행이다.

## 21. Validation / 오류 계약

생성 필수값, PATCH의 생략/null, ID 양수, 페이지 0 이상/size 1~100, 진도/점수 범위, Enum 및 중첩 선택지 입력을 검토했다.
확정된 정책을 바꾸는 새 입력 제한은 추가하지 않았다.
PageResponse는 회원/과정/수강 페이지에서 동일하며 응시 이력은 기존 List 계약을 유지한다.
오류는 {code,message,errors}: 입력 400, 인증 401, 권한/소유권 403, 없음 404, 중복/상태/잠금 409, 예상 밖 오류 500이다.
DB 오류 원문/SQL/거부된 입력값을 응답에 포함하지 않는다. ErrorCode 및 GlobalExceptionHandler 변경은 없다.

## 22. 시간대

업무 날짜(신입 조건·만료)는 Clock + Asia/Seoul이다. assignedAt/startedAt/completedAt/submittedAt/resignedAt은 UTC로 계산한다.
BaseTimeEntity의 감사 시각도 UTC이며 OS 기본 시간대에 의존하지 않는다.
dueDate는 배정 당시 Course.endDate를 복사하며 과정 종료일 변경으로 기존 수강 마감일을 바꾸지 않는다.

## 23. 설정 / 비밀정보 / gitignore

추적 중인 application.yaml의 DB 접속 값과 JWT secret은 placeholder다.
application-local.yaml은 Git ignore 대상이며 해당 로컬 설정 파일의 추적 이력은 발견하지 못했다.
확인한 application 설정 변경 이력에서 명백한 비밀번호/secret 리터럴을 발견하지 못했고 프로덕션 소스에서 JWT/개인 키 패턴도 발견하지 못했다.
이 검사는 전체 Git 객체에 대한 전문 비밀정보 스캐너 검증을 대체하지 않는다.
로그인·토큰·회원 생성 DTO 및 JwtProperties는 기존 문자열 비노출을 유지했다.
실제 비밀값을 출력·수정하거나 Git 이력을 재작성하지 않았다.

## 24. API 문서 변경

조직/회원 문서의 자동배정 미구현, 인증 문서의 이전 단계 경로만 허용하는 설명, 콘텐츠 required가 저장만 된다는 설명을 수정했다.
배정 문서에 후속 학습/만료 서비스 연결, 진도 문서에 시험 합격 후 수료와 Exam-only, 시험 관리 문서에 첫 응시 이후 동결을 반영했다.
각 단계의 과거 테스트 결과는 날짜별 기록으로 보존하고 현재 부팅 스모크 테스트와 본 보고서로 연결했다.
URI/Method/JSON/권한 계약 자체를 변경하지 않았다.

## 25. 현재 사용자 정보 API

GET /api/auth/me 또는 직원이 자신의 name/status 등을 조회하는 동등 API는 없다.
GET /api/members/{id}는 ADMIN 전용이고 /api/enrollments/me는 수강 목록이다.
새 API를 추가하지 않았으며 프론트 연동 전 고려 사항으로 남긴다.

## 26. 최종 전체 테스트

`mvn -B -ntp compile test` — 테스트 이름 제외 패턴 없음.

| 전체 | 통과 | 실패 | 오류 | 건너뜀 | 빌드 |
| --- | --- | --- | --- | --- | --- |
| 515 | 512 | 0 | 0 | 3 | BUILD SUCCESS |

Redis 3건만 기존 명시적 실행 옵션 부재로 건너뛰었다. BeApplicationTests도 실행·통과했다.
실제 MySQL/Redis 데이터를 생성·삭제하지 않았고 Seed·SQL 파일도 실행하지 않았다.

## 27. Entity / Schema 변경

없음. Enum, Entity 필드/매핑, PK/FK/UNIQUE/CHECK/인덱스, SQL, 의존성 및 프로덕션 설정은 그대로다.

## 28. 남은 문제 / 검증 한계

이번에 수정한 재현 가능한 문제와 기본 전체 테스트 실패는 남지 않았다. 다음은 미검증 범위 또는 기존 정책 경계다.

- 실제 MySQL 트랜잭션 롤백, 동시 시작/제출/진도/만료/배정 경쟁, UNIQUE 및 SQL 실행은 미검증.
- 실제 Redis 통합 3건은 미실행.
- 자정은 만료 대상 시점이며 실제 차단은 EXPIRED 저장 이후다. 중단/충돌 건은 다음 정기 실행까지 지연될 수 있다.
- 운영 중 콘텐츠/수료 기준 변경과 학습 판정의 경합, 시험 생성과 이미 진행 중인 진도 판정의 직렬화는 기존 문서의 미확정 운영 경계다. 새 동결 정책을 임의로 추가하지 않았다.
- 기존 자동배정의 잠금 순서 차이/대량 동기 작업은 실환경 계측이 필요하다.

## 29. 다음 단계 전 확인 사항

- 격리된 MySQL 및 테스트 Redis로 실제 저장·롤백·경쟁 테스트를 실행할 환경을 준비한다.
- 현재 사용자 정보 조회 API를 별도 요구사항으로 확정한다.
- EMPLOYEE 전용 콘텐츠 열람 경로를 확정한다. 현재 CourseContent GET은 ADMIN/INSTRUCTOR용이고 진도 응답에는 contentUrl이 없으므로 일반 직원 화면에서 실제 영상/문서 URL을 얻을 경로가 부족하다. 이번 작업에서 새 API나 권한 확대를 임의로 추가하지 않았다.
- 별도 Origin의 Web 직접 호출을 위한 CORS 설정은 현재 없다. 허용 Origin/메서드를 확정하거나 같은 Origin 프록시를 사용해야 한다. 전체 Origin 허용은 추가하지 않았다.
- 강사 담당 과정 한정 ACL, 운영 중 구성 변경, 만료 지연 정책은 현재 MVP 경계로 확인한다.
- Seed는 요청대로 다음 별도 작업으로 남겼다.

## 30. 변경 범위 확인

프로덕션 수정은 회원 페이지 역할 조회와 관리자 선택지 DTO 문자열 표현에 한정했다.
패키지/Service/Entity/URI 대규모 재설계, 새 Framework, 새로운 기능, CORS 확대, Frontend/Mobile, Seed 수정은 없다.
git diff 기준으로 이번 점검과 관계없는 변경을 만들지 않았다. 커밋/푸시는 수행하지 않았다.
