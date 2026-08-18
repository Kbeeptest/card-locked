package com.cardrestricted.quest;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.foil.FoilEntitlementResolver;
import com.cardrestricted.foil.FoilRewardRegistry;
import com.cardrestricted.persistence.CollectionState;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FoilQuestReadinessTest
{
    @Test
    public void foilDerivedItemIsUsableButNotReportedAsOwned()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        FoilEntitlementResolver resolver = new FoilEntitlementResolver(
            catalogue,
            FoilRewardRegistry.load(
                FoilQuestReadinessTest.class.getClassLoader(),
                catalogue));
        QuestRequirementRegistry registry = QuestRequirementRegistry.load(
            FoilQuestReadinessTest.class.getClassLoader(),
            catalogue);
        String source = "item.mithril_axe.1355";
        CollectionState state = new CollectionState(
            UUID.randomUUID(),
            "foil-quest-test",
            "Foil Quest Test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.EPOCH,
            1,
            catalogue.getCatalogueVersion(),
            1,
            0L,
            0L,
            0L,
            Set.of(source),
            Set.of(source));

        QuestReadinessEntry entry = new QuestReadinessService(
            catalogue,
            registry,
            resolver).calculate(state).getEntries().stream()
                .filter(candidate -> "Inaidofthemyreque".equals(
                    candidate.getDefinition().getQuestKey()))
                .findFirst()
                .orElseThrow(AssertionError::new);

        assertTrue(entry.getFoilUnlockedItemCardIds().contains(
            "item.bronze_axe"));
        assertTrue(entry.getAvailableItemCardIds().contains(
            "item.bronze_axe"));
        assertFalse(entry.getOwnedItemCardIds().contains(
            "item.bronze_axe"));
        assertFalse(entry.getMissingItemCardIds().contains(
            "item.bronze_axe"));
    }
}
