package com.cardrestricted.points;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Awards level-up points on the same accelerating shape as the OSRS XP curve.
 */
public final class SkillLevelRewardPolicy
{
    public static final long LEVEL_1_REWARD = 1_250L;
    public static final long LEVEL_99_REWARD = 25_000L;
    private static final int MAX_LEVEL = 99;
    private static final long LEVEL_99_XP = experienceForLevel(MAX_LEVEL);

    public PointAward createAward(
        String skillKey,
        int previousLevel,
        int currentLevel,
        Instant occurredAt)
    {
        Objects.requireNonNull(skillKey, "skillKey");
        if (skillKey.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                "skillKey cannot be blank.");
        }
        if (previousLevel < 1
            || currentLevel <= previousLevel
            || currentLevel > MAX_LEVEL)
        {
            throw new IllegalArgumentException(
                "A level reward must advance between levels 1 and 99.");
        }

        long points = 0L;
        for (int level = previousLevel + 1;
             level <= currentLevel;
             level++)
        {
            points = Math.addExact(points, rewardForLevel(level));
        }
        String sourceId = String.format(
            Locale.ROOT,
            "skill-level:v2:%s:%d-%d",
            skillKey,
            previousLevel,
            currentLevel);
        return new PointAward(
            sourceId,
            PointSourceType.SKILL_LEVEL,
            points,
            occurredAt);
    }

    public static long rewardForLevel(int level)
    {
        if (level < 1 || level > MAX_LEVEL)
        {
            throw new IllegalArgumentException(
                "level must be between 1 and 99.");
        }
        if (level == 1)
        {
            return LEVEL_1_REWARD;
        }
        if (level == MAX_LEVEL)
        {
            return LEVEL_99_REWARD;
        }
        long xp = experienceForLevel(level);
        double progress = xp / (double) LEVEL_99_XP;
        return LEVEL_1_REWARD + Math.round(
            progress * (LEVEL_99_REWARD - LEVEL_1_REWARD));
    }

    static long experienceForLevel(int level)
    {
        if (level < 1 || level > MAX_LEVEL)
        {
            throw new IllegalArgumentException(
                "level must be between 1 and 99.");
        }
        long accumulated = 0L;
        for (int current = 1; current < level; current++)
        {
            accumulated += (long) Math.floor(
                current + 300.0 * Math.pow(2.0, current / 7.0));
        }
        return accumulated / 4L;
    }
}
