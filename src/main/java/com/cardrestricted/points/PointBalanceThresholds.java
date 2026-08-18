package com.cardrestricted.points;

import java.util.OptionalLong;

public final class PointBalanceThresholds
{
    public static final long NOTIFICATION_INTERVAL = 2_500L;

    private PointBalanceThresholds()
    {
    }

    public static OptionalLong highestCrossedThreshold(
        long previousBalance,
        long currentBalance)
    {
        if (currentBalance <= previousBalance || currentBalance < 0L)
        {
            return OptionalLong.empty();
        }
        long previousThreshold = Math.max(0L, previousBalance)
            / NOTIFICATION_INTERVAL;
        long currentThreshold = currentBalance
            / NOTIFICATION_INTERVAL;
        if (currentThreshold <= previousThreshold)
        {
            return OptionalLong.empty();
        }
        return OptionalLong.of(
            currentThreshold * NOTIFICATION_INTERVAL);
    }
}
