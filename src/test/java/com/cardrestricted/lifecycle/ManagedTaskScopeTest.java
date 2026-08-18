package com.cardrestricted.lifecycle;

import com.cardrestricted.diagnostics.DiagnosticOperation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ManagedTaskScopeTest
{
    @Test
    public void closeRejectsNewTasksWithoutShuttingDownSharedExecutor()
        throws Exception
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try
        {
            ManagedTaskScope scope = new ManagedTaskScope(
                executor,
                (operation, failure) -> { });
            ManagedTaskScope.ShutdownResult result =
                scope.close(Duration.ofSeconds(1));

            assertTrue(result.isQuiescent());
            assertFalse(scope.isAccepting());
            assertFalse(scope.submit(
                DiagnosticOperation.PACK_PURCHASE,
                () -> { }));
            assertFalse(executor.isShutdown());
        }
        finally
        {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void closeInterruptsRunningTaskAndWaitsForItsExit()
        throws Exception
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        try
        {
            ManagedTaskScope scope = new ManagedTaskScope(
                executor,
                (operation, failure) -> { });
            assertTrue(scope.submit(DiagnosticOperation.SESSION_OPEN, () -> {
                started.countDown();
                try
                {
                    Thread.sleep(30_000L);
                }
                catch (InterruptedException exception)
                {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));

            ManagedTaskScope.ShutdownResult result =
                scope.close(Duration.ofSeconds(2));
            assertTrue(result.isQuiescent());
            assertTrue(interrupted.get());
            assertEquals(0, scope.runningTaskCount());
        }
        finally
        {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void queuedTasksAreCancelledDuringShutdown()
        throws Exception
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger queuedRuns = new AtomicInteger();
        try
        {
            ManagedTaskScope scope = new ManagedTaskScope(
                executor,
                (operation, failure) -> { });
            scope.submit(DiagnosticOperation.STARTUP, () -> {
                blockerStarted.countDown();
                try
                {
                    release.await();
                }
                catch (InterruptedException exception)
                {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));
            for (int index = 0; index < 10; index++)
            {
                scope.submit(
                    DiagnosticOperation.PACK_PURCHASE,
                    queuedRuns::incrementAndGet);
            }

            ManagedTaskScope.ShutdownResult result =
                scope.close(Duration.ofSeconds(2));
            release.countDown();
            assertTrue(result.isQuiescent());
            assertEquals(0, queuedRuns.get());
        }
        finally
        {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void shutdownTimeoutReportsAStillRunningInterruptResistantTask()
        throws Exception
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try
        {
            ManagedTaskScope scope = new ManagedTaskScope(
                executor,
                (operation, failure) -> { });
            scope.submit(DiagnosticOperation.ARTWORK_WARMUP, () -> {
                started.countDown();
                while (release.getCount() > 0)
                {
                    try
                    {
                        release.await(20, TimeUnit.MILLISECONDS);
                    }
                    catch (InterruptedException ignored)
                    {
                        // Deliberately resist interruption for timeout coverage.
                    }
                }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));

            ManagedTaskScope.ShutdownResult timedOut =
                scope.close(Duration.ofMillis(50));
            assertFalse(timedOut.isQuiescent());
            assertEquals(1, timedOut.getRunningTasks());

            release.countDown();
            assertTrue(scope.close(Duration.ofSeconds(2)).isQuiescent());
        }
        finally
        {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void unexpectedTaskFailuresAreReportedWithoutEscapingExecutor()
        throws Exception
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch reported = new CountDownLatch(1);
        try
        {
            ManagedTaskScope scope = new ManagedTaskScope(
                executor,
                (operation, failure) -> {
                    failures.add(operation.name() + ":" + failure.getClass().getSimpleName());
                    reported.countDown();
                });
            assertTrue(scope.submit(DiagnosticOperation.PROFILE_CREATE, () -> {
                throw new IllegalArgumentException("private path");
            }));
            assertTrue(reported.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("PROFILE_CREATE:IllegalArgumentException"), failures);
            assertTrue(scope.close(Duration.ofSeconds(1)).isQuiescent());
        }
        finally
        {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    public void executorRejectionReturnsFalseAndDoesNotRemainTracked()
    {
        List<Class<?>> failures = new ArrayList<>();
        ManagedTaskScope scope = new ManagedTaskScope(
            command -> {
                throw new RejectedExecutionException("offline");
            },
            (operation, failure) -> failures.add(failure.getClass()));

        assertFalse(scope.submit(DiagnosticOperation.SESSION_OPEN, () -> { }));
        assertTrue(failures.isEmpty());
        assertEquals(0, scope.trackedTaskCount());
        assertTrue(scope.close(Duration.ZERO).isQuiescent());
    }
}
