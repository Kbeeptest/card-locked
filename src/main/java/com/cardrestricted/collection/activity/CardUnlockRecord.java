package com.cardrestricted.collection.activity;

import java.time.Instant;
import java.util.Objects;

public final class CardUnlockRecord
{
    private final String cardId;
    private final CardUnlockSource source;
    private final Instant occurredAt;
    private final String sourceId;

    public CardUnlockRecord(
        String cardId,
        CardUnlockSource source,
        Instant occurredAt,
        String sourceId)
    {
        this.cardId = requireText(cardId, "cardId");
        this.source = Objects.requireNonNull(source, "source");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.sourceId = sourceId == null ? "" : sourceId.trim();
    }

    public String getCardId()
    {
        return cardId;
    }

    public CardUnlockSource getSource()
    {
        return source;
    }

    public Instant getOccurredAt()
    {
        return occurredAt;
    }

    public String getSourceId()
    {
        return sourceId;
    }

    private static String requireText(String value, String field)
    {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value;
    }
}
