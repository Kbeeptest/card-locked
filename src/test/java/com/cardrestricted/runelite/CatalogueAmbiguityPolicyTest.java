package com.cardrestricted.runelite;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.MembersCatalogue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Exhaustive proof that name fallback is unique-only and ambiguous names fail closed. */
public final class CatalogueAmbiguityPolicyTest
{
    @Test
    public void everyCatalogueNameMatchesRuntimeFallbackPolicy()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        InteractionFamilyIndex index = new InteractionFamilyIndex(catalogue);
        CatalogueAmbiguityAudit.Result audit =
            CatalogueAmbiguityAudit.analyse(catalogue);

        assertFalse("Expected real ambiguous catalogue names.",
            audit.ambiguous.isEmpty());
        for (CatalogueAmbiguityAudit.Entry entry : audit.unique)
        {
            String expected = entry.familyIds.iterator().next();
            if (entry.type == CardType.ITEM)
            {
                assertFalse(index.isAmbiguousItemName(entry.normalisedName));
                assertEquals(expected,
                    index.familyIdForUniqueItemName(entry.normalisedName));
            }
            else
            {
                assertFalse(index.isAmbiguousNpcName(entry.normalisedName));
                assertEquals(expected,
                    index.familyIdForUniqueNpcName(entry.normalisedName));
            }
        }
        for (CatalogueAmbiguityAudit.Entry entry : audit.ambiguous)
        {
            assertTrue(entry.familyIds.size() > 1);
            if (entry.type == CardType.ITEM)
            {
                assertTrue(index.isAmbiguousItemName(entry.normalisedName));
                assertNull(index.familyIdForUniqueItemName(
                    entry.normalisedName));
            }
            else
            {
                assertTrue(index.isAmbiguousNpcName(entry.normalisedName));
                assertNull(index.familyIdForUniqueNpcName(
                    entry.normalisedName));
            }
        }

        assertEquals(index.ambiguousItemNameCount(),
            audit.ambiguous.stream()
                .filter(entry -> entry.type == CardType.ITEM).count());
        assertEquals(index.ambiguousNpcNameCount(),
            audit.ambiguous.stream()
                .filter(entry -> entry.type == CardType.NPC).count());
    }
}
