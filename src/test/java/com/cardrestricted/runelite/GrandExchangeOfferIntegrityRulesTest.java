package com.cardrestricted.runelite;

import java.util.Set;
import net.runelite.api.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GrandExchangeOfferIntegrityRulesTest
{
    @Test
    public void onlyTheCurrentOfferItemIsUsed()
    {
        assertTrue(GrandExchangeOfferIntegrityRules.selectedItemIds(
            ItemID.LAW_RUNE).equals(Set.of(ItemID.LAW_RUNE)));
        assertTrue(GrandExchangeOfferIntegrityRules.selectedItemIds(-1).isEmpty());
        assertTrue(GrandExchangeOfferIntegrityRules.selectedItemIds(0).isEmpty());
    }

    @Test
    public void geSubmissionRequiresCoinMilestoneAndVerifiedItem()
    {
        assertFalse(GrandExchangeOfferIntegrityRules.coinsPermitSubmission(999));
        assertTrue(GrandExchangeOfferIntegrityRules.coinsPermitSubmission(1000));
        assertFalse(GrandExchangeOfferIntegrityRules.hasVerifiedSelectedItem(
            Set.of()));
        assertTrue(GrandExchangeOfferIntegrityRules.hasVerifiedSelectedItem(
            Set.of(ItemID.LAW_RUNE)));
    }
    @Test
    public void invalidOrNullSelectedItemMetadataFailsClosed()
    {
        assertFalse(GrandExchangeOfferIntegrityRules.hasVerifiedSelectedItem(
            null));
        assertFalse(GrandExchangeOfferIntegrityRules.hasVerifiedSelectedItem(
            Set.of(0)));
        assertFalse(GrandExchangeOfferIntegrityRules.hasVerifiedSelectedItem(
            Set.of(-1)));
    }

}
