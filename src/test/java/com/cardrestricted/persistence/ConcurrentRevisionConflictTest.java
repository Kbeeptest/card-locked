package com.cardrestricted.persistence;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Two store instances must never both commit the same next revision. */
public final class ConcurrentRevisionConflictTest
{
    private static final int REPETITIONS = 40;

    @Test
    public void competingWritersProduceOneCommitAndOneConflict()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        for (int iteration = 0; iteration < REPETITIONS; iteration++)
        {
            assertCompetingWriters(catalogue, iteration);
        }
    }


    @Test
    public void externalProfileLockSerializesStoreAccess()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path root = Files.createTempDirectory("cl-external-store-lock-");
        Path directory = root.resolve("state");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try
        {
            SnapshotCodec codec = new SnapshotCodec();
            CollectionState initial = PersistenceTestFixtures.state(
                catalogue,
                "external-lock",
                100L);
            TransactionalStateStore store =
                new TransactionalStateStore(directory, codec);
            store.save(initial, -1L);

            Path lockPath = root.resolve(
                ".state.card-locked-transaction.lock");
            try (FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
                 FileLock held = channel.lock())
            {
                CountDownLatch started = new CountDownLatch(1);
                Future<CollectionState> waiting = executor.submit(() -> {
                    started.countDown();
                    return new TransactionalStateStore(directory, codec)
                        .loadHighestValid()
                        .orElseThrow(AssertionError::new);
                });
                started.await();
                try
                {
                    waiting.get(150L, TimeUnit.MILLISECONDS);
                    throw new AssertionError(
                        "Store access ignored the external profile lock.");
                }
                catch (TimeoutException expected)
                {
                    // The store is correctly waiting for the profile lock.
                }
                held.release();
                CollectionState loaded = waiting.get(2L, TimeUnit.SECONDS);
                assertEquals(initial.getRevision(), loaded.getRevision());
                assertEquals(initial.getPoints(), loaded.getPoints());
            }
        }
        finally
        {
            executor.shutdownNow();
            deleteTree(root);
        }
    }

    private static void assertCompetingWriters(
        CardCatalogue catalogue,
        int iteration)
        throws Exception
    {
        Path directory = Files.createTempDirectory("cl-concurrent-store-");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try
        {
            SnapshotCodec codec = new SnapshotCodec();
            CollectionState initial = PersistenceTestFixtures.state(
                catalogue,
                "concurrent-store-" + iteration,
                100L);
            new TransactionalStateStore(directory, codec).save(initial, -1L);
            TransactionalStateStore first =
                new TransactionalStateStore(directory, codec);
            // Use a syntactically different path to verify normalized directory
            // identities still share the same cross-instance transaction guard.
            TransactionalStateStore second =
                new TransactionalStateStore(directory.resolve("."), codec);
            CountDownLatch start = new CountDownLatch(1);

            Future<String> a = executor.submit(() -> write(
                first,
                PersistenceTestFixtures.advance(initial, 200L),
                start,
                "A"));
            Future<String> b = executor.submit(() -> write(
                second,
                PersistenceTestFixtures.advance(initial, 300L),
                start,
                "B"));
            start.countDown();
            String resultA = a.get();
            String resultB = b.get();

            int committed = (resultA.startsWith("COMMIT") ? 1 : 0)
                + (resultB.startsWith("COMMIT") ? 1 : 0);
            int rejected = (resultA.startsWith("REJECT") ? 1 : 0)
                + (resultB.startsWith("REJECT") ? 1 : 0);
            assertEquals(resultA + " / " + resultB, 1, committed);
            assertEquals(resultA + " / " + resultB, 1, rejected);
            assertTrue(
                resultA + " / " + resultB,
                resultA.contains("RevisionConflictException")
                    || resultB.contains("RevisionConflictException"));

            CollectionState recovered = new TransactionalStateStore(
                directory,
                codec).loadHighestValid().orElseThrow(AssertionError::new);
            assertEquals(1L, recovered.getRevision());
            assertTrue(recovered.getPoints() == 200L
                || recovered.getPoints() == 300L);
        }
        finally
        {
            executor.shutdownNow();
            deleteTree(directory);
        }
    }

    private static String write(
        TransactionalStateStore store,
        CollectionState state,
        CountDownLatch start,
        String name)
        throws Exception
    {
        start.await();
        try
        {
            store.save(
                state,
                0L,
                JournalEventType.STATE_UPDATED,
                "writer=" + name,
                Instant.parse("2026-08-05T02:40:00Z"));
            return "COMMIT:" + name;
        }
        catch (java.io.IOException expected)
        {
            return "REJECT:" + name + ':' + expected.getClass().getSimpleName();
        }
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
                catch (Exception exception)
                {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }
}
