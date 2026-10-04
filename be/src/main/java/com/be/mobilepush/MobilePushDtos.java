package com.be.mobilepush;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class MobilePushDtos {
    private MobilePushDtos() {}
    public record Binding(@NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") String installationId,
                          @NotNull MobilePushSubscription.Platform platform, @NotNull @PositiveOrZero Long version) {}
    public record Registration(@NotBlank @Size(max = 512)
                               @Pattern(regexp = "(?:ExpoPushToken|ExponentPushToken)\\[[A-Za-z0-9_-]+\\]") String expoPushToken,
                               @NotNull MobilePushSubscription.Platform platform, @NotNull @PositiveOrZero Long version) {
        @Override public String toString() { return "Registration[redacted]"; }
    }
    public record Response(Long subscriptionId, MobilePushSubscription.Platform platform, boolean enabled, long version, Instant updatedAt) {
        static Response from(MobilePushSubscription s) { return new Response(s.getId(), s.getPlatform(), s.isEnabled(), s.getVersion(), s.getUpdatedAt()); }
    }
}
