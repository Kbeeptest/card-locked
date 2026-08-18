package com.cardrestricted.selftest;

import java.util.Random;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Long-running deterministic model checks for interface provenance. These
 * sequences deliberately mix valid proofs, unrelated actions, stale delays,
 * panel closes and lifecycle resets to expose state leakage between systems.
 */
public final class LongSequenceAuthorizationModelTest
{
    private static int packed(int group)
    {
        return group << 16;
    }

    @Test
    public void fiftyThousandMixedTransitionsNeverLeakAuthorization()
    {
        Random random = new Random(0xC1893A11L);
        IntegrityReplayHarness replay = new IntegrityReplayHarness().at(1);
        boolean shopExpected = false;
        boolean bankExpected = false;
        boolean geExpected = false;
        boolean serviceExpected = false;

        for (int step = 0; step < 50_000; step++)
        {
            switch (random.nextInt(12))
            {
                case 0:
                    replay.world(MenuAction.NPC_SECOND_OPTION, "Trade",
                        "Merchant", true, true).advance(1)
                        .load(WidgetID.SHOP_GROUP_ID);
                    shopExpected = true;
                    break;
                case 1:
                    replay.world(MenuAction.GAME_OBJECT_FIRST_OPTION, "Bank",
                        "Bank booth", false, false).advance(1)
                        .load(WidgetID.BANK_GROUP_ID);
                    bankExpected = true;
                    break;
                case 2:
                    replay.world(MenuAction.GAME_OBJECT_FIRST_OPTION,
                        "Exchange", "Grand Exchange booth", false, false)
                        .advance(1).load(WidgetID.GRAND_EXCHANGE_GROUP_ID);
                    geExpected = true;
                    break;
                case 3:
                    replay.world(MenuAction.NPC_SECOND_OPTION, "Tan", "Tanner",
                        true, true).advance(1).load(InterfaceID.TANNER);
                    serviceExpected = true;
                    break;
                case 4:
                    replay.world(MenuAction.NPC_SECOND_OPTION, "Trade",
                        "Locked merchant", true, false).advance(1)
                        .load(WidgetID.SHOP_GROUP_ID);
                    // A rejected source must not create new proof, but it
                    // also cannot revoke an already-open valid shop.
                    break;
                case 5:
                    replay.world(MenuAction.GAME_OBJECT_FIRST_OPTION, "Climb",
                        "Ladder", false, false).advance(1);
                    // Unrelated world actions invalidate pending proof but
                    // do not close already loaded interfaces.
                    break;
                case 6:
                    replay.close(WidgetID.SHOP_GROUP_ID);
                    shopExpected = false;
                    break;
                case 7:
                    replay.close(WidgetID.BANK_GROUP_ID);
                    bankExpected = false;
                    break;
                case 8:
                    replay.close(WidgetID.GRAND_EXCHANGE_GROUP_ID);
                    geExpected = false;
                    break;
                case 9:
                    replay.close(InterfaceID.TANNER);
                    serviceExpected = false;
                    break;
                case 10:
                    replay.reset();
                    shopExpected = bankExpected = geExpected = serviceExpected = false;
                    break;
                default:
                    replay.advance(250);
                    // Open interfaces remain valid, but pending proof must not
                    // authorise a newly loaded interface after this delay.
                    break;
            }

            boolean shopActual = replay.shopAllowed(MenuAction.CC_OP, "Buy-1",
                packed(WidgetID.SHOP_GROUP_ID));
            boolean bankActual = replay.storageAllowed(MenuAction.CC_OP,
                "Withdraw-1", packed(WidgetID.BANK_GROUP_ID));
            boolean geActual = replay.exchangeAllowed(MenuAction.CC_OP,
                "Create buy offer", packed(WidgetID.GRAND_EXCHANGE_GROUP_ID));
            boolean serviceActual = replay.serviceAllowed(MenuAction.CC_OP,
                "Tan-all", packed(InterfaceID.TANNER));

            // This model is intentionally safety-oriented: implementation may
            // revoke proof more aggressively than the model, but it must never
            // authorise after the model has observed close/reset/no valid proof.
            if (!shopExpected) assertFalse(shopActual);
            if (!bankExpected) assertFalse(bankActual);
            if (!geExpected) assertFalse(geActual);
            if (!serviceExpected) assertFalse(serviceActual);
        }
    }

    @Test
    public void staleProofCannotBeRevivedByUnrelatedWidgetLoads()
    {
        Random random = new Random(0x89357A1EL);
        int[] unrelatedGroups = {
            WidgetID.INVENTORY_GROUP_ID,
            WidgetID.EQUIPMENT_GROUP_ID,
            WidgetID.CHATBOX_GROUP_ID,
            WidgetID.MINIMAP_GROUP_ID
        };
        for (int iteration = 0; iteration < 5_000; iteration++)
        {
            IntegrityReplayHarness replay = new IntegrityReplayHarness()
                .at(iteration * 300 + 1)
                .world(MenuAction.NPC_SECOND_OPTION, "Trade", "Merchant",
                    true, true)
                .advance(20 + random.nextInt(500));
            for (int i = 0; i < 1 + random.nextInt(8); i++)
            {
                replay.load(unrelatedGroups[random.nextInt(unrelatedGroups.length)]);
            }
            replay.load(WidgetID.SHOP_GROUP_ID);
            assertFalse(replay.shopAllowed(MenuAction.CC_OP, "Buy-1",
                packed(WidgetID.SHOP_GROUP_ID)));
        }
    }
}
