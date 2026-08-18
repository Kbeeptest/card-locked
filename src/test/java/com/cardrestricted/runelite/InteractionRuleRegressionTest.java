package com.cardrestricted.runelite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class InteractionRuleRegressionTest
{
    @Test
    public void bankTabPlaceholderActionsAlwaysFailOpen()
    {
        assertTrue(BankTabInteractionRules.isBankTabNavigation("View tab 5"));
        assertTrue(BankTabInteractionRules.isBankTabNavigation(
            "Use", "<col=ff9040>View tab 12</col>"));
        assertTrue(BankTabInteractionRules.isBankTabNavigation("View all items"));
        assertTrue(BankTabInteractionRules.isBankTabNavigation("Collapse tab"));
        assertFalse(BankTabInteractionRules.isBankTabNavigation("Withdraw-1"));
        assertFalse(BankTabInteractionRules.isBankTabNavigation("Use"));
    }

    @Test
    public void furnaceClassifierRemainsNarrow()
    {
        assertTrue(FurnaceInteractionRules.isFurnaceInteraction(
            "Smelt", "Furnace"));
        assertTrue(FurnaceInteractionRules.isPotentialFurnaceIngredient(
            "Adamantite ore"));
        assertTrue(FurnaceInteractionRules.isPotentialFurnaceIngredient(
            "Amulet mould"));
        assertFalse(FurnaceInteractionRules.isFurnaceInteraction(
            "Cook", "Range"));
        assertFalse(FurnaceInteractionRules.isPotentialFurnaceIngredient(
            "Rune pickaxe"));
    }
}
