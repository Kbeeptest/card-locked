package com.cardrestricted.runelite;

import net.runelite.api.MenuAction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class InteractionIntegrityRulesTest
{
    @Test
    public void eventMetadataBackfillsMissingOrUnknownEntries()
    {
        assertEquals("Pickpocket", InteractionIntegrityRules.effectiveText(
            "", "Pickpocket"));
        assertEquals("Attack", InteractionIntegrityRules.effectiveOption(
            "Trade", "Attack"));
        assertEquals(MenuAction.NPC_SECOND_OPTION,
            InteractionIntegrityRules.effectiveAction(
                MenuAction.UNKNOWN,
                MenuAction.NPC_SECOND_OPTION));
    }

    @Test
    public void attachedNpcCannotBypassThroughRuneliteMenuTypes()
    {
        assertTrue(InteractionIntegrityRules.isNpcInteraction(
            MenuAction.RUNELITE,
            true));
        assertTrue(InteractionIntegrityRules.isNpcInteraction(
            MenuAction.NPC_FIRST_OPTION,
            false));
        assertFalse(InteractionIntegrityRules.isNpcInteraction(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            false));
    }

    @Test
    public void pendingProfileStateFailsClosedForGameplayButNotRecovery()
    {
        assertTrue(InteractionIntegrityRules.shouldBlockWhileStatePending(
            MenuAction.NPC_SECOND_OPTION,
            "Pickpocket",
            true,
            false));
        assertTrue(InteractionIntegrityRules.shouldBlockWhileStatePending(
            MenuAction.WIDGET_TARGET,
            "Cast",
            false,
            false));
        assertFalse(InteractionIntegrityRules.shouldBlockWhileStatePending(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            true,
            false));
        assertFalse(InteractionIntegrityRules.shouldBlockWhileStatePending(
            MenuAction.WIDGET_CONTINUE,
            "Continue",
            false,
            false));
        assertFalse(InteractionIntegrityRules.shouldBlockWhileStatePending(
            MenuAction.WALK,
            "Walk here",
            false,
            false));
        assertTrue(InteractionIntegrityRules.shouldBlockWhileStatePending(
            MenuAction.CC_OP,
            "Withdraw-1",
            false,
            false,
            false));
        assertFalse(InteractionIntegrityRules.shouldBlockWhileStatePending(
            MenuAction.CC_OP,
            "Deposit-All",
            false,
            false,
            true));
    }

    @Test
    public void pendingProfileStatePreservesRecoveryNavigation()
    {
        for (String option : new String[] {
            "Inventory", "Worn Equipment", "Combat Options", "Quest List",
            "Logout", "Report"
        })
        {
            assertFalse(option,
                InteractionIntegrityRules.shouldBlockWhileStatePending(
                    MenuAction.CC_OP,
                    option,
                    false,
                    false));
        }
        assertTrue(InteractionIntegrityRules.shouldBlockWhileStatePending(
            MenuAction.CC_OP,
            "Use Special Attack",
            false,
            false));
        assertTrue(InteractionIntegrityRules.shouldBlockWhileStatePending(
            MenuAction.CC_OP,
            "Future gameplay operation",
            false,
            false));
        assertFalse(InteractionIntegrityRules.shouldBlockWhileStatePending(
            MenuAction.CC_OP,
            "Wiki",
            false,
            false));
    }

    @Test
    public void clientUiActionsDoNotBecomeEquipmentFalsePositives()
    {
        assertFalse(InteractionIntegrityRules.isFunctionalAction(
            MenuAction.WIDGET_CONTINUE,
            "Continue",
            false));
        assertFalse(InteractionIntegrityRules.isFunctionalAction(
            MenuAction.RUNELITE_OVERLAY_CONFIG,
            "Configure",
            false));
        assertFalse(InteractionIntegrityRules.isFunctionalAction(
            MenuAction.WIDGET_CLOSE,
            "Close",
            false));
        assertTrue(InteractionIntegrityRules.isFunctionalAction(
            MenuAction.CC_OP,
            "Use Special Attack",
            false));
        assertTrue(InteractionIntegrityRules.isFunctionalAction(
            MenuAction.CC_OP,
            "Make-X",
            false));
        assertTrue(InteractionIntegrityRules.isFunctionalAction(
            MenuAction.ITEM_FIRST_OPTION,
            "Release",
            false));
    }

    @Test
    public void rewrittenNpcOptionsWithoutIdentityFailClosed()
    {
        assertTrue(InteractionIntegrityRules
            .shouldBlockUnresolvedNpcFunctionalAction(
                MenuAction.UNKNOWN,
                "Pickpocket",
                false,
                false,
                false));
        assertTrue(InteractionIntegrityRules
            .shouldBlockUnresolvedNpcFunctionalAction(
                MenuAction.RUNELITE,
                "Trade",
                false,
                false,
                false));
        assertFalse(InteractionIntegrityRules
            .shouldBlockUnresolvedNpcFunctionalAction(
                MenuAction.CC_OP,
                "Buy",
                false,
                false,
                true));
        assertFalse(InteractionIntegrityRules
            .shouldBlockUnresolvedNpcFunctionalAction(
                MenuAction.UNKNOWN,
                "Talk-to",
                false,
                false,
                false));
    }

    @Test
    public void productionAndAutocastOptionsAreRecognised()
    {
        assertTrue(InteractionIntegrityRules.isProductionOption("Smith All"));
        assertTrue(InteractionIntegrityRules.isProductionOption("Make-X"));
        assertTrue(InteractionIntegrityRules.isAutocastSelection("Autocast"));
        assertTrue(InteractionIntegrityRules.isAutocastSelection(
            "Defensive Autocast"));
        assertTrue(InteractionIntegrityRules.isAttackOption(
            "<col=ff0000>Attack</col>"));
    }
    @Test
    public void activeCombatReclickCanRecoverKnownNpcFromGenericMenuAction()
    {
        assertTrue(InteractionIntegrityRules.mayResolveNpcActorFromLiveIndex(
            MenuAction.RUNELITE,
            false,
            true));
        assertTrue(InteractionIntegrityRules.mayResolveNpcActorFromLiveIndex(
            MenuAction.UNKNOWN,
            false,
            true));
        assertFalse(InteractionIntegrityRules.mayResolveNpcActorFromLiveIndex(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            false,
            true));
        assertFalse(InteractionIntegrityRules.mayResolveNpcActorFromLiveIndex(
            MenuAction.ITEM_FIRST_OPTION,
            false,
            true));
    }

}
