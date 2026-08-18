package com.cardrestricted.diagnostics;

import com.cardrestricted.runelite.InteractionRiskClass;
import com.cardrestricted.runelite.InteractionSurface;
import java.time.Instant;
import java.util.Objects;

/** One bounded, sanitised interaction decision retained only in memory. */
public final class IntegrityTraceEvent
{
    private final long sequence;
    private final Instant timestamp;
    private final int clientTick;
    private final String menuAction;
    private final InteractionSurface surface;
    private final InteractionRiskClass riskClass;
    private final IntegrityOptionClass optionClass;
    private final IntegrityTraceDecision decision;
    private final IntegrityTraceReason reason;
    private final int widgetGroup;
    private final int entityId;

    IntegrityTraceEvent(
        long sequence,
        Instant timestamp,
        int clientTick,
        String menuAction,
        InteractionSurface surface,
        InteractionRiskClass riskClass,
        IntegrityOptionClass optionClass,
        IntegrityTraceDecision decision,
        IntegrityTraceReason reason,
        int widgetGroup,
        int entityId)
    {
        this.sequence = sequence;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.clientTick = Math.max(-1, clientTick);
        this.menuAction = safeEnumLike(menuAction);
        this.surface = Objects.requireNonNull(surface, "surface");
        this.riskClass = Objects.requireNonNull(riskClass, "riskClass");
        this.optionClass = Objects.requireNonNull(optionClass, "optionClass");
        this.decision = Objects.requireNonNull(decision, "decision");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.widgetGroup = widgetGroup < 0 ? -1 : widgetGroup;
        this.entityId = entityId < 0 ? -1 : entityId;
    }

    public long getSequence()
    {
        return sequence;
    }

    public Instant getTimestamp()
    {
        return timestamp;
    }

    public int getClientTick()
    {
        return clientTick;
    }

    public String getMenuAction()
    {
        return menuAction;
    }

    public InteractionSurface getSurface()
    {
        return surface;
    }

    public InteractionRiskClass getRiskClass()
    {
        return riskClass;
    }

    public IntegrityOptionClass getOptionClass()
    {
        return optionClass;
    }

    public IntegrityTraceDecision getDecision()
    {
        return decision;
    }

    public IntegrityTraceReason getReason()
    {
        return reason;
    }

    public int getWidgetGroup()
    {
        return widgetGroup;
    }

    public int getEntityId()
    {
        return entityId;
    }

    private static String safeEnumLike(String value)
    {
        if (value == null || value.isEmpty())
        {
            return "UNKNOWN";
        }
        String safe = value.replaceAll("[^A-Za-z0-9_]", "_");
        return safe.length() <= 64 ? safe : safe.substring(0, 64);
    }
}
