package com.cardrestricted.persistence;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Exhaustive crash-injection matrix for every transactional commit stage. */
public final class PersistenceCrashMatrixTest
{
    private static final Instant MUTATED_AT =
        Instant.parse("2026-08-05T02:30:00Z");

    @Test
    public void everyCommitStageRecoversToExactlyOneDurableRevision()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Map<PersistenceCommitStage, Long> recoveredRevisions =
            new EnumMap<>(PersistenceCommitStage.class);

        for (PersistenceCommitStage stage : PersistenceCommitStage.values())
        {
            Path directory = Files.createTempDirectory(
                "cl-crash-matrix-" + stage.name().toLowerCase() + '-');
            try
            {
                SnapshotCodec codec = new SnapshotCodec();
                CollectionState initial = PersistenceTestFixtures.state(
                    catalogue,
                    "crash-matrix-" + stage.name(),
                    100L);
                new TransactionalStateStore(directory, codec)
                    .save(initial, -1L);

                AtomicBoolean failed = new AtomicBoolean();
                TransactionalStateStore interrupted =
                    new TransactionalStateStore(
                        directory,
                        codec,
                        new JournalEventCodec(),
                        checkpoint -> {
                            if (checkpoint == stage
                                && failed.compareAndSet(false, true))
                            {
                                throw new IOException(
                                    "Injected crash at " + checkpoint);
                            }
                        });
                CollectionState intended = PersistenceTestFixtures.advance(
                    initial,
                    200L);
                expectIOException(() -> interrupted.save(
                    intended,
                    initial.getRevision(),
                    JournalEventType.STATE_UPDATED,
                    "points=200",
                    MUTATED_AT));
                assertTrue("Fault was not reached at " + stage, failed.get());

                TransactionalStateStore reopened =
                    new TransactionalStateStore(directory, codec);
                CollectionState recovered = reopened.loadHighestValid()
                    .orElseThrow(AssertionError::new);
                long expectedRevision = committedBy(stage) ? 1L : 0L;
                long expectedPoints = committedBy(stage) ? 200L : 100L;
                assertEquals(stage.name(), expectedRevision,
                    recovered.getRevision());
                assertEquals(stage.name(), expectedPoints,
                    recovered.getPoints());
                recoveredRevisions.put(stage, recovered.getRevision());

                assertFalse(stage.name(),
                    Files.exists(directory.resolve("pending.snapshot")));
                assertFalse(stage.name(),
                    Files.exists(directory.resolve("pending.event")));
                assertEquals(stage.name(),
                    expectedRevision + 1,
                    reopened.loadJournal().size());

                CollectionState next = PersistenceTestFixtures.advance(
                    recovered,
                    300L);
                reopened.save(
                    next,
                    recovered.getRevision(),
                    JournalEventType.STATE_UPDATED,
                    "points=300",
                    MUTATED_AT.plusSeconds(1));
                CollectionState finalState = new TransactionalStateStore(
                    directory,
                    codec).loadHighestValid().orElseThrow(AssertionError::new);
                assertEquals(stage.name(), recovered.getRevision() + 1,
                    finalState.getRevision());
                assertEquals(stage.name(), 300L, finalState.getPoints());
            }
            finally
            {
                deleteTree(directory);
            }
        }

        assertEquals(PersistenceCommitStage.values().length,
            recoveredRevisions.size());
    }

    private static boolean committedBy(PersistenceCommitStage stage)
    {
        switch (stage)
        {
            case AFTER_EVENT_COMMIT:
            case AFTER_RECOVERY_ROTATION:
            case AFTER_SNAPSHOT_PROMOTION:
                return true;
            case AFTER_PENDING_SNAPSHOT_FLUSH:
            case AFTER_PENDING_EVENT_FLUSH:
            default:
                return false;
        }
    }

    private static void expectIOException(IoAction action) throws Exception
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
        assertTrue("Expected injected IOException.", thrown);
    }

    private static void deleteTree(Path root) throws Exception
    {
        if (!Files.exists(root))
        {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root))
        {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try
                {
                    Files.deleteIfExists(path);
                }
                catch (IOException exception)
                {
                    throw new java.io.UncheckedIOException(exception);
                }
            });
        }
    }

    @FunctionalInterface
    private interface IoAction
    {
        void run() throws Exception;
    }
}
