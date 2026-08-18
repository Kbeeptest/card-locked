package com.cardrestricted.runelite;

import net.runelite.api.MenuAction;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PassiveInventoryUsageRulesTest
{
    @Test
    public void movementAndEnvironmentalTransitionsCheckPassiveItems()
    {
        assertTrue(PassiveInventoryUsageRules.shouldCheckInventory(
            MenuAction.WALK, "Walk here"));
        assertTrue(PassiveInventoryUsageRules.shouldCheckInventory(
            MenuAction.GAME_OBJECT_FIRST_OPTION, "Enter cave"));
        assertFalse(PassiveInventoryUsageRules.shouldCheckInventory(
            MenuAction.CC_OP, "Close"));
    }

    @Test
    public void litSourcesAndWaterskinsAreDetectedConservatively()
    {
        assertTrue(PassiveInventoryUsageRules.isPotentialPassiveItem(
            "Waterskin(4)"));
        assertTrue(PassiveInventoryUsageRules.isPotentialPassiveItem(
            "Bullseye lantern",
            new String[]{"Drop", "Extinguish"}));
        assertTrue(PassiveInventoryUsageRules.isPotentialPassiveItem(
            "Bruma torch"));
        assertFalse(PassiveInventoryUsageRules.isPotentialPassiveItem(
            "Bullseye lantern",
            new String[]{"Drop"}));
        assertFalse(PassiveInventoryUsageRules.isPotentialPassiveItem(
            "Rune sword"));
    }

    @Test
    public void ordinaryItemsDoNotCreatePassiveEnvironmentalGate()
    {
        assertFalse(PassiveInventoryUsageRules.isPotentialPassiveItem(
            "Lobster",
            new String[]{"Eat", "Use", "Drop"}));
        assertFalse(PassiveInventoryUsageRules.isPotentialPassiveItem(
            "Unlit torch",
            new String[]{"Light", "Drop"}));
        assertFalse(PassiveInventoryUsageRules.shouldCheckInventory(
            MenuAction.CC_OP, "Close"));
    }
}
