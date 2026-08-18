package com.cardrestricted.diagnostics;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * In-memory, bounded and non-sensitive plugin diagnostics.
 *
 * <p>The API deliberately accepts only fixed enums and exception class names.
 * Exception messages, account identifiers, collection state and file paths are
 * never retained.</p>
 */
public final class LocalDiagnosticLog
{
    public static final int DEFAULT_CAPACITY = 64;
    public static final int MAX_CAPACITY = 512;

    private final int capacity;
    private final Clock clock;
    private final Deque<DiagnosticEvent> events = new ArrayDeque<>();
    private long nextSequence = 1L;

    public LocalDiagnosticLog()
    {
        this(DEFAULT_CAPACITY, Clock.systemUTC());
    }

    public LocalDiagnosticLog(int capacity, Clock clock)
    {
        if (capacity < 1 || capacity > MAX_CAPACITY)
        {
            throw new IllegalArgumentException(
                "Diagnostic capacity must be between 1 and " + MAX_CAPACITY + ".");
        }
        this.capacity = capacity;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized void record(
        DiagnosticEventCode eventCode,
        DiagnosticOperation operation)
    {
        add(eventCode, operation, "");
    }

    public synchronized void recordFailure(
        DiagnosticEventCode eventCode,
        DiagnosticOperation operation,
        Throwable failure)
    {
        add(
            eventCode,
            operation,
            failure == null ? "" : failure.getClass().getSimpleName());
    }

    public synchronized void recordFailureType(
        DiagnosticEventCode eventCode,
        DiagnosticOperation operation,
        String failureType)
    {
        add(eventCode, operation, failureType);
    }

    public synchronized List<DiagnosticEvent> snapshot()
    {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public synchronized int size()
    {
        return events.size();
    }

    public int capacity()
    {
        return capacity;
    }

    private void add(
        DiagnosticEventCode eventCode,
        DiagnosticOperation operation,
        String failureType)
    {
        Instant timestamp = Instant.now(clock);
        events.addLast(new DiagnosticEvent(
            nextSequence++,
            timestamp,
            Objects.requireNonNull(eventCode, "eventCode"),
            operation,
            failureType));
        while (events.size() > capacity)
        {
            events.removeFirst();
        }
    }
}
