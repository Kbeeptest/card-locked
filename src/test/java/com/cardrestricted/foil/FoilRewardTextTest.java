package com.cardrestricted.foil;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class FoilRewardTextTest
{
    @Test
    public void potentialSummaryListsUnlockNamesWithoutClassifierMetadata()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        FoilRewardRegistry registry = FoilRewardRegistry.load(
            getClass().getClassLoader(),
            catalogue);

        String summary = FoilRewardText.potentialSummary(
            registry,
            catalogue,
            "item.grimy_harralander.205");

        assertEquals("Harralander, Harralander seed", summary);
        assertFalse(summary.contains("Farming seed"));
        assertFalse(summary.contains("Material conversion"));
        assertFalse(summary.contains("card:"));
        assertFalse(summary.contains("cards:"));
    }

    @Test
    public void compatibilityOverloadNeverTruncatesUnlockNames()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        FoilRewardRegistry registry = FoilRewardRegistry.load(
            getClass().getClassLoader(),
            catalogue);

        assertEquals(
            FoilRewardText.potentialSummary(
                registry,
                catalogue,
                "item.grimy_harralander.205"),
            FoilRewardText.potentialSummary(
                registry,
                catalogue,
                "item.grimy_harralander.205",
                1));
    }
}
