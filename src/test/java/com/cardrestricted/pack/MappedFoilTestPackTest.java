package com.cardrestricted.pack;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.foil.FoilRewardGrant;
import com.cardrestricted.foil.FoilRewardKind;
import com.cardrestricted.foil.FoilRewardRegistry;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.SnapshotCodec;
import com.cardrestricted.persistence.TransactionalStateStore;
import com.cardrestricted.starter.StarterRewardState;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MappedFoilTestPackTest
{
    private static final Instant PURCHASED_AT =
        Instant.parse("2026-08-03T21:30:00Z");

    @Test
    public void tierPackContainsFiveDistinctMappedFoils() throws Exception
    {
        verifyPack(
            Set.of(FoilRewardKind.TIER_CASCADE),
            Collections.emptySet(),
            StandardPackService.TIER_FOIL_TEST_PACK_ID,
            (service, random) -> service.purchaseTierFoilTestPack(
                random, PURCHASED_AT));
    }

    @Test
    public void armourPackContainsFiveDistinctVerticalFoils() throws Exception
    {
        verifyPack(
            Set.of(FoilRewardKind.TIER_CASCADE),
            StandardPackService.ARMOUR_TIER_RULE_IDS,
            StandardPackService.ARMOUR_FOIL_TEST_PACK_ID,
            (service, random) -> service.purchaseArmourFoilTestPack(
                random, PURCHASED_AT));
    }

    @Test
    public void bossPackContainsFiveDistinctMappedFoils() throws Exception
    {
        verifyPack(
            Set.of(FoilRewardKind.SOURCE_UNIQUES),
            Collections.emptySet(),
            StandardPackService.BOSS_FOIL_TEST_PACK_ID,
            (service, random) -> service.purchaseBossFoilTestPack(
                random, PURCHASED_AT));
    }

    @Test
    public void itemRelationshipPackContainsFiveDistinctMappedFoils()
        throws Exception
    {
        verifyPack(
            StandardPackService.ITEM_RELATIONSHIP_KINDS,
            Collections.emptySet(),
            StandardPackService.INGREDIENT_FOIL_TEST_PACK_ID,
            (service, random) -> service.purchaseIngredientFoilTestPack(
                random, PURCHASED_AT));
    }

    @Test
    public void signaturePackContainsFiveDistinctSetFoils()
        throws Exception
    {
        verifyPack(
            Set.of(FoilRewardKind.SIGNATURE_SET),
            Collections.emptySet(),
            StandardPackService.SIGNATURE_FOIL_TEST_PACK_ID,
            (service, random) -> service.purchaseSignatureFoilTestPack(
                random, PURCHASED_AT));
    }

    @Test
    public void npcRelationshipPackContainsOnlyAcceptedNpcRelationships()
        throws Exception
    {
        verifyPack(
            StandardPackService.NPC_RELATIONSHIP_KINDS,
            Collections.emptySet(),
            StandardPackService.NPC_RELATIONSHIP_FOIL_TEST_PACK_ID,
            (service, random) ->
                service.purchaseNpcRelationshipFoilTestPack(
                    random, PURCHASED_AT));
    }

    @Test
    public void mappedTestPacksPreferSourcesNotAlreadyOwnedAsFoils()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        FoilRewardRegistry registry = FoilRewardRegistry.load(
            getClass().getClassLoader(), catalogue);
        List<String> mapped = registry.getSourceCardIdsForKind(
            FoilRewardKind.SOURCE_UNIQUES);
        Set<String> existingFoils = new HashSet<>(mapped.subList(0, 5));
        CollectionState initial = state(catalogue, existingFoils);
        TransactionalStateStore store = new TransactionalStateStore(
            Files.createTempDirectory("card-locked-mapped-foil-priority-"),
            new SnapshotCodec());
        store.save(initial, -1L);

        PackPurchaseResult purchase = new StandardPackService(
            catalogue, store).purchaseBossFoilTestPack(
                new Random(88204L), PURCHASED_AT);

        assertTrue(purchase.getReveal().getCardResults().stream()
            .map(PackCardResult::getCardId)
            .noneMatch(existingFoils::contains));
    }

    private void verifyPack(
        Set<FoilRewardKind> expectedKinds,
        Set<String> expectedRuleIds,
        String expectedPackId,
        Purchase purchase)
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        FoilRewardRegistry registry = FoilRewardRegistry.load(
            getClass().getClassLoader(), catalogue);
        CollectionState initial = state(catalogue, Collections.emptySet());
        TransactionalStateStore store = new TransactionalStateStore(
            Files.createTempDirectory("card-locked-mapped-foil-pack-"),
            new SnapshotCodec());
        store.save(initial, -1L);

        PackPurchaseResult result = purchase.purchase(
            new StandardPackService(catalogue, store),
            new Random(88200L + Math.abs(expectedPackId.hashCode())));
        List<PackCardResult> cards = result.getReveal().getCardResults();

        assertEquals(expectedPackId, result.getReveal().getPackId());
        assertEquals(StandardPackService.CARD_COUNT, cards.size());
        assertEquals(
            StandardPackService.CARD_COUNT,
            cards.stream().map(PackCardResult::getCardId).distinct().count());
        assertTrue(cards.stream().allMatch(PackCardResult::isFoil));
        assertEquals(
            initial.getPoints() - StandardPackService.MAPPED_FOIL_TEST_PRICE,
            result.getState().getPoints());

        for (PackCardResult card : cards)
        {
            assertTrue(result.getState().getFoilCardIds()
                .contains(card.getCardId()));
            List<FoilRewardGrant> grants = registry.getRewardsForSource(
                card.getCardId());
            assertTrue(grants.stream()
                .anyMatch(grant -> expectedKinds.contains(grant.getKind())
                    && (expectedRuleIds.isEmpty()
                        || expectedRuleIds.contains(grant.getRuleId()))));
        }
    }

    private static CollectionState state(
        CardCatalogue catalogue,
        Set<String> foilCardIds)
    {
        return new CollectionState(
            UUID.randomUUID(),
            "mapped-foil-test-character",
            "Mapped Foil Test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-08-03T21:00:00Z"),
            1,
            catalogue.getCatalogueVersion(),
            1,
            0L,
            1_000_000L,
            0L,
            foilCardIds,
            foilCardIds,
            Set.of(StarterRewardState.POINTS_CHOICE_MARKER));
    }

    @FunctionalInterface
    private interface Purchase
    {
        PackPurchaseResult purchase(
            StandardPackService service,
            Random random)
            throws Exception;
    }
}
