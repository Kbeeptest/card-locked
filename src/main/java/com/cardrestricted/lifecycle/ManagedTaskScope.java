package com.cardrestricted.lifecycle;

import com.cardrestricted.diagnostics.DiagnosticOperation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;

/**
 * Tracks plugin-owned tasks submitted to RuneLite's shared executor.
 * Closing the scope rejects new work, cancels queued work and interrupts active
 * work without shutting down the shared executor itself.
 */
public final class ManagedTaskScope implements AutoCloseable
{
    private final Executor executor;
    private final BiConsumer<DiagnosticOperation, Throwable> failureListener;
    private final Object monitor = new Object();
    private final Set<TrackedTask> tasks = new HashSet<>();
    private boolean accepting = true;
    private int runningTasks;

    public ManagedTaskScope(
        Executor executor,
        BiConsumer<DiagnosticOperation, Throwable> failureListener)
    {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.failureListener = Objects.requireNonNull(
            failureListener,
            "failureListener");
    }

    public boolean submit(DiagnosticOperation operation, Runnable action)
    {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(action, "action");
        TrackedTask task = new TrackedTask(operation, action);
        synchronized (monitor)
        {
            if (!accepting)
            {
                return false;
            }
            tasks.add(task);
        }
        try
        {
            executor.execute(task);
            return true;
        }
        catch (RejectedExecutionException exception)
        {
            synchronized (monitor)
            {
                tasks.remove(task);
                monitor.notifyAll();
            }
            return false;
        }
    }

    public ShutdownResult close(Duration timeout)
    {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative())
        {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        int cancelled;
        synchronized (monitor)
        {
            if (!accepting && tasks.isEmpty() && runningTasks == 0)
            {
                return new ShutdownResult(0, true, 0);
            }
            accepting = false;
            ArrayList<TrackedTask> pending = new ArrayList<>(tasks);
            cancelled = pending.size();
            for (TrackedTask task : pending)
            {
                task.cancel(true);
            }

            long remainingNanos = timeout.toNanos();
            long deadline = System.nanoTime() + remainingNanos;
            while (runningTasks > 0 && remainingNanos > 0L)
            {
                try
                {
                    long millis = Math.max(1L, remainingNanos / 1_000_000L);
                    monitor.wait(millis);
                }
                catch (InterruptedException exception)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
                remainingNanos = deadline - System.nanoTime();
            }
            return new ShutdownResult(
                cancelled,
                runningTasks == 0,
                runningTasks);
        }
    }

    @Override
    public void close()
    {
        close(Duration.ofSeconds(2));
    }

    public boolean isAccepting()
    {
        synchronized (monitor)
        {
            return accepting;
        }
    }

    public int trackedTaskCount()
    {
        synchronized (monitor)
        {
            return tasks.size();
        }
    }

    public int runningTaskCount()
    {
        synchronized (monitor)
        {
            return runningTasks;
        }
    }

    private void notifyFailure(
        DiagnosticOperation operation,
        Throwable failure)
    {
        try
        {
            failureListener.accept(operation, failure);
        }
        catch (RuntimeException ignored)
        {
            // Diagnostics must never destabilise the task executor.
        }
    }

    private final class TrackedTask extends FutureTask<Void>
    {
        private TrackedTask(
            DiagnosticOperation operation,
            Runnable action)
        {
            super(() -> {
                synchronized (monitor)
                {
                    if (!accepting)
                    {
                        return null;
                    }
                    runningTasks++;
                }
                try
                {
                    action.run();
                }
                catch (Throwable failure)
                {
                    notifyFailure(operation, failure);
                }
                finally
                {
                    synchronized (monitor)
                    {
                        runningTasks--;
                        monitor.notifyAll();
                    }
                }
                return null;
            });
        }

        @Override
        protected void done()
        {
            synchronized (monitor)
            {
                tasks.remove(this);
                monitor.notifyAll();
            }
        }
    }

    public static final class ShutdownResult
    {
        private final int cancelledTasks;
        private final boolean quiescent;
        private final int runningTasks;

        private ShutdownResult(
            int cancelledTasks,
            boolean quiescent,
            int runningTasks)
        {
            this.cancelledTasks = cancelledTasks;
            this.quiescent = quiescent;
            this.runningTasks = runningTasks;
        }

        public int getCancelledTasks()
        {
            return cancelledTasks;
        }

        public boolean isQuiescent()
        {
            return quiescent;
        }

        public int getRunningTasks()
        {
            return runningTasks;
        }
    }
}
