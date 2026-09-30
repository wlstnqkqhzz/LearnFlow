package com.be.push;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Both immediate and delayed work are bounded. Never runs rejected work on the caller. */
@lombok.extern.slf4j.Slf4j
public final class BoundedPushTasks implements PushTasks, AutoCloseable {
    private final ThreadPoolExecutor workers;
    private final ScheduledThreadPoolExecutor timer;
    private final Semaphore delayedSlots;
    public BoundedPushTasks(int threads, int capacity) {
        workers = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), factory("push-worker-"), new ThreadPoolExecutor.AbortPolicy());
        timer = new ScheduledThreadPoolExecutor(1, factory("push-retry-"));
        timer.setRemoveOnCancelPolicy(true);
        timer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        delayedSlots = new Semaphore(capacity);
    }
    private static ThreadFactory factory(String name) {
        var sequence = new AtomicInteger();
        return action -> { var t = new Thread(action, name + sequence.incrementAndGet()); t.setDaemon(true); return t; };
    }
    @Override public boolean execute(Runnable task) {
        try { workers.execute(task); return true; } catch (RejectedExecutionException e) { return false; }
    }
    @Override public boolean schedule(Runnable task, Duration delay) {
        if (!delayedSlots.tryAcquire()) return false;
        try {
            timer.schedule(() -> {
                try { if (!execute(task)) log.warn("Push retry worker capacity exhausted"); }
                finally { delayedSlots.release(); }
            }, delay.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (RejectedExecutionException e) { delayedSlots.release(); return false; }
    }
    @Override public void close() { timer.shutdownNow(); workers.shutdownNow(); }
}
