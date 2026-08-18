package com.cardrestricted.collection.achievement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AchievementSnapshot
{
    private final List<AchievementProgress> progress;
    private final Map<String, AchievementProgress> progressById;
    private final int completedCount;

    AchievementSnapshot(List<AchievementProgress> progress)
    {
        this.progress = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(progress, "progress")));
        Map<String, AchievementProgress> indexed = new LinkedHashMap<>();
        int completed = 0;
        for (AchievementProgress value : progress)
        {
            AchievementProgress previous = indexed.put(
                value.getDefinition().getAchievementId(),
                value);
            if (previous != null)
            {
                throw new IllegalArgumentException(
                    "Duplicate achievement progress ID.");
            }
            if (value.isCompleted())
            {
                completed++;
            }
        }
        this.progressById = Collections.unmodifiableMap(indexed);
        this.completedCount = completed;
    }

    public List<AchievementProgress> getProgress()
    {
        return progress;
    }

    public AchievementProgress requireProgress(String achievementId)
    {
        AchievementProgress value = progressById.get(
            Objects.requireNonNull(achievementId, "achievementId"));
        if (value == null)
        {
            throw new IllegalArgumentException(
                "Unknown achievement progress " + achievementId + ".");
        }
        return value;
    }

    public int getCompletedCount()
    {
        return completedCount;
    }

    public int getTotalCount()
    {
        return progress.size();
    }

    public boolean isComplete()
    {
        return !progress.isEmpty() && completedCount == progress.size();
    }

    public Optional<AchievementProgress> getClosestIncomplete()
    {
        return progress.stream()
            .filter(value -> !value.isCompleted())
            .max(Comparator.comparingDouble(
                AchievementProgress::getGoalPercent));
    }
}
