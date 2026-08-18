package com.cardrestricted.quest;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.persistence.CollectionState;
import java.time.Instant;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Confirms the precomputed Quest Tracker search index retains prior behaviour. */
public final class QuestReadinessSearchIndexTest
{
    @Test
    public void indexedSearchMatchesQuestAndRequirementCardNames()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        QuestRequirementRegistry registry = QuestRequirementRegistry.load(
            QuestReadinessSearchIndexTest.class.getClassLoader(),
            catalogue);
        QuestReadinessSnapshot snapshot = new QuestReadinessService(
            catalogue,
            registry).calculate(emptyState());

        QuestReadinessEntry candidate = snapshot.getEntries().stream()
            .filter(entry -> !entry.getMissingCardIds().isEmpty())
            .findFirst()
            .orElseThrow(AssertionError::new);
        String questName = candidate.getDefinition().getQuestName()
            .toLowerCase(Locale.ROOT);
        String cardName = catalogue.requireCard(
            candidate.getMissingCardIds().get(0)).getDisplayName()
            .toLowerCase(Locale.ROOT);

        assertTrue(candidate.matchesNormalized(questName));
        assertTrue(candidate.matchesNormalized(cardName));
        assertTrue(candidate.matches("  " + cardName.toUpperCase(Locale.ROOT)
            + "  ", catalogue));
        assertNotNull(candidate.getStatus());
    }

    private static CollectionState emptyState()
    {
        return new CollectionState(
            UUID.randomUUID(),
            "performance-test",
            "Performance Test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.EPOCH,
            1,
            1,
            1,
            0L,
            0L,
            0L,
            Collections.emptySet(),
            Collections.emptySet());
    }
}
