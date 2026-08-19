package com.cardrestricted.runelite;

import com.cardrestricted.progression.ProgressionMilestonePolicy;
import java.util.Collections;
import java.util.Set;
import net.runelite.api.ItemID;
import net.runelite.api.gameval.InterfaceID;

/** Focused interaction rules for the 1,000-card coin milestone. */
public final class CoinMilestoneRules
{
    private static final Set<Integer> COIN_ITEM_IDS = Set.of(
        995,
        6964,
        8890,
        14440,
        18028,
        ItemID.PLATINUM_TOKEN);

    private CoinMilestoneRules()
    {
    }

    public static boolean coinsUnlocked(int uniqueOwnedCards)
    {
        return uniqueOwnedCards >= ProgressionMilestonePolicy.COINS;
    }

    public static boolean containsCoins(Set<Integer> itemIds)
    {
        if (itemIds == null || itemIds.isEmpty())
        {
            return false;
        }
        for (Integer itemId : itemIds)
        {
            if (itemId != null && COIN_ITEM_IDS.contains(itemId))
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isCoinItem(int itemId)
    {
        return COIN_ITEM_IDS.contains(itemId);
    }

    public static boolean shouldBlockCoinInteraction(
        int uniqueOwnedCards,
        Set<Integer> itemIds,
        String option,
        boolean allowBanking)
    {
        return !coinsUnlocked(uniqueOwnedCards)
            && containsCoins(itemIds)
            && !SimpleRestrictionService.isSafeItemOption(
                option,
                allowBanking);
    }

    /**
     * Preserve the resolved coin identity so the ordinary item-card check can
     * verify the progression-granted Coins card. Removing it here makes a
     * direct coin click look unresolved to the later fail-closed guard.
     */
    public static Set<Integer> cardRestrictedItemIds(
        int uniqueOwnedCards,
        Set<Integer> itemIds)
    {
        if (itemIds == null || itemIds.isEmpty())
        {
            return Collections.emptySet();
        }
        return itemIds;
    }

    public static Set<String> cardRestrictedItemNames(
        int uniqueOwnedCards,
        Set<Integer> itemIds,
        Set<String> itemNames)
    {
        if (itemNames == null || itemNames.isEmpty())
        {
            return Collections.emptySet();
        }
        return itemNames;
    }

    public static boolean isShopBuyAction(
        int packedWidgetId,
        String option)
    {
        if (packedWidgetId < 0 || option == null)
        {
            return false;
        }
        int groupId = packedWidgetId >>> 16;
        return groupId == InterfaceID.SHOPMAIN
            && option.trim().toLowerCase(java.util.Locale.ROOT)
                .startsWith("buy");
    }

    public static boolean shouldBlockShopBuy(
        int uniqueOwnedCards,
        boolean inventoryContainsCoins,
        String option,
        int... packedWidgetIds)
    {
        if (coinsUnlocked(uniqueOwnedCards)
            || !inventoryContainsCoins
            || packedWidgetIds == null)
        {
            return false;
        }
        for (int packedWidgetId : packedWidgetIds)
        {
            if (isShopBuyAction(packedWidgetId, option))
            {
                return true;
            }
        }
        return false;
    }
}
