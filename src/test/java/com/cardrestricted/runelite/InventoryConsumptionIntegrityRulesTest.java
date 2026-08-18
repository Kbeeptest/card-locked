package com.cardrestricted.runelite;

import java.util.Set;
import net.runelite.api.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class InventoryConsumptionIntegrityRulesTest
{
    @Test
    public void serviceAndDialogueConsumptionVerbsAreQuarantined()
    {
        assertTrue(InventoryConsumptionIntegrityRules.canConsumeImplicitInventory(
            "Yes, please",
            "Repair my armour",
            "That will cost materials"));
        assertTrue(InventoryConsumptionIntegrityRules.canConsumeImplicitInventory(
            "Redeem",
            "Reward",
            "Hand in your tokens"));
        assertFalse(InventoryConsumptionIntegrityRules.canConsumeImplicitInventory(
            "Talk-to",
            "Guide",
            "How are you?"));
    }

    @Test
    public void coinIdentityRemainsAvailableForCardVerificationAfterMilestone()
    {
        Set<Integer> before = InventoryConsumptionIntegrityRules.restrictedCandidates(
            "Pay",
            "Fare",
            "Pay with coins",
            999,
            Set.of(ItemID.COINS_995, ItemID.LAW_RUNE));
        assertTrue(before.contains(ItemID.COINS_995));
        assertTrue(before.contains(ItemID.LAW_RUNE));

        Set<Integer> after = InventoryConsumptionIntegrityRules.restrictedCandidates(
            "Pay",
            "Fare",
            "Pay with coins",
            1000,
            Set.of(ItemID.COINS_995, ItemID.LAW_RUNE));
        // At the milestone the progression track grants the Coins
        // card. The resolved coin identity must remain in the candidate set so
        // the ordinary card entitlement check can verify that grant.
        assertTrue(after.contains(ItemID.COINS_995));
        assertTrue(after.contains(ItemID.LAW_RUNE));
    }
}
