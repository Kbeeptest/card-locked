package com.cardrestricted.collection.achievement;

import java.util.Locale;
import java.util.Objects;

public final class AchievementProgress
{
    private final AchievementDefinition definition;
    private final int scopeTotal;
    private final int currentCount;
    private final int requiredCount;

    AchievementProgress(
        AchievementDefinition definition,
        int scopeTotal,
        int currentCount,
        int requiredCount)
    {
        this.definition = Objects.requireNonNull(definition, "definition");
        if (scopeTotal <= 0 || currentCount < 0 || requiredCount <= 0)
        {
            throw new IllegalArgumentException(
                "Achievement progress counts are invalid.");
        }
        if (currentCount > scopeTotal || requiredCount > scopeTotal)
        {
            throw new IllegalArgumentException(
                "Achievement progress cannot exceed its scope total.");
        }
        this.scopeTotal = scopeTotal;
        this.currentCount = currentCount;
        this.requiredCount = requiredCount;
    }

    public AchievementDefinition getDefinition()
    {
        return definition;
    }

    public int getScopeTotal()
    {
        return scopeTotal;
    }

    public int getCurrentCount()
    {
        return currentCount;
    }

    public int getRequiredCount()
    {
        return requiredCount;
    }

    public int getRemainingCount()
    {
        return Math.max(0, requiredCount - currentCount);
    }

    public boolean isCompleted()
    {
        return currentCount >= requiredCount;
    }

    public double getGoalPercent()
    {
        return Math.min(100.0, currentCount * 100.0 / requiredCount);
    }

    public double getScopeCompletionPercent()
    {
        return currentCount * 100.0 / scopeTotal;
    }

    public String formatProgress()
    {
        switch (definition.getMeasure())
        {
            case COMPLETION_PERCENT:
                return String.format(
                    Locale.ROOT,
                    "%d / %d cards (%.1f%% / %d%%)",
                    currentCount,
                    scopeTotal,
                    getScopeCompletionPercent(),
                    definition.getTarget());
            case FOIL_COUNT:
                return currentCount + " / " + requiredCount + " foils";
            case OWNED_COUNT:
            default:
                return currentCount + " / " + requiredCount + " cards";
        }
    }
}
