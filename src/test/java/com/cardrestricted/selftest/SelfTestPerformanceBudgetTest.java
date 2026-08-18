package com.cardrestricted.selftest;

import com.cardrestricted.diagnostics.IntegrityTraceDecision;
import com.cardrestricted.diagnostics.IntegrityTraceLog;
import com.cardrestricted.diagnostics.IntegrityTraceReason;
import com.cardrestricted.runelite.InteractionSurfacePolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.WidgetID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Conservative speed and bounded-memory gates for code executed on menu paths. */
public final class SelfTestPerformanceBudgetTest
{
    @Test
    public void actionClassificationHandlesOneMillionCallsWithinBudget()
    {
        MenuAction[] actions = MenuAction.values();
        long started = System.nanoTime();
        long checksum = 0L;
        for (int index = 0; index < 1_000_000; index++)
        {
            MenuAction action = actions[index % actions.length];
            checksum += InteractionSurfacePolicy.surfaceFor(action).ordinal();
            checksum += InteractionSurfacePolicy.riskClassFor(action).ordinal();
        }
        long elapsed = elapsedMillis(started);
        assertTrue("Classification checksum was unexpectedly empty.",
            checksum > 0L);
        assertTrue("One million action classifications exceeded 5 seconds: "
                + elapsed + " ms",
            elapsed < 5_000L);
    }

    @Test
    public void replayHarnessHandlesTenThousandInterfaceLifecycles()
    {
        long started = System.nanoTime();
        int allowed = 0;
        for (int index = 0; index < 10_000; index++)
        {
            IntegrityReplayHarness replay = new IntegrityReplayHarness()
                .at(index * 5)
                .world(MenuAction.NPC_SECOND_OPTION, "Trade", "Merchant",
                    true, true)
                .advance(1)
                .load(WidgetID.SHOP_GROUP_ID);
            if (replay.shopAllowed(
                MenuAction.CC_OP,
                "Buy-1",
                WidgetID.SHOP_GROUP_ID << 16))
            {
                allowed++;
            }
            replay.close(WidgetID.SHOP_GROUP_ID).reset();
        }
        long elapsed = elapsedMillis(started);
        assertEquals(10_000, allowed);
        assertTrue("Replay lifecycle stress exceeded 10 seconds: "
                + elapsed + " ms",
            elapsed < 10_000L);
    }

    @Test
    public void traceRemainsBoundedAfterOneHundredThousandRecords()
    {
        IntegrityTraceLog trace = new IntegrityTraceLog(
            IntegrityTraceLog.MAX_CAPACITY,
            Clock.fixed(
                Instant.parse("2026-08-05T01:00:00Z"),
                ZoneOffset.UTC));
        MenuAction[] actions = MenuAction.values();
        long started = System.nanoTime();
        for (int index = 0; index < 100_000; index++)
        {
            trace.record(
                index,
                actions[index % actions.length],
                index % 2 == 0 ? "Trade" : "Use",
                index % 2 == 0
                    ? IntegrityTraceDecision.BLOCKED
                    : IntegrityTraceDecision.ALLOWED,
                IntegrityTraceReason.POLICY_BLOCKED,
                WidgetID.SHOP_GROUP_ID << 16,
                index);
        }
        long elapsed = elapsedMillis(started);
        assertEquals(IntegrityTraceLog.MAX_CAPACITY, trace.size());
        assertEquals(IntegrityTraceLog.MAX_CAPACITY,
            trace.snapshot().size());
        assertTrue("Trace stress exceeded 10 seconds: "
                + elapsed + " ms",
            elapsed < 10_000L);
    }

    private static long elapsedMillis(long started)
    {
        return (System.nanoTime() - started) / 1_000_000L;
    }
}
