package com.cardrestricted.starter;

import java.util.Objects;

public final class StarterPackCandidate
{
    private final StarterPackPool pool;
    private final String cardId;
    private final int maximumHealing;
    private final String accessNote;

    public StarterPackCandidate(
        StarterPackPool pool,
        String cardId,
        int maximumHealing,
        String accessNote)
    {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.cardId = requireText(cardId, "cardId");
        this.maximumHealing = maximumHealing;
        this.accessNote = requireText(accessNote, "accessNote");
        if (maximumHealing < 0)
        {
            throw new IllegalArgumentException(
                "maximumHealing cannot be negative.");
        }
    }

    public StarterPackPool getPool()
    {
        return pool;
    }

    public String getCardId()
    {
        return cardId;
    }

    public int getMaximumHealing()
    {
        return maximumHealing;
    }

    public String getAccessNote()
    {
        return accessNote;
    }

    private static String requireText(String value, String field)
    {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value.trim();
    }
}
