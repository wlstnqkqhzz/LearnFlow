package com.be.push;

import java.security.*;
import java.time.*;
import java.util.*;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec;

final class PushTestSupport {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC);
    static final PushProperties ENABLED = new PushProperties(true, "public-test", "private-test", "mailto:ops@example.com");
    static final String AUTH = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
    static KeyPair keyPair() {
        try {
            if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
            var generator = KeyPairGenerator.getInstance("ECDH", "BC");
            generator.initialize(new ECNamedCurveGenParameterSpec("prime256v1"));
            return generator.generateKeyPair();
        } catch (Exception e) { throw new AssertionError(e); }
    }
    static String publicKey(KeyPair pair) { return Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode((ECPublicKey) pair.getPublic())); }
    static PushSubscriptionRequest request(String suffix) {
        return new PushSubscriptionRequest("https://fcm.googleapis.com/fcm/send/" + suffix, publicKey(keyPair()), AUTH, null);
    }
    static final class Tasks implements PushTasks {
        final Deque<Runnable> immediate = new ArrayDeque<>(), retries = new ArrayDeque<>();
        final List<Duration> delays = new ArrayList<>();
        boolean accept = true;
        public boolean execute(Runnable task) { if (accept) immediate.add(task); return accept; }
        public boolean schedule(Runnable task, Duration delay) { if (accept) { retries.add(task); delays.add(delay); } return accept; }
        void run() { while (!immediate.isEmpty()) immediate.remove().run(); }
        void retryAll() { int safety = 0; while (!retries.isEmpty()) { if (++safety > 10) throw new AssertionError("unbounded retry"); retries.remove().run(); } }
    }
}
