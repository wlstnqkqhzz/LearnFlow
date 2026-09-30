package com.be.push;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record PushSubscriptionRequest(@NotBlank @Size(max = 2048) String endpoint,
        @NotBlank @Size(max = 100) String p256dh, @NotBlank @Size(max = 32) String auth,
        Instant expirationTime) {
    @Override public String toString() { return "PushSubscriptionRequest[REDACTED]"; }
}
