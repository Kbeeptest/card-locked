package com.cardrestricted.selftest;

import java.util.Random;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Property-style state-machine checks for interface provenance windows. */
public final class InterfaceAuthorizationFuzzTest
{
    private static int packed(int group)
    {
        return group << 16;
    }

    @Test
    public void unprovenInterfaceLoadsNeverAuthorizeTransactions()
    {
        int[] groups = {
            WidgetID.SHOP_GROUP_ID,
            WidgetID.BANK_GROUP_ID,
            WidgetID.GRAND_EXCHANGE_GROUP_ID,
            InterfaceID.TANNER,
            InterfaceID.SLAYER_REWARDS,
            InterfaceID.PVP_STORE,
            InterfaceID.SAILING_CUSTOMISATION
        };
        Random random = new Random(0x892FA11L);
        for (int iteration = 0; iteration < 10_000; iteration++)
        {
            int group = groups[random.nextInt(groups.length)];
            IntegrityReplayHarness replay = new IntegrityReplayHarness()
                .at(random.nextInt(1_000_000))
                .load(group);
            int widget = packed(group);
            if (group == WidgetID.SHOP_GROUP_ID)
            {
                assertFalse(replay.shopAllowed(
                    MenuAction.CC_OP, "Buy-10", widget));
            }
            else if (group == WidgetID.BANK_GROUP_ID)
            {
                assertFalse(replay.storageAllowed(
                    MenuAction.CC_OP, "Withdraw-10", widget));
            }
            else if (group == WidgetID.GRAND_EXCHANGE_GROUP_ID)
            {
                assertFalse(replay.exchangeAllowed(
                    MenuAction.CC_OP, "Create buy offer", widget));
            }
            else
            {
                assertFalse(replay.serviceAllowed(
                    MenuAction.CC_OP, "Buy", widget));
            }
        }
    }

    @Test
    public void sourceWindowsAreStrictAtAndBeyondTheirBoundaries()
    {
        Random random = new Random(892_79L);
        for (int iteration = 0; iteration < 2_000; iteration++)
        {
            int start = random.nextInt(100_000);
            IntegrityReplayHarness fresh = new IntegrityReplayHarness()
                .at(start)
                .world(MenuAction.NPC_SECOND_OPTION, "Trade", "Merchant",
                    true, true)
                .advance(random.nextInt(5))
                .load(WidgetID.SHOP_GROUP_ID);
            assertTrue(fresh.shopAllowed(
                MenuAction.CC_OP, "Buy-1", packed(WidgetID.SHOP_GROUP_ID)));

            IntegrityReplayHarness stale = new IntegrityReplayHarness()
                .at(start)
                .world(MenuAction.NPC_SECOND_OPTION, "Trade", "Merchant",
                    true, true)
                .advance(5 + random.nextInt(100))
                .load(WidgetID.SHOP_GROUP_ID);
            assertFalse(stale.shopAllowed(
                MenuAction.CC_OP, "Buy-1", packed(WidgetID.SHOP_GROUP_ID)));
        }
    }

    @Test
    public void closeAndResetAreAbsorbingUntilNewProofAppears()
    {
        Random random = new Random(892_5105L);
        for (int iteration = 0; iteration < 2_000; iteration++)
        {
            int start = random.nextInt(100_000);
            IntegrityReplayHarness replay = new IntegrityReplayHarness()
                .at(start)
                .world(MenuAction.GAME_OBJECT_FIRST_OPTION,
                    "Bank", "Bank booth", false, false)
                .advance(1)
                .load(WidgetID.BANK_GROUP_ID);
            assertTrue(replay.storageAllowed(
                MenuAction.CC_OP, "Withdraw-1", packed(WidgetID.BANK_GROUP_ID)));
            if (random.nextBoolean())
            {
                replay.close(WidgetID.BANK_GROUP_ID);
            }
            else
            {
                replay.reset();
            }
            assertFalse(replay.storageAllowed(
                MenuAction.CC_OP, "Withdraw-1", packed(WidgetID.BANK_GROUP_ID)));
        }
    }
}
