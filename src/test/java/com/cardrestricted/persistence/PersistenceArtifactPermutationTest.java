package com.cardrestricted.persistence;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Systematic filesystem-state matrix for startup recovery. This exercises
 * combinations which are difficult to reproduce manually without corrupting a
 * real profile.
 */
public final class PersistenceArtifactPermutationTest
{
    private static final Instant NEXT_AT =
        Instant.parse("2026-08-05T21:00:00Z");

    @Test
    public void snapshotAndJournalPermutationsAreRecoverableOrFailExplicitly()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path master = Files.createTempDirectory("cl-artifact-master-");
        try
        {
            SnapshotCodec codec = new SnapshotCodec();
            CollectionState revisionZero = PersistenceTestFixtures.state(
                catalogue, "artifact-matrix", 100L);
            CollectionState revisionOne = PersistenceTestFixtures.advance(
                revisionZero, 200L);
            TransactionalStateStore seed =
                new TransactionalStateStore(master, codec);
            seed.save(revisionZero, -1L);
            seed.save(revisionOne, 0L);

            byte[] latest = Files.readAllBytes(
                master.resolve("current.snapshot"));
            byte[] older = Files.readAllBytes(
                master.resolve("recovery-1.snapshot"));
            byte[] eventZero = Files.readAllBytes(master.resolve("journal")
                .resolve("00000000000000000000.event"));
            byte[] eventOne = Files.readAllBytes(master.resolve("journal")
                .resolve("00000000000000000001.event"));

            int executed = 0;
            for (SnapshotVariant current : SnapshotVariant.values())
            {
                for (SnapshotVariant recovery : SnapshotVariant.values())
                {
                    for (JournalVariant journal : JournalVariant.values())
                    {
                        executed++;
                        Path directory = Files.createTempDirectory(
                            "cl-artifact-case-");
                        try
                        {
                            writeSnapshot(directory.resolve("current.snapshot"),
                                current, latest, older);
                            writeSnapshot(directory.resolve(
                                "recovery-1.snapshot"), recovery,
                                latest, older);
                            writeJournal(directory, journal,
                                eventZero, eventOne);

                            TransactionalStateStore store =
                                new TransactionalStateStore(directory, codec);
                            int highestValid = Math.max(
                                current.revision(), recovery.revision());
                            boolean committedArtifacts =
                                current != SnapshotVariant.MISSING
                                    || recovery != SnapshotVariant.MISSING
                                    || journal != JournalVariant.MISSING;
                            if (highestValid < 0)
                            {
                                if (committedArtifacts)
                                {
                                    expectCorrupt(store);
                                }
                                else
                                {
                                    assertFalse(store.loadHighestValid()
                                        .isPresent());
                                }
                                continue;
                            }

                            CollectionState loaded = store.loadHighestValid()
                                .orElseThrow(AssertionError::new);
                            long expectedRevision = highestValid;
                            if (journal == JournalVariant.CORRUPT_TAIL
                                && (current.revision() == 0
                                    || recovery.revision() == 0))
                            {
                                expectedRevision = 0L;
                            }
                            assertEquals(caseName(current, recovery, journal),
                                expectedRevision, loaded.getRevision());
                            assertEquals(caseName(current, recovery, journal),
                                expectedRevision == 1 ? 200L : 100L,
                                loaded.getPoints());
                            assertFalse(Files.exists(
                                directory.resolve("pending.snapshot")));
                            assertFalse(Files.exists(
                                directory.resolve("pending.event")));

                            CollectionState next =
                                PersistenceTestFixtures.advance(loaded, 300L);
                            store.save(next, loaded.getRevision(),
                                JournalEventType.STATE_UPDATED,
                                "artifact-matrix", NEXT_AT);
                            CollectionState reopened =
                                new TransactionalStateStore(directory, codec)
                                    .loadHighestValid()
                                    .orElseThrow(AssertionError::new);
                            assertEquals(loaded.getRevision() + 1,
                                reopened.getRevision());
                            assertEquals(300L, reopened.getPoints());
                        }
                        finally
                        {
                            deleteTree(directory);
                        }
                    }
                }
            }
            assertEquals(48, executed);
        }
        finally
        {
            deleteTree(master);
        }
    }

    @Test
    public void everyAbandonedPendingCombinationPreservesCommittedState()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        SnapshotCodec codec = new SnapshotCodec();
        int executed = 0;
        for (PendingVariant snapshot : PendingVariant.values())
        {
            for (PendingVariant event : PendingVariant.values())
            {
                executed++;
                Path directory = Files.createTempDirectory(
                    "cl-pending-case-");
                try
                {
                    CollectionState initial = PersistenceTestFixtures.state(
                        catalogue, "pending-matrix", 100L);
                    new TransactionalStateStore(directory, codec)
                        .save(initial, -1L);
                    CollectionState uncommitted =
                        PersistenceTestFixtures.advance(initial, 999L);
                    writePending(directory.resolve("pending.snapshot"),
                        snapshot, codec.encode(uncommitted));
                    writePending(directory.resolve("pending.event"),
                        event, new byte[]{7, 8, 9, 10});

                    TransactionalStateStore reopened =
                        new TransactionalStateStore(directory, codec);
                    CollectionState loaded = reopened.loadHighestValid()
                        .orElseThrow(AssertionError::new);
                    assertEquals(0L, loaded.getRevision());
                    assertEquals(100L, loaded.getPoints());
                    assertFalse(Files.exists(
                        directory.resolve("pending.snapshot")));
                    assertFalse(Files.exists(
                        directory.resolve("pending.event")));
                }
                finally
                {
                    deleteTree(directory);
                }
            }
        }
        assertEquals(9, executed);
    }

    private static String caseName(
        SnapshotVariant current,
        SnapshotVariant recovery,
        JournalVariant journal)
    {
        return current + "/" + recovery + "/" + journal;
    }

    private static void writeSnapshot(
        Path target,
        SnapshotVariant variant,
        byte[] latest,
        byte[] older)
        throws IOException
    {
        switch (variant)
        {
            case VALID_LATEST:
                Files.write(target, latest);
                break;
            case VALID_OLDER:
                Files.write(target, older);
                break;
            case CORRUPT:
                Files.write(target, new byte[]{1, 3, 3, 7});
                break;
            case MISSING:
            default:
                break;
        }
    }

    private static void writeJournal(
        Path directory,
        JournalVariant variant,
        byte[] eventZero,
        byte[] eventOne)
        throws IOException
    {
        if (variant == JournalVariant.MISSING)
        {
            return;
        }
        Path journal = directory.resolve("journal");
        Files.createDirectories(journal);
        Files.write(journal.resolve("00000000000000000000.event"),
            eventZero);
        Files.write(journal.resolve("00000000000000000001.event"),
            variant == JournalVariant.VALID
                ? eventOne
                : new byte[]{9, 9, 9});
    }

    private static void writePending(
        Path target,
        PendingVariant variant,
        byte[] valid)
        throws IOException
    {
        if (variant == PendingVariant.VALID)
        {
            Files.write(target, valid);
        }
        else if (variant == PendingVariant.CORRUPT)
        {
            Files.write(target, new byte[]{4, 2});
        }
    }

    private static void expectCorrupt(TransactionalStateStore store)
        throws Exception
    {
        boolean rejected = false;
        try
        {
            store.loadHighestValid();
        }
        catch (CorruptSnapshotException expected)
        {
            rejected = true;
        }
        assertTrue("Committed invalid artifacts must fail explicitly.",
            rejected);
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

    private enum SnapshotVariant
    {
        VALID_LATEST(1),
        VALID_OLDER(0),
        CORRUPT(-1),
        MISSING(-1);

        private final int revision;

        SnapshotVariant(int revision)
        {
            this.revision = revision;
        }

        int revision()
        {
            return revision;
        }
    }

    private enum JournalVariant
    {
        VALID,
        CORRUPT_TAIL,
        MISSING
    }

    private enum PendingVariant
    {
        VALID,
        CORRUPT,
        MISSING
    }
}
