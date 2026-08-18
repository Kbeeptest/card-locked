package com.cardrestricted.prototype;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.nexus.NexusExchangeCosts;
import com.cardrestricted.nexus.NexusExchangeResult;
import com.cardrestricted.nexus.NexusExchangeService;
import com.cardrestricted.pack.PackPurchaseResult;
import com.cardrestricted.pack.PendingPackReveal;
import com.cardrestricted.pack.StandardPackService;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.SnapshotCodec;
import com.cardrestricted.persistence.TransactionalStateStore;
import com.cardrestricted.starter.StarterRewardState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Transaction checks for the retained persistence, pack and Nexus systems. */
public final class StableCoreSystemsVerification
{
    private StableCoreSystemsVerification()
    {
    }

    public static void main(String[] args) throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        verifyPackTransaction(catalogue);
        verifyNexusTransaction(catalogue);
        verifyRecoveryGeneration(catalogue);
        System.out.println("Stable core systems verification passed.");
    }

    private static void verifyPackTransaction(CardCatalogue catalogue)
        throws Exception
    {
        Path directory = Files.createTempDirectory("card-locked-pack-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState initial = state(
            catalogue,
            StandardPackService.PRICE + 50_000L,
            0L);
        store.save(initial, -1L);

        StandardPackService packs = new StandardPackService(catalogue, store);
        PackPurchaseResult purchase = packs.purchase(
            new Random(17L),
            Instant.parse("2026-07-27T20:00:00Z"));
        require(
            purchase.getState().getPoints()
                == initial.getPoints() - StandardPackService.PRICE,
            "A pack purchase must deduct its price exactly once.");
        PendingPackReveal pending = purchase.getReveal();
        require(
            pending.getCardResults().size() == StandardPackService.CARD_COUNT,
            "A standard pack must commit the configured card count.");
        require(
            purchase.getState().getOwnedCardIds().containsAll(
                pending.getCardResults().stream()
                    .map(result -> result.getCardId())
                    .collect(java.util.stream.Collectors.toSet())),
            "Committed pack cards must already be owned before presentation.");

        for (int position = pending.getCardResults().size() - 1;
             position >= 0;
             position--)
        {
            packs.revealCard(
                position,
                Instant.parse("2026-07-27T20:01:00Z")
                    .plusSeconds(position));
        }
        CollectionState completed = store.loadHighestValid().orElseThrow();
        require(
            completed.getPendingPackReveal().isEmpty(),
            "Revealing every committed position must close the pending pack.");
        require(
            store.loadJournal().size()
                == 2 + StandardPackService.CARD_COUNT,
            "Creation, purchase and every reveal must be journalled once.");
    }

    private static void verifyNexusTransaction(CardCatalogue catalogue)
        throws Exception
    {
        Path directory = Files.createTempDirectory("card-locked-nexus-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        long cost = NexusExchangeCosts.forRarity(Rarity.COMMON);
        CollectionState initial = state(catalogue, 0L, cost * 2L);
        store.save(initial, -1L);

        NexusExchangeResult result = new NexusExchangeService(
            catalogue,
            store).exchange(
                Rarity.COMMON,
                new Random(23L),
                Instant.parse("2026-07-27T20:10:00Z"));
        require(
            result.getState().getShards() == cost,
            "One Nexus click must deduct one exchange cost.");
        require(
            result.getState().getOwnedCardIds().size() == 1,
            "One Nexus exchange must award exactly one missing card.");
        require(
            store.loadJournal().size() == 2,
            "One Nexus exchange must write exactly one mutation event.");
    }

    private static void verifyRecoveryGeneration(CardCatalogue catalogue)
        throws Exception
    {
        Path directory = Files.createTempDirectory("card-locked-recovery-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState initial = state(catalogue, 100L, 0L);
        store.save(initial, -1L);
        CollectionState updated = initial.withProgress(
            1L,
            200L,
            0L,
            Collections.emptySet(),
            Collections.emptySet());
        store.save(updated, 0L);

        Files.write(
            directory.resolve("current.snapshot"),
            new byte[]{1, 2, 3, 4});
        CollectionState recovered = store.loadHighestValid().orElseThrow();
        require(
            recovered.getRevision() == 0L,
            "A corrupt current snapshot must fall back to a valid recovery generation.");
        require(
            recovered.getPoints() == 100L,
            "Recovery must return the last valid persisted state.");
    }

    private static CollectionState state(
        CardCatalogue catalogue,
        long points,
        long shards)
    {
        return new CollectionState(
            UUID.randomUUID(),
            "verification-character",
            "Verification Player",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-07-27T19:00:00Z"),
            1,
            catalogue.getCatalogueVersion(),
            1,
            0L,
            points,
            shards,
            Collections.emptySet(),
            Collections.emptySet(),
            Set.of(StarterRewardState.POINTS_CHOICE_MARKER));
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
