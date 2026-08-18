package com.cardrestricted.selftest;

import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Stateful replay scenarios which previously required manual client testing. */
public final class IntegrityEventReplayTest
{
    private static int packed(int group)
    {
        return group << 16;
    }

    @Test
    public void shopOpenedBeforeEnforcementCannotTransact()
    {
        IntegrityReplayHarness replay = new IntegrityReplayHarness()
            .at(100)
            .load(WidgetID.SHOP_GROUP_ID);
        assertFalse(replay.shopAllowed(
            MenuAction.CC_OP, "Buy-10", packed(WidgetID.SHOP_GROUP_ID)));
    }

    @Test
    public void directUnlockedNpcShopSequenceAuthorizesTransaction()
    {
        IntegrityReplayHarness replay = new IntegrityReplayHarness()
            .at(100)
            .world(MenuAction.NPC_SECOND_OPTION, "Trade", "Merchant",
                true, true)
            .advance(1)
            .load(WidgetID.SHOP_GROUP_ID);
        assertTrue(replay.shopAllowed(
            MenuAction.CC_OP, "Buy-1", packed(WidgetID.SHOP_GROUP_ID)));
    }

    @Test
    public void lockedNpcSourceCannotAuthorizeShop()
    {
        IntegrityReplayHarness replay = new IntegrityReplayHarness()
            .at(100)
            .world(MenuAction.NPC_SECOND_OPTION, "Trade", "Merchant",
                true, false)
            .advance(1)
            .load(WidgetID.SHOP_GROUP_ID);
        assertFalse(replay.shopAllowed(
            MenuAction.CC_OP, "Buy-1", packed(WidgetID.SHOP_GROUP_ID)));
    }

    @Test
    public void unlockedTalkDialogueAndDelayedShopOpenAreReplayed()
    {
        IntegrityReplayHarness replay = new IntegrityReplayHarness()
            .at(100)
            .world(MenuAction.NPC_FIRST_OPTION, "Talk-to", "Merchant",
                true, true)
            .advance(40)
            .dialogue(MenuAction.CC_OP, "I'd like to see your shop.", "",
                "Show me what you have",
                packed(WidgetID.DIALOG_OPTION_GROUP_ID))
            .advance(2)
            .load(WidgetID.SHOP_GROUP_ID);
        assertTrue(replay.shopAllowed(
            MenuAction.CC_OP, "Buy-50", packed(WidgetID.SHOP_GROUP_ID)));
    }

    @Test
    public void staleTalkProofDoesNotAuthorizeDialogueOrShop()
    {
        IntegrityReplayHarness replay = new IntegrityReplayHarness()
            .at(100)
            .world(MenuAction.NPC_FIRST_OPTION, "Talk-to", "Merchant",
                true, true)
            .advance(201)
            .dialogue(MenuAction.CC_OP, "Shop", "", "Browse the shop",
                packed(WidgetID.DIALOG_OPTION_GROUP_ID))
            .advance(1)
            .load(WidgetID.SHOP_GROUP_ID);
        assertFalse(replay.shopAllowed(
            MenuAction.CC_OP, "Buy-1", packed(WidgetID.SHOP_GROUP_ID)));
    }

    @Test
    public void closingShopInvalidatesAuthorizationImmediately()
    {
        IntegrityReplayHarness replay = new IntegrityReplayHarness()
            .at(10)
            .world(MenuAction.NPC_SECOND_OPTION, "Trade", "Merchant",
                true, true)
            .advance(1)
            .load(WidgetID.SHOP_GROUP_ID);
        assertTrue(replay.shopAllowed(
            MenuAction.CC_OP, "Buy-1", packed(WidgetID.SHOP_GROUP_ID)));
        replay.close(WidgetID.SHOP_GROUP_ID);
        assertFalse(replay.shopAllowed(
            MenuAction.CC_OP, "Buy-1", packed(WidgetID.SHOP_GROUP_ID)));
    }

    @Test
    public void objectOpenedBankAuthorizesButPreexistingBankDoesNot()
    {
        IntegrityReplayHarness unauthorized = new IntegrityReplayHarness()
            .at(50)
            .load(WidgetID.BANK_GROUP_ID);
        assertFalse(unauthorized.storageAllowed(
            MenuAction.CC_OP, "Withdraw-1", packed(WidgetID.BANK_GROUP_ID)));

        IntegrityReplayHarness authorized = new IntegrityReplayHarness()
            .at(50)
            .world(MenuAction.GAME_OBJECT_FIRST_OPTION, "Bank", "Bank booth",
                false, false)
            .advance(2)
            .load(WidgetID.BANK_GROUP_ID);
        assertTrue(authorized.storageAllowed(
            MenuAction.CC_OP, "Withdraw-1", packed(WidgetID.BANK_GROUP_ID)));
    }

    @Test
    public void geBoothSequenceAuthorizesAllLoadedExchangePanels()
    {
        IntegrityReplayHarness replay = new IntegrityReplayHarness()
            .at(60)
            .world(MenuAction.GAME_OBJECT_FIRST_OPTION, "Exchange",
                "Grand Exchange booth", false, false)
            .advance(2)
            .load(WidgetID.GRAND_EXCHANGE_GROUP_ID)
            .load(WidgetID.GRAND_EXCHANGE_INVENTORY_GROUP_ID);
        assertTrue(replay.exchangeAllowed(
            MenuAction.CC_OP, "Create buy offer",
            packed(WidgetID.GRAND_EXCHANGE_GROUP_ID)));
        assertTrue(replay.exchangeAllowed(
            MenuAction.ITEM_FIRST_OPTION, "Offer",
            packed(WidgetID.GRAND_EXCHANGE_INVENTORY_GROUP_ID)));
    }

    @Test
    public void geAuthorizationSurvivesOnePanelCloseButNotFinalClose()
    {
        IntegrityReplayHarness replay = new IntegrityReplayHarness()
            .at(60)
            .world(MenuAction.GAME_OBJECT_FIRST_OPTION, "Exchange",
                "Grand Exchange booth", false, false)
            .advance(1)
            .load(WidgetID.GRAND_EXCHANGE_GROUP_ID)
            .load(WidgetID.GRAND_EXCHANGE_INVENTORY_GROUP_ID);
        replay.close(WidgetID.GRAND_EXCHANGE_INVENTORY_GROUP_ID);
        assertTrue(replay.exchangeAllowed(
            MenuAction.CC_OP, "Create sell offer",
            packed(WidgetID.GRAND_EXCHANGE_GROUP_ID)));
        replay.close(WidgetID.GRAND_EXCHANGE_GROUP_ID);
        assertFalse(replay.exchangeAllowed(
            MenuAction.CC_OP, "Create sell offer",
            packed(WidgetID.GRAND_EXCHANGE_GROUP_ID)));
    }

    @Test
    public void servicePanelRequiresAuthenticatedSourceAndMatchingFamily()
    {
        IntegrityReplayHarness unauthorized = new IntegrityReplayHarness()
            .at(70)
            .load(InterfaceID.TANNER);
        assertFalse(unauthorized.serviceAllowed(
            MenuAction.CC_OP, "Tan-all", packed(InterfaceID.TANNER)));

        IntegrityReplayHarness authorized = new IntegrityReplayHarness()
            .at(70)
            .world(MenuAction.NPC_SECOND_OPTION, "Tan", "Tanner",
                true, true)
            .advance(1)
            .load(InterfaceID.TANNER);
        assertTrue(authorized.serviceAllowed(
            MenuAction.CC_OP, "Tan-all", packed(InterfaceID.TANNER)));
        assertFalse(authorized.serviceAllowed(
            MenuAction.CC_OP, "Buy", packed(InterfaceID.PVP_STORE)));
    }

    @Test
    public void multiPanelServiceStaysAuthorizedUntilLastFamilyPanelCloses()
    {
        IntegrityReplayHarness replay = new IntegrityReplayHarness()
            .at(80)
            .world(MenuAction.NPC_SECOND_OPTION, "Slayer rewards",
                "Slayer master", true, true)
            .advance(1)
            .load(InterfaceID.SLAYER_REWARDS)
            .load(InterfaceID.SLAYER_REWARDS_TASK_LIST);
        assertTrue(replay.serviceAllowed(
            MenuAction.CC_OP, "Buy", packed(InterfaceID.SLAYER_REWARDS)));
        replay.close(InterfaceID.SLAYER_REWARDS_TASK_LIST);
        assertTrue(replay.serviceAllowed(
            MenuAction.CC_OP, "Buy", packed(InterfaceID.SLAYER_REWARDS)));
        replay.close(InterfaceID.SLAYER_REWARDS);
        assertFalse(replay.serviceAllowed(
            MenuAction.CC_OP, "Buy", packed(InterfaceID.SLAYER_REWARDS)));
    }

    @Test
    public void unrelatedWorldInteractionClearsPendingInterfaceProof()
    {
        IntegrityReplayHarness replay = new IntegrityReplayHarness()
            .at(90)
            .world(MenuAction.NPC_SECOND_OPTION, "Trade", "Merchant",
                true, true)
            .advance(1)
            .world(MenuAction.GAME_OBJECT_FIRST_OPTION, "Climb", "Ladder",
                false, false)
            .advance(1)
            .load(WidgetID.SHOP_GROUP_ID);
        assertFalse(replay.shopAllowed(
            MenuAction.CC_OP, "Buy-1", packed(WidgetID.SHOP_GROUP_ID)));
    }

    @Test
    public void resetInvalidatesEveryInterfaceProof()
    {
        IntegrityReplayHarness replay = new IntegrityReplayHarness()
            .at(100)
            .world(MenuAction.GAME_OBJECT_FIRST_OPTION, "Bank", "Bank booth",
                false, false)
            .advance(1)
            .load(WidgetID.BANK_GROUP_ID)
            .reset();
        assertFalse(replay.storageAllowed(
            MenuAction.CC_OP, "Withdraw-1", packed(WidgetID.BANK_GROUP_ID)));
        assertFalse(replay.shop().isShopOpen());
        assertFalse(replay.storage().isStorageOpen());
        assertFalse(replay.exchange().isExchangeOpen());
        assertFalse(replay.service().isServiceOpen());
    }
}
