package com.cardrestricted.lifecycle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** LIFO, idempotent cleanup for partially completed plugin startups. */
public final class LifecycleCleanupRegistry implements AutoCloseable
{
    private final Deque<Entry> entries = new ArrayDeque<>();
    private boolean closed;

    public synchronized void register(String name, Runnable cleanup)
    {
        if (closed)
        {
            throw new IllegalStateException("Cleanup registry is already closed.");
        }
        entries.push(new Entry(
            safeName(name),
            Objects.requireNonNull(cleanup, "cleanup")));
    }

    public List<CleanupFailure> closeAndCollect()
    {
        List<Entry> pending;
        synchronized (this)
        {
            if (closed)
            {
                return Collections.emptyList();
            }
            closed = true;
            pending = new ArrayList<>(entries);
            entries.clear();
        }

        List<CleanupFailure> failures = new ArrayList<>();
        for (Entry entry : pending)
        {
            try
            {
                entry.cleanup.run();
            }
            catch (Throwable failure)
            {
                failures.add(new CleanupFailure(entry.name, failure));
            }
        }
        return Collections.unmodifiableList(failures);
    }

    @Override
    public void close()
    {
        closeAndCollect();
    }

    public synchronized int size()
    {
        return entries.size();
    }

    public synchronized boolean isClosed()
    {
        return closed;
    }

    private static String safeName(String value)
    {
        Objects.requireNonNull(value, "name");
        String safe = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        if (safe.isEmpty())
        {
            throw new IllegalArgumentException("Cleanup name must not be empty.");
        }
        return safe.length() <= 64 ? safe : safe.substring(0, 64);
    }

    private static final class Entry
    {
        private final String name;
        private final Runnable cleanup;

        private Entry(String name, Runnable cleanup)
        {
            this.name = name;
            this.cleanup = cleanup;
        }
    }

    public static final class CleanupFailure
    {
        private final String resourceName;
        private final Throwable failure;

        private CleanupFailure(String resourceName, Throwable failure)
        {
            this.resourceName = resourceName;
            this.failure = failure;
        }

        public String getResourceName()
        {
            return resourceName;
        }

        public Throwable getFailure()
        {
            return failure;
        }
    }
}
