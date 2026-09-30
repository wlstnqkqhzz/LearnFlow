package com.be.push;

import java.net.URI;
import java.security.Security;
import nl.martijndwars.webpush.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PushProperties.class)
public class PushConfiguration {
    @Bean(destroyMethod = "close")
    BoundedPushTasks pushTasks() { return new BoundedPushTasks(2, 200); }
    @Bean
    PushTransport pushTransport(PushProperties properties, PushEndpointPolicy policy) {
        if (!properties.enabled()) return (target, id) -> new PushTransport.Result(503, null);
        try {
            if (properties.subject() == null || properties.subject().isBlank()) throw new IllegalArgumentException();
            var subject = URI.create(properties.subject());
            if (!("mailto".equals(subject.getScheme()) && subject.getSchemeSpecificPart().contains("@"))
                    && !("https".equals(subject.getScheme()) && subject.getHost() != null)) throw new IllegalArgumentException();
            if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
            var service = new PushService(properties.publicKey(), properties.privateKey(), properties.subject());
            if (!Utils.verifyKeyPair(service.getPrivateKey(), service.getPublicKey())) throw new IllegalArgumentException();
            return new WebPushTransport(service, policy);
        } catch (Exception e) {
            // Never retain the original cause: crypto exceptions may contain credential values.
            throw new IllegalStateException("Invalid Web Push VAPID configuration");
        }
    }
}
