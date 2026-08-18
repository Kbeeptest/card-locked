package com.cardrestricted.runelite;

import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GrandExchangeInteractionAuthorizationTest
{
    private static final int EXCHANGE_WIDGET =
        WidgetID.GRAND_EXCHANGE_GROUP_ID << 16;

    @Test
    public void verifiedClerkAndBoothAuthorizeExchangeActions()
    {
        GrandExchangeInteractionAuthorization clerk =
            new GrandExchangeInteractionAuthorization();
        clerk.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Exchange",
            "Grand Exchange Clerk",
            100,
            true,
            true);
        clerk.onWidgetLoaded(WidgetID.GRAND_EXCHANGE_GROUP_ID, 102);
        assertTrue(clerk.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Buy offer", EXCHANGE_WIDGET));

        GrandExchangeInteractionAuthorization booth =
            new GrandExchangeInteractionAuthorization();
        booth.observeAllowedWorldInteraction(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            "Exchange",
            "Grand Exchange booth",
            200,
            false,
            false);
        booth.onWidgetLoaded(WidgetID.GRAND_EXCHANGE_GROUP_ID, 201);
        assertTrue(booth.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Sell offer", EXCHANGE_WIDGET));
    }

    @Test
    public void lockedClerkAndInheritedInterfaceFailClosed()
    {
        GrandExchangeInteractionAuthorization locked =
            new GrandExchangeInteractionAuthorization();
        locked.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Exchange",
            "Grand Exchange Clerk",
            10,
            true,
            false);
        locked.onWidgetLoaded(WidgetID.GRAND_EXCHANGE_GROUP_ID, 11);
        assertFalse(locked.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Buy offer", EXCHANGE_WIDGET));

        GrandExchangeInteractionAuthorization inherited =
            new GrandExchangeInteractionAuthorization();
        inherited.onWidgetLoaded(WidgetID.GRAND_EXCHANGE_GROUP_ID, 20);
        assertFalse(inherited.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Confirm offer", EXCHANGE_WIDGET));
    }

    @Test
    public void verifiedTalkAndExchangeDialogueAuthorize()
    {
        GrandExchangeInteractionAuthorization authorization =
            new GrandExchangeInteractionAuthorization();
        authorization.observeAllowedWorldInteraction(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            "Grand Exchange Clerk",
            100,
            true,
            true);
        assertTrue(authorization.observeAllowedDialogueChoice(
            "Manage my offers",
            "",
            "",
            110,
            WidgetID.DIALOG_OPTION_GROUP_ID << 16));
        authorization.onWidgetLoaded(WidgetID.GRAND_EXCHANGE_GROUP_ID, 112);
        assertTrue(authorization.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Buy offer", EXCHANGE_WIDGET));
    }

    @Test
    public void observationAndExitActionsRemainAvailable()
    {
        GrandExchangeInteractionAuthorization authorization =
            new GrandExchangeInteractionAuthorization();
        authorization.onWidgetLoaded(WidgetID.GRAND_EXCHANGE_GROUP_ID, 1);
        assertTrue(authorization.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Close", EXCHANGE_WIDGET));
        assertTrue(authorization.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Examine", EXCHANGE_WIDGET));
        assertFalse(authorization.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Collect", EXCHANGE_WIDGET));
    }

    @Test
    public void collectionInterfaceRequiresAndRetainsVerifiedProvenance()
    {
        int collectWidget = InterfaceID.GE_COLLECT << 16;
        GrandExchangeInteractionAuthorization inherited =
            new GrandExchangeInteractionAuthorization();
        inherited.onWidgetLoaded(InterfaceID.GE_COLLECT, 10);
        assertFalse(inherited.isInterfaceActionAuthorized(
            MenuAction.CC_OP,
            "Collect to inventory",
            collectWidget));

        GrandExchangeInteractionAuthorization verified =
            new GrandExchangeInteractionAuthorization();
        verified.observeAllowedWorldInteraction(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            "Exchange",
            "Grand Exchange booth",
            20,
            false,
            false);
        verified.onWidgetLoaded(WidgetID.GRAND_EXCHANGE_GROUP_ID, 21);
        verified.onWidgetLoaded(InterfaceID.GE_COLLECT, 22);
        verified.onWidgetClosed(WidgetID.GRAND_EXCHANGE_GROUP_ID);
        assertTrue(verified.isInterfaceActionAuthorized(
            MenuAction.CC_OP,
            "Collect to bank",
            collectWidget));
        assertTrue(GrandExchangeInteractionAuthorization.isCollectionAction(
            "Collect to bank",
            collectWidget));
        verified.onWidgetClosed(InterfaceID.GE_COLLECT);
        assertFalse(verified.isInterfaceActionAuthorized(
            MenuAction.CC_OP,
            "Collect to bank",
            collectWidget));
    }

    @Test
    public void immediateExchangeOpenedByUnlockedTalkIsAuthorizedBriefly()
    {
        GrandExchangeInteractionAuthorization authorization =
            new GrandExchangeInteractionAuthorization();
        authorization.observeAllowedWorldInteraction(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            "Grand Exchange Clerk",
            100,
            true,
            true);
        authorization.onWidgetLoaded(WidgetID.GRAND_EXCHANGE_GROUP_ID, 102);
        assertTrue(authorization.isInterfaceActionAuthorized(
            MenuAction.CC_OP,
            "Buy offer",
            EXCHANGE_WIDGET));
    }

}
