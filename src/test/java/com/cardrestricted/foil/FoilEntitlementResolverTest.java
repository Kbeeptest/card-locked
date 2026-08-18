package com.cardrestricted.foil;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.collection.progress.CollectionProgressSnapshot;
import com.cardrestricted.collection.progress.CollectionProgressService;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.runelite.InteractionFamilyIndex;
import com.cardrestricted.runelite.SimpleRestrictionService;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FoilEntitlementResolverTest
{
    private CardCatalogue catalogue;
    private FoilRewardRegistry registry;
    private FoilEntitlementResolver resolver;

    @Before
    public void setUp()
    {
        catalogue = MembersCatalogue.create();
        registry = FoilRewardRegistry.load(
            FoilEntitlementResolverTest.class.getClassLoader(),
            catalogue);
        resolver = new FoilEntitlementResolver(catalogue, registry);
    }

    @Test
    public void mithrilAxeFoilUnlocksOnlyLowerReviewedTiers()
    {
        String source = "item.mithril_axe.1355";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertEquals(Set.of(source), snapshot.getOwnedCardIds());
        assertTrue(snapshot.isDerivedUnlocked("item.bronze_axe"));
        assertTrue(snapshot.isDerivedUnlocked("item.iron_axe.1349"));
        assertTrue(snapshot.isDerivedUnlocked("item.steel_axe.1353"));
        assertTrue(snapshot.isDerivedUnlocked("item.black_axe.1361"));
        assertFalse(snapshot.isDerivedUnlocked("item.adamant_axe.1357"));
        assertFalse(snapshot.isDerivedUnlocked("item.rune_axe.1359"));
    }

    @Test
    public void ordinaryOwnershipDoesNotCreateFoilEntitlements()
    {
        String source = "item.mithril_axe.1355";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Collections.emptySet());

        assertTrue(snapshot.getDerivedCardIds().isEmpty());
        assertEquals(Set.of(source), snapshot.getUsableCardIds());
    }

    @Test
    public void derivedTargetsDoNotRecursivelyActAsFoilSources()
    {
        String source = "item.rune-platebody";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertTrue(snapshot.isDerivedUnlocked("item.adamant-platebody"));
        assertTrue(snapshot.isDerivedUnlocked("item.mithril-platebody"));
        assertFalse(snapshot.isDerivedUnlocked("item.rune-full-helm"));
        assertFalse(snapshot.isDerivedUnlocked("item.adamant-full-helm"));
    }

    @Test
    public void adamantFullHelmFoilUnlocksOnlyLowerFullHelmTiers()
    {
        String source = "item.adamant-full-helm";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertTrue(snapshot.isDerivedUnlocked("item.bronze_full_helm"));
        assertTrue(snapshot.isDerivedUnlocked("item.iron_full_helm"));
        assertTrue(snapshot.isDerivedUnlocked("item.steel-full-helm"));
        assertTrue(snapshot.isDerivedUnlocked("item.black_full_helm.1165"));
        assertTrue(snapshot.isDerivedUnlocked("item.mithril-full-helm"));
        assertFalse(snapshot.isDerivedUnlocked("item.adamant-platebody"));
        assertFalse(snapshot.isDerivedUnlocked("item.adamant-platelegs"));
        assertFalse(snapshot.isDerivedUnlocked("item.rune-full-helm"));
    }

    @Test
    public void graardorFoilUnlocksOnlyDirectUnsharedDrops()
    {
        String source = "npc.general_graardor.2215";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertTrue(snapshot.isDerivedUnlocked("item.bandos_chestplate.11832"));
        assertTrue(snapshot.isDerivedUnlocked("item.bandos_tassets.11834"));
        assertTrue(snapshot.isDerivedUnlocked("item.bandos_boots.11836"));
        assertTrue(snapshot.isDerivedUnlocked("item.bandos_hilt.11812"));
        assertFalse(snapshot.isDerivedUnlocked("item.godsword_shard_1.11818"));
        assertFalse(snapshot.isDerivedUnlocked("item.bandos_godsword.11804"));
    }

    @Test
    public void bossComponentsDoNotRecursivelyGrantCompletedProducts()
    {
        String source = "npc.cerberus.5862";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertTrue(snapshot.isDerivedUnlocked("item.primordial_crystal.13231"));
        assertTrue(snapshot.isDerivedUnlocked("item.pegasian_crystal.13229"));
        assertTrue(snapshot.isDerivedUnlocked("item.eternal_crystal.13227"));
        assertFalse(snapshot.isDerivedUnlocked("item.primordial_boots.13239"));
        assertFalse(snapshot.isDerivedUnlocked("item.pegasian_boots.13237"));
        assertFalse(snapshot.isDerivedUnlocked("item.eternal_boots.13235"));
    }

    @Test
    public void alchemicalHydraFoilGrantsRawDropsNotFinishedUpgrades()
    {
        String source = "npc.alchemical_hydra.8615";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertTrue(snapshot.isDerivedUnlocked("item.hydra_tail.22988"));
        assertTrue(snapshot.isDerivedUnlocked("item.hydra_leather.22983"));
        assertTrue(snapshot.isDerivedUnlocked("item.hydra_s_claw.22966"));
        assertFalse(snapshot.isDerivedUnlocked("item.dragon_hunter_lance.22978"));
        assertFalse(snapshot.isDerivedUnlocked("item.ferocious_gloves.22981"));
    }

    @Test
    public void prayerPotionFoilUnlocksOnlyDirectIngredients()
    {
        String source = "item.prayer_potion.143";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertEquals(
            Set.of("item.ranarr_weed.257", "item.snape_grass.231"),
            snapshot.getDerivedCardIds());
    }

    @Test
    public void diamondAmuletFoilUsesVerticalJewelleryForm()
    {
        String source = "item.diamond_amulet.1700";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertTrue(snapshot.isDerivedUnlocked("item.sapphire_amulet.1694"));
        assertTrue(snapshot.isDerivedUnlocked("item.emerald_amulet.1696"));
        assertTrue(snapshot.isDerivedUnlocked("item.ruby_amulet.1698"));
        assertFalse(snapshot.isDerivedUnlocked("item.dragonstone_amulet.1702"));
        assertFalse(snapshot.isDerivedUnlocked("item.diamond-ring"));
    }

    @Test
    public void torvaFoilCompletesOnlyTheSignatureSet()
    {
        String source = "item.torva_full_helm.26382";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertTrue(snapshot.isDerivedUnlocked("item.torva_platebody.26384"));
        assertTrue(snapshot.isDerivedUnlocked("item.torva_platelegs.26386"));
        assertFalse(snapshot.isDerivedUnlocked("item.bandos_chestplate.11832"));
    }

    @Test
    public void blessedDragonhideFoilStaysWithinItsGodSet()
    {
        String source = "item.saradomin_d_hide_body.10386";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertTrue(snapshot.isDerivedUnlocked("item.saradomin_coif.10390"));
        assertTrue(snapshot.isDerivedUnlocked("item.saradomin_chaps.10388"));
        assertTrue(snapshot.isDerivedUnlocked("item.saradomin_bracers.10384"));
        assertTrue(snapshot.isDerivedUnlocked("item.saradomin_d_hide_boots.19933"));
        assertTrue(snapshot.isDerivedUnlocked("item.saradomin_d_hide_shield.23191"));
        assertFalse(snapshot.isDerivedUnlocked("item.guthix_d_hide_body.10378"));
    }

    @Test
    public void npcFoilCanUnlockItsRequiredTool()
    {
        String source = "npc.banshee.414";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertEquals(
            Set.of("item.earmuffs.4166"),
            snapshot.getDerivedCardIds());
    }

    @Test
    public void achievementNpcFoilPermitsEarnedReward()
    {
        String source = "npc.tztok_jad.3127";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertTrue(snapshot.isDerivedUnlocked("item.fire_cape.6570"));
        assertTrue(snapshot.isDerivedUnlocked("npc.tz_kih.2189"));
        assertTrue(snapshot.isDerivedUnlocked("npc.ket_zek.3125"));
        assertFalse(snapshot.isDerivedUnlocked("npc.jaltok_jad.7700"));
    }

    @Test
    public void barrowsBrotherFoilUnlocksOnlyHisSet()
    {
        String source = "npc.dharok_the_wretched.1673";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source),
            Set.of(source));

        assertTrue(snapshot.isDerivedUnlocked("item.dharok_s_helm.4716"));
        assertTrue(snapshot.isDerivedUnlocked("item.dharok_s_platebody.4720"));
        assertTrue(snapshot.isDerivedUnlocked("item.dharok_s_platelegs.4722"));
        assertTrue(snapshot.isDerivedUnlocked("item.dharok_s_greataxe.4718"));
        assertFalse(snapshot.isDerivedUnlocked("item.guthan_s_helm.4724"));
    }

    @Test
    public void multiSourceCompletionRequiresEveryOwnedFoil()
    {
        String branda = "npc.branda_the_fire_queen.12596";
        String eldric = "npc.eldric_the_ice_king.14147";
        String staff = "item.twinflame_staff.30634";

        FoilEntitlementSnapshot incomplete = resolver.resolve(
            Set.of(branda),
            Set.of(branda));
        assertFalse(incomplete.isDerivedUnlocked(staff));

        FoilEntitlementSnapshot complete = resolver.resolve(
            Set.of(branda, eldric),
            Set.of(branda, eldric));
        assertTrue(complete.isDerivedUnlocked(staff));
        assertEquals(2, complete.getProvenance(staff).size());
    }

    @Test
    public void unrelatedNpcFamilyCascadesRemainAbsent()
    {
        assertFalse(registry.hasReward("npc.rune_dragon.8027"));
        assertFalse(registry.hasReward("npc.black_dragon.252"));
        assertFalse(registry.getTargetCardIdsForSource(
            "npc.cerberus.5862").contains("npc.hellhound.104"));
        assertFalse(registry.getTargetCardIdsForSource(
            "npc.general_graardor.2215").stream()
                .anyMatch(cardId -> cardId.startsWith("npc.")));
    }

    @Test
    public void herbFoilsUnlockOnlyTheirCorrespondingSeeds()
    {
        FoilEntitlementSnapshot clean = resolver.resolve(
            Set.of("item.ranarr_weed.257"),
            Set.of("item.ranarr_weed.257"));
        FoilEntitlementSnapshot grimy = resolver.resolve(
            Set.of("item.grimy_ranarr_weed.207"),
            Set.of("item.grimy_ranarr_weed.207"));

        assertEquals(Set.of(
            "item.ranarr_seed.5295",
            "item.grimy_ranarr_weed.207"),
            clean.getDerivedCardIds());
        assertEquals(Set.of(
            "item.ranarr_seed.5295",
            "item.ranarr_weed.257"),
            grimy.getDerivedCardIds());
    }

    @Test
    public void farmingHarvestFoilUnlocksItsDirectSeed()
    {
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of("item.watermelon.5982"),
            Set.of("item.watermelon.5982"));

        assertEquals(Set.of("item.watermelon_seed.5321"),
            snapshot.getDerivedCardIds());
    }

    @Test
    public void runeSetPackageFoilUnlocksCanonicalContents()
    {
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of("item.rune_armour_set_lg.13024"),
            Set.of("item.rune_armour_set_lg.13024"));

        assertEquals(Set.of(
            "item.rune-full-helm",
            "item.rune-platebody",
            "item.rune-platelegs",
            "item.rune-kiteshield"), snapshot.getDerivedCardIds());
        assertFalse(snapshot.isDerivedUnlocked("item.rune_plateskirt.1093"));
    }

    @Test
    public void oreAndBarFoilsExcludeCoal()
    {
        FoilEntitlementSnapshot mithrilOre = resolver.resolve(
            Set.of("item.mithril-ore"),
            Set.of("item.mithril-ore"));
        FoilEntitlementSnapshot steelBar = resolver.resolve(
            Set.of("item.steel-bar"),
            Set.of("item.steel-bar"));

        assertEquals(Set.of("item.mithril-bar"),
            mithrilOre.getDerivedCardIds());
        assertTrue(steelBar.isDerivedUnlocked("item.iron-ore"));
        assertTrue(steelBar.isDerivedUnlocked("item.iron-bar"));
        assertTrue(steelBar.isDerivedUnlocked("item.bronze-bar"));
        assertFalse(mithrilOre.isDerivedUnlocked("item.coal"));
        assertFalse(steelBar.isDerivedUnlocked("item.coal"));
    }

    @Test
    public void encounterCascadesAreStrictlyLowerByReviewedLevel()
    {
        FoilEntitlementSnapshot infernoPeer = resolver.resolve(
            Set.of("npc.jal_akrek_ket.7696"),
            Set.of("npc.jal_akrek_ket.7696"));
        assertTrue(infernoPeer.isDerivedUnlocked("npc.jal_nib.7691"));
        assertFalse(infernoPeer.isDerivedUnlocked("npc.jal_akrek_mej.7694"));
        assertFalse(infernoPeer.isDerivedUnlocked("npc.jal_akrek_xil.7695"));

        FoilEntitlementSnapshot sol = resolver.resolve(
            Set.of("npc.sol_heredit.12821"),
            Set.of("npc.sol_heredit.12821"));
        assertTrue(sol.isDerivedUnlocked("npc.manticore.12818"));
        assertTrue(sol.isDerivedUnlocked(
            "npc.fremennik_warband_berserker.12816"));
        assertFalse(sol.isDerivedUnlocked("npc.tz_kih.2189"));
    }

    @Test
    public void exactProcessingPairsRemainDirectAndNonRecursive()
    {
        FoilEntitlementSnapshot raw = resolver.resolve(
            Set.of("item.raw_shark.383"),
            Set.of("item.raw_shark.383"));
        assertTrue(raw.isDerivedUnlocked("item.shark.385"));
        assertFalse(raw.isDerivedUnlocked("item.raw_manta_ray.389"));

        FoilEntitlementSnapshot strung = resolver.resolve(
            Set.of("item.magic_shortbow.861"),
            Set.of("item.magic_shortbow.861"));
        assertTrue(strung.isDerivedUnlocked("item.magic_shortbow_u.72"));
        assertFalse(strung.isDerivedUnlocked("item.bow_string.1777"));

        FoilEntitlementSnapshot coconut = resolver.resolve(
            Set.of("item.coconut.5974"),
            Set.of("item.coconut.5974"));
        assertTrue(coconut.isDerivedUnlocked("item.coconut_milk.5935"));
        assertFalse(coconut.isDerivedUnlocked("item.vial.229"));
    }

    @Test
    public void ammunitionPairsDoNotGrantGenericInputs()
    {
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of("item.rune_dart_tip.824"),
            Set.of("item.rune_dart_tip.824"));

        assertTrue(snapshot.isDerivedUnlocked("item.rune_dart.811"));
        assertFalse(snapshot.isDerivedUnlocked("item.feather.314"));
    }

    @Test
    public void enchantedJewelleryUnlocksOnlyItsPrecursorDirection()
    {
        FoilEntitlementSnapshot enchanted = resolver.resolve(
            Set.of("item.amulet_of_fury.6585"),
            Set.of("item.amulet_of_fury.6585"));
        assertTrue(enchanted.isDerivedUnlocked("item.onyx_amulet.6581"));

        FoilEntitlementSnapshot precursor = resolver.resolve(
            Set.of("item.onyx_amulet.6581"),
            Set.of("item.onyx_amulet.6581"));
        assertFalse(precursor.isDerivedUnlocked("item.amulet_of_fury.6585"));
    }

    @Test
    public void componentCompletionRequiresEveryDistinctFoil()
    {
        Set<String> shards = Set.of(
            "item.godsword_shard_1.11818",
            "item.godsword_shard_2.11820",
            "item.godsword_shard_3.11822");
        FoilEntitlementSnapshot incomplete = resolver.resolve(
            Set.of("item.godsword_shard_1.11818",
                "item.godsword_shard_2.11820"),
            Set.of("item.godsword_shard_1.11818",
                "item.godsword_shard_2.11820"));
        assertFalse(incomplete.isDerivedUnlocked("item.godsword_blade.11798"));

        FoilEntitlementSnapshot complete = resolver.resolve(shards, shards);
        assertTrue(complete.isDerivedUnlocked("item.godsword_blade.11798"));
        assertEquals(3, complete.getProvenance(
            "item.godsword_blade.11798").size());
    }

    @Test
    public void completedItemFoilDisclosesOnlyItsDistinctComponents()
    {
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of("item.voidwaker.27690"),
            Set.of("item.voidwaker.27690"));

        assertEquals(Set.of(
            "item.voidwaker_blade.27684",
            "item.voidwaker_hilt.27681",
            "item.voidwaker_gem.27687"),
            snapshot.getDerivedCardIds());
    }

    @Test
    public void skillingOutfitFoilCompletesItsReviewedSet()
    {
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of("item.prospector_helmet.12013"),
            Set.of("item.prospector_helmet.12013"));

        assertTrue(snapshot.isDerivedUnlocked("item.prospector_jacket.12014"));
        assertTrue(snapshot.isDerivedUnlocked("item.prospector_legs.12015"));
        assertTrue(snapshot.isDerivedUnlocked("item.prospector_boots.12016"));
        assertFalse(snapshot.isDerivedUnlocked(
            "item.golden_prospector_helmet.25549"));
    }

    @Test
    public void runecraftAndBonemealPairsStayWithinExactIdentity()
    {
        FoilEntitlementSnapshot tiara = resolver.resolve(
            Set.of("item.nature_tiara.5541"),
            Set.of("item.nature_tiara.5541"));
        assertTrue(tiara.isDerivedUnlocked("item.nature_talisman.1462"));
        assertFalse(tiara.isDerivedUnlocked("item.nature_rune.561"));

        FoilEntitlementSnapshot meal = resolver.resolve(
            Set.of("item.dragon_bonemeal.4261"),
            Set.of("item.dragon_bonemeal.4261"));
        assertTrue(meal.isDerivedUnlocked("item.dragon_bones.536"));
        assertFalse(meal.isDerivedUnlocked("item.big_bones.532"));
    }

    @Test
    public void fixedPackAndGodBookRulesAreDeterministic()
    {
        FoilEntitlementSnapshot pack = resolver.resolve(
            Set.of("item.feather_pack.11881"),
            Set.of("item.feather_pack.11881"));
        assertEquals(Set.of("item.feather.314"), pack.getDerivedCardIds());

        Set<String> pages = Set.of(
            "item.saradomin_page_1.3827",
            "item.saradomin_page_2.3828",
            "item.saradomin_page_3.3829",
            "item.saradomin_page_4.3830");
        FoilEntitlementSnapshot book = resolver.resolve(pages, pages);
        assertTrue(book.isDerivedUnlocked("item.holy_book.3840"));
    }

    @Test
    public void derivedAccessDoesNotIncreaseCollectionOwnershipCounts()
    {
        String source = "item.mithril_axe.1355";
        CollectionState state = state(Set.of(source), Set.of(source));
        FoilEntitlementSnapshot entitlements = resolver.resolve(
            state.getOwnedCardIds(), state.getFoilCardIds());
        CollectionProgressSnapshot progress =
            new CollectionProgressService(catalogue).calculate(state);

        assertEquals(1, progress.getOverall().getOwned());
        assertEquals(1, progress.getOverall().getFoil());
        assertTrue(entitlements.getDerivedCardIds().size() >= 4);
    }

    @Test
    public void derivedAccessCanSatisfyRuntimeItemRestriction()
    {
        String source = "item.mithril_axe.1355";
        String target = "item.bronze_axe";
        FoilEntitlementSnapshot entitlements = resolver.resolve(
            Set.of(source), Set.of(source));
        CardDefinition targetCard = catalogue.requireCard(target);
        int itemId = catalogue.requireFamily(
            targetCard.getEntityFamilyId()).getCanonicalEntityId();
        SimpleRestrictionService service = new SimpleRestrictionService(
            new InteractionFamilyIndex(catalogue));

        assertTrue(service.evaluateItems(
            Set.of(itemId),
            "Wield",
            Set.of(source),
            false).isBlocked());
        assertFalse(service.evaluateItems(
            Set.of(itemId),
            "Wield",
            entitlements.getUsableCardIds(),
            false).isBlocked());
    }

    @Test
    public void overlappingFoilsProduceOneTargetWithCompleteProvenance()
    {
        String graardor = "npc.general_graardor.2215";
        String bandosBoots = "item.bandos_boots.11836";
        String chestplate = "item.bandos_chestplate.11832";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(graardor, bandosBoots),
            Set.of(graardor, bandosBoots));

        assertTrue(snapshot.isDerivedUnlocked(chestplate));
        assertEquals(2, snapshot.getProvenance(chestplate).size());
        assertEquals(1L, snapshot.getDerivedCardIds().stream()
            .filter(chestplate::equals).count());
    }

    @Test
    public void alreadyOwnedTargetsRemainOwnedRatherThanDerived()
    {
        String source = "item.mithril_axe.1355";
        String target = "item.bronze_axe";
        FoilEntitlementSnapshot snapshot = resolver.resolve(
            Set.of(source, target),
            Set.of(source));

        assertTrue(snapshot.getOwnedCardIds().contains(target));
        assertFalse(snapshot.getDerivedCardIds().contains(target));
        assertTrue(snapshot.getProvenance(target).isEmpty());
    }

    @Test
    public void reviewedRegistryHasStableExpectedCoverage()
    {
        assertEquals(3351, registry.getDirectGrantCount());
        assertEquals(21, registry.getConditionalGrantCount());
        assertEquals(1344, registry.getMappedSourceCount());
        assertTrue(registry.hasReward("item.mithril_axe.1355"));
        assertTrue(registry.hasReward("npc.general_graardor.2215"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void foilSourceMustAlsoBeOwned()
    {
        resolver.resolve(
            Collections.emptySet(),
            Set.of("item.mithril_axe.1355"));
    }

    private CollectionState state(Set<String> owned, Set<String> foils)
    {
        return new CollectionState(
            UUID.randomUUID(),
            "foil-test",
            "Foil Test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.EPOCH,
            1,
            catalogue.getCatalogueVersion(),
            1,
            0L,
            0L,
            0L,
            owned,
            foils);
    }
}
