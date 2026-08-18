package com.cardrestricted.runelite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ImplicitItemUsageRulesTest
{
    @Test
    public void gatheringToolsAreDetectedWithoutSelectedItemMetadata()
    {
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Chop down", "Oak", "Rune axe"));
        assertFalse(ImplicitItemUsageRules.isPotentialParticipant(
            "Chop down", "Oak", "Rune pickaxe"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Mine", "Runite rocks", "Dragon pickaxe"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Harpoon", "Fishing spot", "Dragon harpoon"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Bait", "Fishing spot", "Fishing bait"));
    }

    @Test
    public void productionInputsAndToolsAreDetected()
    {
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Smith", "Anvil", "Rune bar"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Smith", "Anvil", "Hammer"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Smelt", "Furnace", "Runite ore"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Cook", "Range", "Raw shark"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Craft-rune", "Air altar", "Pure essence"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Light", "Logs", "Tinderbox"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Make-X", "Yew longbow", "Yew logs"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Mix", "Potion", "Pestle and mortar"));
    }

    @Test
    public void unrelatedInventoryItemsAreNotGatheringParticipants()
    {
        assertFalse(ImplicitItemUsageRules.isPotentialParticipant(
            "Mine", "Iron rocks", "Lobster pot"));
        assertFalse(ImplicitItemUsageRules.isPotentialParticipant(
            "Chop down", "Tree", "Hammer"));
        assertFalse(ImplicitItemUsageRules.actionCanUseImplicitItems(
            "Talk-to", "Banker"));
    }
    @Test
    public void farmingUtilityAndContainerToolsAreDetected()
    {
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Rake", "Herb patch", "Rake"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Dig-up", "Dead herb", "Spade"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Pick-lock", "Door", "Lockpick"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Smash", "Gargoyle", "Rock hammer"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Milk", "Dairy cow", "Bucket"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Shear", "Sheep", "Shears"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Fill", "Fountain", "Vial"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Build", "Chair space", "Saw"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Build", "Chair space", "Steel nails"));
    }

    @Test
    public void grappleShortcutsRequireTheirImplicitEquipment()
    {
        assertTrue(ImplicitItemUsageRules.actionCanUseImplicitItems(
            "Grapple", "Grapple shortcut"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Grapple", "Wall", "Mith grapple"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Cross", "Grapple shortcut", "Rune crossbow"));
        assertTrue(ImplicitItemUsageRules.isPotentialParticipant(
            "Cross", "Grapple shortcut", "Rope"));
        assertFalse(ImplicitItemUsageRules.isPotentialParticipant(
            "Cross", "Grapple shortcut", "Dragon axe"));
    }

}
