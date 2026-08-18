package com.cardrestricted.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class StateJournalEvent
{
    private final UUID eventId;
    private final UUID collectionId;
    private final String characterKey;
    private final long previousRevision;
    private final long revision;
    private final JournalEventType type;
    private final String payload;
    private final Instant occurredAt;
    private final String previousEventHash;
    private final String stateHash;
    private final String eventHash;

    public StateJournalEvent(
        UUID eventId,
        UUID collectionId,
        String characterKey,
        long previousRevision,
        long revision,
        JournalEventType type,
        String payload,
        Instant occurredAt,
        String previousEventHash,
        String stateHash,
        String eventHash)
    {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.collectionId =
            Objects.requireNonNull(collectionId, "collectionId");
        this.characterKey = requireText(characterKey, "characterKey");
        this.previousRevision = previousRevision;
        this.revision = revision;
        this.type = Objects.requireNonNull(type, "type");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.previousEventHash =
            Objects.requireNonNull(previousEventHash, "previousEventHash");
        this.stateHash = requireText(stateHash, "stateHash");
        this.eventHash = requireText(eventHash, "eventHash");
        if (previousRevision < -1 || revision != previousRevision + 1)
        {
            throw new IllegalArgumentException(
                "Journal revisions must advance by exactly one.");
        }
    }

    public UUID getEventId()
    {
        return eventId;
    }

    public UUID getCollectionId()
    {
        return collectionId;
    }

    public String getCharacterKey()
    {
        return characterKey;
    }

    public long getPreviousRevision()
    {
        return previousRevision;
    }

    public long getRevision()
    {
        return revision;
    }

    public JournalEventType getType()
    {
        return type;
    }

    public String getPayload()
    {
        return payload;
    }

    public Instant getOccurredAt()
    {
        return occurredAt;
    }

    public String getPreviousEventHash()
    {
        return previousEventHash;
    }

    public String getStateHash()
    {
        return stateHash;
    }

    public String getEventHash()
    {
        return eventHash;
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
