package com.cardrestricted.runelite;

import net.runelite.api.MenuAction;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class EquippedItemActionRulesTest
{
    @Test
    public void wornLockedGearGatesCombatAndProductionButNotWalking()
    {
        assertFalse(EquippedItemActionRules.shouldGate(
            MenuAction.WALK, "Walk here", false, false));
        assertTrue(EquippedItemActionRules.shouldGate(
            MenuAction.NPC_SECOND_OPTION, "Attack", false, false));
        assertTrue(EquippedItemActionRules.shouldGate(
            MenuAction.CC_OP, "Make-X", false, false));
    }

    @Test
    public void textOnlyBankingDoesNotCreateAnExemption()
    {
        assertTrue(EquippedItemActionRules.shouldGate(
            MenuAction.CC_OP, "Withdraw-1", false, false));
        assertFalse(EquippedItemActionRules.shouldGate(
            MenuAction.CC_OP, "Withdraw-1", false, true));
        assertFalse(EquippedItemActionRules.shouldGate(
            MenuAction.ITEM_FIRST_OPTION, "Remove", false, false));
    }

    @Test
    public void wornLockedGearCannotTrapClientNavigationOrLogout()
    {
        for (String option : new String[] {
            "Inventory", "Worn Equipment", "Combat Options", "Quest List",
            "Logout", "Report"
        })
        {
            assertFalse(option, EquippedItemActionRules.shouldGate(
                MenuAction.CC_OP, option, false, false));
        }
        assertFalse(EquippedItemActionRules.shouldGate(
            MenuAction.WIDGET_CLOSE, "Close", false, false));
        assertFalse(EquippedItemActionRules.shouldGate(
            MenuAction.ITEM_FIRST_OPTION, "Unequip", false, false));

        assertTrue(EquippedItemActionRules.shouldGate(
            MenuAction.CC_OP, "Use Special Attack", false, false));
        assertTrue(EquippedItemActionRules.shouldGate(
            MenuAction.WIDGET_TARGET, "Cast", true, false));
        assertTrue(EquippedItemActionRules.shouldGate(
            MenuAction.CC_OP, "Future gameplay operation", false, false));
        assertFalse(EquippedItemActionRules.shouldGate(
            MenuAction.CC_OP, "Wiki", false, false));
    }
}
