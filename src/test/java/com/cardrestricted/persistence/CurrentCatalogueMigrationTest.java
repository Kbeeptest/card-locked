package com.cardrestricted.persistence;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.pack.PendingPackReveal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CurrentCatalogueMigrationTest
{
    private static final String MERGED_SOURCE =
        "item.1_2_anchovy_pizza.2299";
    private static final String MERGED_TARGET =
        "item.anchovy_pizza.2297";
    private static final String RETIRED_CARD =
        "item.24_carat_sword.24539";
    private static final String ACTIVE_CARD = "item.meat_pizza.2293";

    @Test
    public void oldOwnershipAndPendingRevealMigrateToCurrentCatalogue()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        assertTrue(catalogue.getCatalogueVersion() >= 16);
        assertEquals(MERGED_TARGET, catalogue.resolveCardId(MERGED_SOURCE));
        assertFalse(catalogue.containsCard(RETIRED_CARD));
        assertTrue(catalogue.findHistoricalCard(RETIRED_CARD).isPresent());

        Path directory = Files.createTempDirectory(
            "card-locked-current-migration-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        Instant createdAt = Instant.parse("2026-08-03T12:00:00Z");
        PendingPackReveal pending = new PendingPackReveal(
            UUID.randomUUID(),
            "pack.standard.item.v1",
            createdAt.plusSeconds(60),
            Arrays.asList(
                new PackCardResult(MERGED_SOURCE, false, 0),
                new PackCardResult(RETIRED_CARD, false, 0),
                new PackCardResult(ACTIVE_CARD, false, 0),
                new PackCardResult(MERGED_TARGET, false, 0)),
            new LinkedHashSet<>(Arrays.asList(0, 1)));
        CollectionState legacy = new CollectionState(
            UUID.randomUUID(),
            "current-migration-test",
            "Current Migration Test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            createdAt,
            1,
            13,
            1,
            0L,
            25_000L,
            500L,
            new LinkedHashSet<>(Arrays.asList(
                MERGED_SOURCE,
                MERGED_TARGET,
                RETIRED_CARD,
                ACTIVE_CARD)),
            new LinkedHashSet<>(Arrays.asList(
                MERGED_SOURCE,
                RETIRED_CARD)),
            Set.of("starter.points.choice"),
            0L,
            Map.of(),
            pending);
        store.save(legacy, -1L);

        CatalogueMigrationResult migration = new CatalogueMigrationService(
            catalogue,
            store).migrateIfRequired(
                Instant.parse("2026-08-03T12:05:00Z"));
        CollectionState migrated = migration.getState();

        assertTrue(migration.isMigrated());
        assertArrayEquals(
            new SnapshotCodec().encode(legacy),
            Files.readAllBytes(directory.resolve("recovery-1.snapshot")));
        assertEquals(
            catalogue.getCatalogueVersion(),
            migrated.getCatalogueVersion());
        assertEquals(
            Set.of(MERGED_TARGET, ACTIVE_CARD),
            migrated.getOwnedCardIds());
        assertEquals(Set.of(MERGED_TARGET), migrated.getFoilCardIds());
        assertEquals(1, migration.getAliasesResolved());
        assertEquals(1, migration.getOwnershipCollisions());
        assertEquals(1, migration.getFoilAliasesResolved());
        assertEquals(1, migration.getPendingAliasesResolved());

        PendingPackReveal migratedPending = migrated.getPendingPackReveal()
            .orElseThrow(AssertionError::new);
        assertEquals(3, migratedPending.getCardResults().size());
        assertEquals(MERGED_TARGET, migratedPending.getCardAt(0).getCardId());
        assertEquals(ACTIVE_CARD, migratedPending.getCardAt(1).getCardId());
        assertEquals(MERGED_TARGET, migratedPending.getCardAt(2).getCardId());
        assertEquals(Set.of(0), migratedPending.getRevealedPositions());

        List<StateJournalEvent> journal = store.loadJournal();
        assertEquals(2, journal.size());
        assertEquals(JournalEventType.CATALOGUE_MIGRATED,
            journal.get(1).getType());
        assertTrue(journal.get(1).getPayload().contains(
            "retiredOwnedCards=1"));
        assertTrue(journal.get(1).getPayload().contains(
            "pendingRetiredCards=1"));

        CatalogueMigrationResult secondPass = new CatalogueMigrationService(
            catalogue,
            store).migrateIfRequired(
                Instant.parse("2026-08-03T12:06:00Z"));
        assertFalse(secondPass.isMigrated());
        assertEquals(2, store.loadJournal().size());
    }
}
