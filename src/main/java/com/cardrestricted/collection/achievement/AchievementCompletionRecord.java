package com.cardrestricted.collection.achievement;

import java.time.Instant;
import java.util.Objects;

public final class AchievementCompletionRecord
{
    private final String achievementId;
    private final Instant completedAt;

    public AchievementCompletionRecord(
        String achievementId,
        Instant completedAt)
    {
        this.achievementId = requireText(
            achievementId,
            "achievementId");
        this.completedAt = Objects.requireNonNull(
            completedAt,
            "completedAt");
    }

    public String getAchievementId()
    {
        return achievementId;
    }

    public Instant getCompletedAt()
    {
        return completedAt;
    }

    private static String requireText(String value, String field)
    {
        Objects.requireNonNull(value, field);
        String text = value.trim();
        if (text.isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return text;
    }
}
