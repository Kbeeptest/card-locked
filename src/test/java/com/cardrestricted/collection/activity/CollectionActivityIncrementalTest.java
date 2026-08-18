package com.cardrestricted.collection.activity;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.JournalEventCodec;
import com.cardrestricted.persistence.JournalEventType;
import com.cardrestricted.persistence.SnapshotCodec;
import com.cardrestricted.persistence.StateJournalEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CollectionActivityIncrementalTest
{
    @Test
    public void incrementalReplayMatchesFullReplay()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        CardDefinition card = catalogue.getCards().iterator().next();
        SnapshotCodec snapshotCodec = new SnapshotCodec();
        JournalEventCodec journalCodec = new JournalEventCodec();
        CollectionActivityService service =
            new CollectionActivityService(catalogue);
        CollectionState state = new CollectionState(
            UUID.randomUUID(),
            "activity-incremental",
            "Activity Test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-08-07T12:00:00Z"),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            10_000L,
            0L,
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptySet());

        List<StateJournalEvent> events = new ArrayList<>();
        String previousHash = "";
        StateJournalEvent created = journalCodec.create(
            state,
            -1L,
            JournalEventType.COLLECTION_CREATED,
            "starterChoice=POINTS;starterBonusPoints=3000;starterCards=",
            Instant.parse("2026-08-07T12:00:01Z"),
            previousHash,
            snapshotCodec.encode(state));
        events.add(created);
        previousHash = created.getEventHash();

        UUID openingId = UUID.randomUUID();
        state = state.withProgress(
            1L,
            state.getPoints() - 1000L,
            state.getShards(),
            state.getOwnedCardIds(),
            state.getFoilCardIds());
        StateJournalEvent pack = journalCodec.create(
            state,
            0L,
            JournalEventType.PACK_PURCHASED,
            "openingId=" + openingId
                + ";packId=pack.standard;price=1000;results="
                + card.getCardId(),
            Instant.parse("2026-08-07T12:00:02Z"),
            previousHash,
            snapshotCodec.encode(state));
        events.add(pack);
        previousHash = pack.getEventHash();

        state = state.withProgress(
            2L,
            state.getPoints(),
            state.getShards(),
            state.getOwnedCardIds(),
            state.getFoilCardIds());
        StateJournalEvent reveal = journalCodec.create(
            state,
            1L,
            JournalEventType.PACK_REVEAL_ADVANCED,
            "openingId=" + openingId + ";reveal=1;position=1;cardId="
                + card.getCardId() + ";foil=false",
            Instant.parse("2026-08-07T12:00:03Z"),
            previousHash,
            snapshotCodec.encode(state));
        events.add(reveal);
        previousHash = reveal.getEventHash();

        state = state.withProgress(
            3L,
            state.getPoints() + 250L,
            state.getShards(),
            state.getOwnedCardIds(),
            state.getFoilCardIds());
        events.add(journalCodec.create(
            state,
            2L,
            JournalEventType.POINTS_AWARDED,
            "sourceId=test;sourceType=NPC_KILL;amount=250;count=1",
            Instant.parse("2026-08-07T12:00:04Z"),
            previousHash,
            snapshotCodec.encode(state)));

        CollectionActivitySnapshot baseline = service.calculate(
            events.subList(0, 1));
        CollectionActivitySnapshot incremental = service.calculateIncremental(
            baseline,
            events.subList(1, events.size()));
        CollectionActivitySnapshot full = service.calculate(events);

        assertEquals(full.getRecordedPointsEarned(),
            incremental.getRecordedPointsEarned());
        assertEquals(full.getTotalCardsDrawn(),
            incremental.getTotalCardsDrawn());
        assertEquals(full.getPackCount(), incremental.getPackCount());
        assertEquals(full.getPacks().get(0).getRevealedCount(),
            incremental.getPacks().get(0).getRevealedCount());
        assertEquals(full.getUnlocks().size(), incremental.getUnlocks().size());
        assertEquals(full.getDuplicateCounts(), incremental.getDuplicateCounts());
        assertEquals(full.getIgnoredEventCount(),
            incremental.getIgnoredEventCount());
    }
}
