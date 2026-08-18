package com.cardrestricted.runelite;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.EntityFamily;
import com.cardrestricted.catalog.MembersCatalogue;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SimpleRestrictionServiceContextTest
{
    @Test
    public void removeOnlyFailsOpenForEquipmentOrPermittedStorage()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        CardDefinition itemCard = catalogue.getCards().stream()
            .filter(card -> card.getCardType() == CardType.ITEM)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No item card available."));
        EntityFamily family = catalogue.getFamilies().stream()
            .filter(candidate -> candidate.getFamilyId().equals(
                itemCard.getEntityFamilyId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing item family."));
        SimpleRestrictionService service = new SimpleRestrictionService(
            new InteractionFamilyIndex(catalogue));
        Set<Integer> itemIds = Set.of(family.getCanonicalEntityId());

        assertTrue(service.evaluateItems(
            itemIds,
            Collections.emptySet(),
            "Remove",
            Collections.emptySet(),
            false,
            false).isBlocked());
        assertFalse(service.evaluateItems(
            itemIds,
            Collections.emptySet(),
            "Remove",
            Collections.emptySet(),
            false,
            true).isBlocked());
        assertFalse(service.evaluateItems(
            itemIds,
            Collections.emptySet(),
            "Remove",
            Collections.emptySet(),
            true,
            false).isBlocked());
    }
}
