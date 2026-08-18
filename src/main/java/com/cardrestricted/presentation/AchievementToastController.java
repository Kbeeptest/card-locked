package com.cardrestricted.presentation;

import com.cardrestricted.collection.achievement.AchievementDefinition;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class AchievementToastController
{
    public static final long ENTER_MILLIS = 260L;
    public static final long HOLD_MILLIS = 4_200L;
    public static final long EXIT_MILLIS = 320L;
    private static final long REDUCED_ENTER_MILLIS = 120L;
    private static final long REDUCED_EXIT_MILLIS = 120L;

    private final Deque<AchievementDefinition> queue = new ArrayDeque<>();
    private final Set<String> pendingIds = new HashSet<>();
    private AchievementToastState state = AchievementToastState.IDLE;
    private AchievementDefinition current;
    private boolean reducedMotion;
    private long stateElapsedMillis;
    private long lastTickMillis = -1L;

    public synchronized void setReducedMotion(boolean reducedMotion)
    {
        this.reducedMotion = reducedMotion;
    }

    public synchronized void enqueue(
        Collection<AchievementDefinition> achievements)
    {
        Objects.requireNonNull(achievements, "achievements");
        for (AchievementDefinition achievement : achievements)
        {
            AchievementDefinition value = Objects.requireNonNull(
                achievement,
                "achievement");
            if (pendingIds.add(value.getAchievementId()))
            {
                queue.addLast(value);
            }
        }
        if (state == AchievementToastState.IDLE)
        {
            startNext();
        }
    }

    public synchronized void tick(long nowMillis)
    {
        if (lastTickMillis < 0L)
        {
            lastTickMillis = nowMillis;
            return;
        }
        long elapsed = Math.max(0L, nowMillis - lastTickMillis);
        lastTickMillis = nowMillis;
        advance(elapsed);
    }

    public synchronized void synchroniseClock(long nowMillis)
    {
        lastTickMillis = nowMillis;
    }

    public synchronized void advance(long elapsedMillis)
    {
        if (elapsedMillis < 0L)
        {
            throw new IllegalArgumentException(
                "Elapsed milestone time cannot be negative.");
        }
        long remaining = elapsedMillis;
        while (remaining > 0L && state != AchievementToastState.IDLE)
        {
            long duration = stateDuration();
            long available = Math.max(0L, duration - stateElapsedMillis);
            long consumed = Math.min(remaining, available);
            stateElapsedMillis += consumed;
            remaining -= consumed;
            if (stateElapsedMillis >= duration)
            {
                advanceState();
            }
            else
            {
                break;
            }
        }
    }

    public synchronized void dismissCurrent()
    {
        if (state == AchievementToastState.ENTERING
            || state == AchievementToastState.HOLDING)
        {
            state = AchievementToastState.EXITING;
            stateElapsedMillis = 0L;
        }
    }

    public synchronized AchievementToastSnapshot snapshot()
    {
        if (state == AchievementToastState.IDLE)
        {
            return AchievementToastSnapshot.idle();
        }
        double transitionProgress = 1.0;
        double holdProgress = 0.0;
        if (state == AchievementToastState.ENTERING
            || state == AchievementToastState.EXITING)
        {
            transitionProgress = ratio(
                stateElapsedMillis,
                stateDuration());
        }
        else if (state == AchievementToastState.HOLDING)
        {
            holdProgress = ratio(stateElapsedMillis, HOLD_MILLIS);
        }
        return new AchievementToastSnapshot(
            state,
            current,
            queue.size(),
            transitionProgress,
            holdProgress);
    }

    public synchronized boolean isActive()
    {
        return state != AchievementToastState.IDLE;
    }

    public synchronized void clear()
    {
        queue.clear();
        pendingIds.clear();
        current = null;
        state = AchievementToastState.IDLE;
        stateElapsedMillis = 0L;
        lastTickMillis = -1L;
    }

    private void startNext()
    {
        current = queue.pollFirst();
        stateElapsedMillis = 0L;
        if (current == null)
        {
            state = AchievementToastState.IDLE;
            lastTickMillis = -1L;
        }
        else
        {
            state = AchievementToastState.ENTERING;
        }
    }

    private void advanceState()
    {
        switch (state)
        {
            case ENTERING:
                state = AchievementToastState.HOLDING;
                stateElapsedMillis = 0L;
                break;
            case HOLDING:
                state = AchievementToastState.EXITING;
                stateElapsedMillis = 0L;
                break;
            case EXITING:
                if (current != null)
                {
                    pendingIds.remove(current.getAchievementId());
                }
                current = null;
                startNext();
                break;
            case IDLE:
            default:
                break;
        }
    }

    private long stateDuration()
    {
        switch (state)
        {
            case ENTERING:
                return reducedMotion
                    ? REDUCED_ENTER_MILLIS
                    : ENTER_MILLIS;
            case HOLDING:
                return HOLD_MILLIS;
            case EXITING:
                return reducedMotion
                    ? REDUCED_EXIT_MILLIS
                    : EXIT_MILLIS;
            case IDLE:
            default:
                return 1L;
        }
    }

    private static double ratio(long elapsed, long duration)
    {
        return Math.max(
            0.0,
            Math.min(1.0, elapsed / (double) Math.max(1L, duration)));
    }
}
