# Access Token 인증 1단계

## 설정

필수 환경변수:

| 이름 | 내용 |
|---|---|
| JWT_SECRET | 무작위 32바이트 이상을 Base64로 인코딩한 HS256 서명 키 |
| JWT_ACCESS_TOKEN_TTL_SECONDS | Access Token 유효 기간(초), 양의 정수. 예: 1800 |

Secret과 만료 시간의 기본값은 제공하지 않는다. 값이 없거나 키가 짧으면 기동을 중단한다.
Secret은 비밀번호 문구가 아니라 암호학적 난수로 생성하고 저장소에 커밋하지 않는다.
기존 DB 연결 설정도 필요하다. 환경변수는 실행하는 IDE 또는 프로세스에 설정한다.

## 로그인

```http
POST /api/auth/login
Content-Type: application/json

{"email":"user@example.com","password":"사용자의 비밀번호"}
```

정상 응답은 200이며 Cache-Control: no-store를 적용한다.

```json
{
  "accessToken": "<서명된 JWT>",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

이메일은 trim/lowercase로 정규화하고 기존 PBKDF2 PasswordEncoder로 검증한다.
ACTIVE와 ON_LEAVE는 로그인 가능하다. RESIGNED, 비밀번호 불일치, 없는 계정은
모두 같은 401 오류를 반환하여 계정의 존재·상태를 응답으로 구분하지 않는다.

JWT 업무 클레임은 memberId, email, roles만 포함한다.
표준 시간 클레임 iat(발급 시각), exp(만료 시각)를 추가하며 비밀번호·부서 등은 넣지 않는다.
HS256만 허용하고 서명, 만료, 발급 시각, 필수 클레임을 검증한다.

## 인증 흐름

1. 로그인 요청의 이메일·비밀번호 검증.
2. DB Member의 상태·역할을 조회하여 Access Token 발급.
3. 이후 요청은 Authorization: Bearer <accessToken> 헤더로 전달.
4. JwtAuthenticationFilter에서 토큰 검증.
5. memberId로 현재 DB 회원을 재조회하고 퇴사·삭제 여부 확인.
6. 현재 역할을 ROLE_EMPLOYEE / ROLE_INSTRUCTOR / ROLE_ADMIN 권한으로 변환.
7. 비밀번호 없는 MemberPrincipal을 SecurityContext에 저장하고 API 접근 판단.

roles/email 클레임은 발급 시점의 정보다. 실제 요청 권한은 DB의 최신 역할을 사용하므로
이미 발급된 토큰이 있어도 퇴사·ADMIN 제거는 이후 요청에 반영된다.
이 설계는 매 요청 DB 조회가 필요하며 Redis나 토큰 저장소를 사용하지 않는다.

로그인 요청에서는 오래된 Bearer 헤더를 무시하여 재로그인이 가능하다.
SessionCreationPolicy.STATELESS를 사용하고 Form Login, HTTP Basic, 기본 Logout,
Request Cache를 비활성화했다. 세션·쿠키 인증을 사용하지 않는다.

## 권한 및 오류

| 경로 | 정책 |
|---|---|
| POST /api/auth/login | 인증 없이 허용 |
| /api/departments 및 하위 경로 | ADMIN |
| /api/job-positions 및 하위 경로 | ADMIN |
| /api/members 및 하위 경로 | ADMIN |
| 그 외 경로 | 차단 |

로그인 성공과 관리 API 권한은 별개이므로 EMPLOYEE·INSTRUCTOR도 로그인은 가능하지만
관리 API 호출은 403이다. ON_LEAVE 회원도 ADMIN 역할이 있으면 관리 API를 사용할 수 있다.
기존 local-api 무인증 예외는 제거되었다.

```json
{"code":"UNAUTHORIZED","message":"인증이 필요하거나 인증 정보가 유효하지 않습니다.","errors":[]}
```

- 400: 로그인 요청 입력 오류
- 401: 인증 없음, 유효하지 않은 토큰, 로그인 실패. WWW-Authenticate: Bearer 포함
- 403: 인증된 회원의 권한 부족

첫 ADMIN 계정은 기존 관리 절차나 별도 초기 데이터로 준비해야 한다.
이번 구현에는 공개 회원가입이나 관리자 부트스트랩 API를 추가하지 않았다.

## 구현 범위와 검증

새 파일: auth/controller/AuthController, auth/service/AuthService,
auth/dto/LoginRequest·LoginResponse, global/config/SecurityConfig,
global/security/JwtProperties·JwtTokenProvider·JwtAuthenticationFilter·MemberPrincipal·
MemberAuthenticationService·JsonAuthenticationEntryPoint·JsonAccessDeniedHandler.

수정: pom.xml(JWT 라이브러리), application.yaml(환경변수),
MemberRepository(역할 포함 인증 조회), GlobalExceptionHandler(로그인 401),
기존 API 테스트(ADMIN 인증 문맥). MemberService·Entity와 비밀번호 해시는 유지한다.
삭제: LocalApiSecurityConfig(기존 커밋에서 복구 가능하지만 무인증 우회를 다시 적용하면 안 됨).

검증 명령: Maven에서 `-Dtest=*Test test`.
JWT·인증 API·설정 검증과 기존 API/Service 회귀 테스트를 포함한다.
실제 MySQL 연결 및 전체 애플리케이션 기동 테스트인 BeApplicationTests는 별도 환경이 필요하다.

Refresh Token, Redis 인증, Rotation, refresh/logout API, OAuth2, 비밀번호 찾기,
Course 기능은 포함하지 않는다.

라이브러리 참고: [Spring Security JWT SecretKey 검증](https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.SecretKeyJwtDecoderBuilder.html).
