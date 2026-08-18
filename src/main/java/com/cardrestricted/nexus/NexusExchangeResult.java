package com.cardrestricted.nexus;

import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.persistence.CollectionState;
import java.util.Objects;

public final class NexusExchangeResult
{
    private final CollectionState state;
    private final String cardId;
    private final Rarity rarity;
    private final long shardsSpent;

    public NexusExchangeResult(
        CollectionState state,
        String cardId,
        Rarity rarity,
        long shardsSpent)
    {
        this.state = Objects.requireNonNull(state, "state");
        this.cardId = Objects.requireNonNull(cardId, "cardId");
        this.rarity = Objects.requireNonNull(rarity, "rarity");
        this.shardsSpent = shardsSpent;
    }

    public CollectionState getState()
    {
        return state;
    }

    public String getCardId()
    {
        return cardId;
    }

    public Rarity getRarity()
    {
        return rarity;
    }

    public long getShardsSpent()
    {
        return shardsSpent;
    }
}
