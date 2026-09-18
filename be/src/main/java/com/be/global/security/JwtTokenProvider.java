package com.be.global.security;

import com.be.member.enums.Role;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

// HS256 기반 Access Token / Refresh Token 발급 및 검증
// JWT Secret은 Base64 인코딩된 최소 32바이트 키를 사용하고,
// Access Token과 Refresh Token은 tokenType Claim으로 명확하게 구분한다.
public class JwtTokenProvider {
    // JWT 내부에서 토큰 종류를 구분하기 위한 Claim 이름
    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    // Access Token 식별 값
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";

    // Refresh Token 식별 값
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final JwtProperties properties;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;

        // application 설정의 Base64 JWT Secret을 실제 바이트 키로 변환
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("JWT_SECRET은 유효한 Base64 키여야 합니다.");
        }
        if (key.length < 32 || properties.accessTokenTtlSeconds() <= 0 || properties.refreshTokenTtlSeconds() <= 0) {
            throw new IllegalArgumentException("JWT 키는 최소 32바이트, 만료 시간은 양수여야 합니다.");
        }
        // HS256 서명에 사용할 SecretKey 생성
        var secret = new SecretKeySpec(key, "HmacSHA256");

        // JWT 발급 Encoder 설정
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret));

        // JWT 검증 Decoder 설정
        // 허용 알고리즘은 HS256으로 고정한다.
        decoder = NimbusJwtDecoder.withSecretKey(secret).macAlgorithm(MacAlgorithm.HS256).build();

        // JWT의 issuedAt / expiresAt 시간을 검증한다.
        // Clock을 주입받아 테스트에서도 동일한 시간 기준을 사용할 수 있도록 한다.
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(timestamps);
    }

    // Access Token 생성
    // 실제 API 인증에 필요한 회원 식별자, 이메일, 현재 역할 정보를 포함한다.
    public String createAccessToken(MemberPrincipal principal) {
        Instant now = clock.instant();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.accessTokenTtlSeconds()))
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .claim("memberId", principal.memberId())
                .claim("email", principal.email())
                .claim("roles", principal.roles().stream().map(Role::name).sorted().toList())
                .build();
        return encode(claims);
    }

    // Refresh Token 생성
    // Refresh Token은 Access Token 재발급 용도로만 사용하므로
    // 회원 식별에 필요한 memberId만 포함한다.
    //
    // 이메일 및 역할은 재발급 시 DB에서 다시 조회하여
    // 최신 회원 정보와 권한을 Access Token에 반영한다.
    public String createRefreshToken(Long memberId) {
        Instant now = clock.instant();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.refreshTokenTtlSeconds()))
                // 같은 초에도 다른 토큰을 발급하여 회전된 이전 토큰 재사용 차단
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .claim("memberId", memberId)
                .build();

        return encode(claims);
    }

    public long expiresInSeconds() {
        return properties.accessTokenTtlSeconds();
    }

    // Access Token 검증 및 인증 정보 추출
    //
    // 서명, 만료 시간, tokenType 및 필수 Claim을 검증한 뒤
    // Spring Security 인증에 사용할 MemberPrincipal로 변환한다.
    public MemberPrincipal parseAccessToken(String token) {
        try {
            Jwt jwt = decoder.decode(token);

            validateCommonClaims(jwt);
            validateTokenType(jwt, ACCESS_TOKEN_TYPE);

            Long memberId = Long.valueOf(
                    jwt.getClaimAsString("memberId")
            );

            String email = jwt.getClaimAsString("email");

            Set<Role> roles = jwt.getClaimAsStringList("roles")
                    .stream()
                    .map(Role::valueOf)
                    .collect(Collectors.toUnmodifiableSet());

            // 유효한 회원 ID, 이메일 및 기본 EMPLOYEE 역할이 반드시 존재해야 한다.
            if (memberId <= 0
                    || email == null
                    || email.isBlank()
                    || !roles.contains(Role.EMPLOYEE)) {
                throw new IllegalArgumentException();
            }

            return new MemberPrincipal(
                    memberId,
                    email,
                    roles
            );

        } catch (RuntimeException exception) {
            throw new BadJwtException(
                    "유효하지 않은 Access Token입니다."
            );
        }
    }

    // Refresh Token 검증 및 memberId 추출
    //
    // Refresh Token에는 회원의 현재 역할이나 이메일을 신뢰하지 않고,
    // memberId만 추출하여 이후 DB에서 최신 회원 정보를 다시 조회한다.
    public Long parseRefreshToken(String token) {
        try {
            Jwt jwt = decoder.decode(token);

            validateCommonClaims(jwt);
            validateTokenType(jwt, REFRESH_TOKEN_TYPE);

            Long memberId = Long.valueOf(
                    jwt.getClaimAsString("memberId")
            );

            if (memberId <= 0) {
                throw new IllegalArgumentException();
            }

            return memberId;

        } catch (RuntimeException exception) {
            throw new BadJwtException(
                    "유효하지 않은 Refresh Token입니다."
            );
        }
    }

    // Access Token 만료 시간(초) 반환
    // 로그인 응답의 expiresIn 등에 사용할 수 있다.
    public long accessTokenExpiresInSeconds() {
        return properties.accessTokenTtlSeconds();
    }

    // Refresh Token 만료 시간(초) 반환
    // Redis Refresh Token TTL 설정에 사용한다.
    public long refreshTokenExpiresInSeconds() {
        return properties.refreshTokenTtlSeconds();
    }

    // 공통 JWT 인코딩 처리
    // 알고리즘은 HS256, Token Type Header는 JWT로 고정한다.
    private String encode(JwtClaimsSet claims) {
        return encoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256)
                                .type("JWT")
                                .build(),
                        claims
                )
        ).getTokenValue();
    }

    // Access Token과 Refresh Token의 용도가 섞이지 않도록
    // tokenType Claim이 기대한 종류인지 검증한다.
    private void validateTokenType(
            Jwt jwt,
            String expectedTokenType
    ) {
        String tokenType = jwt.getClaimAsString(
                TOKEN_TYPE_CLAIM
        );

        if (!expectedTokenType.equals(tokenType)) {
            throw new IllegalArgumentException();
        }
    }

    // JWT 공통 시간 Claim 검증
    //
    // issuedAt / expiresAt 존재 여부,
    // 미래에 발급된 토큰 여부,
    // 이미 만료된 토큰 여부,
    // 발급 시각보다 만료 시각이 뒤인지 확인한다.
    private void validateCommonClaims(Jwt jwt) {
        Instant now = clock.instant();

        if (jwt.getIssuedAt() == null
                || jwt.getExpiresAt() == null
                || !jwt.getExpiresAt().isAfter(now)
                || jwt.getIssuedAt().isAfter(now)
                || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())) {
            throw new IllegalArgumentException();
        }
    }
}
