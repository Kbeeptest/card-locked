package com.cardrestricted.points;

import java.time.Instant;
import java.util.Objects;

public final class QuestCompletionObservation
{
    private final String questKey;
    private final String questName;
    private final Instant completedAt;

    public QuestCompletionObservation(
        String questKey,
        String questName,
        Instant completedAt)
    {
        this.questKey = requireText(questKey, "questKey");
        this.questName = requireText(questName, "questName");
        this.completedAt =
            Objects.requireNonNull(completedAt, "completedAt");
    }

    public String getQuestKey()
    {
        return questKey;
    }

    public String getQuestName()
    {
        return questName;
    }

    public Instant getCompletedAt()
    {
        return completedAt;
    }

    private static String requireText(String value, String field)
    {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                field + " cannot be blank.");
        }
        return value;
    }
}
