package com.be.mobilepush;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.*;
import org.apache.http.impl.client.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.*;

/** Fixed HTTPS destination, bounded response, explicit timeouts, no redirects or implicit retries. */
public final class HttpExpoPushTransport implements ExpoPushTransport {
    private final MobilePushProperties properties;
    private final ObjectMapper mapper;
    private final CloseableHttpClient client;
    public HttpExpoPushTransport(MobilePushProperties properties, ObjectMapper mapper) {
        this.properties = properties; this.mapper = mapper;
        client = HttpClients.custom().disableRedirectHandling().disableAutomaticRetries().disableCookieManagement()
                .setMaxConnTotal(2).setMaxConnPerRoute(2)
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(3000).setSocketTimeout(5000)
                        .setConnectionRequestTimeout(1000).build()).build();
    }
    static Map<String, Object> payload(String token, Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Invalid notification ID");
        return Map.of("to", token, "title", "LearnFlow", "body", "새 교육 알림이 있습니다. 앱에서 확인하세요.",
                "data", Map.of("notificationId", id.toString()), "channelId", "learnflow-notifications", "ttl", 600);
    }
    @Override public Result send(MobilePushDeliveryStore.Target target, Long id) throws IOException {
        var response = post("send", payload(target.token(), id));
        return ticket(response.status, response.body, response.retryAfter);
    }
    static Result ticket(int status, JsonNode body, String retryAfter) {
        var data = body.path("data");
        if (data.isArray() && data.size() == 1) data = data.get(0);
        String ticketId = data.path("id").asText("");
        boolean accepted = status == 200 && "ok".equals(data.path("status").asText()) && ticketId.matches("[A-Za-z0-9_-]{1,128}");
        return new Result(status, accepted ? ticketId : null, accepted ? null : error(data), retryAfter);
    }
    @Override public Receipts receipts(List<String> ids) throws IOException {
        if (ids.isEmpty() || ids.size() > 1000) throw new IllegalArgumentException("Invalid receipt batch");
        var response = post("getReceipts", Map.of("ids", ids));
        return parseReceipts(response.status, response.body, response.retryAfter, ids);
    }
    static Receipts parseReceipts(int status, JsonNode body, String retryAfter, List<String> ids) {
        Map<String, String> results = new HashMap<>();
        var data = body.path("data");
        for (String id : ids) if (data.has(id)) {
            var item = data.path(id);
            results.put(id, "ok".equals(item.path("status").asText()) ? "ok" : error(item));
        }
        return new Receipts(status, Map.copyOf(results), retryAfter);
    }
    private static String error(JsonNode node) {
        String code = node.path("details").path("error").asText("");
        return switch (code) {
            case "DeviceNotRegistered", "MessageTooBig", "MessageRateExceeded", "InvalidCredentials", "MismatchSenderId" -> code;
            default -> "UnknownError";
        };
    }
    private record Response(int status, JsonNode body, String retryAfter) {}
    private Response post(String path, Object payload) throws IOException {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Expo HTTP inside transaction");
        var request = new HttpPost("https://exp.host/--/api/v2/push/" + path);
        request.setHeader("Accept", "application/json");
        if (properties.accessToken() != null && !properties.accessToken().isBlank()) request.setHeader("Authorization", "Bearer " + properties.accessToken());
        request.setEntity(new StringEntity(mapper.writeValueAsString(payload), ContentType.APPLICATION_JSON));
        try (var response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            var retry = response.getFirstHeader("Retry-After");
            JsonNode body = mapper.createObjectNode();
            if (status == 200 && response.getEntity() != null) {
                try (var input = response.getEntity().getContent()) {
                    byte[] bytes = input.readNBytes(262145);
                    if (bytes.length <= 262144) {
                        try { body = mapper.readTree(new String(bytes, StandardCharsets.UTF_8)); }
                        catch (RuntimeException ignored) { /* Malformed response: never log response text. */ }
                    }
                }
            }
            return new Response(status, body == null ? mapper.createObjectNode() : body, retry == null ? null : retry.getValue());
        } finally { request.abort(); }
    }
    @Override public void close() throws IOException { client.close(); }
}
