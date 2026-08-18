package com.cardrestricted.runelite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ShopCurrencyRulesTest
{
    @Test
    public void commonAlternativeShopCurrenciesAreRecognised()
    {
        assertTrue(ShopCurrencyRules.isPotentialCurrency("Tokkul"));
        assertTrue(ShopCurrencyRules.isPotentialCurrency("Ecto-tokens"));
        assertTrue(ShopCurrencyRules.isPotentialCurrency("Anima-infused bark"));
        assertTrue(ShopCurrencyRules.isPotentialCurrency("Abyssal pearls"));
        assertTrue(ShopCurrencyRules.isPotentialCurrency("Archery ticket"));
        assertTrue(ShopCurrencyRules.isPotentialCurrency("Sawmill voucher"));
        assertTrue(ShopCurrencyRules.isPotentialCurrency("Hallowed token"));
        assertTrue(ShopCurrencyRules.isPotentialCurrency("Ship ticket"));
        assertFalse(ShopCurrencyRules.isPotentialCurrency("Law rune"));
    }

    @Test
    public void implicitCoinSpendVerbsAreConservative()
    {
        assertTrue(ShopCurrencyRules.isImplicitCoinSpendOption(
            "Buy 10", "Shop item"));
        assertTrue(ShopCurrencyRules.isImplicitCoinSpendOption(
            "Pay-fare", "Boat"));
        assertTrue(ShopCurrencyRules.isImplicitCoinSpendOption(
            "Yes", "Pay 30 coins"));
        assertFalse(ShopCurrencyRules.isImplicitCoinSpendOption(
            "Talk-to", "Banker"));
    }
}
