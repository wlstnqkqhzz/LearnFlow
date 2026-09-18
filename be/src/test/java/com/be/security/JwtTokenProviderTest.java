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
    private final JwtProperties properties = new JwtProperties(JwtTestSupport.secret(), 300);
    private final MemberPrincipal principal = new MemberPrincipal(1L, "user@example.com",
            Set.of(Role.EMPLOYEE, Role.ADMIN));

    private JwtTokenProvider provider(Instant instant) {
        return new JwtTokenProvider(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void issuesOnlyExpectedClaimsAndConvertsAuthorities() throws Exception {
        String token = provider(now).createAccessToken(principal);
        var claims = SignedJWT.parse(token).getJWTClaimsSet();
        assertThat(claims.getClaims()).containsOnlyKeys("memberId", "email", "roles", "iat", "exp");
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(now.plusSeconds(300));
        assertThat(provider(now).parse(token)).isEqualTo(principal);
        assertThat(principal.authorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_EMPLOYEE", "ROLE_ADMIN");
    }

    @Test
    void rejectsExpiredTokenIncludingExactBoundary() {
        String token = provider(now).createAccessToken(principal);
        assertThatThrownBy(() -> provider(now.plusSeconds(300)).parse(token)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider(now.plusSeconds(301)).parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsDifferentSigningKey() {
        String token = provider(now).createAccessToken(principal);
        var other = new JwtTokenProvider(new JwtProperties(JwtTestSupport.newSecret(), 300),
                Clock.fixed(now, ZoneOffset.UTC));
        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedSignatureAndMalformedToken() {
        String token = provider(now).createAccessToken(principal);
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        String tampered = String.join(".", parts);
        assertThatThrownBy(() -> provider(now).parse(tampered)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider(now).parse("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnsignedAndWrongAlgorithm() throws Exception {
        var claims = claims().build();
        assertThatThrownBy(() -> provider(now).parse(new PlainJWT(claims).serialize()))
                .isInstanceOf(JwtException.class);
        String hs512 = sign(claims, JWSAlgorithm.HS512);
        assertThatThrownBy(() -> provider(now).parse(hs512)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMissingExpirationOrUnknownRoles() throws Exception {
        String missingExpiry = sign(new JWTClaimsSet.Builder().issueTime(Date.from(now))
                .claim("memberId", 1L).claim("email", "user@example.com")
                .claim("roles", List.of("EMPLOYEE")).build(), JWSAlgorithm.HS256);
        String unknownRole = sign(claims().claim("roles", List.of("EMPLOYEE", "SUPERUSER")).build(),
                JWSAlgorithm.HS256);
        assertThatThrownBy(() -> provider(now).parse(missingExpiry)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider(now).parse(unknownRole)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsFutureIssuedToken() {
        String token = provider(now.plusSeconds(10)).createAccessToken(principal);
        assertThatThrownBy(() -> provider(now).parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWeakOrInvalidConfiguration() {
        for (String secret : List.of("not base64!", Base64.getEncoder().encodeToString(new byte[16]))) {
            assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties(secret, 300), Clock.systemUTC()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new JwtTokenProvider(
                new JwtProperties(JwtTestSupport.secret(), 0), Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(properties.toString()).doesNotContain(properties.secret());
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("memberId", 1L).claim("email", "user@example.com")
                .claim("roles", List.of("EMPLOYEE"));
    }

    private String sign(JWTClaimsSet claims, JWSAlgorithm algorithm) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new MACSigner(Base64.getDecoder().decode(properties.secret())));
        return jwt.serialize();
    }
}
