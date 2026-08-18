package com.cardrestricted.diagnostics;

import java.time.Instant;
import java.util.Objects;

public final class DiagnosticEvent
{
    private final long sequence;
    private final Instant timestamp;
    private final DiagnosticEventCode eventCode;
    private final DiagnosticOperation operation;
    private final String failureType;

    DiagnosticEvent(
        long sequence,
        Instant timestamp,
        DiagnosticEventCode eventCode,
        DiagnosticOperation operation,
        String failureType)
    {
        this.sequence = sequence;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.eventCode = Objects.requireNonNull(eventCode, "eventCode");
        this.operation = operation;
        this.failureType = safeFailureType(failureType);
    }

    public long getSequence()
    {
        return sequence;
    }

    public Instant getTimestamp()
    {
        return timestamp;
    }

    public DiagnosticEventCode getEventCode()
    {
        return eventCode;
    }

    public DiagnosticOperation getOperation()
    {
        return operation;
    }

    public String getFailureType()
    {
        return failureType;
    }

    private static String safeFailureType(String value)
    {
        if (value == null || value.isEmpty())
        {
            return "";
        }
        String safe = value.replaceAll("[^A-Za-z0-9_$.-]", "_");
        return safe.length() <= 80 ? safe : safe.substring(0, 80);
    }
}
