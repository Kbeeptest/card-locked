package com.cardrestricted.presentation;

import com.cardrestricted.collection.achievement.AchievementDefinition;
import java.util.Objects;
import java.util.Optional;

public final class AchievementToastSnapshot
{
    private final AchievementToastState state;
    private final AchievementDefinition achievement;
    private final int queuedCount;
    private final double transitionProgress;
    private final double holdProgress;

    AchievementToastSnapshot(
        AchievementToastState state,
        AchievementDefinition achievement,
        int queuedCount,
        double transitionProgress,
        double holdProgress)
    {
        this.state = Objects.requireNonNull(state, "state");
        this.achievement = achievement;
        if (queuedCount < 0)
        {
            throw new IllegalArgumentException(
                "Queued achievement count cannot be negative.");
        }
        this.queuedCount = queuedCount;
        this.transitionProgress = clamp(transitionProgress);
        this.holdProgress = clamp(holdProgress);
        if (state == AchievementToastState.IDLE && achievement != null)
        {
            throw new IllegalArgumentException(
                "Idle milestone snapshots cannot contain an achievement.");
        }
        if (state != AchievementToastState.IDLE && achievement == null)
        {
            throw new IllegalArgumentException(
                "Active milestone snapshots require an achievement.");
        }
    }

    public static AchievementToastSnapshot idle()
    {
        return new AchievementToastSnapshot(
            AchievementToastState.IDLE,
            null,
            0,
            0.0,
            0.0);
    }

    public AchievementToastState getState()
    {
        return state;
    }

    public Optional<AchievementDefinition> getAchievement()
    {
        return Optional.ofNullable(achievement);
    }

    public int getQueuedCount()
    {
        return queuedCount;
    }

    public int getRemainingCount()
    {
        return achievement == null ? 0 : queuedCount + 1;
    }

    public double getTransitionProgress()
    {
        return transitionProgress;
    }

    public double getHoldProgress()
    {
        return holdProgress;
    }

    public boolean isActive()
    {
        return state != AchievementToastState.IDLE;
    }

    private static double clamp(double value)
    {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
