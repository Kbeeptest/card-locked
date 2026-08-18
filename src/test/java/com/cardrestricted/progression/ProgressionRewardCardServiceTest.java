package com.cardrestricted.progression;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.collection.activity.CardUnlockSource;
import com.cardrestricted.collection.activity.CollectionActivityService;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.JournalEventType;
import com.cardrestricted.persistence.SnapshotCodec;
import com.cardrestricted.persistence.TransactionalStateStore;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProgressionRewardCardServiceTest
{
    private static final Instant NOW =
        Instant.parse("2026-08-06T22:00:00Z");

    @Test
    public void coinsCardIsGrantedExactlyOnceAtProgressionThreshold()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Set<String> owned = firstOrdinaryCards(catalogue, 1_000);
        TransactionalStateStore store = storeWith(catalogue, owned);
        CollectionState initial = store.loadHighestValid().orElseThrow();

        ProgressionRewardCardService service =
            new ProgressionRewardCardService(catalogue, store);
        CollectionState granted = service.reconcile(initial, NOW);

        assertTrue(granted.getOwnedCardIds().contains(
            ProgressionRewardCardPolicy.COINS_CARD_ID));
        assertEquals(1_000,
            ProgressionMilestonePolicy.uniqueOwnedCardCount(
                catalogue, granted));
        assertEquals(JournalEventType.PROGRESSION_REWARD_GRANTED,
            store.loadJournal().get(1).getType());
        assertEquals(CardUnlockSource.PROGRESSION_TRACK,
            new CollectionActivityService(catalogue)
                .calculate(store.loadJournal())
                .findUnlock(ProgressionRewardCardPolicy.COINS_CARD_ID)
                .orElseThrow()
                .getSource());

        CollectionState second = service.reconcile(
            granted, NOW.plusSeconds(1));
        assertEquals(granted.getRevision(), second.getRevision());
        assertEquals(2, store.loadJournal().size());
    }

    @Test
    public void coinsCardRemainsLockedBeforeProgressionThreshold()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        TransactionalStateStore store = storeWith(
            catalogue,
            firstOrdinaryCards(catalogue, 999));
        CollectionState initial = store.loadHighestValid().orElseThrow();

        CollectionState result = new ProgressionRewardCardService(
            catalogue, store).reconcile(initial, NOW);

        assertFalse(result.getOwnedCardIds().contains(
            ProgressionRewardCardPolicy.COINS_CARD_ID));
        assertEquals(initial.getRevision(), result.getRevision());
        assertEquals(1, store.loadJournal().size());
    }

    @Test
    public void catalogueMapsEveryKnownCoinIdentityToCoinsCard()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        com.cardrestricted.runelite.InteractionFamilyIndex index =
            new com.cardrestricted.runelite.InteractionFamilyIndex(catalogue);
        for (int itemId : new int[]{995, 6964, 8890, 13204, 14440, 18028})
        {
            assertEquals("cache-item-family.coins.995",
                index.familyIdForItem(itemId));
        }
        assertEquals(Set.of(ProgressionRewardCardPolicy.COINS_CARD_ID),
            index.cardIdsForFamily("cache-item-family.coins.995"));
    }

    private static Set<String> firstOrdinaryCards(
        CardCatalogue catalogue,
        int count)
    {
        Set<String> result = catalogue.getCards().stream()
            .map(card -> card.getCardId())
            .filter(cardId -> !ProgressionRewardCardPolicy
                .isTrackOnlyReward(cardId))
            .limit(count)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(count, result.size());
        return result;
    }

    private static TransactionalStateStore storeWith(
        CardCatalogue catalogue,
        Set<String> owned)
        throws Exception
    {
        TransactionalStateStore store = new TransactionalStateStore(
            Files.createTempDirectory("card-locked-coins-card-"),
            new SnapshotCodec());
        CollectionState state = new CollectionState(
            UUID.randomUUID(),
            "coins-card-test",
            "Coins Card Test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            NOW.minusSeconds(60),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            0L,
            0L,
            owned,
            Set.of());
        store.save(state, -1L);
        return store;
    }
}
