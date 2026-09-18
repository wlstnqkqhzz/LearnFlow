package com.be.global.security;

import com.be.member.enums.Role;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

// HS256 Access Token 발급·검증 - 임의 알고리즘이나 만료 없는 토큰은 허용하지 않음
public class JwtTokenProvider {
    private final JwtProperties properties;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("JWT_SECRET은 유효한 Base64 키여야 합니다.");
        }
        if (key.length < 32 || properties.accessTokenTtlSeconds() <= 0) {
            throw new IllegalArgumentException("JWT 키는 최소 32바이트, 만료 시간은 양수여야 합니다.");
        }
        var secret = new SecretKeySpec(key, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret));
        decoder = NimbusJwtDecoder.withSecretKey(secret).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(timestamps);
    }

    public String createAccessToken(MemberPrincipal principal) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.accessTokenTtlSeconds()))
                .claim("memberId", principal.memberId())
                .claim("email", principal.email())
                .claim("roles", principal.roles().stream().map(Role::name).sorted().toList())
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }

    public long expiresInSeconds() {
        return properties.accessTokenTtlSeconds();
    }

    public MemberPrincipal parse(String token) {
        Jwt jwt = decoder.decode(token);
        try {
            Instant now = clock.instant();
            if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
                    || !jwt.getExpiresAt().isAfter(now)
                    || jwt.getIssuedAt().isAfter(now)
                    || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())) {
                throw new IllegalArgumentException();
            }
            Long memberId = Long.valueOf(jwt.getClaimAsString("memberId"));
            String email = jwt.getClaimAsString("email");
            Set<Role> roles = jwt.getClaimAsStringList("roles").stream()
                    .map(Role::valueOf).collect(Collectors.toUnmodifiableSet());
            if (memberId <= 0 || email == null || email.isBlank() || !roles.contains(Role.EMPLOYEE)) {
                throw new IllegalArgumentException();
            }
            return new MemberPrincipal(memberId, email, roles);
        } catch (RuntimeException exception) {
            throw new BadJwtException("유효하지 않은 Access Token입니다.");
        }
    }
}
