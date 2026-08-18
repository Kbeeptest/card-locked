package com.cardrestricted.points;

import java.time.Instant;
import java.util.Objects;

public final class PointAward
{
    private final String sourceId;
    private final PointSourceType sourceType;
    private final long amount;
    private final Instant occurredAt;

    public PointAward(
        String sourceId,
        PointSourceType sourceType,
        long amount,
        Instant occurredAt)
    {
        Objects.requireNonNull(sourceId, "sourceId");
        if (sourceId.trim().isEmpty())
        {
            throw new IllegalArgumentException("sourceId cannot be blank.");
        }
        if (amount <= 0)
        {
            throw new IllegalArgumentException(
                "Point awards must be positive.");
        }
        this.sourceId = sourceId;
        this.sourceType =
            Objects.requireNonNull(sourceType, "sourceType");
        this.amount = amount;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public String getSourceId()
    {
        return sourceId;
    }

    public PointSourceType getSourceType()
    {
        return sourceType;
    }

    public long getAmount()
    {
        return amount;
    }

    public Instant getOccurredAt()
    {
        return occurredAt;
    }
}
