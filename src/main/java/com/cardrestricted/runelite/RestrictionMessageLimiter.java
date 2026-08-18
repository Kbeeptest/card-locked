package com.cardrestricted.runelite;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded per-message cooldown used to keep blocked-action chat readable. */
public final class RestrictionMessageLimiter
{
    private static final int DEFAULT_REPEAT_COOLDOWN_TICKS = 5;
    private static final int DEFAULT_CAPACITY = 64;

    private final int repeatCooldownTicks;
    private final int capacity;
    private final LinkedHashMap<String, Integer> lastEmittedTicks =
        new LinkedHashMap<>();
    private int lastObservedTick = Integer.MIN_VALUE;
    private int lastAnyMessageTick = Integer.MIN_VALUE;

    public RestrictionMessageLimiter()
    {
        this(DEFAULT_REPEAT_COOLDOWN_TICKS, DEFAULT_CAPACITY);
    }

    RestrictionMessageLimiter(int repeatCooldownTicks, int capacity)
    {
        if (repeatCooldownTicks < 1)
        {
            throw new IllegalArgumentException(
                "repeatCooldownTicks must be positive");
        }
        if (capacity < 1)
        {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.repeatCooldownTicks = repeatCooldownTicks;
        this.capacity = capacity;
    }

    public boolean shouldEmit(String messageKey, int tick)
    {
        String key = Objects.requireNonNull(messageKey, "messageKey");
        if (tick < lastObservedTick)
        {
            clear();
        }
        lastObservedTick = tick;

        if (lastAnyMessageTick == tick)
        {
            return false;
        }
        Integer previousTick = lastEmittedTicks.get(key);
        if (previousTick != null
            && (long) tick - previousTick < repeatCooldownTicks)
        {
            return false;
        }

        lastEmittedTicks.remove(key);
        lastEmittedTicks.put(key, tick);
        trimToCapacity();
        lastAnyMessageTick = tick;
        return true;
    }

    public void clear()
    {
        lastEmittedTicks.clear();
        lastObservedTick = Integer.MIN_VALUE;
        lastAnyMessageTick = Integer.MIN_VALUE;
    }

    int trackedMessageCount()
    {
        return lastEmittedTicks.size();
    }

    private void trimToCapacity()
    {
        Iterator<Map.Entry<String, Integer>> entries =
            lastEmittedTicks.entrySet().iterator();
        while (lastEmittedTicks.size() > capacity && entries.hasNext())
        {
            entries.next();
            entries.remove();
        }
    }
}
