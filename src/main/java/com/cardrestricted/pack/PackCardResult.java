package com.cardrestricted.pack;

import java.util.Objects;

public final class PackCardResult
{
    private final String cardId;
    private final boolean duplicate;
    private final long shardsAwarded;
    private final boolean foil;

    public PackCardResult(
        String cardId,
        boolean duplicate,
        long shardsAwarded)
    {
        this(cardId, duplicate, shardsAwarded, false);
    }

    public PackCardResult(
        String cardId,
        boolean duplicate,
        long shardsAwarded,
        boolean foil)
    {
        Objects.requireNonNull(cardId, "cardId");
        if (cardId.trim().isEmpty() || shardsAwarded < 0)
        {
            throw new IllegalArgumentException(
                "Pack card result is invalid.");
        }
        if (duplicate != (shardsAwarded > 0))
        {
            throw new IllegalArgumentException(
                "Only duplicate cards can award shards.");
        }
        this.cardId = cardId;
        this.duplicate = duplicate;
        this.shardsAwarded = shardsAwarded;
        this.foil = foil;
    }

    public String getCardId()
    {
        return cardId;
    }

    public boolean isDuplicate()
    {
        return duplicate;
    }

    public long getShardsAwarded()
    {
        return shardsAwarded;
    }

    public boolean isFoil()
    {
        return foil;
    }
}
