package com.cardrestricted.persistence;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.pack.PackPurchaseResult;
import com.cardrestricted.pack.PendingPackReveal;
import com.cardrestricted.pack.PendingRevealException;
import com.cardrestricted.pack.StandardPackService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PackTransactionRecoveryTest
{
    private static final Instant PURCHASED_AT =
        Instant.parse("2026-08-03T15:00:00Z");

    @Test
    public void committedPurchaseSurvivesCrashAndCannotChargeTwice()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-pack-commit-recovery-");
        SnapshotCodec codec = new SnapshotCodec();
        long startingPoints = StandardPackService.PRICE + 50_000L;
        CollectionState initial = PersistenceTestFixtures.state(
            catalogue,
            "pack-commit-recovery",
            startingPoints);
        new TransactionalStateStore(directory, codec).save(initial, -1L);

        AtomicBoolean failed = new AtomicBoolean();
        TransactionalStateStore interrupted = new TransactionalStateStore(
            directory,
            codec,
            new JournalEventCodec(),
            failOnceAt(PersistenceCommitStage.AFTER_EVENT_COMMIT, failed));
        StandardPackService interruptedPacks = new StandardPackService(
            catalogue,
            interrupted);
        PackPurchaseResult recoveredPurchase = interruptedPacks.purchase(
            new Random(100L),
            PURCHASED_AT);
        assertEquals(
            startingPoints - StandardPackService.PRICE,
            recoveredPurchase.getState().getPoints());
        assertTrue(recoveredPurchase.getState().getPendingPackReveal().isPresent());

        TransactionalStateStore recoveredStore = new TransactionalStateStore(
            directory,
            codec);
        CollectionState recovered = recoveredStore.loadHighestValid()
            .orElseThrow(AssertionError::new);
        assertEquals(
            startingPoints - StandardPackService.PRICE,
            recovered.getPoints());
        PendingPackReveal pending = recovered.getPendingPackReveal()
            .orElseThrow(AssertionError::new);
        assertEquals(StandardPackService.CARD_COUNT,
            pending.getCardResults().size());

        boolean blocked = false;
        try
        {
            new StandardPackService(catalogue, recoveredStore).purchase(
                new Random(100L),
                PURCHASED_AT.plusSeconds(1));
        }
        catch (PendingRevealException expected)
        {
            blocked = true;
        }
        assertTrue("A recovered pending pack must prevent a second charge.",
            blocked);
        assertEquals(
            startingPoints - StandardPackService.PRICE,
            recoveredStore.loadHighestValid()
                .orElseThrow(AssertionError::new).getPoints());
    }

    @Test
    public void uncommittedPurchaseLeavesBalanceUntouchedAndCanRetry()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-pack-uncommitted-");
        SnapshotCodec codec = new SnapshotCodec();
        long startingPoints = StandardPackService.PRICE + 25_000L;
        CollectionState initial = PersistenceTestFixtures.state(
            catalogue,
            "pack-uncommitted",
            startingPoints);
        new TransactionalStateStore(directory, codec).save(initial, -1L);

        AtomicBoolean failed = new AtomicBoolean();
        StandardPackService interruptedPacks = new StandardPackService(
            catalogue,
            new TransactionalStateStore(
                directory,
                codec,
                new JournalEventCodec(),
                failOnceAt(
                    PersistenceCommitStage.AFTER_PENDING_EVENT_FLUSH,
                    failed)));
        expectIOException(() -> interruptedPacks.purchase(
            new Random(200L),
            PURCHASED_AT));

        TransactionalStateStore recoveredStore = new TransactionalStateStore(
            directory,
            codec);
        CollectionState recovered = recoveredStore.loadHighestValid()
            .orElseThrow(AssertionError::new);
        assertEquals(startingPoints, recovered.getPoints());
        assertFalse(recovered.getPendingPackReveal().isPresent());

        PackPurchaseResult retry = new StandardPackService(
            catalogue,
            recoveredStore).purchase(
                new Random(200L),
                PURCHASED_AT.plusSeconds(1));
        assertEquals(
            startingPoints - StandardPackService.PRICE,
            retry.getState().getPoints());
        assertTrue(retry.getState().getPendingPackReveal().isPresent());
    }

    @Test
    public void committedRevealSurvivesCrashAndCannotAdvanceTwice()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-reveal-recovery-");
        SnapshotCodec codec = new SnapshotCodec();
        long startingPoints = StandardPackService.PRICE + 25_000L;
        CollectionState initial = PersistenceTestFixtures.state(
            catalogue,
            "reveal-recovery",
            startingPoints);
        TransactionalStateStore seed = new TransactionalStateStore(
            directory,
            codec);
        seed.save(initial, -1L);
        PackPurchaseResult purchase = new StandardPackService(
            catalogue,
            seed).purchase(new Random(300L), PURCHASED_AT);
        int position = purchase.getReveal().getNextUnrevealedPosition();

        AtomicBoolean failed = new AtomicBoolean();
        StandardPackService interruptedPacks = new StandardPackService(
            catalogue,
            new TransactionalStateStore(
                directory,
                codec,
                new JournalEventCodec(),
                failOnceAt(PersistenceCommitStage.AFTER_EVENT_COMMIT, failed)));
        interruptedPacks.revealCard(
            position,
            PURCHASED_AT.plusSeconds(10));

        TransactionalStateStore recoveredStore = new TransactionalStateStore(
            directory,
            codec);
        CollectionState recovered = recoveredStore.loadHighestValid()
            .orElseThrow(AssertionError::new);
        PendingPackReveal pending = recovered.getPendingPackReveal()
            .orElseThrow(AssertionError::new);
        assertTrue(pending.isRevealed(position));
        assertEquals(1, pending.getRevealedCount());
        assertEquals(
            startingPoints - StandardPackService.PRICE,
            recovered.getPoints());

        boolean duplicateBlocked = false;
        try
        {
            new StandardPackService(catalogue, recoveredStore).revealCard(
                position,
                PURCHASED_AT.plusSeconds(11));
        }
        catch (IllegalStateException expected)
        {
            duplicateBlocked = true;
        }
        assertTrue("The same card position cannot be revealed twice.",
            duplicateBlocked);
        assertEquals(1, recoveredStore.loadHighestValid()
            .orElseThrow(AssertionError::new)
            .getPendingPackReveal()
            .orElseThrow(AssertionError::new)
            .getRevealedCount());
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

    @FunctionalInterface
    private interface IoAction
    {
        void run() throws Exception;
    }
}
