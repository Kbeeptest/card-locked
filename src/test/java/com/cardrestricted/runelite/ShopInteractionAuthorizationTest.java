package com.cardrestricted.runelite;

import net.runelite.api.MenuAction;
import net.runelite.api.widgets.WidgetID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ShopInteractionAuthorizationTest
{
    @Test
    public void expiredTalkDoesNotAuthorizeLaterShop()
    {
        ShopInteractionAuthorization auth = new ShopInteractionAuthorization();
        auth.observeAllowedWorldInteraction(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            "Shop keeper",
            100,
            true,
            true);
        auth.onWidgetLoaded(WidgetID.SHOP_GROUP_ID, 104);
        assertFalse(auth.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Buy 1",
            104,
            packed(WidgetID.SHOP_GROUP_ID, 5)));
    }

    @Test
    public void explicitShopDialogueAfterUnlockedTalkAuthorizesShop()
    {
        ShopInteractionAuthorization auth = new ShopInteractionAuthorization();
        auth.observeAllowedWorldInteraction(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            "Shop keeper",
            100,
            true,
            true);
        assertTrue(auth.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Buy",
            102,
            packed(WidgetID.DIALOG_OPTION_GROUP_ID, 5)));
        assertTrue(auth.observeAllowedDialogueChoice(
            MenuAction.CC_OP,
            "Show me what you have",
            "",
            "Browse my wares",
            102,
            packed(WidgetID.DIALOG_OPTION_GROUP_ID, 5)));
        auth.onWidgetLoaded(WidgetID.SHOP_GROUP_ID, 104);
        assertTrue(auth.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Buy 1",
            104,
            packed(WidgetID.SHOP_GROUP_ID, 5)));
    }

    @Test
    public void lockedTalkCannotAuthorizeShopDialogue()
    {
        ShopInteractionAuthorization auth = new ShopInteractionAuthorization();
        auth.observeAllowedWorldInteraction(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            "Locked merchant",
            100,
            true,
            false);
        assertFalse(auth.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Buy",
            102,
            packed(WidgetID.DIALOG_OPTION_GROUP_ID, 5)));
        assertFalse(auth.observeAllowedDialogueChoice(
            MenuAction.CC_OP,
            "Buy",
            "",
            "Browse wares",
            102,
            packed(WidgetID.DIALOG_OPTION_GROUP_ID, 5)));
        assertFalse(auth.isTransactionAuthorized(
            MenuAction.RUNELITE,
            "Buy",
            102,
            packed(WidgetID.DIALOG_OPTION_GROUP_ID, 5)));
    }

    @Test
    public void directUnlockedTradeAndShopObjectAuthorizeBriefly()
    {
        ShopInteractionAuthorization npc = new ShopInteractionAuthorization();
        npc.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Trade",
            "Merchant",
            100,
            true,
            true);
        npc.onWidgetLoaded(WidgetID.SHOP_GROUP_ID, 102);
        assertTrue(npc.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Sell 1",
            102,
            packed(WidgetID.SHOP_GROUP_ID, 5)));

        ShopInteractionAuthorization object = new ShopInteractionAuthorization();
        object.observeAllowedWorldInteraction(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            "Open",
            "Shop counter",
            200,
            false,
            false);
        object.onWidgetLoaded(WidgetID.SHOP_GROUP_ID, 202);
        assertTrue(object.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Buy 1",
            202,
            packed(WidgetID.SHOP_GROUP_ID, 5)));
    }

    @Test
    public void grandExchangeIsNotMistakenForNpcShop()
    {
        ShopInteractionAuthorization auth = new ShopInteractionAuthorization();
        assertTrue(auth.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Buy",
            1,
            packed(WidgetID.GRAND_EXCHANGE_GROUP_ID, 5)));
    }

    @Test
    public void buyAndSellOptionsRemainRecognisableWithoutWidgetMetadata()
    {
        assertTrue(ShopInteractionAuthorization.isBuyOption("Buy-50"));
        assertTrue(ShopInteractionAuthorization.isSellOption("Sell 10"));
        assertFalse(ShopInteractionAuthorization.isBuyOrSellOption("Exchange"));
    }

    @Test
    public void customShopInterfacesRequireRecentVerifiedSource()
    {
        int customShop = packed(777, 4);
        ShopInteractionAuthorization inherited =
            new ShopInteractionAuthorization();
        assertFalse(inherited.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Purchase 1",
            100,
            customShop));

        ShopInteractionAuthorization verified =
            new ShopInteractionAuthorization();
        verified.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Trade",
            "Reward merchant",
            100,
            true,
            true);
        assertTrue(verified.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Purchase 1",
            120,
            customShop));
        assertTrue(verified.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Buy 10",
            250,
            customShop));
        verified.onWidgetClosed(777);
        assertFalse(verified.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Buy 1",
            251,
            customShop));
    }

    private static int packed(int groupId, int childId)
    {
        return groupId << 16 | childId;
    }

    @Test
    public void standardShopInventorySideUsesTheSameAuthorization()
    {
        ShopInteractionAuthorization authorization =
            new ShopInteractionAuthorization();
        authorization.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Trade",
            "Merchant",
            10,
            true,
            true);
        authorization.onWidgetLoaded(
            WidgetID.SHOP_INVENTORY_GROUP_ID,
            11);
        authorization.onWidgetLoaded(WidgetID.SHOP_GROUP_ID, 12);
        assertTrue(authorization.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Sell 1",
            12,
            packed(WidgetID.SHOP_INVENTORY_GROUP_ID, 5)));
    }

    @Test
    public void immediateShopOpenedByUnlockedTalkIsAuthorizedBriefly()
    {
        ShopInteractionAuthorization unlocked =
            new ShopInteractionAuthorization();
        unlocked.observeAllowedWorldInteraction(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            "Merchant",
            100,
            true,
            true);
        unlocked.onWidgetLoaded(WidgetID.SHOP_GROUP_ID, 102);
        assertTrue(unlocked.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Buy 1",
            102,
            packed(WidgetID.SHOP_GROUP_ID, 2)));

        ShopInteractionAuthorization locked =
            new ShopInteractionAuthorization();
        locked.observeAllowedWorldInteraction(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            "Locked merchant",
            100,
            true,
            false);
        locked.onWidgetLoaded(WidgetID.SHOP_GROUP_ID, 102);
        assertFalse(locked.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Buy 1",
            102,
            packed(WidgetID.SHOP_GROUP_ID, 2)));
    }

    @Test
    public void conflictingCustomShopMetadataFailsClosed()
    {
        ShopInteractionAuthorization authorization =
            new ShopInteractionAuthorization();
        authorization.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Trade",
            "Reward merchant",
            10,
            true,
            true);
        assertFalse(authorization.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Buy 1",
            11,
            packed(777, 1),
            packed(778, 1)));
    }


    @Test
    public void movementClearsStaleCustomShopProvenance()
    {
        ShopInteractionAuthorization authorization =
            new ShopInteractionAuthorization();
        authorization.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Trade",
            "Reward merchant",
            10,
            true,
            true);
        authorization.observeAllowedWorldInteraction(
            MenuAction.WALK,
            "Walk here",
            "",
            11,
            false,
            false);
        assertFalse(authorization.isTransactionAuthorized(
            MenuAction.CC_OP,
            "Buy 1",
            12,
            packed(777, 1)));
    }

}
