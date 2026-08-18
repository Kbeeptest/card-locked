package com.cardrestricted.runelite;

import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NpcServiceInterfaceAuthorizationTest
{
    @Test
    public void inheritedOrLockedNpcServiceFailsClosed()
    {
        int tanner = InterfaceID.TANNER << 16;
        NpcServiceInterfaceAuthorization inherited =
            new NpcServiceInterfaceAuthorization();
        inherited.onWidgetLoaded(InterfaceID.TANNER, 10);
        assertFalse(inherited.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Tan all", tanner));

        NpcServiceInterfaceAuthorization locked =
            new NpcServiceInterfaceAuthorization();
        locked.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Tan",
            "Tanner",
            20,
            true,
            false);
        locked.onWidgetLoaded(InterfaceID.TANNER, 21);
        assertFalse(locked.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Tan all", tanner));
    }

    @Test
    public void verifiedDirectAndDialogueSourcesAuthorizeServices()
    {
        int tanner = InterfaceID.TANNER << 16;
        NpcServiceInterfaceAuthorization direct =
            new NpcServiceInterfaceAuthorization();
        direct.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Tan",
            "Tanner",
            100,
            true,
            true);
        direct.onWidgetLoaded(InterfaceID.TANNER, 102);
        assertTrue(direct.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Tan all", tanner));

        int decant = InterfaceID.DECANT << 16;
        NpcServiceInterfaceAuthorization dialogue =
            new NpcServiceInterfaceAuthorization();
        dialogue.observeAllowedWorldInteraction(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            "Bob Barter",
            200,
            true,
            true);
        assertTrue(dialogue.observeAllowedDialogueChoice(
            "Decant my potions",
            "",
            "",
            205,
            WidgetID.DIALOG_OPTION_GROUP_ID << 16));
        dialogue.onWidgetLoaded(InterfaceID.DECANT, 207);
        assertTrue(dialogue.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Decant", decant));
    }

    @Test
    public void serviceFamiliesCoverCustomRewardAndTravelInterfaces()
    {
        assertTrue(NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.SLAYER_REWARDS) >= 0);
        assertTrue(NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.PEST_REWARDSHOP) >= 0);
        assertTrue(NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.GIANTS_FOUNDRY_REWARD_SHOP) >= 0);
        assertTrue(NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.CHARTERING_MENU_SIDE) >= 0);
        assertTrue(NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.MAKEOVER_MAGE)
            == NpcServiceInterfaceAuthorization.serviceFamily(
                InterfaceID.MAKEOVER));
    }

    @Test
    public void exitActionsAndUnrelatedInterfacesRemainUsable()
    {
        NpcServiceInterfaceAuthorization authorization =
            new NpcServiceInterfaceAuthorization();
        int tanner = InterfaceID.TANNER << 16;
        authorization.onWidgetLoaded(InterfaceID.TANNER, 1);
        assertTrue(authorization.isInterfaceActionAuthorized(
            MenuAction.CC_OP, "Close", tanner));
        assertTrue(authorization.isInterfaceActionAuthorized(
            MenuAction.CC_OP,
            "Make-X",
            WidgetID.INVENTORY_GROUP_ID << 16));
    }
    @Test
    public void unlockedTradeCanAuthorizeProtectedCustomRewardShop()
    {
        NpcServiceInterfaceAuthorization authorization =
            new NpcServiceInterfaceAuthorization();
        authorization.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Trade",
            "Reward merchant",
            10,
            true,
            true);
        authorization.onWidgetLoaded(InterfaceID.PEST_REWARDSHOP, 12);
        assertTrue(authorization.isInterfaceActionAuthorized(
            MenuAction.CC_OP,
            "Buy reward",
            InterfaceID.PEST_REWARDSHOP << 16));
    }

    @Test
    public void conflictingServiceWidgetMetadataFailsClosed()
    {
        NpcServiceInterfaceAuthorization authorization =
            new NpcServiceInterfaceAuthorization();
        assertFalse(authorization.isInterfaceActionAuthorized(
            MenuAction.CC_OP,
            "Claim",
            InterfaceID.SLAYER_REWARDS << 16,
            InterfaceID.PEST_REWARDSHOP << 16));
    }


    @Test
    public void sailingInterfacesFormOneProtectedServiceFamily()
    {
        int family = NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.SAILING_CUSTOMISATION);
        assertTrue(family >= 0);
        assertTrue(family == NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.SAILING_BOAT_SELECTION));
        assertTrue(family == NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.SAILING_CREW));
        assertTrue(NpcServiceInterfaceAuthorization.canOpenService(
            MenuAction.NPC_SECOND_OPTION,
            "Customise-boat",
            "Shipwright Sornik"));
    }

    @Test
    public void immediateServiceOpenedByUnlockedTalkIsAuthorizedBriefly()
    {
        NpcServiceInterfaceAuthorization authorization =
            new NpcServiceInterfaceAuthorization();
        authorization.observeAllowedWorldInteraction(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            "Shipwright Sornik",
            100,
            true,
            true);
        authorization.onWidgetLoaded(InterfaceID.SAILING_CUSTOMISATION, 102);
        assertTrue(authorization.isInterfaceActionAuthorized(
            MenuAction.CC_OP,
            "Select",
            InterfaceID.SAILING_CUSTOMISATION << 16));
    }

    @Test
    public void closingOneChildDoesNotDeauthorizeRemainingServiceFamily()
    {
        NpcServiceInterfaceAuthorization authorization =
            new NpcServiceInterfaceAuthorization();
        authorization.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Slayer rewards",
            "Slayer master",
            10,
            true,
            true);
        authorization.onWidgetLoaded(InterfaceID.SLAYER_REWARDS, 11);
        authorization.onWidgetLoaded(InterfaceID.SLAYER_REWARDS_TASK_LIST, 12);
        authorization.onWidgetClosed(InterfaceID.SLAYER_REWARDS_TASK_LIST);
        assertTrue(authorization.isInterfaceActionAuthorized(
            MenuAction.CC_OP,
            "Buy",
            InterfaceID.SLAYER_REWARDS << 16));
        authorization.onWidgetClosed(InterfaceID.SLAYER_REWARDS);
        assertFalse(authorization.isInterfaceActionAuthorized(
            MenuAction.CC_OP,
            "Buy",
            InterfaceID.SLAYER_REWARDS << 16));
    }


    @Test
    public void additionalCustomStoresAreProtectedServiceFamilies()
    {
        assertTrue(NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.CASTLEWARS_TRADE)
            == NpcServiceInterfaceAuthorization.serviceFamily(
                InterfaceID.CASTLEWARS_SHOPSIDE));
        assertTrue(NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.PVP_STORE)
            == NpcServiceInterfaceAuthorization.serviceFamily(
                InterfaceID.PVP_STORE_SIDE));
        assertTrue(NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.LEAGUE_REWARDS)
            == NpcServiceInterfaceAuthorization.serviceFamily(
                InterfaceID.LEAGUE_SKILLCAPES_SHOP));
        assertTrue(NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.EVENT_REWARDS) >= 0);
        assertTrue(NpcServiceInterfaceAuthorization.serviceFamily(
            InterfaceID.TOB_MIDWAY_STORES) >= 0);
        assertTrue(NpcServiceInterfaceAuthorization.canOpenService(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            "Open",
            "Raid supply chest"));
    }

}
