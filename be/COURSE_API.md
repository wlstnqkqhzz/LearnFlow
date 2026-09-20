# 교육과정 / 콘텐츠 관리 API

Course는 특정 기간에 운영되는 실제 교육과정이다. 연도가 다른 교육은 별도 Course로 생성한다.
기존 Entity 필드·테이블·FK·UNIQUE·CHECK·Enum은 변경하지 않았다.
기존 생성자는 protected로 유지하고 생성/일반 수정/상태·순서 변경 메서드만 추가했다.

## 권한 및 공통 응답

| 요청 | ADMIN | INSTRUCTOR | EMPLOYEE만 보유 | 미인증 |
|---|---|---|---|---|
| Course 및 콘텐츠 GET | 허용 | 허용 | 403 | 401 |
| 생성·수정·상태·순서·콘텐츠 삭제 | 허용 | 403 | 403 | 401 |

다중 역할 중 하나가 허용되면 접근 가능하다. INSTRUCTOR는 담당 여부와 무관하게 조회 가능하다.
직원용 학습 조회, 담당 강사 소유권 ACL, 자율 수강 신청은 이번 API 범위가 아니다.
기존 JWT, STATELESS, 401/403 JSON 응답을 그대로 사용한다.

성공 응답은 DTO를 직접 반환한다. 생성은 201 + Location, 조회·수정은 200, 콘텐츠 삭제는 204다.
오류는 기존 `{ "code": "...", "message": "...", "errors": [] }` 형식이다.

## Course API

| Method | 경로 | 기능 |
|---|---|---|
| POST | /api/courses | DRAFT 생성 |
| GET | /api/courses | 검색·페이징 |
| GET | /api/courses/{courseId} | 단건 조회 |
| PATCH | /api/courses/{courseId} | 일반 정보 부분 수정 |
| PATCH | /api/courses/{courseId}/status | 상태 변경 |

생성 요청 예:

```json
{
  "title": "2026 개인정보보호 교육",
  "description": "전 직원 필수 온라인 교육",
  "courseType": "MANDATORY",
  "startDate": "2026-09-18",
  "endDate": "2026-10-31",
  "passingProgressRate": 100.00,
  "instructorId": null
}
```

- title: 필수, 최대 200자. courseType: MANDATORY / OPTIONAL, 필수.
- description: 선택, 최대 16,383 UTF-16 코드 단위(utf8mb4 TEXT 바이트 한도 내 보수적 제한).
- startDate/endDate: DRAFT에서는 각각 null 허용. 둘 다 있으면 시작일 ≤ 종료일.
- passingProgressRate: 0~100, 소수점 이하 최대 2자리. 생성 시 생략/null은 100.00.
- instructorId: 선택. 값이 있으면 회원 존재(없으면 MEMBER_NOT_FOUND)와 INSTRUCTOR 역할 검증.
- 상태·ID·생성/수정 시각은 요청 DTO에 없으며 외부에서 지정하지 않는다.
- 강사 회원의 ACTIVE/ON_LEAVE/RESIGNED에 관한 별도 배정 제한은 요청에 없으므로 추가하지 않았다.
- OPTIONAL은 분류일 뿐 수강 신청 기능과 연결되지 않는다.

검색 파라미터: page(기본 0), size(기본 20, 최대 100), status, type, instructorId, keyword.
ID 오름차순으로 정렬한다. keyword는 제목 부분 검색이며 %, _, !는 일반 문자로 처리한다.
응답은 기존 PageResponse(content, page, size, totalElements, totalPages)를 재사용한다.
각 Course 응답에는 일반 정보, instructorId/instructorName, createdAt/updatedAt이 포함된다.

PATCH는 생략 필드를 유지한다. description/startDate/endDate/instructorId는 명시적 null로 제거한다.
title/courseType/passingProgressRate는 명시적 null을 거부한다. `{}`는 변경 없는 요청으로 허용한다.
일반 PATCH로 status를 변경할 수 없으며 별도 상태 API를 사용한다.

```json
{"status":"OPEN"}
```

상태 전이는 DRAFT → OPEN → CLOSED만 허용한다. 건너뛰기·역방향·동일 상태 요청은 409다.
OPEN 전환 시 시작일/종료일 필수, 시작일 ≤ 종료일을 검사한다. 같은 날짜도 허용한다.
강사는 OPEN에서도 선택값이며, 지정된 경우 현재 INSTRUCTOR 역할을 다시 검사한다.
기존 DB CHECK가 CLOSED에도 날짜를 요구하므로 OPEN/CLOSED 일반 수정에서 날짜 제거는 400이다.
제목·설명·유형·콘텐츠 등 나머지 필드는 OPEN/CLOSED라는 이유로 편집을 금지하지 않는다.
강사 지정/해제 또는 일반 정보 수정 시 남아 있는 강사도 현재 역할을 검증한다.
Course 자체 DELETE API는 제공하지 않는다.

## CourseContent API

공통 경로: `/api/courses/{courseId}/contents`

| Method | 추가 경로 | 기능 |
|---|---|---|
| POST | 없음 | 콘텐츠 추가 |
| GET | 없음 | sortOrder 오름차순 목록 |
| GET | /{contentId} | 단건 조회 |
| PATCH | /{contentId} | 일반 정보 및 개별 순서 수정 |
| DELETE | /{contentId} | 콘텐츠 삭제 |
| PATCH | /order | 전체 콘텐츠 재정렬 |

```json
{
  "title": "1강 개인정보보호",
  "contentType": "VIDEO",
  "contentUrl": "https://example.com/training/video",
  "durationSeconds": 600,
  "sortOrder": 1,
  "required": true
}
```

- title: 필수, 최대 200자. contentType: VIDEO / DOCUMENT / LINK.
- contentUrl: 필수 문자열, 최대 2,048자. 업로드·외부 URL 호출은 수행하지 않는다.
- durationSeconds: 선택, 0 이상. 단위는 초.
- sortOrder: 필수 양의 정수. 과정 내 중복은 409.
- required: 생성 시 생략/null이면 true. 현재 수료 판정은 필수 콘텐츠의 평균 진도만 사용한다.
- PATCH는 생략 필드를 유지한다. durationSeconds만 명시적 null로 제거 가능하다.
- courseId는 생성 경로에서 고정하며 PATCH로 다른 과정으로 이동할 수 없다.
- 단건 조회/수정/삭제는 courseId + contentId로 조회한다. 소속 불일치와 없는 콘텐츠는 모두 404다.
- 개별 순서를 변경할 때 이미 점유된 값으로 바꾸면 409다. 서로 교환하려면 전체 재정렬을 사용한다.
- 삭제 후 순서의 빈 번호는 유지한다. 필요하면 전체 재정렬로 1..N으로 정리한다.
- 기존 FK RESTRICT를 유지한다. 참조 데이터가 있으면 DATA_CONFLICT(409), cascade 삭제는 하지 않는다.

전체 순서 요청:

```json
{"contentIds":[3,1,2]}
```

현재 과정의 **전체 ID**를 정확히 한 번씩 전달해야 한다. 부분 목록·중복·없는 ID·다른 과정 ID는 400이다.
빈 과정에는 빈 목록을 허용한다. null 목록/요소, 0 이하 ID는 Validation 400이다.
요청 순서대로 sortOrder=1..N을 부여하고 같은 순서의 DTO 목록을 반환한다.

모든 콘텐츠 쓰기는 부모 Course 행에 PESSIMISTIC_WRITE 잠금을 먼저 획득한다.
재정렬은 현재 최댓값보다 큰 임시 양수 순서로 이동 후 flush하고, 1..N을 부여한 후 다시 flush한다.
두 단계는 하나의 트랜잭션이며, 중간 오류는 전체 롤백된다. MySQL 즉시 UNIQUE 검사와 양수 CHECK를 지킨다.
임시 범위가 INT 최댓값을 넘으면 변경 전 CONTENT_ORDER_LIMIT_EXCEEDED(400)로 거부한다.
이 경우 개별 콘텐츠 순서를 낮은 미사용 양수로 조정한 후 다시 요청할 수 있다.

## 추가 ErrorCode

| Code | HTTP |
|---|---|
| COURSE_NOT_FOUND | 404 |
| COURSE_CONTENT_NOT_FOUND | 404 |
| INVALID_COURSE_INSTRUCTOR | 400 |
| INVALID_COURSE_PERIOD | 400 |
| COURSE_DATES_REQUIRED | 400 |
| INVALID_COURSE_STATUS_TRANSITION | 409 |
| DUPLICATE_CONTENT_SORT_ORDER | 409 |
| INVALID_CONTENT_ORDER | 400 |
| CONTENT_ORDER_LIMIT_EXCEEDED | 400 |

강사 회원 없음은 기존 MEMBER_NOT_FOUND(404)를 재사용한다.
상태 전이 실패는 기존 회원 상태 전이와 동일하게 409로 처리한다.
DTO 검증·Enum·JSON 오류와 FK 충돌은 기존 공통 오류 처리를 재사용한다.

## 파일 구성 및 검증

새 파일:

- course/controller: CourseController, CourseContentController
- course/repository: CourseRepository, CourseContentRepository
- course/service: CourseService, CourseContentService
- course/dto: CourseCreateRequest, CoursePatchRequest, CourseStatusRequest, CourseSearchRequest, CourseResponse,
  CourseContentCreateRequest, CourseContentPatchRequest, CourseContentResponse, ContentOrderRequest
- 테스트: CourseServiceTest, CourseContentServiceTest, CourseApiTest
- 문서: COURSE_API.md

수정 파일:

- Course, CourseContent: 기존 필드·매핑 변경 없이 생성/수정 메서드 추가
- SecurityConfig: Course 조회/변경 권한
- ErrorCode, GlobalExceptionHandler, UniqueConstraintErrors: 업무 오류 및 HTTP/UNIQUE 매핑
- EntityMappingTest: CourseRepository JPQL 파싱 검증 추가
- ServiceValidationTest: Course/Content Service 경계 검증
- UniqueConstraintErrorsTest: 콘텐츠 순서 UNIQUE 오류 변환

검증 명령: `mvn -Dtest=*Test test`.
Service 단위 테스트, 실제 MVC/Security 체인과 Mock Service 기반 API 테스트,
Hibernate MySQL DDL 생성 및 JPQL 파싱을 포함한다. 실제 MySQL SQL 실행·잠금 경합 검증은 포함하지 않는다.
기존 Redis 통합 테스트는 활성화 옵션이 없으면 건너뛴다. BeApplicationTests는 외부 저장소를 대체한 부팅 스모크 테스트다.
기본 전체 실행은 `mvn test`이며 최신 검증 범위는 STABILIZATION_REVIEW.md를 참고한다.

AssignmentRule/Enrollment, ContentProgress, Exam 및 실제 응시·자동 만료는 후속 구현이 완료되었다.
각 ASSIGNMENT_API.md, PROGRESS_API.md, EXAM_API.md, EXAM_ATTEMPT_API.md, ENROLLMENT_EXPIRATION.md를 참고한다.
CourseSession 및 프론트/모바일은 이 백엔드 API 문서 범위에 포함하지 않는다.
