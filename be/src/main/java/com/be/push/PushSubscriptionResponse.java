package com.be.push;

import java.time.Instant;

public record PushSubscriptionResponse(Long subscriptionId, boolean enabled, Instant expirationTime,
        Instant createdAt, Instant updatedAt) {
    public static PushSubscriptionResponse from(PushSubscription value) {
        return new PushSubscriptionResponse(value.getId(), value.isEnabled(), value.getExpirationTime(),
                value.getCreatedAt(), value.getUpdatedAt());
    }
}
