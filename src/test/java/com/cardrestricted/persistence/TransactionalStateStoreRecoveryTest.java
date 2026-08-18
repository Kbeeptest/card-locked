package com.cardrestricted.persistence;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TransactionalStateStoreRecoveryTest
{
    private static final Instant MUTATED_AT =
        Instant.parse("2026-08-03T14:05:00Z");

    @Test
    public void everyCommitCheckpointRecoversToAWritableState()
        throws Exception
    {
        for (PersistenceCommitStage stage : PersistenceCommitStage.values())
        {
            verifyCheckpoint(stage);
        }
    }

    @Test
    public void handledFailureAfterEventCommitDoesNotPoisonJournalCache()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-cache-recovery-");
        SnapshotCodec codec = new SnapshotCodec();
        CollectionState initial = PersistenceTestFixtures.state(
            catalogue,
            "cache-recovery",
            100L);
        TransactionalStateStore seed = new TransactionalStateStore(
            directory,
            codec);
        seed.save(initial, -1L);

        AtomicBoolean failed = new AtomicBoolean();
        TransactionalStateStore interrupted = new TransactionalStateStore(
            directory,
            codec,
            new JournalEventCodec(),
            failOnceAt(PersistenceCommitStage.AFTER_EVENT_COMMIT, failed));
        CollectionState first = PersistenceTestFixtures.advance(
            initial,
            200L);
        expectIOException(() -> interrupted.save(
            first,
            initial.getRevision(),
            JournalEventType.STATE_UPDATED,
            "points=200",
            MUTATED_AT));

        CollectionState recovered = interrupted.loadHighestValid()
            .orElseThrow(AssertionError::new);
        assertEquals(1L, recovered.getRevision());
        CollectionState second = PersistenceTestFixtures.advance(
            recovered,
            300L);
        interrupted.save(
            second,
            recovered.getRevision(),
            JournalEventType.STATE_UPDATED,
            "points=300",
            MUTATED_AT.plusSeconds(1));

        TransactionalStateStore reopened = new TransactionalStateStore(
            directory,
            codec);
        assertEquals(2L, reopened.loadHighestValid()
            .orElseThrow(AssertionError::new).getRevision());
        assertEquals(3, reopened.loadJournal().size());
    }

    @Test
    public void corruptCurrentRollsBackAndCanBeSavedAgain()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-corrupt-current-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState initial = PersistenceTestFixtures.state(
            catalogue,
            "corrupt-current",
            100L);
        store.save(initial, -1L);
        CollectionState lost = PersistenceTestFixtures.advance(initial, 200L);
        store.save(lost, 0L);

        Files.write(
            directory.resolve("current.snapshot"),
            new byte[]{1, 2, 3, 4});

        TransactionalStateStore recoveredStore = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState recovered = recoveredStore.loadHighestValid()
            .orElseThrow(AssertionError::new);
        assertEquals(0L, recovered.getRevision());
        assertEquals(100L, recovered.getPoints());
        String recoveryNotice = recoveredStore.consumeRecoveryNotice()
            .orElseThrow(AssertionError::new);
        assertTrue(recoveryNotice.contains("local backup"));
        assertTrue(recoveryNotice.contains("recovery-quarantine"));
        assertFalse(recoveredStore.consumeRecoveryNotice().isPresent());

        CollectionState replacement = PersistenceTestFixtures.advance(
            recovered,
            250L);
        recoveredStore.save(replacement, 0L);

        TransactionalStateStore reopened = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState finalState = reopened.loadHighestValid()
            .orElseThrow(AssertionError::new);
        assertEquals(1L, finalState.getRevision());
        assertEquals(250L, finalState.getPoints());
        assertEquals(2, reopened.loadJournal().size());
        assertTrue(hasQuarantineFiles(directory));
    }

    @Test
    public void corruptLatestJournalEventRollsBackToMatchingSnapshot()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-corrupt-journal-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState initial = PersistenceTestFixtures.state(
            catalogue,
            "corrupt-journal",
            100L);
        store.save(initial, -1L);
        store.save(PersistenceTestFixtures.advance(initial, 200L), 0L);

        Files.write(
            directory.resolve("journal")
                .resolve("00000000000000000001.event"),
            new byte[]{9, 8, 7});

        TransactionalStateStore recoveredStore = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState recovered = recoveredStore.loadHighestValid()
            .orElseThrow(AssertionError::new);
        assertEquals(0L, recovered.getRevision());
        assertEquals(1, recoveredStore.loadJournal().size());

        CollectionState replacement = PersistenceTestFixtures.advance(
            recovered,
            275L);
        recoveredStore.save(replacement, 0L);
        assertEquals(2, recoveredStore.loadJournal().size());
        assertTrue(hasQuarantineFiles(directory));
    }

    @Test
    public void invalidDuplicateJournalTailIsQuarantinedWithoutRollback()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-duplicate-journal-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState initial = PersistenceTestFixtures.state(
            catalogue,
            "duplicate-journal",
            100L);
        store.save(initial, -1L);
        CollectionState current = PersistenceTestFixtures.advance(
            initial,
            200L);
        store.save(current, 0L);

        Path journal = directory.resolve("journal");
        Files.copy(
            journal.resolve("00000000000000000001.event"),
            journal.resolve("00000000000000000002.event"));

        TransactionalStateStore reopened = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState loaded = reopened.loadHighestValid()
            .orElseThrow(AssertionError::new);
        assertEquals(1L, loaded.getRevision());
        assertEquals(2, reopened.loadJournal().size());
        assertFalse(Files.exists(
            journal.resolve("00000000000000000002.event")));
        assertTrue(hasQuarantineFiles(directory));
    }

    @Test
    public void abandonedPendingFilesDoNotOverrideCommittedState()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-abandoned-pending-");
        SnapshotCodec codec = new SnapshotCodec();
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            codec);
        CollectionState initial = PersistenceTestFixtures.state(
            catalogue,
            "abandoned-pending",
            100L);
        store.save(initial, -1L);

        CollectionState uncommitted = PersistenceTestFixtures.advance(
            initial,
            999L);
        Files.write(
            directory.resolve("pending.snapshot"),
            codec.encode(uncommitted));
        Files.write(
            directory.resolve("pending.event"),
            new byte[]{4, 5, 6});

        TransactionalStateStore reopened = new TransactionalStateStore(
            directory,
            codec);
        CollectionState loaded = reopened.loadHighestValid()
            .orElseThrow(AssertionError::new);
        assertEquals(0L, loaded.getRevision());
        assertEquals(100L, loaded.getPoints());
        assertFalse(Files.exists(directory.resolve("pending.snapshot")));
        assertFalse(Files.exists(directory.resolve("pending.event")));
        assertTrue(hasQuarantineFiles(directory));
    }

    @Test
    public void committedButUnrecoverableProfileDoesNotBecomeNewSetup()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-unrecoverable-profile-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState initial = PersistenceTestFixtures.state(
            catalogue,
            "unrecoverable-profile",
            100L);
        store.save(initial, -1L);
        Files.deleteIfExists(directory.resolve("recovery-1.snapshot"));
        Files.write(
            directory.resolve("current.snapshot"),
            new byte[]{1, 2, 3});

        boolean rejected = false;
        try
        {
            new TransactionalStateStore(directory, new SnapshotCodec())
                .loadHighestValid();
        }
        catch (CorruptSnapshotException expected)
        {
            rejected = true;
            assertTrue(expected.getMessage().contains(
                "No valid collection snapshot"));
        }
        assertTrue("Committed corrupt data must not look like a new profile.",
            rejected);
        assertTrue(hasQuarantineFiles(directory));
    }

    @Test
    public void mutationCannotReplaceCollectionOrCharacterIdentity()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-identity-guard-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState initial = PersistenceTestFixtures.state(
            catalogue,
            "identity-a",
            100L);
        store.save(initial, -1L);

        CollectionState wrongIdentity = new CollectionState(
            java.util.UUID.randomUUID(),
            "identity-b",
            initial.getDisplayName(),
            initial.getEconomyMode(),
            initial.getIntegrityMode(),
            initial.getCreatedAt(),
            initial.getSchemaVersion(),
            initial.getCatalogueVersion(),
            initial.getRuleSetVersion(),
            1L,
            200L,
            0L,
            initial.getOwnedCardIds(),
            initial.getFoilCardIds(),
            initial.getClaimedPointSourceIds());
        boolean rejected = false;
        try
        {
            store.save(wrongIdentity, 0L);
        }
        catch (IllegalArgumentException expected)
        {
            rejected = true;
        }
        assertTrue(rejected);
        assertEquals("identity-a", store.loadHighestValid()
            .orElseThrow(AssertionError::new).getCharacterKey());
    }

    @Test
    public void rotatingHistoricalAuditDetectsModifiedOldEvent()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-rotating-audit-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState state = PersistenceTestFixtures.state(
            catalogue,
            "rotating-audit",
            100L);
        store.save(state, -1L);
        for (int revision = 1; revision <= 3; revision++)
        {
            state = PersistenceTestFixtures.advance(
                state,
                100L + revision);
            store.save(
                state,
                revision - 1L,
                JournalEventType.STATE_UPDATED,
                "revision=" + revision,
                MUTATED_AT.plusSeconds(revision));
        }

        // Establish the healthy fast path, then mutate the oldest durable
        // event behind the live store. The rotating audit should discover it
        // without requiring an O(history) scan on every normal load.
        assertEquals(3L, store.loadHighestValid()
            .orElseThrow(AssertionError::new).getRevision());
        Files.write(
            directory.resolve("journal")
                .resolve("00000000000000000000.event"),
            new byte[]{7, 7, 7, 7});

        for (int index = 0; index < 70; index++)
        {
            assertEquals(3L, store.loadHighestValid()
                .orElseThrow(AssertionError::new).getRevision());
        }
        assertTrue(hasQuarantineFiles(directory));
    }

    private void verifyCheckpoint(PersistenceCommitStage stage)
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-stage-" + stage.name().toLowerCase() + "-");
        SnapshotCodec codec = new SnapshotCodec();
        TransactionalStateStore seed = new TransactionalStateStore(
            directory,
            codec);
        CollectionState initial = PersistenceTestFixtures.state(
            catalogue,
            "stage-" + stage.name(),
            100L);
        seed.save(initial, -1L);

        AtomicBoolean failed = new AtomicBoolean();
        TransactionalStateStore interrupted = new TransactionalStateStore(
            directory,
            codec,
            new JournalEventCodec(),
            failOnceAt(stage, failed));
        CollectionState attempted = PersistenceTestFixtures.advance(
            initial,
            200L);
        expectIOException(() -> interrupted.save(
            attempted,
            0L,
            JournalEventType.STATE_UPDATED,
            "stage=" + stage,
            MUTATED_AT));

        TransactionalStateStore reopened = new TransactionalStateStore(
            directory,
            codec);
        CollectionState recovered = reopened.loadHighestValid()
            .orElseThrow(AssertionError::new);
        long expectedRevision = stage.ordinal()
            >= PersistenceCommitStage.AFTER_EVENT_COMMIT.ordinal()
                ? 1L
                : 0L;
        assertEquals(stage.name(), expectedRevision, recovered.getRevision());
        long replacementPoints = expectedRevision == 1L ? 300L : 250L;
        CollectionState next = PersistenceTestFixtures.advance(
            recovered,
            replacementPoints);
        reopened.save(
            next,
            recovered.getRevision(),
            JournalEventType.STATE_UPDATED,
            "postRecovery=true",
            MUTATED_AT.plusSeconds(1));

        TransactionalStateStore finalStore = new TransactionalStateStore(
            directory,
            codec);
        CollectionState finalState = finalStore.loadHighestValid()
            .orElseThrow(AssertionError::new);
        assertEquals(expectedRevision + 1, finalState.getRevision());
        assertEquals(replacementPoints, finalState.getPoints());
    }

    private PersistenceFaultInjector failOnceAt(
        PersistenceCommitStage target,
        AtomicBoolean failed)
    {
        return stage -> {
            if (stage == target && failed.compareAndSet(false, true))
            {
                throw new IOException("Injected failure at " + stage);
            }
        };
    }

    private void expectIOException(IoAction action) throws Exception
    {
        boolean thrown = false;
        try
        {
            action.run();
        }
        catch (IOException expected)
        {
            thrown = true;
        }
        assertTrue("Expected an injected IOException.", thrown);
    }

    private boolean hasQuarantineFiles(Path directory) throws IOException
    {
        Path quarantine = directory.resolve("recovery-quarantine");
        if (!Files.isDirectory(quarantine))
        {
            return false;
        }
        try (java.util.stream.Stream<Path> paths = Files.list(quarantine))
        {
            return paths.findAny().isPresent();
        }
    }

    @FunctionalInterface
    private interface IoAction
    {
        void run() throws Exception;
    }
}
