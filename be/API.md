# 조직·회원 REST API

## 구현 파일

- 조직 Controller: DepartmentController, JobPositionController
- 회원 Controller: MemberController
- 공통 응답: ActivationUpdateRequest, ApiErrorResponse, PageResponse
- 요청 DTO: DepartmentPatchRequest, MemberPatchRequest, MemberSearchRequest
- 예외 처리: GlobalExceptionHandler
- 보안 설정: SecurityConfig (Access Token 기반, 조직·회원 API는 ADMIN 전용)
- 기존 코드 확장: DepartmentService.patch, MemberService.patch/search, MemberRepository.search
- 테스트: OrganizationMemberApiTest, ApiServiceExtensionTest

DTO 5개는 이전 커밋에서 복구했고, 부분 수정·검색 메서드는 누락된 작업 내용을 다시 반영했다.
기존 전체 수정 메서드와 DTO, Entity 및 DB 설계는 유지한다.

## 실행 범위

DB 연결 설정은 별도로 필요하다. JWT 로그인과 ADMIN 접근 제한이 적용되어 있다.
인증 환경변수와 로그인 예시는 [AUTH.md](AUTH.md)를 참고한다.
기존 local-api 무인증 프로필은 제거되었으며 모든 프로필에서 동일한 인증 정책을 적용한다.
자동 교육 배정은 구현되어 있으며 회원 생성·부서/직무·입사일 변경과 연결된다.
세부 트리거와 이력 보존 정책은 [ASSIGNMENT_API.md](ASSIGNMENT_API.md)를 참고한다.

## Endpoint

| Method | 경로 | 기능 | 성공 |
|---|---|---|---|
| POST | /api/departments | 부서 생성 | 201 |
| GET | /api/departments | 전체/활성 부서 목록 | 200 |
| GET | /api/departments/{departmentId} | 부서 조회 | 200 |
| PATCH | /api/departments/{departmentId} | 부서 부분 수정 | 200 |
| PATCH | /api/departments/{departmentId}/status | 부서 활성 상태 변경 | 200 |
| POST | /api/job-positions | 직무 생성 | 201 |
| GET | /api/job-positions | 전체/활성 직무 목록 | 200 |
| GET | /api/job-positions/{jobPositionId} | 직무 조회 | 200 |
| PATCH | /api/job-positions/{jobPositionId} | 직무명 수정 | 200 |
| PATCH | /api/job-positions/{jobPositionId}/status | 직무 활성 상태 변경 | 200 |
| POST | /api/members | 직원 등록 | 201 |
| GET | /api/members | 직원 검색·페이징 | 200 |
| GET | /api/members/{memberId} | 직원 조회 | 200 |
| PATCH | /api/members/{memberId} | 일반 정보 부분 수정 | 200 |
| PATCH | /api/members/{memberId}/department | 부서 변경 | 200 |
| PATCH | /api/members/{memberId}/job-position | 직무 변경 | 200 |
| PATCH | /api/members/{memberId}/status | 재직 상태 변경 | 200 |
| POST | /api/members/{memberId}/roles/{role} | INSTRUCTOR/ADMIN 역할 추가 | 200 |
| DELETE | /api/members/{memberId}/roles/{role} | 역할 제거 | 200 |

생성 응답은 Location 헤더와 생성된 DTO를 반환한다.
조회·수정·역할 변경은 DTO를 반환하며 Entity와 비밀번호 해시는 노출하지 않는다.

## 요청·응답 규약

- 조직 목록: `active=true`는 활성만, 생략 또는 `false`는 전체.
- 조직 상태 변경: `{"active":false}`.
- 직원 목록: `page=0&size=20`이 기본, size 범위는 1~100, ID 오름차순.
- 선택 검색 조건: `name`, `departmentId`, `jobPositionId`, `status`.
- name은 부분 일치 검색이며 사용자 입력의 %, _, !는 검색 문자 그대로 취급한다.
- 부서 PATCH: name 생략은 유지, parentDepartmentId 생략은 유지, 명시적 null은 상위 부서 해제.
- 회원 PATCH: email/name/hireDate 중 제공된 필드만 변경한다. 명시적 null은 400.
- 직무 PATCH: name 필수. 코드·사번은 수정 필드에 포함하지 않는다.
- 빈 회원·부서 PATCH 객체는 현재 정보를 유지한다.
- EMPLOYEE 역할 제거는 기존 업무 규칙으로 400을 반환한다.

직원 목록 응답 예:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

## 오류 응답과 HTTP 상태

```json
{
  "code": "VALIDATION_ERROR",
  "message": "입력값을 확인해주세요.",
  "errors": [
    {"field": "name", "message": "공백일 수 없습니다"}
  ]
}
```

errors는 항상 배열이며 필드 오류가 없으면 빈 배열이다.
잘못된 JSON·Enum에는 원문 입력이나 내부 예외 내용을 응답하지 않는다.

| HTTP | 대상 |
|---|---|
| 401 | 토큰 누락·오류·만료, 로그인 실패, 퇴사 또는 삭제된 회원 |
| 403 | 인증되었으나 ADMIN 권한 없음 |
| 400 | 입력 검증, 잘못된 JSON/Enum/경로 값, 부서 순환·자기 참조, EMPLOYEE 제거 |
| 404 | 부서·직무·회원 없음 |
| 409 | 중복 데이터, 비활성 조직 지정, 허용되지 않은 상태 전이, 퇴사자 정보 수정 |
| 405 / 415 | 지원하지 않는 HTTP 메서드 / 미디어 타입 |
| 500 | 예상하지 못한 서버 오류 (고정 메시지 반환) |

업무 오류의 code는 기존 ErrorCode 이름을 사용한다.
그 외에는 VALIDATION_ERROR, INVALID_REQUEST, INVALID_ARGUMENT,
DATA_CONFLICT, INTERNAL_SERVER_ERROR 등을 사용한다.

## Service 확장 이유

직원 목록 API를 위해 Repository에 선택 조건 JPQL 조회와 Service에 페이지 DTO 변환을 추가했다.
QueryDSL은 사용하지 않는다.
PATCH가 생략된 필드를 삭제하지 않도록 기존 데이터와 병합하는 별도 메서드를 추가했다.
병합·검증은 기존 잠금 트랜잭션 안에서 수행하며 Controller에는 업무 로직을 두지 않는다.

## 검증

- API MockMvc 테스트 42개 통과: 전체 Endpoint 연결, 입력 검증, 상태 코드, 오류 형식, 로컬 보안 필터.
- 복구 Service 확장 테스트 7개 통과: 부분 수정 데이터 보존, null 상위 부서 해제, 검색·페이징.
- 기존 Service·Validation·설정·매핑 테스트 55개 통과.
- 합계 104개, 실패·오류 없음 (두 차례 테스트 실행 결과 합산).
- 실제 MySQL 쿼리 실행·잠금 동시성 및 전체 애플리케이션 기동 테스트는 미수행.
