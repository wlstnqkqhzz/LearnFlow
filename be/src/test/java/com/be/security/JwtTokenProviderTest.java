package com.be.security;

import com.be.global.security.*;
import com.be.member.enums.Role;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.*;

// 실제 HMAC 서명·만료·필수 클레임 검증
class JwtTokenProviderTest {
    private final Instant now = Instant.parse("2026-09-18T01:00:00Z");
    private final JwtProperties properties = new JwtProperties(JwtTestSupport.secret(), 300, 3600);
    private final MemberPrincipal principal = new MemberPrincipal(1L, "user@example.com",
            Set.of(Role.EMPLOYEE, Role.ADMIN));

    private JwtTokenProvider provider(Instant instant) {
        return new JwtTokenProvider(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void issuesOnlyExpectedClaimsAndConvertsAuthorities() throws Exception {
        String token = provider(now).createAccessToken(principal);
        var claims = SignedJWT.parse(token).getJWTClaimsSet();
        assertThat(claims.getClaims()).containsOnlyKeys("memberId", "email", "roles", "iat", "exp", "tokenType");
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(now.plusSeconds(300));
        assertThat(provider(now).parseAccessToken(token)).isEqualTo(principal);
        assertThat(principal.authorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_EMPLOYEE", "ROLE_ADMIN");
    }

    @Test
    void rejectsExpiredTokenIncludingExactBoundary() {
        String token = provider(now).createAccessToken(principal);
        assertThatThrownBy(() -> provider(now.plusSeconds(300)).parseAccessToken(token)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider(now.plusSeconds(301)).parseAccessToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsDifferentSigningKey() {
        String token = provider(now).createAccessToken(principal);
        var other = new JwtTokenProvider(new JwtProperties(JwtTestSupport.newSecret(), 300, 3600),
                Clock.fixed(now, ZoneOffset.UTC));
        assertThatThrownBy(() -> other.parseAccessToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedSignatureAndMalformedToken() {
        String token = provider(now).createAccessToken(principal);
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        String tampered = String.join(".", parts);
        assertThatThrownBy(() -> provider(now).parseAccessToken(tampered)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider(now).parseAccessToken("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnsignedAndWrongAlgorithm() throws Exception {
        var claims = claims().build();
        assertThatThrownBy(() -> provider(now).parseAccessToken(new PlainJWT(claims).serialize()))
                .isInstanceOf(JwtException.class);
        String hs512 = sign(claims, JWSAlgorithm.HS512);
        assertThatThrownBy(() -> provider(now).parseAccessToken(hs512)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMissingExpirationOrUnknownRoles() throws Exception {
        String missingExpiry = sign(new JWTClaimsSet.Builder().issueTime(Date.from(now))
                .claim("tokenType", "ACCESS").claim("memberId", 1L).claim("email", "user@example.com")
                .claim("roles", List.of("EMPLOYEE")).build(), JWSAlgorithm.HS256);
        String unknownRole = sign(claims().claim("roles", List.of("EMPLOYEE", "SUPERUSER")).build(),
                JWSAlgorithm.HS256);
        assertThatThrownBy(() -> provider(now).parseAccessToken(missingExpiry)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider(now).parseAccessToken(unknownRole)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsFutureIssuedToken() {
        String token = provider(now.plusSeconds(10)).createAccessToken(principal);
        assertThatThrownBy(() -> provider(now).parseAccessToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWeakOrInvalidConfiguration() {
        for (String secret : List.of("not base64!", Base64.getEncoder().encodeToString(new byte[16]))) {
            assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties(secret, 300, 3600), Clock.systemUTC()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new JwtTokenProvider(
                new JwtProperties(JwtTestSupport.secret(), 0, 3600), Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(properties.toString()).doesNotContain(properties.secret());
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("memberId", 1L).claim("email", "user@example.com")
                .claim("roles", List.of("EMPLOYEE"));
    }

    @Test
    void refreshTokensAreUniqueEvenWithinSameSecondAndHaveMinimalClaims() throws Exception {
        String first = provider(now).createRefreshToken(1L);
        String second = provider(now).createRefreshToken(1L);
        assertThat(first).isNotEqualTo(second);
        assertThat(provider(now).parseRefreshToken(first)).isEqualTo(1L);
        var claims = SignedJWT.parse(first).getJWTClaimsSet();
        assertThat(claims.getClaims()).containsOnlyKeys("memberId", "tokenType", "jti", "iat", "exp");
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(now.plusSeconds(3600));
        assertThatThrownBy(() -> provider(now).parseAccessToken(first)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider(now).parseRefreshToken(provider(now).createAccessToken(principal)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider(now.plusSeconds(3600)).parseRefreshToken(first))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesNonPositiveRefreshTtl() {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties(JwtTestSupport.secret(), 300, 0),
                Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    }

    private String sign(JWTClaimsSet claims, JWSAlgorithm algorithm) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new MACSigner(Base64.getDecoder().decode(properties.secret())));
        return jwt.serialize();
    }
}
