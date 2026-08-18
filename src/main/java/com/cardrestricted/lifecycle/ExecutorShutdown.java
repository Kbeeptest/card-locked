package com.cardrestricted.lifecycle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExecutorShutdown
{
    private ExecutorShutdown()
    {
    }

    public static boolean shutdownNowAndAwait(
        ExecutorService executor,
        Duration timeout)
    {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(timeout, "timeout");
        executor.shutdownNow();
        try
        {
            return executor.awaitTermination(
                Math.max(0L, timeout.toMillis()),
                TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            return executor.isTerminated();
        }
    }
}
