package com.be.mobilepush;

import com.be.push.*;
import java.time.Duration;

/** Separate bean type and executor instance: never competes with Web Push injection or capacity. */
public final class MobilePushTasks implements AutoCloseable {
    private final PushTasks delegate;
    public MobilePushTasks() { this(new BoundedPushTasks(2, 200)); }
    MobilePushTasks(PushTasks delegate) { this.delegate = delegate; }
    boolean execute(Runnable task) { return delegate.execute(task); }
    boolean schedule(Runnable task, Duration delay) { return delegate.schedule(task, delay); }
    @Override public void close() throws Exception { if (delegate instanceof AutoCloseable closeable) closeable.close(); }
}
