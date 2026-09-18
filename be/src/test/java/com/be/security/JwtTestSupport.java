package com.be.security;

import java.security.SecureRandom;
import java.util.Base64;

// 운영 설정과 무관한 테스트 실행 전용 무작위 Secret
public final class JwtTestSupport {
    private static final String SECRET = newSecret();

    private JwtTestSupport() {
    }

    public static String secret() {
        return SECRET;
    }

    public static String newSecret() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
