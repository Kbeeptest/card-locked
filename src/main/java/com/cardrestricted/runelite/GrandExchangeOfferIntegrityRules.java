package com.cardrestricted.runelite;

import java.util.Collections;
import java.util.Set;

/** Fail-closed rules for Grand Exchange submissions with metadata-free buttons. */
public final class GrandExchangeOfferIntegrityRules
{
    private GrandExchangeOfferIntegrityRules()
    {
    }

    public static Set<Integer> selectedItemIds(int currentGeItemId)
    {
        return currentGeItemId > 0
            ? Collections.singleton(currentGeItemId)
            : Collections.emptySet();
    }

    /**
     * The coin milestone governs both spending and normal coin acquisition, so
     * GE buy and sell offers remain unavailable before it is reached.
     */
    public static boolean coinsPermitSubmission(int uniqueOwnedCards)
    {
        return CoinMilestoneRules.coinsUnlocked(uniqueOwnedCards);
    }

    public static boolean hasVerifiedSelectedItem(Set<Integer> itemIds)
    {
        return itemIds != null
            && !itemIds.isEmpty()
            && itemIds.stream().allMatch(itemId -> itemId != null && itemId > 0);
    }
}
