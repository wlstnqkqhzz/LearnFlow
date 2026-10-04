package com.be.mobilepush;

import com.be.push.PushTasks;
import java.time.*;
import java.util.*;

final class MobilePushTestSupport {
    static final String SECRET = "s".repeat(43);
    static final MobilePushProperties ENABLED = new MobilePushProperties(true, null);
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-04T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
    }
    static class Tasks implements PushTasks {
        final Deque<Runnable> immediate = new ArrayDeque<>(), delayed = new ArrayDeque<>();
        final List<Duration> delays = new ArrayList<>();
        boolean accept = true;
        public boolean execute(Runnable task) { if (accept) immediate.add(task); return accept; }
        public boolean schedule(Runnable task, Duration delay) { if (accept) { delayed.add(task); delays.add(delay); } return accept; }
        void run() { while (!immediate.isEmpty()) immediate.remove().run(); }
        void retryAll() { int n = 0; while (!delayed.isEmpty()) { if (++n > 10) throw new AssertionError("unbounded retry"); delayed.remove().run(); } }
        void reset() { immediate.clear(); delayed.clear(); delays.clear(); accept = true; }
    }
    static MobilePushDtos.Binding binding(String id, long version) { return new MobilePushDtos.Binding(id, MobilePushSubscription.Platform.ANDROID, version); }
    static MobilePushDtos.Registration token(String token, long version) { return new MobilePushDtos.Registration("ExpoPushToken[" + token + "]", MobilePushSubscription.Platform.ANDROID, version); }
}
