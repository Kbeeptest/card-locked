package com.cardrestricted.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LifecycleCleanupRegistryTest
{
    @Test
    public void cleanupRunsInReverseRegistrationOrder()
    {
        LifecycleCleanupRegistry registry = new LifecycleCleanupRegistry();
        List<String> order = new ArrayList<>();
        registry.register("first", () -> order.add("first"));
        registry.register("second", () -> order.add("second"));
        registry.register("third", () -> order.add("third"));

        assertTrue(registry.closeAndCollect().isEmpty());
        assertEquals(List.of("third", "second", "first"), order);
    }

    @Test
    public void oneCleanupFailureDoesNotPreventRemainingCleanup()
    {
        LifecycleCleanupRegistry registry = new LifecycleCleanupRegistry();
        AtomicInteger completed = new AtomicInteger();
        registry.register("last", completed::incrementAndGet);
        registry.register("broken", () -> {
            throw new IllegalStateException("sensitive detail");
        });
        registry.register("first", completed::incrementAndGet);

        List<LifecycleCleanupRegistry.CleanupFailure> failures =
            registry.closeAndCollect();
        assertEquals(2, completed.get());
        assertEquals(1, failures.size());
        assertEquals("broken", failures.get(0).getResourceName());
        assertEquals(IllegalStateException.class,
            failures.get(0).getFailure().getClass());
    }

    @Test
    public void closeIsIdempotent()
    {
        LifecycleCleanupRegistry registry = new LifecycleCleanupRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.register("resource", calls::incrementAndGet);

        registry.closeAndCollect();
        registry.closeAndCollect();
        registry.close();
        assertEquals(1, calls.get());
    }

    @Test
    public void repeatedFailedStartupCyclesDoNotCarryResourcesForward()
    {
        AtomicInteger cleanups = new AtomicInteger();
        for (int attempt = 0; attempt < 5; attempt++)
        {
            LifecycleCleanupRegistry registry =
                new LifecycleCleanupRegistry();
            registry.register("resource-a", cleanups::incrementAndGet);
            registry.register("resource-b", cleanups::incrementAndGet);
            assertEquals(2, registry.size());
            registry.closeAndCollect();
            assertTrue(registry.isClosed());
            assertEquals(0, registry.size());
        }
        assertEquals(10, cleanups.get());
    }

    @Test(expected = IllegalStateException.class)
    public void closedRegistryRejectsLateRegistration()
    {
        LifecycleCleanupRegistry registry = new LifecycleCleanupRegistry();
        registry.close();
        registry.register("late", () -> { });
    }
}
