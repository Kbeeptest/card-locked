package com.cardrestricted.runelite;

import java.util.Set;
import net.runelite.api.widgets.WidgetID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CoinMilestoneRulesTest
{
    @Test
    public void recognisesAllKnownCoinStackVariants()
    {
        assertTrue(CoinMilestoneRules.containsCoins(Set.of(995)));
        assertTrue(CoinMilestoneRules.containsCoins(Set.of(6964)));
        assertFalse(CoinMilestoneRules.containsCoins(Set.of(9950)));
    }

    @Test
    public void onlyRecognisesBuyActionsInsideShopInterface()
    {
        int shopWidget = WidgetID.SHOP_GROUP_ID << 16 | 16;
        int inventoryWidget = WidgetID.INVENTORY_GROUP_ID << 16 | 0;
        assertTrue(CoinMilestoneRules.isShopBuyAction(shopWidget, "Buy 10"));
        assertFalse(CoinMilestoneRules.isShopBuyAction(shopWidget, "Value"));
        assertFalse(CoinMilestoneRules.isShopBuyAction(inventoryWidget, "Buy 10"));
    }

    @Test
    public void blocksFunctionalCoinUseBeforeMilestoneButAllowsStorage()
    {
        assertTrue(CoinMilestoneRules.shouldBlockCoinInteraction(
            999, Set.of(995), "Use", true));
        assertFalse(CoinMilestoneRules.shouldBlockCoinInteraction(
            999, Set.of(995), "Deposit-All", true));
        assertFalse(CoinMilestoneRules.shouldBlockCoinInteraction(
            1_000, Set.of(995), "Use", false));
    }

    @Test
    public void retainsCoinsForOrdinaryCardVerificationAfterUnlock()
    {
        assertEquals(Set.of(995, 4151), CoinMilestoneRules.cardRestrictedItemIds(
            1_000, Set.of(995, 4151)));
        assertEquals(Set.of(995, 4151), CoinMilestoneRules.cardRestrictedItemIds(
            999, Set.of(995, 4151)));
    }

    @Test
    public void retainsCoinNameForOrdinaryCardVerificationAfterUnlock()
    {
        assertEquals(Set.of("coins", "rune platebody"),
            CoinMilestoneRules.cardRestrictedItemNames(
                1_000,
                Set.of(995, 1127),
                Set.of("coins", "rune platebody")));
        assertEquals(Set.of("coins"),
            CoinMilestoneRules.cardRestrictedItemNames(
                999,
                Set.of(995),
                Set.of("coins")));
    }

    @Test
    public void blocksCoinFundedShopPurchasesBeforeMilestone()
    {
        int shopWidget = WidgetID.SHOP_GROUP_ID << 16 | 16;
        assertTrue(CoinMilestoneRules.shouldBlockShopBuy(
            999, true, "Buy 5", shopWidget));
        assertFalse(CoinMilestoneRules.shouldBlockShopBuy(
            999, false, "Buy 5", shopWidget));
        assertFalse(CoinMilestoneRules.shouldBlockShopBuy(
            1_000, true, "Buy 5", shopWidget));
    }
}
