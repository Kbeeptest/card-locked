package com.cardrestricted.points;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.JournalEventType;
import com.cardrestricted.persistence.SnapshotCodec;
import com.cardrestricted.persistence.TransactionalStateStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PointsLedgerBatchTest
{
    @Test
    public void batchesUniqueNpcAwardsIntoOneDurableJournalEvent()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory("card-locked-point-batch-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState initial = new CollectionState(
            UUID.randomUUID(),
            "batch-self-test",
            "Batch Self Test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-08-07T12:00:00Z"),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            100L,
            0L,
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptySet());
        store.save(initial, -1L);
        CollectionState current = store.loadHighestValid()
            .orElseThrow(AssertionError::new);

        PointAward first = new PointAward(
            "npc-kill:v2:test:301:100:1:10",
            PointSourceType.NPC_KILL,
            25L,
            Instant.parse("2026-08-07T12:00:01Z"));
        PointAward second = new PointAward(
            "npc-kill:v2:test:301:100:2:11",
            PointSourceType.NPC_KILL,
            40L,
            Instant.parse("2026-08-07T12:00:01Z"));

        CollectionState result = new PointsLedgerService(store).awardAll(
            current,
            Arrays.asList(first, second, first));

        assertEquals(165L, result.getPoints());
        assertTrue(result.getClaimedPointSourceIds().contains(first.getSourceId()));
        assertTrue(result.getClaimedPointSourceIds().contains(second.getSourceId()));
        assertEquals(
            1L,
            store.loadJournal().stream()
                .filter(event -> event.getType() == JournalEventType.POINTS_AWARDED)
                .count());
    }
}
