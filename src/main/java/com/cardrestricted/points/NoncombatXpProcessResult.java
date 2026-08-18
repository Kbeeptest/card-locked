package com.cardrestricted.points;

import com.cardrestricted.persistence.CollectionState;
import java.util.Objects;

public final class NoncombatXpProcessResult
{
    private final CollectionState state;
    private final NoncombatXpResultStatus status;
    private final long xpProcessed;
    private final long pointsAwarded;

    public NoncombatXpProcessResult(
        CollectionState state,
        NoncombatXpResultStatus status,
        long xpProcessed,
        long pointsAwarded)
    {
        this.state = Objects.requireNonNull(state, "state");
        this.status = Objects.requireNonNull(status, "status");
        if (xpProcessed < 0 || pointsAwarded < 0)
        {
            throw new IllegalArgumentException(
                "Processed XP and awarded points cannot be negative.");
        }
        this.xpProcessed = xpProcessed;
        this.pointsAwarded = pointsAwarded;
    }

    public CollectionState getState()
    {
        return state;
    }

    public NoncombatXpResultStatus getStatus()
    {
        return status;
    }

    public long getXpProcessed()
    {
        return xpProcessed;
    }

    public long getPointsAwarded()
    {
        return pointsAwarded;
    }
}
