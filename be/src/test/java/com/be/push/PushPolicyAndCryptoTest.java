package com.be.push;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import nl.martijndwars.webpush.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.be.push.PushTestSupport.*;

class PushPolicyAndCryptoTest {
    PushEndpointPolicy policy = new PushEndpointPolicy();
    @Test void acceptsSupportedProviderEndpointsIncludingWindowsToken() {
        for (var url : List.of("https://fcm.googleapis.com/fcm/send/test", "https://web.push.apple.com/test",
                "https://updates.push.services.mozilla.com/wpush/v2/test", "https://wns2-test.notify.windows.com/w/?token=opaque%2Btoken%3D"))
            assertThatCode(() -> policy.validateEndpoint(url)).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings = {"http://fcm.googleapis.com/test", "https://127.0.0.1/test", "https://10.0.0.1/test",
        "https://[::1]/test", "https://fcm.googleapis.com.evil.com/test", "https://user@fcm.googleapis.com/test",
        "https://fcm.googleapis.com:8443/test", "https://fcm.googleapis.com/test#fragment", "https://example.com/test",
        "https://fcm.googleapis.com/test?url=http://localhost"})
    void rejectsUntrustedEndpoints(String endpoint) { assertThatThrownBy(() -> policy.validateEndpoint(endpoint)).isInstanceOf(com.be.global.exception.BusinessException.class); }
    @ParameterizedTest @ValueSource(strings = {"127.0.0.1", "10.0.0.1", "169.254.169.254", "172.16.1.1", "192.168.1.1", "100.64.0.1", "::1", "fc00::1", "fe80::1", "2001:db8::1"})
    void rejectsPrivateDnsAtSocketResolution(String ip) throws Exception {
        var address = InetAddress.getByName(ip);
        assertThatThrownBy(() -> PushEndpointPolicy.requirePublic(new InetAddress[]{address})).isInstanceOf(UnknownHostException.class);
    }
    @Test void validatesRealBrowserKeyAndExpiry() {
        var request = request("device");
        assertThatCode(() -> policy.validate(request, CLOCK.instant())).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate(new PushSubscriptionRequest(request.endpoint(), "invalid", AUTH, null), CLOCK.instant())).isInstanceOf(com.be.global.exception.BusinessException.class);
        assertThatThrownBy(() -> policy.validate(new PushSubscriptionRequest(request.endpoint(), request.p256dh(), AUTH, CLOCK.instant()), CLOCK.instant())).isInstanceOf(com.be.global.exception.BusinessException.class);
    }
    @Test void actualLibraryBuildsEncryptedVapidRequestOnJava21() throws Exception {
        var vapid = keyPair(); var subscriber = request("test");
        var service = new PushService(vapid, "mailto:ops@example.com");
        byte[] payload = WebPushTransport.payload(123L);
        var post = service.preparePost(new Notification(subscriber.endpoint(), subscriber.p256dh(), AUTH, payload), Encoding.AES128GCM);
        assertThat(post.getFirstHeader("Authorization").getValue()).startsWith("vapid ");
        assertThat(post.getFirstHeader("Content-Encoding").getValue()).isEqualTo("aes128gcm");
        byte[] encrypted = post.getEntity().getContent().readAllBytes();
        assertThat(encrypted).isNotEqualTo(payload);
        assertThat(new String(payload, StandardCharsets.UTF_8)).contains("\"notificationId\":123").doesNotContain("email", "course", "JWT", "member");
    }
    @Test void invalidVapidHasSanitizedFailure() {
        var config = new PushConfiguration();
        assertThatThrownBy(() -> config.pushTransport(new PushProperties(true, "secret-public", "secret-private", "mailto:ops@example.com"), policy))
                .isInstanceOf(IllegalStateException.class).hasMessage("Invalid Web Push VAPID configuration").hasNoCause();
        assertThat(ENABLED.toString()).doesNotContain("private-test", "public-test");
        assertThat(request("secret-endpoint").toString()).doesNotContain("secret-endpoint", AUTH);
    }
    @Test void validVapidConfigurationAndTransportTransactionGuard() throws Exception {
        var pair = keyPair();
        var scalar = ((org.bouncycastle.jce.interfaces.ECPrivateKey) pair.getPrivate()).getD();
        var privateKey = Base64.getUrlEncoder().withoutPadding().encodeToString(org.bouncycastle.util.BigIntegers.asUnsignedByteArray(32, scalar));
        var properties = new PushProperties(true, publicKey(pair), privateKey, "mailto:ops@example.com");
        try (var transport = (WebPushTransport) new PushConfiguration().pushTransport(properties, policy)) {
            var request = request("test");
            var target = new PushDeliveryStore.Target(1L, 0, request.endpoint(), request.p256dh(), request.auth());
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                assertThatThrownBy(() -> transport.send(target, 1L)).isInstanceOf(IllegalStateException.class)
                        .hasMessage("Push must run outside DB transaction");
            } finally { org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false); }
            var unsafe = new PushDeliveryStore.Target(1L, 0, "https://127.0.0.1/secret", request.p256dh(), request.auth());
            assertThatThrownBy(() -> transport.send(unsafe, 1L)).isInstanceOf(com.be.global.exception.BusinessException.class);
        }
    }
}
