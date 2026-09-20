# JWT 인증 2단계

## 설정

| 환경변수 | 내용 |
|---|---|
| JWT_SECRET | 무작위 32바이트 이상을 Base64로 인코딩한 HS256 서명 키 |
| JWT_ACCESS_TOKEN_TTL_SECONDS | Access Token 유효 기간(초), 양수 |
| JWT_REFRESH_TOKEN_TTL_SECONDS | Refresh Token 유효 기간 및 Redis TTL(초), 양수 |
| REDIS_HOST | Redis 주소, 기본 localhost |
| REDIS_PORT | Redis 포트, 기본 6379 |

Secret과 두 만료 시간은 필수 설정이다. 키나 토큰을 로그·저장소에 남기지 않는다.
기존 DB 연결 설정도 필요하다. local 프로필에서도 동일한 인증 정책을 사용한다.

## API

| 요청 | 접근 정책 | 성공 |
|---|---|---|
| POST /api/auth/login | permitAll | 200 |
| POST /api/auth/refresh | permitAll, 본문의 Refresh Token 검증 | 200 |
| POST /api/auth/logout | Access Token 인증 필요, 역할 무관 | 204 |
| /api/departments 및 하위 경로 | ADMIN | 기존 정책 유지 |
| /api/job-positions 및 하위 경로 | ADMIN | 기존 정책 유지 |
| /api/members 및 하위 경로 | ADMIN | 기존 정책 유지 |
| Course/콘텐츠 조회 | ADMIN 또는 INSTRUCTOR | 200 |
| Course/콘텐츠 변경, 규칙·수동배정·시험 구성 관리 | ADMIN | 각 API 정책 |
| 본인 수강 목록·진도·시험 응시 | EMPLOYEE, Service에서 소유권 확인 | 각 API 정책 |
| 타인 진도·시험 결과·이력 조회 | ADMIN 예외 허용, 변경은 불가 | 200 |
| 명시적으로 허용하지 않은 경로 | 차단 | - |

권한 matcher의 정확한 경로와 관리/학습 구분은 COURSE_API.md, ASSIGNMENT_API.md,
PROGRESS_API.md, EXAM_API.md, EXAM_ATTEMPT_API.md를 함께 참고한다.

로그인 본문:

```json
{"email":"user@example.com","password":"사용자의 비밀번호"}
```

재발급 본문 (Authorization 헤더가 아님):

```json
{"refreshToken":"<현재 Refresh Token>"}
```

로그인과 재발급은 기존 LoginResponse를 공유하고 Cache-Control: no-store를 적용한다.

```json
{
  "accessToken": "<Access Token>",
  "refreshToken": "<새 Refresh Token>",
  "tokenType": "Bearer",
  "accessTokenExpiresInSeconds": 1800,
  "refreshTokenExpiresInSeconds": 604800
}
```

위 시간은 예시이며 실제 값은 환경변수 설정을 따른다.
로그아웃은 Authorization: Bearer <accessToken> 헤더로 호출하며 응답 본문은 없다.

## 인증 및 재발급 흐름

- 로그인은 이메일 정규화 후 기존 PasswordEncoder로 비밀번호를 검증한다.
- ACTIVE와 ON_LEAVE는 로그인·재발급 가능하며, 없는 회원과 RESIGNED는 401이다.
- Access Token 업무 클레임은 memberId/email/roles이며 tokenType=ACCESS, iat/exp를 포함한다.
- Refresh Token은 memberId, tokenType=REFRESH, iat/exp 및 무작위 jti를 포함한다.
  jti는 같은 초에 재발급해도 이전 Refresh Token과 값이 같아지는 문제를 방지한다.
- HS256 서명, 시간 및 토큰 종류를 검증한다. 서로의 용도로 사용할 수 없다.
- JWT 필터는 Access Token 전용이며 현재 DB 회원 상태·역할을 매 요청 재조회한다.
- POST login/refresh에서는 Bearer 헤더를 무시한다. 재발급은 본문의 토큰만 검증한다.
- SessionCreationPolicy.STATELESS를 유지하며 세션·쿠키 인증은 사용하지 않는다.

재발급 처리 순서:

1. 요청 검증 및 parseRefreshToken으로 JWT 검증, memberId 추출.
2. Redis 저장값과 요청 토큰 일치 확인.
3. 기존 MemberAuthenticationService를 통해 역할 포함 회원 조회 및 퇴사 검증.
4. MemberPrincipal.from(member)로 최신 DB 이메일·역할을 사용하여 새 토큰 발급.
5. Redis Lua 스크립트에서 이전 값이 여전히 일치할 때만 새 값과 TTL로 교체.
6. 교체 성공 시 두 토큰 반환. 경쟁 요청에 패배하면 같은 401 응답.

Access Token은 새로 발급하지만 정보와 발급 초가 동일하면 문자열도 같을 수 있다.
Refresh Token은 jti 덕분에 항상 새 값으로 회전한다.

## Rotation 및 로그아웃

Redis 키는 기존 auth:refresh:{memberId}를 유지한다. 회원당 Refresh Token은 하나다.
로그인 시 기존 값을 덮어쓰므로 다른 기기의 이전 Refresh Token도 사용할 수 없다.

Rotation은 GET 비교와 SET ... EX를 한 Lua 스크립트에서 수행한다.
delete 후 save하지 않는다. 동시 요청은 한 건만 교체할 수 있으며,
로그아웃이 먼저 키를 지웠다면 재발급 요청이 키를 복원하지 못한다.
불일치·재사용 실패 시 현재 유효한 Refresh Token은 삭제하지 않는다.
원자 실행 보장은 [Redis 공식 문서](https://redis.io/docs/latest/develop/programmability/eval-intro/)를 따른다.

로그아웃은 인증된 MemberPrincipal의 memberId로 해당 Redis 키만 삭제한다.
Access Token blacklist는 없으므로 기존 Access Token은 만료 전까지 유효할 수 있다.
이미 로그아웃한 Access Token으로 다시 logout을 호출해도 204를 반환한다.
회원당 하나의 키이므로 현재 회원의 Refresh Token 전체가 삭제된다.
Redis 장애는 인증 불일치로 위장하지 않으며 기존 공통 서버 오류(500)로 처리한다.

## 오류 계약

```json
{"code":"UNAUTHORIZED","message":"인증이 필요하거나 인증 정보가 유효하지 않습니다.","errors":[]}
```

- 400: 누락·빈 Refresh Token 등 요청 검증 실패 또는 잘못된 JSON.
- 401: JWT 오류·만료·토큰 종류 불일치, Redis 값 없음/불일치/재사용,
  삭제·퇴사 회원, 미인증 logout. WWW-Authenticate: Bearer 포함.
- 403: 인증된 회원의 관리 API 권한 부족.
- 500: 예상하지 못한 서버/인프라 오류.

응답에는 인증 실패의 세부 원인이나 토큰 값을 노출하지 않는다.
요청/응답 DTO의 toString은 민감값을 REDACTED 처리한다.
로그인과 재발급 외 공개 회원가입·관리자 초기화 API는 제공하지 않는다.

## 테스트

일반 회귀 테스트:

```text
mvn test
```

JWT 생성·검증, 로그인·재발급·로그아웃 API, Security, Redis 호출 계약 및 기존
조직/회원 테스트를 포함한다. 일반 테스트에서는 DB와 Redis 저장소를 대체한다.

실제 테스트용 Redis가 준비된 경우 원자 교체·TTL·동시성 테스트를 명시적으로 실행한다:

```text
mvn -Dtest=RefreshTokenRedisIntegrationTest -Dredis.integration-test=true test
```

기본 localhost:6379이며 redis.test.host / redis.test.port로 변경할 수 있다.
인증 없는 개발용 Redis를 대상으로 하며 실제 양수 회원 ID와 겹치지 않는
임의 음수 ID 키만 사용한다. 해당 임시 키만 정리하며 FLUSHDB는 사용하지 않는다.
활성화 옵션이 없으면 이 통합 테스트는 건너뛴다.
BeApplicationTests는 외부 DataSource/Redis 연결만 대체하는 부팅 스모크 테스트이며 기본 실행에 포함된다.
실제 MySQL SQL·잠금·롤백 검증은 별도 격리 환경이 필요하다. 최신 결과는 STABILIZATION_REVIEW.md를 참고한다.

기존 Entity·조직/회원 Service·DB 설계는 변경하지 않았다.
Access blacklist, 멀티 디바이스, RedisHash, DB 토큰 테이블, OAuth2, 회원가입,
비밀번호 찾기, MFA 및 Course/Frontend/Mobile 기능은 범위에 포함하지 않는다.
