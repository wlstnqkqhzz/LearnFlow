package com.be.push;

import java.time.Duration;

public interface PushTasks {
    boolean execute(Runnable task);
    boolean schedule(Runnable task, Duration delay);
}
