package com.cardrestricted.points;

import java.util.Locale;

public enum ClueTier
{
    BEGINNER(500L),
    EASY(1_000L),
    MEDIUM(2_000L),
    HARD(3_000L),
    ELITE(4_000L),
    MASTER(5_000L);

    private final long points;

    ClueTier(long points)
    {
        this.points = points;
    }

    public long getPoints()
    {
        return points;
    }

    public String key()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ClueTier fromKey(String value)
    {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
