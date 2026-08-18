package com.cardrestricted.persistence;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.nexus.NexusExchangeCosts;
import com.cardrestricted.nexus.NexusExchangeResult;
import com.cardrestricted.nexus.NexusExchangeService;
import com.cardrestricted.pack.NexusCachePurchaseResult;
import com.cardrestricted.pack.PackPurchaseResult;
import com.cardrestricted.pack.PackRevealResult;
import com.cardrestricted.pack.PendingRevealException;
import com.cardrestricted.pack.StandardPackService;
import com.cardrestricted.points.DuplicatePointAwardException;
import com.cardrestricted.points.F2pNoncombatXpPolicy;
import com.cardrestricted.points.NoncombatSkill;
import com.cardrestricted.points.NoncombatXpLedgerService;
import com.cardrestricted.points.NoncombatXpObservation;
import com.cardrestricted.points.NoncombatXpProcessResult;
import com.cardrestricted.points.NoncombatXpResultStatus;
import com.cardrestricted.points.PointAward;
import com.cardrestricted.points.PointSourceType;
import com.cardrestricted.points.PointsLedgerService;
import com.cardrestricted.starter.StarterRewardState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies that a mutation committed to the journal is reported as successful
 * even when an I/O failure is injected immediately after the durable commit.
 * This prevents callers from retrying and duplicating points, packs, reveals,
 * shards or Nexus exchanges.
 */
public final class CommittedMutationRecoveryTest
{
    private static final Instant NOW =
        Instant.parse("2026-08-05T01:00:00Z");

    @Test
    public void committedPointAwardReturnsSuccessAndCannotRepeat()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        StoreFixture fixture = fixture(
            catalogue,
            state(catalogue, Collections.emptySet(), 1_000L, 0L),
            "points");
        PointsLedgerService service = new PointsLedgerService(
            fixture.interruptedStore());
        PointAward award = new PointAward(
            "selftest:level:42",
            PointSourceType.SKILL_LEVEL,
            750L,
            NOW);

        CollectionState result = service.award(award);
        assertEquals(1_750L, result.getPoints());
        assertEquals(1L, result.getRevision());
        assertTrue(result.getClaimedPointSourceIds().contains(
            award.getSourceId()));

        TransactionalStateStore recovered = fixture.cleanStore();
        assertEquivalent(result, recovered.loadHighestValid()
            .orElseThrow(AssertionError::new));
        try
        {
            new PointsLedgerService(recovered).award(award);
            fail("A committed point source must not be awarded twice.");
        }
        catch (DuplicatePointAwardException expected)
        {
            assertTrue(expected.getMessage().contains(award.getSourceId()));
        }
    }

    @Test
    public void committedXpWatermarkReturnsSuccessAndCannotReplay()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        StoreFixture fixture = fixture(
            catalogue,
            state(catalogue, Collections.emptySet(), 100L, 0L),
            "xp");
        NoncombatXpObservation observation = new NoncombatXpObservation(
            NoncombatSkill.MINING,
            0L,
            1_000L,
            NOW);
        NoncombatXpLedgerService service = new NoncombatXpLedgerService(
            fixture.interruptedStore(),
            new F2pNoncombatXpPolicy());

        NoncombatXpProcessResult result = service.process(observation);
        assertEquals(NoncombatXpResultStatus.AWARDED, result.getStatus());
        assertEquals(100L, result.getPointsAwarded());
        assertEquals(200L, result.getState().getPoints());
        assertEquals(Long.valueOf(1_000L),
            result.getState().getNoncombatXpWatermarks().get("MINING"));

        NoncombatXpProcessResult duplicate = new NoncombatXpLedgerService(
            fixture.cleanStore(),
            new F2pNoncombatXpPolicy()).process(observation);
        assertEquals(NoncombatXpResultStatus.DUPLICATE,
            duplicate.getStatus());
        assertEquals(0L, duplicate.getPointsAwarded());
        assertEquals(200L, duplicate.getState().getPoints());
        assertEquals(1L, duplicate.getState().getRevision());
    }

    @Test
    public void xpOnlyCommitsCompleteThousandXpBatches()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        StoreFixture fixture = fixture(
            catalogue,
            state(catalogue, Collections.emptySet(), 100L, 0L),
            "xp-batch");
        NoncombatXpLedgerService service = new NoncombatXpLedgerService(
            fixture.cleanStore(),
            new F2pNoncombatXpPolicy());

        NoncombatXpProcessResult belowThreshold = service.process(
            new NoncombatXpObservation(
                NoncombatSkill.MINING, 0L, 999L, NOW));
        assertEquals(NoncombatXpResultStatus.ACCUMULATED,
            belowThreshold.getStatus());
        assertEquals(0L, belowThreshold.getPointsAwarded());
        assertEquals(0L, belowThreshold.getState().getRevision());
        assertFalse(belowThreshold.getState().getNoncombatXpWatermarks()
            .containsKey("MINING"));

        NoncombatXpProcessResult twoBatches = service.process(
            new NoncombatXpObservation(
                NoncombatSkill.MINING, 0L, 2_500L, NOW.plusSeconds(1)));
        assertEquals(200L, twoBatches.getPointsAwarded());
        assertEquals(300L, twoBatches.getState().getPoints());
        assertEquals(2_000L, twoBatches.getXpProcessed());
        assertEquals(Long.valueOf(2_000L),
            twoBatches.getState().getNoncombatXpWatermarks().get("MINING"));
        assertEquals(1L, twoBatches.getState().getRevision());
    }

    @Test
    public void committedPackPurchaseReturnsSuccessAndChargesOnce()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        long startingPoints = StandardPackService.PRICE;
        StoreFixture fixture = fixture(
            catalogue,
            state(catalogue, Collections.emptySet(), startingPoints, 0L),
            "pack-purchase");
        StandardPackService service = new StandardPackService(
            catalogue,
            fixture.interruptedStore());

        PackPurchaseResult result = service.purchase(
            new Random(10L), NOW);
        assertEquals(0L, result.getState().getPoints());
        assertTrue(result.getState().getPendingPackReveal().isPresent());
        assertEquals(1L, result.getState().getRevision());

        TransactionalStateStore recovered = fixture.cleanStore();
        assertEquivalent(result.getState(), recovered.loadHighestValid()
            .orElseThrow(AssertionError::new));
        try
        {
            new StandardPackService(catalogue, recovered).purchase(
                new Random(10L), NOW.plusSeconds(1));
            fail("A pending committed pack must prevent a second purchase.");
        }
        catch (PendingRevealException expected)
        {
            assertEquals(0L, recovered.loadHighestValid()
                .orElseThrow(AssertionError::new).getPoints());
        }
    }

    @Test
    public void committedPackRevealReturnsSuccessAndAdvancesOnce()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory(
            "card-locked-selftest-pack-reveal-");
        SnapshotCodec codec = new SnapshotCodec();
        TransactionalStateStore seed = new TransactionalStateStore(
            directory, codec);
        seed.save(state(
            catalogue,
            Collections.emptySet(),
            StandardPackService.PRICE,
            0L), -1L);
        PackPurchaseResult purchase = new StandardPackService(
            catalogue, seed).purchase(new Random(20L), NOW);
        int position = purchase.getReveal().getNextUnrevealedPosition();

        AtomicBoolean failed = new AtomicBoolean();
        TransactionalStateStore interrupted = new TransactionalStateStore(
            directory,
            codec,
            new JournalEventCodec(),
            failOnceAfterCommit(failed));
        PackRevealResult reveal = new StandardPackService(
            catalogue, interrupted).revealCard(
                position, NOW.plusSeconds(1));
        assertEquals(1, reveal.getRevealNumber());
        assertEquals(2L, reveal.getState().getRevision());
        assertTrue(reveal.getState().getPendingPackReveal()
            .orElseThrow(AssertionError::new).isRevealed(position));

        TransactionalStateStore recovered = new TransactionalStateStore(
            directory, codec);
        try
        {
            new StandardPackService(catalogue, recovered).revealCard(
                position, NOW.plusSeconds(2));
            fail("The same committed reveal position must not advance twice.");
        }
        catch (IllegalStateException expected)
        {
            assertEquals(1, recovered.loadHighestValid()
                .orElseThrow(AssertionError::new)
                .getPendingPackReveal()
                .orElseThrow(AssertionError::new)
                .getRevealedCount());
        }
    }

    @Test
    public void committedNexusCacheReturnsSuccessAndChargesOnce()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        StoreFixture fixture = fixture(
            catalogue,
            state(
                catalogue,
                owned(catalogue, 1_750),
                StandardPackService.NEXUS_CACHE_PRICE,
                50L),
            "nexus-cache");
        NexusCachePurchaseResult result = new StandardPackService(
            catalogue,
            fixture.interruptedStore()).purchaseNexusCache(
                new Random(30L), NOW);
        assertEquals(0L, result.getState().getPoints());
        assertEquals(50L + result.getShardsAwarded(),
            result.getState().getShards());
        assertEquals(1L, result.getState().getRevision());

        TransactionalStateStore recovered = fixture.cleanStore();
        assertEquivalent(result.getState(), recovered.loadHighestValid()
            .orElseThrow(AssertionError::new));
        try
        {
            new StandardPackService(catalogue, recovered)
                .purchaseNexusCache(new Random(30L), NOW.plusSeconds(1));
            fail("The committed cache price must not be charged twice.");
        }
        catch (RuntimeException expected)
        {
            assertEquals(0L, recovered.loadHighestValid()
                .orElseThrow(AssertionError::new).getPoints());
            assertEquals(result.getState().getShards(),
                recovered.loadHighestValid()
                    .orElseThrow(AssertionError::new).getShards());
        }
    }

    @Test
    public void committedNexusExchangeReturnsSuccessAndSpendsOnce()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        long cost = NexusExchangeCosts.forRarity(Rarity.COMMON);
        StoreFixture fixture = fixture(
            catalogue,
            state(catalogue, Collections.emptySet(), 0L, cost),
            "nexus-exchange");
        NexusExchangeResult result = new NexusExchangeService(
            catalogue,
            fixture.interruptedStore()).exchange(
                Rarity.COMMON, new Random(40L), NOW);
        assertEquals(0L, result.getState().getShards());
        assertEquals(1, result.getState().getOwnedCardIds().size());
        assertEquals(1L, result.getState().getRevision());

        TransactionalStateStore recovered = fixture.cleanStore();
        assertEquivalent(result.getState(), recovered.loadHighestValid()
            .orElseThrow(AssertionError::new));
        try
        {
            new NexusExchangeService(catalogue, recovered).exchange(
                Rarity.COMMON, new Random(41L), NOW.plusSeconds(1));
            fail("A committed Nexus spend must not be repeated.");
        }
        catch (IllegalStateException expected)
        {
            CollectionState finalState = recovered.loadHighestValid()
                .orElseThrow(AssertionError::new);
            assertEquals(0L, finalState.getShards());
            assertEquals(1, finalState.getOwnedCardIds().size());
            assertEquals(1L, finalState.getRevision());
        }
    }

    private static StoreFixture fixture(
        CardCatalogue catalogue,
        CollectionState initial,
        String label)
        throws Exception
    {
        Path directory = Files.createTempDirectory(
            "card-locked-selftest-" + label + "-");
        SnapshotCodec codec = new SnapshotCodec();
        new TransactionalStateStore(directory, codec).save(initial, -1L);
        return new StoreFixture(directory, codec);
    }

    private static CollectionState state(
        CardCatalogue catalogue,
        Set<String> owned,
        long points,
        long shards)
    {
        return new CollectionState(
            UUID.randomUUID(),
            "cl893-self-test",
            "CL893 Self Test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            NOW.minusSeconds(60),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            points,
            shards,
            owned,
            Collections.emptySet(),
            Set.of(StarterRewardState.POINTS_CHOICE_MARKER));
    }

    private static Set<String> owned(CardCatalogue catalogue, int count)
    {
        LinkedHashSet<String> owned = new LinkedHashSet<>();
        for (CardDefinition card : catalogue.getCards())
        {
            if (com.cardrestricted.progression.ProgressionRewardCardPolicy
                .isTrackOnlyReward(card.getCardId()))
            {
                continue;
            }
            owned.add(card.getCardId());
            if (owned.size() == count)
            {
                break;
            }
        }
        if (owned.size() != count)
        {
            throw new AssertionError("Catalogue has fewer than " + count
                + " active cards.");
        }
        return owned;
    }

    private static PersistenceFaultInjector failOnceAfterCommit(
        AtomicBoolean failed)
    {
        return stage -> {
            if (stage == PersistenceCommitStage.AFTER_EVENT_COMMIT
                && failed.compareAndSet(false, true))
            {
                throw new IOException("Injected committed-state failure.");
            }
        };
    }

    private static void assertEquivalent(
        CollectionState expected,
        CollectionState actual)
        throws IOException
    {
        assertTrue("Recovered state must exactly match the returned state.",
            CommittedStateRecovery.equivalent(expected, actual));
    }

    private static final class StoreFixture
    {
        private final Path directory;
        private final SnapshotCodec codec;
        private final AtomicBoolean failed = new AtomicBoolean();

        private StoreFixture(Path directory, SnapshotCodec codec)
        {
            this.directory = directory;
            this.codec = codec;
        }

        private TransactionalStateStore interruptedStore()
        {
            return new TransactionalStateStore(
                directory,
                codec,
                new JournalEventCodec(),
                failOnceAfterCommit(failed));
        }

        private TransactionalStateStore cleanStore()
        {
            return new TransactionalStateStore(directory, codec);
        }
    }
}
