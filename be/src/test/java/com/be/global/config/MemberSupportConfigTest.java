package com.be.global.config;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

// 실제 비밀번호 인코더와 UTC 시계 설정 검증
class MemberSupportConfigTest {
    @Test
    void encodesPasswordWithSaltAndMatchesOriginal() {
        var encoder = new MemberSupportConfig().passwordEncoder();
        String raw = "한글도가능한Password123!";
        String first = encoder.encode(raw);
        String second = encoder.encode(raw);
        assertThat(first).isNotEqualTo(raw).isNotEqualTo(second);
        assertThat(first.length()).isLessThanOrEqualTo(255);
        assertThat(encoder.matches(raw, first)).isTrue();
        assertThat(encoder.matches("incorrect", first)).isFalse();
    }

    @Test
    void providesUtcClock() {
        assertThat(new MemberSupportConfig().clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
