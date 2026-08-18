package com.cardrestricted.pack;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.SnapshotCodec;
import com.cardrestricted.persistence.TransactionalStateStore;
import com.cardrestricted.progression.ProgressionMilestonePolicy;
import com.cardrestricted.starter.StarterRewardState;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Exercises every one-time progression reward through claim, reveal, reload and replay. */
public final class MilestoneRewardIdempotencyMatrixTest
{
    private static final Instant NOW =
        Instant.parse("2026-08-05T01:30:00Z");

    @Test
    public void everyMilestonePackRemainsOneTimeAcrossReload()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        MilestoneCase[] cases = {
            new MilestoneCase(
                "initiate",
                ProgressionMilestonePolicy.INITIATE_FOIL_PACK,
                ProgressionMilestonePolicy.INITIATE_FOIL_MARKER,
                (service, random, at) ->
                    service.redeemInitiateFoilPack(random, at)),
            new MilestoneCase(
                "hero",
                ProgressionMilestonePolicy.HERO_PACK,
                ProgressionMilestonePolicy.HERO_PACK_MARKER,
                (service, random, at) -> service.redeemHeroPack(random, at)),
            new MilestoneCase(
                "noble",
                ProgressionMilestonePolicy.NOBLE_PACK,
                ProgressionMilestonePolicy.NOBLE_PACK_MARKER,
                (service, random, at) -> service.redeemNoblePack(random, at)),
            new MilestoneCase(
                "legend",
                ProgressionMilestonePolicy.LEGEND_PACK,
                ProgressionMilestonePolicy.LEGEND_PACK_MARKER,
                (service, random, at) -> service.redeemLegendPack(random, at)),
            new MilestoneCase(
                "mythical",
                ProgressionMilestonePolicy.MYTHICAL_PACK,
                ProgressionMilestonePolicy.MYTHICAL_PACK_MARKER,
                (service, random, at) ->
                    service.redeemMythicalPack(random, at)),
            new MilestoneCase(
                "gods",
                ProgressionMilestonePolicy.GODS_PACK,
                ProgressionMilestonePolicy.GODS_PACK_MARKER,
                (service, random, at) -> service.redeemGodsPack(random, at))
        };

        for (int caseIndex = 0; caseIndex < cases.length; caseIndex++)
        {
            MilestoneCase milestone = cases[caseIndex];
            java.nio.file.Path directory = Files.createTempDirectory(
                "card-locked-milestone-" + milestone.name + "-");
            TransactionalStateStore store = new TransactionalStateStore(
                directory,
                new SnapshotCodec());
            CollectionState initial = state(
                catalogue,
                owned(catalogue, milestone.threshold));
            store.save(initial, -1L);
            StandardPackService service = new StandardPackService(
                catalogue, store);

            PackPurchaseResult purchase = milestone.redeemer.redeem(
                service,
                new Random(1_000L + caseIndex),
                NOW.plusSeconds(caseIndex * 20L));
            assertTrue(milestone.name + " marker missing after redemption",
                purchase.getState().getClaimedPointSourceIds().contains(
                    milestone.marker));
            assertEquals(milestone.name + " must not charge points",
                initial.getPoints(), purchase.getState().getPoints());
            assertEquals(5, purchase.getReveal().getCardResults().size());

            while (store.loadHighestValid()
                .orElseThrow(AssertionError::new)
                .getPendingPackReveal().isPresent())
            {
                service.revealNext(NOW.plusSeconds(100L + caseIndex));
            }

            TransactionalStateStore reloaded = new TransactionalStateStore(
                directory, new SnapshotCodec());
            CollectionState finalState = reloaded.loadHighestValid()
                .orElseThrow(AssertionError::new);
            assertFalse(finalState.getPendingPackReveal().isPresent());
            assertTrue(finalState.getClaimedPointSourceIds().contains(
                milestone.marker));
            long finalRevision = finalState.getRevision();
            long finalPoints = finalState.getPoints();
            long finalShards = finalState.getShards();
            int finalOwned = finalState.getOwnedCardIds().size();
            int finalFoils = finalState.getFoilCardIds().size();

            try
            {
                milestone.redeemer.redeem(
                    new StandardPackService(catalogue, reloaded),
                    new Random(2_000L + caseIndex),
                    NOW.plusSeconds(1_000L + caseIndex));
                fail(milestone.name + " milestone reward was replayable.");
            }
            catch (IllegalStateException expected)
            {
                assertTrue(expected.getMessage().toLowerCase(
                    java.util.Locale.ROOT).contains("already"));
            }

            CollectionState afterReplay = reloaded.loadHighestValid()
                .orElseThrow(AssertionError::new);
            assertEquals(finalRevision, afterReplay.getRevision());
            assertEquals(finalPoints, afterReplay.getPoints());
            assertEquals(finalShards, afterReplay.getShards());
            assertEquals(finalOwned, afterReplay.getOwnedCardIds().size());
            assertEquals(finalFoils, afterReplay.getFoilCardIds().size());
        }
    }

    private static CollectionState state(
        CardCatalogue catalogue,
        Set<String> owned)
    {
        return new CollectionState(
            UUID.randomUUID(),
            "milestone-idempotency",
            "Milestone Idempotency",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            NOW.minusSeconds(60),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            1_000_000L,
            0L,
            owned,
            Collections.emptySet(),
            Set.of(StarterRewardState.POINTS_CHOICE_MARKER));
    }

    private static Set<String> owned(CardCatalogue catalogue, int count)
    {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (CardDefinition card : catalogue.getCards())
        {
            if (com.cardrestricted.progression.ProgressionRewardCardPolicy
                .isTrackOnlyReward(card.getCardId()))
            {
                continue;
            }
            result.add(card.getCardId());
            if (result.size() == count)
            {
                return result;
            }
        }
        throw new AssertionError("Catalogue did not contain " + count
            + " active cards.");
    }

    private static final class MilestoneCase
    {
        private final String name;
        private final int threshold;
        private final String marker;
        private final Redeemer redeemer;

        private MilestoneCase(
            String name,
            int threshold,
            String marker,
            Redeemer redeemer)
        {
            this.name = name;
            this.threshold = threshold;
            this.marker = marker;
            this.redeemer = redeemer;
        }
    }

    @FunctionalInterface
    private interface Redeemer
    {
        PackPurchaseResult redeem(
            StandardPackService service,
            Random random,
            Instant at)
            throws Exception;
    }
}
