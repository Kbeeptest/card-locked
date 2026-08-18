package com.cardrestricted.prototype;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.pack.FoilRollPolicy;
import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.pack.PackPurchaseResult;
import com.cardrestricted.pack.StandardPackService;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.SnapshotCodec;
import com.cardrestricted.persistence.TransactionalStateStore;
import com.cardrestricted.starter.StarterRewardState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Foil probability, persistence, test-pack and testing-balance checks. */
public final class Phase073FoilVerification
{
    private Phase073FoilVerification()
    {
    }

    public static void main(String[] args) throws Exception
    {
        verifyFoilProbabilityBoundary();
        verifyFoilTestPackAndPersistence();
        verifyPremiumFoilTestPack();
        verifyTestingBalances();
        System.out.println("Phase 0.74 foil presentation and premium test-pack verification passed.");
    }

    private static void verifyFoilProbabilityBoundary()
    {
        Random alwaysFoil = new Random()
        {
            private static final long serialVersionUID = 1L;

            @Override
            public int nextInt(int bound)
            {
                require(bound == FoilRollPolicy.DENOMINATOR,
                    "Foil rolls must use the configured denominator.");
                return 0;
            }
        };
        Random neverFoil = new Random()
        {
            private static final long serialVersionUID = 1L;

            @Override
            public int nextInt(int bound)
            {
                require(bound == FoilRollPolicy.DENOMINATOR,
                    "Foil rolls must use the configured denominator.");
                return bound - 1;
            }
        };
        require(FoilRollPolicy.roll(alwaysFoil),
            "Roll zero must produce a foil.");
        require(!FoilRollPolicy.roll(neverFoil),
            "A non-zero roll must not produce a foil.");
        require(FoilRollPolicy.DENOMINATOR == 100,
            "Production foil odds must be 1 in 100.");
    }

    private static void verifyFoilTestPackAndPersistence() throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory("card-locked-foil-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState initial = state(
            catalogue,
            1_000_000L,
            1_000_000L);
        store.save(initial, -1L);

        PackPurchaseResult purchase = new StandardPackService(
            catalogue,
            store).purchaseFoilTestPack(
                new Random(7301L),
                Instant.parse("2026-07-28T18:00:00Z"));

        require(purchase.getReveal().getCardResults().size() == 5,
            "The temporary foil test pack must contain five cards.");
        Set<Rarity> rarities = purchase.getReveal().getCardResults().stream()
            .map(PackCardResult::getCardId)
            .map(catalogue::requireCard)
            .map(card -> card.getRarity())
            .collect(Collectors.toSet());
        require(rarities.equals(EnumSet.of(
            Rarity.COMMON,
            Rarity.UNCOMMON,
            Rarity.RARE,
            Rarity.EPIC,
            Rarity.LEGENDARY)),
            "The temporary foil test pack must contain five distinct configured rarities.");

        Set<String> foilResults = purchase.getReveal().getCardResults().stream()
            .filter(PackCardResult::isFoil)
            .map(PackCardResult::getCardId)
            .collect(Collectors.toSet());
        require(!foilResults.isEmpty(),
            "The temporary foil test pack must guarantee at least one foil.");
        require(purchase.getState().getFoilCardIds().containsAll(foilResults),
            "Committed foil results must persist as foil ownership.");

        byte[] encoded = new SnapshotCodec().encode(purchase.getState());
        CollectionState decoded = new SnapshotCodec().decode(encoded);
        require(decoded.getFoilCardIds().containsAll(foilResults),
            "Snapshot round-tripping must preserve foil ownership.");
        require(decoded.getPendingPackReveal().orElseThrow()
                .getCardResults().stream()
                .filter(PackCardResult::isFoil)
                .map(PackCardResult::getCardId)
                .collect(Collectors.toSet())
                .equals(foilResults),
            "Snapshot round-tripping must preserve pending foil flags.");
    }

    private static void verifyPremiumFoilTestPack() throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Path directory = Files.createTempDirectory("card-locked-premium-foil-");
        TransactionalStateStore store = new TransactionalStateStore(
            directory,
            new SnapshotCodec());
        CollectionState initial = state(
            catalogue,
            1_000_000L,
            1_000_000L);
        store.save(initial, -1L);

        PackPurchaseResult purchase = new StandardPackService(
            catalogue,
            store).purchasePremiumFoilTestPack(
                new Random(7401L),
                Instant.parse("2026-07-28T19:00:00Z"));

        require(purchase.getReveal().getCardResults().size() == 5,
            "The premium foil test pack must contain five cards.");
        require(purchase.getReveal().getCardResults().stream()
                .map(PackCardResult::getCardId)
                .map(catalogue::requireCard)
                .allMatch(card ->
                    card.getRarity().ordinal() >= Rarity.LEGENDARY.ordinal()),
            "The premium foil test pack must contain only Legendary, Mythic or Godly cards.");
        require(purchase.getReveal().getCardResults().stream()
                .anyMatch(PackCardResult::isFoil),
            "The premium foil test pack must guarantee at least one foil.");
    }

    private static void verifyTestingBalances()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        CollectionState initial = state(catalogue, 20L, 30L);
        CollectionState toppedUp = initial.withTestingBalances(
            1_000_000L,
            1_000_000L);
        require(toppedUp.getPoints() == 1_000_000L,
            "Testing mode must provide one million points.");
        require(toppedUp.getShards() == 1_000_000L,
            "Testing mode must provide one million shards.");
        require(toppedUp.getRevision() == initial.getRevision() + 1,
            "Testing balance application must be a single persisted mutation.");
    }

    private static CollectionState state(
        CardCatalogue catalogue,
        long points,
        long shards)
    {
        return new CollectionState(
            UUID.randomUUID(),
            "foil-verification-character",
            "Foil Verification",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-07-28T17:00:00Z"),
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
