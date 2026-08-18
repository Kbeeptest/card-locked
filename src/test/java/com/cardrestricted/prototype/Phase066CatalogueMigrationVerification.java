package com.cardrestricted.prototype;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.HistoricalCardDefinition;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.collection.activity.CollectionActivityService;
import com.cardrestricted.collection.activity.CollectionActivitySnapshot;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.pack.DuplicateShardValues;
import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.pack.PendingPackReveal;
import com.cardrestricted.persistence.CatalogueMigrationResult;
import com.cardrestricted.persistence.CatalogueMigrationService;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.JournalEventType;
import com.cardrestricted.persistence.SnapshotCodec;
import com.cardrestricted.persistence.StateJournalEvent;
import com.cardrestricted.persistence.TransactionalStateStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Save and immutable-journal compatibility checks for catalogue version 13. */
public final class Phase066CatalogueMigrationVerification
{
    private static final String MERGED_SOURCE =
        "item.1_2_anchovy_pizza.2299";
    private static final String MERGED_TARGET =
        "item.anchovy_pizza.2297";
    private static final String RETIRED_CARD =
        "item.24_carat_sword.24539";
    private static final String ACTIVE_CARD = "item.meat_pizza.2293";

    private Phase066CatalogueMigrationVerification()
    {
    }

    public static void main(String[] args) throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        verifyCatalogueCompatibilityMetadata(catalogue);
        verifySnapshotMigration(catalogue);
        verifyHistoricalActivity(catalogue);
        System.out.println(
            "Phase 0.66 catalogue migration verification passed.");
    }

    private static void verifyCatalogueCompatibilityMetadata(
        CardCatalogue catalogue)
    {
        require(
            catalogue.getCatalogueVersion() == 13,
            "Phase 0.66 must load catalogue version 13.");
        require(
            MERGED_TARGET.equals(catalogue.resolveCardId(MERGED_SOURCE)),
            "The merged pizza card must resolve to its retained identity.");
        require(
            !catalogue.containsCard(RETIRED_CARD),
            "A retired holiday card must not remain active.");
        HistoricalCardDefinition historical = catalogue
            .findHistoricalCard(RETIRED_CARD)
            .orElseThrow(() -> new AssertionError(
                "Retired card metadata must remain available."));
        require(
            "'24-carat' sword".equals(historical.getDisplayName()),
            "Historical metadata must retain the retired display name.");
        require(
            historical.getRetiredVersion() == 13,
            "Historical metadata must identify catalogue version 13.");
    }

    private static void verifySnapshotMigration(CardCatalogue catalogue)
        throws Exception
    {
        Path directory = Files.createTempDirectory(
            "card-locked-phase066-migration-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        UUID collectionId = UUID.randomUUID();
        UUID openingId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-27T21:00:00Z");
        PendingPackReveal pending = new PendingPackReveal(
            openingId,
            "pack.standard.item.v1",
            createdAt.plusSeconds(60),
            Arrays.asList(
                new PackCardResult(MERGED_SOURCE, false, 0),
                new PackCardResult(RETIRED_CARD, false, 0),
                new PackCardResult(ACTIVE_CARD, false, 0),
                new PackCardResult(MERGED_TARGET, false, 0)),
            new LinkedHashSet<>(Arrays.asList(0, 1)));
        CollectionState legacy = new CollectionState(
            collectionId,
            "phase066-verification-character",
            "Phase 066 Verification",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            createdAt,
            1,
            12,
            1,
            0,
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
            0,
            Map.of(),
            pending);
        store.save(legacy, -1L);

        CatalogueMigrationResult migration =
            new CatalogueMigrationService(catalogue, store)
                .migrateIfRequired(
                    Instant.parse("2026-07-27T21:05:00Z"));
        CollectionState migrated = migration.getState();

        require(migration.isMigrated(), "The version 12 save must migrate.");
        require(
            migrated.getCatalogueVersion() == 13,
            "The migrated save must record catalogue version 13.");
        require(
            migrated.getOwnedCardIds().equals(
                Set.of(MERGED_TARGET, ACTIVE_CARD)),
            "Merged ownership must collapse and retired ownership must drop.");
        require(
            migrated.getFoilCardIds().equals(Set.of(MERGED_TARGET)),
            "Foil ownership must transfer through aliases and drop retirements.");
        require(
            migration.getAliasesResolved() == 1,
            "Exactly one owned alias must be resolved.");
        require(
            migration.getOwnershipCollisions() == 1,
            "The source and target ownership collision must collapse once.");
        require(
            migration.getFoilAliasesResolved() == 1,
            "Exactly one foil alias must be resolved.");
        require(
            migration.getPendingAliasesResolved() == 1,
            "Exactly one pending reveal alias must be resolved.");

        PendingPackReveal migratedPending = migrated.getPendingPackReveal()
            .orElseThrow(() -> new AssertionError(
                "The partially revealed pack must survive migration."));
        require(
            migratedPending.getCardResults().size() == 3,
            "The retired pending result must be removed.");
        require(
            MERGED_TARGET.equals(migratedPending.getCardAt(0).getCardId())
                && ACTIVE_CARD.equals(
                    migratedPending.getCardAt(1).getCardId())
                && MERGED_TARGET.equals(
                    migratedPending.getCardAt(2).getCardId()),
            "Pending results must retain order after aliasing and retirement.");
        require(
            migratedPending.getRevealedPositions().equals(Set.of(0)),
            "Revealed positions must be reindexed after a retired result drops.");

        List<StateJournalEvent> journal = store.loadJournal();
        require(
            journal.size() == 2,
            "Creation and migration must each be journalled exactly once.");
        StateJournalEvent migrationEvent = journal.get(1);
        require(
            migrationEvent.getType() == JournalEventType.CATALOGUE_MIGRATED,
            "The migration must write the catalogue migration event.");
        require(
            migrationEvent.getPayload().contains("retiredOwnedCards=1")
                && migrationEvent.getPayload().contains(
                    "pendingRetiredCards=1"),
            "The migration journal must report retired save content.");

        CatalogueMigrationResult secondPass =
            new CatalogueMigrationService(catalogue, store)
                .migrateIfRequired(
                    Instant.parse("2026-07-27T21:06:00Z"));
        require(
            !secondPass.isMigrated(),
            "A migrated save must not be mutated a second time.");
        require(
            store.loadJournal().size() == 2,
            "An idempotent migration pass must not add another event.");
    }

    private static void verifyHistoricalActivity(CardCatalogue catalogue)
    {
        UUID collectionId = UUID.randomUUID();
        String characterKey = "phase066-activity-character";
        UUID openingId = UUID.randomUUID();
        List<StateJournalEvent> events = Arrays.asList(
            event(
                collectionId,
                characterKey,
                -1,
                0,
                JournalEventType.COLLECTION_CREATED,
                "starterCards=" + MERGED_SOURCE + "," + RETIRED_CARD
                    + ";starterRoute=legacy",
                Instant.parse("2026-07-27T22:00:00Z")),
            event(
                collectionId,
                characterKey,
                0,
                1,
                JournalEventType.PACK_PURCHASED,
                "openingId=" + openingId
                    + ";packId=pack.standard.item.v1"
                    + ";price=5000;results="
                    + MERGED_SOURCE + ","
                    + RETIRED_CARD + ":duplicate,"
                    + ACTIVE_CARD,
                Instant.parse("2026-07-27T22:05:00Z")));

        CollectionActivitySnapshot activity =
            new CollectionActivityService(catalogue).calculate(events);
        HistoricalCardDefinition retired = catalogue
            .findHistoricalCard(RETIRED_CARD)
            .orElseThrow();
        long expectedRetiredShards = DuplicateShardValues.forRarity(
            retired.getRarity());

        require(
            activity.getIgnoredEventCount() == 0,
            "Known aliases and retirements must not invalidate old activity.");
        require(
            activity.findUnlock(MERGED_TARGET).isPresent(),
            "A historical merged unlock must appear under the retained ID.");
        require(
            activity.findUnlock(MERGED_SOURCE).isEmpty(),
            "A historical merged source must not remain a separate unlock.");
        require(
            activity.findUnlock(RETIRED_CARD).isPresent(),
            "A retired unlock must remain visible in immutable history.");
        require(
            activity.findUnlock(ACTIVE_CARD).isPresent(),
            "An unchanged active unlock must remain visible.");
        require(
            activity.getDuplicateCount(RETIRED_CARD) == 1,
            "A retired duplicate must remain in card activity counts.");
        require(
            activity.getDuplicateShards() == expectedRetiredShards,
            "A retired duplicate must reconstruct shards from saved rarity.");
        require(
            activity.getTotalCardsDrawn() == 3,
            "All historical pack draws must remain counted.");
        require(
            activity.getNewPackCardCount() == 2
                && activity.getDuplicateCardCount() == 1,
            "Historical new and duplicate draw totals must remain intact.");
    }

    private static StateJournalEvent event(
        UUID collectionId,
        String characterKey,
        long previousRevision,
        long revision,
        JournalEventType type,
        String payload,
        Instant occurredAt)
    {
        return new StateJournalEvent(
            UUID.randomUUID(),
            collectionId,
            characterKey,
            previousRevision,
            revision,
            type,
            payload,
            occurredAt,
            previousRevision < 0 ? "" : "previous-event-hash",
            "state-hash-" + revision,
            "event-hash-" + revision);
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
