package com.cardrestricted.points;

import java.time.Instant;
import java.util.Objects;

public final class ClueCompletionObservation
{
    private final ClueTier tier;
    private final long completionCount;
    private final Instant occurredAt;

    public ClueCompletionObservation(
        ClueTier tier,
        long completionCount,
        Instant occurredAt)
    {
        this.tier = Objects.requireNonNull(tier, "tier");
        if (completionCount <= 0)
        {
            throw new IllegalArgumentException(
                "Clue completion count must be positive.");
        }
        this.completionCount = completionCount;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public ClueTier getTier()
    {
        return tier;
    }

    public long getCompletionCount()
    {
        return completionCount;
    }

    public Instant getOccurredAt()
    {
        return occurredAt;
    }
}
