package com.cardrestricted.pack;

import com.cardrestricted.persistence.CollectionState;
import java.util.Objects;

public final class PackPurchaseResult
{
    private final CollectionState state;
    private final PendingPackReveal reveal;

    public PackPurchaseResult(
        CollectionState state,
        PendingPackReveal reveal)
    {
        this.state = Objects.requireNonNull(state, "state");
        this.reveal = Objects.requireNonNull(reveal, "reveal");
    }

    public CollectionState getState()
    {
        return state;
    }

    public PendingPackReveal getReveal()
    {
        return reveal;
    }
}
