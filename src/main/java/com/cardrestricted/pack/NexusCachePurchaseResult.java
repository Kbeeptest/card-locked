package com.cardrestricted.pack;

import com.cardrestricted.persistence.CollectionState;
import java.util.Objects;

public final class NexusCachePurchaseResult
{
    private final CollectionState state;
    private final long shardsAwarded;

    public NexusCachePurchaseResult(
        CollectionState state,
        long shardsAwarded)
    {
        this.state = Objects.requireNonNull(state, "state");
        if (shardsAwarded <= 0)
        {
            throw new IllegalArgumentException(
                "Nexus Cache must award positive shards.");
        }
        this.shardsAwarded = shardsAwarded;
    }

    public CollectionState getState()
    {
        return state;
    }

    public long getShardsAwarded()
    {
        return shardsAwarded;
    }
}
