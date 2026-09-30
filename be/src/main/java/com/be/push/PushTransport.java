package com.be.push;

public interface PushTransport {
    Result send(PushDeliveryStore.Target target, Long notificationId) throws Exception;
    record Result(int status, String retryAfter) {}
}
