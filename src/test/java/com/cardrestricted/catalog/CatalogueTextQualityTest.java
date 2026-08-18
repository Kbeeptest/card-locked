package com.cardrestricted.catalog;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CatalogueTextQualityTest
{
    @Test
    public void officialStatusRequiresImportedProvenance()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        CardDefinition verified = catalogue.findCard(
            "item.anti_dragon_shield.1540").orElseThrow(AssertionError::new);
        CardDefinition pending = catalogue.findCard(
            "npc.solus_dellagar.4962").orElseThrow(AssertionError::new);

        assertTrue(CatalogueTextQuality.isVerifiedExamine(verified));
        assertTrue(CatalogueTextQuality.hasVerifiedExamineProvenance(
            verified.getCardId()));
        assertFalse(CatalogueTextQuality.isVerifiedExamine(pending));
        assertFalse(CatalogueTextQuality.hasVerifiedExamineProvenance(
            pending.getCardId()));
    }

    @Test
    public void exactlyNineReviewedDescriptionsRemainPending()
    {
        Set<String> pending = MembersCatalogue.create().getCards().stream()
            .filter(card -> !CatalogueTextQuality.isVerifiedExamine(card))
            .map(CardDefinition::getCardId)
            .collect(Collectors.toSet());
        assertEquals(9, pending.size());
        assertTrue(pending.contains("item.burnt_meat.2146"));
        assertTrue(pending.contains("npc.evil_creature.1241"));
        assertTrue(pending.contains("npc.koschei_the_deathless.3897"));
        assertTrue(pending.contains("npc.loar_shade.1277"));
        assertTrue(pending.contains("npc.naiatli.13838"));
        assertTrue(pending.contains("npc.solus_dellagar.4962"));
    }
}
