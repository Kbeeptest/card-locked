package com.cardrestricted.runelite;

import java.util.Set;
import net.runelite.api.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProductionInventoryIntegrityRulesTest
{
    @Test
    public void productionActionsConservativelyIncludeTrackedInventory()
    {
        Set<Integer> result = ProductionInventoryIntegrityRules.restrictedCandidates(
            "Make-X",
            999,
            Set.of(ItemID.COINS_995, ItemID.LOGS));
        assertTrue(result.contains(ItemID.COINS_995));
        assertTrue(result.contains(ItemID.LOGS));
    }

    @Test
    public void ordinaryActionsDoNotQuarantineTheInventory()
    {
        assertTrue(ProductionInventoryIntegrityRules.restrictedCandidates(
            "Examine",
            0,
            Set.of(ItemID.LOGS)).isEmpty());
        assertFalse(ProductionInventoryIntegrityRules.restrictedCandidates(
            "Cook All",
            0,
            Set.of(ItemID.LOGS)).isEmpty());
    }
}
