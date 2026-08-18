package com.cardrestricted.pack;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.domain.ActionType;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.SnapshotCodec;
import com.cardrestricted.persistence.TransactionalStateStore;
import com.cardrestricted.progression.ProgressionMilestonePolicy;
import com.cardrestricted.starter.StarterRewardState;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class ProgressionPackTest
{
    private static final Instant NOW =
        Instant.parse("2026-08-04T18:30:00Z");

    @Test
    public void freshProfileCanRedeemStarterPackAfterCurrentRarityRegrade()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        CollectionState fresh = new CollectionState(
            UUID.randomUUID(),
            "starter-pack-test",
            "Starter Pack Test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            NOW.minusSeconds(60),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            0L,
            0L,
            Set.of(),
            Set.of(),
            Set.of(StarterRewardState.PACK_CHOICE_MARKER));

        PackPurchaseResult result = service(catalogue, fresh)
            .redeemStarterPack(new Random(991L), NOW);

        assertEquals(5, result.getReveal().getCardResults().size());
        assertTrue(result.getState().getClaimedPointSourceIds().contains(
            StarterRewardState.PACK_REDEEMED_MARKER));
        for (PackCardResult cardResult : result.getReveal().getCardResults())
        {
            CardDefinition card = catalogue.requireCard(cardResult.getCardId());
            assertTrue(card.isFreeToPlay());
            assertTrue(card.getRarity() == Rarity.COMMON
                || card.getRarity() == Rarity.UNCOMMON);
        }
    }

    @Test
    public void uncommonPackRequires250UniqueActiveCards() throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        StandardPackService below = service(
            catalogue,
            state(catalogue, owned(catalogue, 249), 1_000_000L));
        try
        {
            below.purchaseUncommonPlusPack(new Random(1L), NOW);
            fail("Expected milestone rejection.");
        }
        catch (IllegalStateException expected)
        {
            assertTrue(expected.getMessage().contains("250 unique cards"));
        }

        PackPurchaseResult result = service(
            catalogue,
            state(catalogue, owned(catalogue, 250), 1_000_000L))
            .purchaseUncommonPlusPack(new Random(2L), NOW);
        assertEquals(StandardPackService.UNCOMMON_PLUS_PACK_ID,
            result.getReveal().getPackId());
    }

    @Test
    public void explorerPackContainsOnlyNoncombatNpcs()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        PackPurchaseResult result = service(
            catalogue,
            state(catalogue, owned(catalogue, 500), 1_000_000L))
            .purchaseExplorerPack(new Random(3L), NOW);
        for (PackCardResult cardResult : result.getReveal().getCardResults())
        {
            CardDefinition card = catalogue.requireCard(cardResult.getCardId());
            assertEquals(CardType.NPC, card.getCardType());
            assertFalse(card.getPermissions().contains(ActionType.NPC_ATTACK));
        }
    }

    @Test
    public void adventurePackContainsOnlyAttackableNpcs()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        PackPurchaseResult result = service(
            catalogue,
            state(catalogue, owned(catalogue, 1_250), 1_000_000L))
            .purchaseAdventurePack(new Random(33L), NOW);
        for (PackCardResult cardResult : result.getReveal().getCardResults())
        {
            CardDefinition card = catalogue.requireCard(cardResult.getCardId());
            assertEquals(CardType.NPC, card.getCardType());
            assertTrue(card.getPermissions().contains(ActionType.NPC_ATTACK));
        }
    }

    @Test
    public void initiateRewardIsOneTimeAndEveryCardIsLowTierFoil()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        TransactionalStateStore store = store(
            state(catalogue, owned(catalogue, 500), 1_000_000L));
        StandardPackService service = new StandardPackService(catalogue, store);
        PackPurchaseResult result = service.redeemInitiateFoilPack(
            new Random(4L), NOW);
        assertTrue(result.getState().getClaimedPointSourceIds().contains(
            ProgressionMilestonePolicy.INITIATE_FOIL_MARKER));
        for (PackCardResult card : result.getReveal().getCardResults())
        {
            assertTrue(card.isFoil());
            Rarity rarity = catalogue.requireCard(card.getCardId()).getRarity();
            assertTrue(rarity == Rarity.COMMON || rarity == Rarity.UNCOMMON);
        }
        for (int index = 0; index < 5; index++)
        {
            service.revealNext(NOW.plusSeconds(index + 1L));
        }
        try
        {
            service.redeemInitiateFoilPack(new Random(5L), NOW.plusSeconds(10));
            fail("Expected one-time redemption rejection.");
        }
        catch (IllegalStateException expected)
        {
            assertTrue(expected.getMessage().contains("already been redeemed"));
        }
    }

    @Test
    public void heroPackHasThreeFoilAndTwoNormalRareCards()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        PackPurchaseResult result = service(
            catalogue,
            state(catalogue, owned(catalogue, 1_500), 1_000_000L))
            .redeemHeroPack(new Random(6L), NOW);
        List<PackCardResult> cards = result.getReveal().getCardResults();
        assertEquals(5, cards.size());
        for (int index = 0; index < cards.size(); index++)
        {
            assertEquals(Rarity.RARE,
                catalogue.requireCard(cards.get(index).getCardId()).getRarity());
            assertEquals(index < 3, cards.get(index).isFoil());
        }
    }

    @Test
    public void nexusCacheAwardsConfiguredRange() throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        NexusCachePurchaseResult result = service(
            catalogue,
            state(catalogue, owned(catalogue, 1_750), 1_000_000L))
            .purchaseNexusCache(new Random(7L), NOW);
        assertTrue(result.getShardsAwarded()
            >= StandardPackService.NEXUS_CACHE_MIN_SHARDS);
        assertTrue(result.getShardsAwarded()
            <= StandardPackService.NEXUS_CACHE_MAX_SHARDS);
        assertEquals(
            1_000_000L - StandardPackService.NEXUS_CACHE_PRICE,
            result.getState().getPoints());
    }

    @Test
    public void collectorPackContainsEveryTierAndAtLeastOneNewCard()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Set<String> initialOwned = owned(catalogue, 2_500);
        PackPurchaseResult result = service(
            catalogue,
            state(catalogue, initialOwned, 1_000_000L))
            .purchaseCollectorPack(new Random(8L), NOW);
        List<PackCardResult> cards = result.getReveal().getCardResults();
        assertEquals(List.of(
            Rarity.RARE,
            Rarity.EPIC,
            Rarity.LEGENDARY,
            Rarity.MYTHIC,
            Rarity.GODLY),
            cards.stream()
                .map(card -> catalogue.requireCard(card.getCardId()).getRarity())
                .collect(java.util.stream.Collectors.toList()));
        assertTrue(cards.stream().anyMatch(card -> !card.isDuplicate()));
    }

    private static StandardPackService service(
        CardCatalogue catalogue,
        CollectionState state)
        throws Exception
    {
        return new StandardPackService(catalogue, store(state));
    }

    private static TransactionalStateStore store(CollectionState state)
        throws Exception
    {
        TransactionalStateStore store = new TransactionalStateStore(
            Files.createTempDirectory("card-locked-progression-pack-"),
            new SnapshotCodec());
        store.save(state, -1L);
        return store;
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
            throw new IllegalStateException("Catalogue is smaller than test milestone.");
        }
        return owned;
    }

    private static CollectionState state(
        CardCatalogue catalogue,
        Set<String> owned,
        long points)
    {
        return new CollectionState(
            UUID.randomUUID(),
            "progression-pack-test",
            "Progression Pack Test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            NOW.minusSeconds(3600),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            points,
            0L,
            owned,
            Set.of(),
            Set.of(StarterRewardState.POINTS_CHOICE_MARKER));
    }
}
