package com.cardrestricted.runelite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RestrictionMessageLimiterTest
{
    @Test
    public void suppressesRepeatedMessageUntilCooldownExpires()
    {
        RestrictionMessageLimiter limiter =
            new RestrictionMessageLimiter(5, 8);
        assertTrue(limiter.shouldEmit("locked item", 10));
        assertFalse(limiter.shouldEmit("locked item", 11));
        assertFalse(limiter.shouldEmit("locked item", 14));
        assertTrue(limiter.shouldEmit("locked item", 15));
    }

    @Test
    public void emitsAtMostOneBlockedMessagePerTick()
    {
        RestrictionMessageLimiter limiter =
            new RestrictionMessageLimiter(5, 8);
        assertTrue(limiter.shouldEmit("item", 20));
        assertFalse(limiter.shouldEmit("npc", 20));
        assertTrue(limiter.shouldEmit("npc", 21));
    }

    @Test
    public void remainsBoundedUnderDistinctMessageFlood()
    {
        RestrictionMessageLimiter limiter =
            new RestrictionMessageLimiter(100, 3);
        for (int index = 0; index < 20; index++)
        {
            assertTrue(limiter.shouldEmit("message-" + index, index));
        }
        assertEquals(3, limiter.trackedMessageCount());
    }

    @Test
    public void tickRollbackStartsAFreshSessionWindow()
    {
        RestrictionMessageLimiter limiter =
            new RestrictionMessageLimiter(100, 8);
        assertTrue(limiter.shouldEmit("locked", 500));
        assertTrue(limiter.shouldEmit("locked", 2));
        assertEquals(1, limiter.trackedMessageCount());
    }

    @Test
    public void clearRemovesCooldownState()
    {
        RestrictionMessageLimiter limiter =
            new RestrictionMessageLimiter(100, 8);
        assertTrue(limiter.shouldEmit("locked", 40));
        limiter.clear();
        assertTrue(limiter.shouldEmit("locked", 40));
    }
}
