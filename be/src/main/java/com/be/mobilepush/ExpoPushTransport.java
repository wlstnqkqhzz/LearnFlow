package com.be.mobilepush;

import java.io.IOException;
import java.util.*;

public interface ExpoPushTransport extends AutoCloseable {
    record Result(int status, String ticketId, String error, String retryAfter) {}
    record Receipts(int status, Map<String, String> errors, String retryAfter) {}
    Result send(MobilePushDeliveryStore.Target target, Long notificationId) throws IOException;
    // A present entry with value "ok" is a successful receipt; missing means not available yet.
    Receipts receipts(List<String> ticketIds) throws IOException;
    @Override default void close() throws IOException {}
}
