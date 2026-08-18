package com.cardrestricted.runelite;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Conservative ingredient quarantine for production interfaces whose clicked
 * widget does not reliably expose every consumed inventory item.
 *
 * <p>A production click may identify only the output or omit item metadata
 * entirely. While the production action is being confirmed, every tracked
 * inventory item is therefore treated as a possible input. Untracked items and
 * coins after their progression milestone are removed by the normal catalogue
 * gate before a decision is made.</p>
 */
public final class ProductionInventoryIntegrityRules
{
    private ProductionInventoryIntegrityRules()
    {
    }

    public static Set<Integer> restrictedCandidates(
        String option,
        int uniqueCardCount,
        Set<Integer> inventoryItemIds)
    {
        if (!InteractionIntegrityRules.isProductionOption(option)
            || inventoryItemIds == null
            || inventoryItemIds.isEmpty())
        {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(
            CoinMilestoneRules.cardRestrictedItemIds(
                uniqueCardCount,
                inventoryItemIds)));
    }
}
