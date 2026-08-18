package com.cardrestricted.diagnostics;

import com.cardrestricted.runelite.InteractionSurfacePolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import net.runelite.api.MenuAction;

/**
 * Bounded in-memory interaction trace used to diagnose restriction decisions.
 * No names, account identifiers, card ownership, balances or free-form text are
 * retained. The trace is written only when the user explicitly exports a local
 * diagnostic report.
 */
public final class IntegrityTraceLog
{
    public static final int DEFAULT_CAPACITY = 96;
    public static final int MAX_CAPACITY = 256;

    private final int capacity;
    private final Clock clock;
    private final Deque<IntegrityTraceEvent> events = new ArrayDeque<>();
    private long nextSequence = 1L;

    public IntegrityTraceLog()
    {
        this(DEFAULT_CAPACITY, Clock.systemUTC());
    }

    public IntegrityTraceLog(int capacity, Clock clock)
    {
        if (capacity < 1 || capacity > MAX_CAPACITY)
        {
            throw new IllegalArgumentException(
                "Integrity trace capacity must be between 1 and "
                    + MAX_CAPACITY + '.');
        }
        this.capacity = capacity;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized void record(
        int clientTick,
        MenuAction action,
        String option,
        IntegrityTraceDecision decision,
        IntegrityTraceReason reason,
        int packedWidgetId,
        int entityId)
    {
        MenuAction safeAction = action == null ? MenuAction.UNKNOWN : action;
        int widgetGroup = packedWidgetId < 0 ? -1 : packedWidgetId >>> 16;
        IntegrityTraceEvent event = new IntegrityTraceEvent(
            nextSequence++,
            Instant.now(clock),
            clientTick,
            safeAction.name(),
            InteractionSurfacePolicy.surfaceFor(safeAction),
            InteractionSurfacePolicy.riskClassFor(safeAction),
            IntegrityOptionClass.classify(option),
            Objects.requireNonNull(decision, "decision"),
            Objects.requireNonNull(reason, "reason"),
            widgetGroup,
            entityId);
        while (events.size() >= capacity)
        {
            events.removeFirst();
        }
        events.addLast(event);
    }

    public synchronized List<IntegrityTraceEvent> snapshot()
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

    public synchronized void clear()
    {
        events.clear();
    }
}
