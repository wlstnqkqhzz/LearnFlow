package com.be.push;

import java.nio.charset.StandardCharsets;
import nl.martijndwars.webpush.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Library does crypto/VAPID; our HTTP client owns egress, timeouts and redirects. */
public final class WebPushTransport implements PushTransport, AutoCloseable {
    private final PushService service;
    private final PushEndpointPolicy policy;
    private final CloseableHttpClient client;
    public WebPushTransport(PushService service, PushEndpointPolicy policy) {
        this.service = service; this.policy = policy;
        this.client = HttpClients.custom().setDnsResolver(policy::resolve)
                .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement()
                .setMaxConnTotal(2).setMaxConnPerRoute(2)
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(3000)
                        .setSocketTimeout(5000).setConnectionRequestTimeout(1000).build()).build();
    }
    static byte[] payload(Long notificationId) {
        if (notificationId == null || notificationId <= 0) throw new IllegalArgumentException("Invalid notification ID");
        return ("{\"version\":1,\"notificationId\":" + notificationId
                + ",\"title\":\"LearnFlow 새 알림\",\"message\":\"새 교육 알림이 있습니다. LearnFlow에서 확인하세요.\"}")
                .getBytes(StandardCharsets.UTF_8);
    }
    @Override public Result send(PushDeliveryStore.Target target, Long notificationId) throws Exception {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Push must run outside DB transaction");
        policy.validateEndpoint(target.endpoint());
        var notification = new Notification(target.endpoint(), target.p256dh(), target.auth(), payload(notificationId), 600);
        var request = service.preparePost(notification, Encoding.AES128GCM);
        policy.validateEndpoint(request.getURI().toString());
        try (var response = client.execute(request)) {
            var retry = response.getFirstHeader("Retry-After");
            return new Result(response.getStatusLine().getStatusCode(), retry == null ? null : retry.getValue());
        } finally { request.abort(); }
    }
    @Override public void close() throws java.io.IOException { client.close(); }
}
