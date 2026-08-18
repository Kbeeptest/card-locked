package com.cardrestricted.pack;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.domain.ActionType;
import com.cardrestricted.progression.ProgressionRewardCardPolicy;
import com.cardrestricted.foil.FoilRewardKind;
import com.cardrestricted.foil.FoilRewardRegistry;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.CommittedStateRecovery;
import com.cardrestricted.persistence.JournalEventType;
import com.cardrestricted.persistence.TransactionalStateStore;
import com.cardrestricted.progression.ProgressionMilestonePolicy;
import com.cardrestricted.starter.RandomisedStarterPackGenerator;
import com.cardrestricted.starter.StarterPackPoolRegistry;
import com.cardrestricted.starter.StarterRewardState;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class StandardPackService
{
    public static final String STARTER_PACK_ID =
        "pack.starter-randomised.v1";
    public static final int STARTER_PACK_VERSION = 1;
    private static final PackDefinition STANDARD_DEFINITION =
        StandardPackDefinitions.version4();
    private static final PackDefinition UNCOMMON_PLUS_DEFINITION =
        StandardPackDefinitions.uncommonPlusPack();
    private static final PackDefinition EXPLORER_DEFINITION =
        StandardPackDefinitions.explorerPack();
    private static final PackDefinition RARE_PLUS_DEFINITION =
        StandardPackDefinitions.rarePlusItemPack();
    private static final PackDefinition ADVENTURE_DEFINITION =
        StandardPackDefinitions.adventurePack();
    private static final PackDefinition COLLECTOR_DEFINITION =
        StandardPackDefinitions.collectorPack();
    private static final PackDefinition INITIATE_FOIL_DEFINITION =
        StandardPackDefinitions.initiateFoilPack();
    private static final PackDefinition HERO_DEFINITION =
        StandardPackDefinitions.heroPack();
    private static final PackDefinition NOBLE_DEFINITION =
        StandardPackDefinitions.noblePack();
    private static final PackDefinition LEGEND_DEFINITION =
        StandardPackDefinitions.legendPack();
    private static final PackDefinition MYTHICAL_DEFINITION =
        StandardPackDefinitions.mythicalPack();
    private static final PackDefinition GODS_DEFINITION =
        StandardPackDefinitions.godsPack();
    private static final PackDefinition NONCOMBAT_NPC_DEFINITION =
        StandardPackDefinitions.noncombatNpcPack();
    private static final PackDefinition ATTACKABLE_NPC_DEFINITION =
        StandardPackDefinitions.attackableNpcPack();
    private static final PackDefinition FOIL_TEST_DEFINITION =
        StandardPackDefinitions.foilTestPack();
    private static final PackDefinition PREMIUM_FOIL_TEST_DEFINITION =
        StandardPackDefinitions.premiumFoilTestPack();
    public static final String PACK_ID = STANDARD_DEFINITION.getPackId();
    public static final long PRICE = STANDARD_DEFINITION.getPrice();
    public static final String UNCOMMON_PLUS_PACK_ID =
        UNCOMMON_PLUS_DEFINITION.getPackId();
    public static final long UNCOMMON_PLUS_PRICE =
        UNCOMMON_PLUS_DEFINITION.getPrice();
    public static final String EXPLORER_PACK_ID =
        EXPLORER_DEFINITION.getPackId();
    public static final long EXPLORER_PRICE =
        EXPLORER_DEFINITION.getPrice();
    public static final String RARE_HUNTER_PACK_ID =
        RARE_PLUS_DEFINITION.getPackId();
    public static final long RARE_HUNTER_PRICE =
        RARE_PLUS_DEFINITION.getPrice();
    public static final String RARE_PLUS_PACK_ID =
        RARE_HUNTER_PACK_ID;
    public static final long RARE_PLUS_PRICE =
        RARE_HUNTER_PRICE;
    public static final String ADVENTURE_PACK_ID =
        ADVENTURE_DEFINITION.getPackId();
    public static final long ADVENTURE_PRICE =
        ADVENTURE_DEFINITION.getPrice();
    public static final String COLLECTOR_PACK_ID =
        COLLECTOR_DEFINITION.getPackId();
    public static final long COLLECTOR_PRICE =
        COLLECTOR_DEFINITION.getPrice();
    public static final long NEXUS_CACHE_PRICE = 6_000L;
    public static final long NEXUS_CACHE_MIN_SHARDS = 225L;
    public static final long NEXUS_CACHE_MAX_SHARDS = 375L;
    public static final String NONCOMBAT_NPC_PACK_ID =
        NONCOMBAT_NPC_DEFINITION.getPackId();
    public static final long NONCOMBAT_NPC_PRICE =
        NONCOMBAT_NPC_DEFINITION.getPrice();
    public static final String ATTACKABLE_NPC_PACK_ID =
        ATTACKABLE_NPC_DEFINITION.getPackId();
    public static final long ATTACKABLE_NPC_PRICE =
        ATTACKABLE_NPC_DEFINITION.getPrice();
    public static final String FOIL_TEST_PACK_ID =
        FOIL_TEST_DEFINITION.getPackId();
    public static final long FOIL_TEST_PRICE =
        FOIL_TEST_DEFINITION.getPrice();
    public static final String PREMIUM_FOIL_TEST_PACK_ID =
        PREMIUM_FOIL_TEST_DEFINITION.getPackId();
    public static final long PREMIUM_FOIL_TEST_PRICE =
        PREMIUM_FOIL_TEST_DEFINITION.getPrice();
    public static final String TIER_FOIL_TEST_PACK_ID =
        "pack.test.foil-tier.v1";
    public static final String ARMOUR_FOIL_TEST_PACK_ID =
        "pack.test.foil-armour-slot.v1";
    public static final String BOSS_FOIL_TEST_PACK_ID =
        "pack.test.foil-boss.v1";
    public static final String INGREDIENT_FOIL_TEST_PACK_ID =
        "pack.test.foil-ingredient.v1";
    public static final String SIGNATURE_FOIL_TEST_PACK_ID =
        "pack.test.foil-signature-set.v1";
    public static final String NPC_RELATIONSHIP_FOIL_TEST_PACK_ID =
        "pack.test.foil-npc-relationship.v1";
    static final Set<FoilRewardKind> ITEM_RELATIONSHIP_KINDS = Set.of(
        FoilRewardKind.RECIPE_COMPONENTS,
        FoilRewardKind.FARMING_SEED,
        FoilRewardKind.PACKAGE_CONTENTS,
        FoilRewardKind.MATERIAL_CONVERSION);
    static final Set<FoilRewardKind> NPC_RELATIONSHIP_KINDS = Set.of(
        FoilRewardKind.NPC_REQUIRED_TOOL,
        FoilRewardKind.ACHIEVEMENT_REWARD,
        FoilRewardKind.SOURCE_EQUIPMENT_SET,
        FoilRewardKind.ENCOUNTER_TIER_CASCADE);
    static final Set<String> ARMOUR_TIER_RULE_IDS = Set.of(
        "tier.full_helm",
        "tier.med_helm",
        "tier.platebody",
        "tier.chainbody",
        "tier.platelegs",
        "tier.plateskirt",
        "tier.kiteshield",
        "tier.sq_shield",
        "tier.boots",
        "tier.gloves");
    public static final long MAPPED_FOIL_TEST_PRICE = 1_000L;
    public static final int CARD_COUNT = STANDARD_DEFINITION.getSlots().size();

    private final CardCatalogue catalogue;
    private final TransactionalStateStore stateStore;
    private final CollectionState sessionCurrentState;

    public StandardPackService(
        CardCatalogue catalogue,
        TransactionalStateStore stateStore)
    {
        this(catalogue, stateStore, null);
    }

    /**
     * Session-owned fast path. The supplied state avoids a redundant durable
     * pre-read before pack generation; TransactionalStateStore.save() still
     * validates its expected revision against the durable current snapshot.
     */
    public StandardPackService(
        CardCatalogue catalogue,
        TransactionalStateStore stateStore,
        CollectionState sessionCurrentState)
    {
        this.catalogue = Objects.requireNonNull(
            catalogue, "catalogue");
        this.stateStore = Objects.requireNonNull(
            stateStore, "stateStore");
        this.sessionCurrentState = sessionCurrentState;
    }


    public synchronized PackPurchaseResult redeemStarterPack(
        Random random,
        Instant redeemedAt)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(redeemedAt, "redeemedAt");
        CollectionState current = loadCurrentState(
            "A collection must exist before redeeming the starter pack.");
        if (current.getPendingPackReveal().isPresent())
        {
            throw new PendingRevealException();
        }
        if (!StarterRewardState.hasPendingStarterPack(current))
        {
            throw new IllegalStateException(
                "This collection does not have an unredeemed starter pack.");
        }

        StarterPackPoolRegistry registry = StarterPackPoolRegistry.load(
            StandardPackService.class.getClassLoader(),
            catalogue);
        List<PackCardResult> generated =
            new RandomisedStarterPackGenerator(
                catalogue,
                registry).generate(current.getOwnedCardIds(), random);
        List<PackCardResult> results = new ArrayList<>();
        Set<String> resultingOwned = new HashSet<>(
            catalogue.canonicalizeCardIds(current.getOwnedCardIds()));
        Set<String> resultingFoils = new HashSet<>(
            catalogue.canonicalizeCardIds(current.getFoilCardIds()));
        for (PackCardResult generatedResult : generated)
        {
            if (!resultingOwned.add(generatedResult.getCardId()))
            {
                throw new IllegalStateException(
                    "Starter pack generation produced an owned card: "
                        + generatedResult.getCardId());
            }
            boolean foil = FoilRollPolicy.roll(random);
            if (foil)
            {
                resultingFoils.add(generatedResult.getCardId());
            }
            results.add(new PackCardResult(
                generatedResult.getCardId(),
                generatedResult.isDuplicate(),
                generatedResult.getShardsAwarded(),
                foil));
        }

        PendingPackReveal reveal = new PendingPackReveal(
            UUID.randomUUID(),
            STARTER_PACK_ID,
            redeemedAt,
            results,
            0);
        CollectionState updated = current.withStarterPackRedeemed(
            resultingOwned,
            resultingFoils,
            reveal,
            catalogue.getCatalogueVersion(),
            StarterRewardState.PACK_CHOICE_MARKER,
            StarterRewardState.PACK_REDEEMED_MARKER);
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                JournalEventType.PACK_PURCHASED,
                "openingId=" + reveal.getOpeningId()
                    + ";packId=" + STARTER_PACK_ID
                    + ";definitionVersion=" + STARTER_PACK_VERSION
                    + ";catalogueVersion=" + catalogue.getCatalogueVersion()
                    + ";price=0;starterReward=true;results="
                    + resultCardIds(results),
                redeemedAt);
        }
        catch (IOException failure)
        {
            updated = CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
        return new PackPurchaseResult(updated, reveal);
    }

    public synchronized PackPurchaseResult purchase(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseDefinition(
            STANDARD_DEFINITION,
            "Standard Pack",
            random,
            purchasedAt,
            false);
    }

    public synchronized PackPurchaseResult purchaseUncommonPlusPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseDefinition(
            UNCOMMON_PLUS_DEFINITION,
            "Uncommon+ Pack",
            random,
            purchasedAt,
            false,
            ProgressionMilestonePolicy.UNCOMMON_PLUS_PACK);
    }

    public synchronized PackPurchaseResult purchaseExplorerPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseDefinition(
            EXPLORER_DEFINITION,
            "Explorer Pack",
            random,
            purchasedAt,
            false,
            ProgressionMilestonePolicy.EXPLORER_PACK);
    }

    public synchronized PackPurchaseResult purchaseRareHunterPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseDefinition(
            RARE_PLUS_DEFINITION,
            "Rare+ Pack",
            random,
            purchasedAt,
            false,
            ProgressionMilestonePolicy.RARE_PLUS_PACK);
    }

    public synchronized PackPurchaseResult purchaseAdventurePack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseDefinition(
            ADVENTURE_DEFINITION,
            "Adventure Pack",
            random,
            purchasedAt,
            false,
            ProgressionMilestonePolicy.ADVENTURE_PACK);
    }

    public synchronized PackPurchaseResult purchaseCollectorPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseCollectorDefinition(random, purchasedAt);
    }

    public synchronized NexusCachePurchaseResult purchaseNexusCache(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(purchasedAt, "purchasedAt");
        CollectionState current = loadPurchasableState();
        requireMilestone(
            current,
            ProgressionMilestonePolicy.NEXUS_CACHE,
            "Nexus Cache");
        if (current.getPoints() < NEXUS_CACHE_PRICE)
        {
            throw new InsufficientPointsException(
                "Nexus Cache",
                NEXUS_CACHE_PRICE,
                current.getPoints());
        }
        long span = NEXUS_CACHE_MAX_SHARDS - NEXUS_CACHE_MIN_SHARDS + 1L;
        long awarded = NEXUS_CACHE_MIN_SHARDS
            + Math.floorMod(random.nextLong(), span);
        CollectionState updated = current.withNexusCachePurchased(
            NEXUS_CACHE_PRICE,
            awarded);
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                JournalEventType.NEXUS_CACHE_PURCHASED,
                "price=" + NEXUS_CACHE_PRICE + ";shards=" + awarded,
                purchasedAt);
        }
        catch (IOException failure)
        {
            updated = CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
        return new NexusCachePurchaseResult(updated, awarded);
    }

    public synchronized PackPurchaseResult redeemInitiateFoilPack(
        Random random,
        Instant redeemedAt)
        throws IOException
    {
        return redeemMilestoneDefinition(
            INITIATE_FOIL_DEFINITION,
            "Initiate's Foil Pack",
            ProgressionMilestonePolicy.INITIATE_FOIL_PACK,
            ProgressionMilestonePolicy.INITIATE_FOIL_MARKER,
            random,
            redeemedAt);
    }

    public synchronized PackPurchaseResult redeemHeroPack(
        Random random,
        Instant redeemedAt)
        throws IOException
    {
        return redeemMilestoneDefinition(
            HERO_DEFINITION,
            "Hero's Pack",
            ProgressionMilestonePolicy.HERO_PACK,
            ProgressionMilestonePolicy.HERO_PACK_MARKER,
            random,
            redeemedAt);
    }

    public synchronized PackPurchaseResult redeemNoblePack(
        Random random,
        Instant redeemedAt)
        throws IOException
    {
        return redeemMilestoneDefinition(
            NOBLE_DEFINITION,
            "Noble's Pack",
            ProgressionMilestonePolicy.NOBLE_PACK,
            ProgressionMilestonePolicy.NOBLE_PACK_MARKER,
            random,
            redeemedAt);
    }

    public synchronized PackPurchaseResult redeemLegendPack(
        Random random,
        Instant redeemedAt)
        throws IOException
    {
        return redeemMilestoneDefinition(
            LEGEND_DEFINITION,
            "Legend's Pack",
            ProgressionMilestonePolicy.LEGEND_PACK,
            ProgressionMilestonePolicy.LEGEND_PACK_MARKER,
            random,
            redeemedAt);
    }

    public synchronized PackPurchaseResult redeemMythicalPack(
        Random random,
        Instant redeemedAt)
        throws IOException
    {
        return redeemMilestoneDefinition(
            MYTHICAL_DEFINITION,
            "Mythical Pack",
            ProgressionMilestonePolicy.MYTHICAL_PACK,
            ProgressionMilestonePolicy.MYTHICAL_PACK_MARKER,
            random,
            redeemedAt);
    }

    public synchronized PackPurchaseResult redeemGodsPack(
        Random random,
        Instant redeemedAt)
        throws IOException
    {
        return redeemMilestoneDefinition(
            GODS_DEFINITION,
            "Pack of the Gods",
            ProgressionMilestonePolicy.GODS_PACK,
            ProgressionMilestonePolicy.GODS_PACK_MARKER,
            random,
            redeemedAt);
    }

    public synchronized PackPurchaseResult purchaseNoncombatNpcPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseDefinition(
            NONCOMBAT_NPC_DEFINITION,
            "Noncombat NPC Pack",
            random,
            purchasedAt,
            false);
    }

    public synchronized PackPurchaseResult purchaseAttackableNpcPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseDefinition(
            ATTACKABLE_NPC_DEFINITION,
            "Attackable NPC Pack",
            random,
            purchasedAt,
            false);
    }

    public synchronized PackPurchaseResult purchaseFoilTestPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseDefinition(
            FOIL_TEST_DEFINITION,
            "Temporary Foil Pack",
            random,
            purchasedAt,
            true);
    }

    public synchronized PackPurchaseResult purchasePremiumFoilTestPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseDefinition(
            PREMIUM_FOIL_TEST_DEFINITION,
            "Temporary Legendary+ Foil Pack",
            random,
            purchasedAt,
            true);
    }

    public synchronized PackPurchaseResult purchaseTierFoilTestPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseMappedFoilTestPack(
            TIER_FOIL_TEST_PACK_ID,
            "Tier-chain Foil Test Pack",
            Set.of(FoilRewardKind.TIER_CASCADE),
            Collections.emptySet(),
            random,
            purchasedAt);
    }

    public synchronized PackPurchaseResult purchaseArmourFoilTestPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseMappedFoilTestPack(
            ARMOUR_FOIL_TEST_PACK_ID,
            "Armour-slot Foil Test Pack",
            Set.of(FoilRewardKind.TIER_CASCADE),
            ARMOUR_TIER_RULE_IDS,
            random,
            purchasedAt);
    }

    public synchronized PackPurchaseResult purchaseBossFoilTestPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseMappedFoilTestPack(
            BOSS_FOIL_TEST_PACK_ID,
            "Boss-reward Foil Test Pack",
            Set.of(FoilRewardKind.SOURCE_UNIQUES),
            Collections.emptySet(),
            random,
            purchasedAt);
    }

    public synchronized PackPurchaseResult purchaseIngredientFoilTestPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseMappedFoilTestPack(
            INGREDIENT_FOIL_TEST_PACK_ID,
            "Item-relationship Foil Test Pack",
            ITEM_RELATIONSHIP_KINDS,
            Collections.emptySet(),
            random,
            purchasedAt);
    }

    public synchronized PackPurchaseResult purchaseSignatureFoilTestPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseMappedFoilTestPack(
            SIGNATURE_FOIL_TEST_PACK_ID,
            "Signature-set Foil Test Pack",
            Set.of(FoilRewardKind.SIGNATURE_SET),
            Collections.emptySet(),
            random,
            purchasedAt);
    }

    public synchronized PackPurchaseResult purchaseNpcRelationshipFoilTestPack(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        return purchaseMappedFoilTestPack(
            NPC_RELATIONSHIP_FOIL_TEST_PACK_ID,
            "NPC-relationship Foil Test Pack",
            NPC_RELATIONSHIP_KINDS,
            Collections.emptySet(),
            random,
            purchasedAt);
    }

    private PackPurchaseResult purchaseMappedFoilTestPack(
        String packId,
        String packName,
        Set<FoilRewardKind> rewardKinds,
        Set<String> requiredRuleIds,
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packName, "packName");
        Objects.requireNonNull(rewardKinds, "rewardKinds");
        if (rewardKinds.isEmpty())
        {
            throw new IllegalArgumentException(
                "At least one foil reward kind is required.");
        }
        Objects.requireNonNull(requiredRuleIds, "requiredRuleIds");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(purchasedAt, "purchasedAt");

        CollectionState current = loadCurrentState(
            "A collection must exist before purchasing a pack.");
        if (StarterRewardState.hasPendingStarterPack(current))
        {
            throw new IllegalStateException(
                "Redeem the one-time starter pack before purchasing other packs.");
        }
        if (current.getPendingPackReveal().isPresent())
        {
            throw new PendingRevealException();
        }
        if (current.getPoints() < MAPPED_FOIL_TEST_PRICE)
        {
            throw new InsufficientPointsException(
                packName,
                MAPPED_FOIL_TEST_PRICE,
                current.getPoints());
        }

        FoilRewardRegistry foilRewardRegistry = FoilRewardRegistry.load(
            StandardPackService.class.getClassLoader(),
            catalogue);
        Set<String> existingFoils = new HashSet<>(
            catalogue.canonicalizeCardIds(current.getFoilCardIds()));
        List<String> sourcePool = requiredRuleIds.isEmpty()
            ? foilRewardRegistry.getSourceCardIdsForKinds(rewardKinds)
            : foilRewardRegistry.getSourceCardIdsForRules(requiredRuleIds);
        List<String> unseenSources = new ArrayList<>();
        List<String> repeatedSources = new ArrayList<>();
        for (String sourceCardId : sourcePool)
        {
            if (existingFoils.contains(sourceCardId))
            {
                repeatedSources.add(sourceCardId);
            }
            else
            {
                unseenSources.add(sourceCardId);
            }
        }
        Collections.shuffle(unseenSources, random);
        Collections.shuffle(repeatedSources, random);
        unseenSources.addAll(repeatedSources);
        if (unseenSources.size() < CARD_COUNT)
        {
            throw new IllegalStateException(
                "The foil reward test pool does not contain five sources for "
                    + rewardKinds + '.');
        }

        Set<String> resultingOwned = new HashSet<>(
            catalogue.canonicalizeCardIds(current.getOwnedCardIds()));
        Set<String> resultingFoils = new HashSet<>(existingFoils);
        List<PackCardResult> results = new ArrayList<>();
        long resultingShards = current.getShards();
        for (int index = 0; index < CARD_COUNT; index++)
        {
            String cardId = unseenSources.get(index);
            CardDefinition card = catalogue.requireCard(cardId);
            boolean duplicate = !resultingOwned.add(cardId);
            long shardAward = duplicate
                ? DuplicateShardValues.forRarity(card.getRarity())
                : 0;
            resultingFoils.add(cardId);
            resultingShards = Math.addExact(
                resultingShards, shardAward);
            results.add(new PackCardResult(
                cardId,
                duplicate,
                shardAward,
                true));
        }

        PendingPackReveal reveal = new PendingPackReveal(
            UUID.randomUUID(),
            packId,
            purchasedAt,
            results,
            0);
        CollectionState updated = current.withPackPurchased(
            MAPPED_FOIL_TEST_PRICE,
            resultingShards,
            resultingOwned,
            resultingFoils,
            reveal,
            catalogue.getCatalogueVersion());
        List<String> rewardKindNames = new ArrayList<>();
        for (FoilRewardKind kind : rewardKinds)
        {
            rewardKindNames.add(kind.name());
        }
        Collections.sort(rewardKindNames);
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                JournalEventType.PACK_PURCHASED,
                "openingId=" + reveal.getOpeningId()
                    + ";packId=" + packId
                    + ";definitionVersion=1"
                    + ";catalogueVersion=" + catalogue.getCatalogueVersion()
                    + ";price=" + MAPPED_FOIL_TEST_PRICE
                    + ";testPack=true"
                    + ";foilRewardKinds=" + String.join(",", rewardKindNames)
                    + (requiredRuleIds.isEmpty()
                        ? ""
                        : ";foilRewardRules=" + String.join(",", requiredRuleIds))
                    + ";results=" + resultCardIds(results),
                purchasedAt);
        }
        catch (IOException failure)
        {
            updated = CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
        return new PackPurchaseResult(updated, reveal);
    }

    private PackPurchaseResult purchaseDefinition(
        PackDefinition definition,
        String packName,
        Random random,
        Instant purchasedAt,
        boolean guaranteeFoil)
        throws IOException
    {
        return purchaseDefinition(
            definition,
            packName,
            random,
            purchasedAt,
            guaranteeFoil,
            0);
    }

    private PackPurchaseResult purchaseDefinition(
        PackDefinition definition,
        String packName,
        Random random,
        Instant purchasedAt,
        boolean guaranteeFoil,
        int requiredUniqueCards)
        throws IOException
    {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(purchasedAt, "purchasedAt");
        CollectionState current = loadPurchasableState();
        requireMilestone(current, requiredUniqueCards, packName);
        if (current.getPoints() < definition.getPrice())
        {
            throw new InsufficientPointsException(
                packName,
                definition.getPrice(),
                current.getPoints());
        }

        GeneratedPack generated = generatePack(
            definition,
            current,
            random,
            guaranteeFoil,
            -1);
        PendingPackReveal reveal = new PendingPackReveal(
            UUID.randomUUID(),
            definition.getPackId(),
            purchasedAt,
            generated.results,
            0);
        CollectionState updated = current.withPackPurchased(
            definition.getPrice(),
            generated.resultingShards,
            generated.resultingOwned,
            generated.resultingFoils,
            reveal,
            catalogue.getCatalogueVersion());
        savePackPurchase(
            current,
            updated,
            definition,
            reveal,
            generated.results,
            "",
            purchasedAt);
        return new PackPurchaseResult(updated, reveal);
    }

    private PackPurchaseResult redeemMilestoneDefinition(
        PackDefinition definition,
        String packName,
        int requiredUniqueCards,
        String redeemedMarker,
        Random random,
        Instant redeemedAt)
        throws IOException
    {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(redeemedMarker, "redeemedMarker");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(redeemedAt, "redeemedAt");
        CollectionState current = loadPurchasableState();
        requireMilestone(current, requiredUniqueCards, packName);
        if (ProgressionMilestonePolicy.hasClaimed(current, redeemedMarker))
        {
            throw new IllegalStateException(
                packName + " has already been redeemed.");
        }

        GeneratedPack generated = generatePack(
            definition,
            current,
            random,
            false,
            -1);
        PendingPackReveal reveal = new PendingPackReveal(
            UUID.randomUUID(),
            definition.getPackId(),
            redeemedAt,
            generated.results,
            0);
        CollectionState updated = current.withMilestonePackRedeemed(
            generated.resultingOwned,
            generated.resultingFoils,
            generated.resultingShards,
            reveal,
            catalogue.getCatalogueVersion(),
            redeemedMarker);
        savePackPurchase(
            current,
            updated,
            definition,
            reveal,
            generated.results,
            ";milestoneReward=true;requiredCards=" + requiredUniqueCards
                + ";marker=" + redeemedMarker,
            redeemedAt);
        return new PackPurchaseResult(updated, reveal);
    }

    private PackPurchaseResult purchaseCollectorDefinition(
        Random random,
        Instant purchasedAt)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(purchasedAt, "purchasedAt");
        CollectionState current = loadPurchasableState();
        requireMilestone(
            current,
            ProgressionMilestonePolicy.COLLECTOR_PACK,
            "Collector Pack");
        if (current.getPoints() < COLLECTOR_DEFINITION.getPrice())
        {
            throw new InsufficientPointsException(
                "Collector Pack",
                COLLECTOR_DEFINITION.getPrice(),
                current.getPoints());
        }

        Map<Rarity, List<CardDefinition>> pools = buildRarityPools(
            COLLECTOR_DEFINITION.getContentPool());
        Set<String> canonicalOwned = new HashSet<>(
            catalogue.canonicalizeCardIds(current.getOwnedCardIds()));
        List<Integer> eligibleNewSlots = new ArrayList<>();
        for (int index = 0;
             index < COLLECTOR_DEFINITION.getSlots().size();
             index++)
        {
            PackSlotDefinition slot = COLLECTOR_DEFINITION.getSlots().get(index);
            Rarity rarity = slot.getRollTable().getMinimumRarity();
            boolean hasMissing = pools.get(rarity).stream()
                .anyMatch(card -> !canonicalOwned.contains(card.getCardId()));
            if (hasMissing)
            {
                eligibleNewSlots.add(index);
            }
        }
        if (eligibleNewSlots.isEmpty())
        {
            throw new IllegalStateException(
                "Every Rare, Epic, Legendary, Mythic and Godly card is already owned.");
        }
        int guaranteedNewPosition = eligibleNewSlots.get(
            random.nextInt(eligibleNewSlots.size()));
        GeneratedPack generated = generatePack(
            COLLECTOR_DEFINITION,
            current,
            random,
            false,
            guaranteedNewPosition);
        PendingPackReveal reveal = new PendingPackReveal(
            UUID.randomUUID(),
            COLLECTOR_DEFINITION.getPackId(),
            purchasedAt,
            generated.results,
            0);
        CollectionState updated = current.withPackPurchased(
            COLLECTOR_DEFINITION.getPrice(),
            generated.resultingShards,
            generated.resultingOwned,
            generated.resultingFoils,
            reveal,
            catalogue.getCatalogueVersion());
        savePackPurchase(
            current,
            updated,
            COLLECTOR_DEFINITION,
            reveal,
            generated.results,
            ";guaranteedNewPosition=" + (guaranteedNewPosition + 1),
            purchasedAt);
        return new PackPurchaseResult(updated, reveal);
    }

    private GeneratedPack generatePack(
        PackDefinition definition,
        CollectionState current,
        Random random,
        boolean guaranteeOneFoil,
        int guaranteedNewPosition)
    {
        Map<Rarity, List<CardDefinition>> pools = buildRarityPools(
            definition.getContentPool());
        Set<String> resultingOwned = new HashSet<>(
            catalogue.canonicalizeCardIds(current.getOwnedCardIds()));
        Set<String> resultingFoils = new HashSet<>(
            catalogue.canonicalizeCardIds(current.getFoilCardIds()));
        List<PackCardResult> results = new ArrayList<>();
        long resultingShards = current.getShards();
        int guaranteedFoilPosition = guaranteeOneFoil
            ? random.nextInt(definition.getSlots().size())
            : -1;

        for (int position = 0;
             position < definition.getSlots().size();
             position++)
        {
            PackSlotDefinition slot = definition.getSlots().get(position);
            Rarity rolledRarity = slot.getRollTable().roll(random);
            List<CardDefinition> pool = resolvePool(
                pools,
                rolledRarity,
                slot.getRollTable().getMinimumRarity());
            CardDefinition card;
            if (position == guaranteedNewPosition)
            {
                List<CardDefinition> missing = new ArrayList<>();
                for (CardDefinition candidate : pool)
                {
                    if (!resultingOwned.contains(candidate.getCardId()))
                    {
                        missing.add(candidate);
                    }
                }
                if (missing.isEmpty())
                {
                    throw new IllegalStateException(
                        "The selected Collector Pack tier has no missing cards.");
                }
                card = missing.get(random.nextInt(missing.size()));
            }
            else
            {
                card = selectWeightedCard(
                    pool,
                    resultingOwned,
                    definition.getUnownedCardWeightPercent(),
                    definition.getNpcCardWeightPercent(),
                    definition.getEquipmentCardWeightPercent(),
                    random);
            }

            boolean duplicate = !resultingOwned.add(card.getCardId());
            long shardAward = duplicate
                ? DuplicateShardValues.forRarity(card.getRarity())
                : 0;
            boolean foil = resolveFoil(
                slot.getFoilRule(),
                position == guaranteedFoilPosition,
                random);
            if (foil)
            {
                resultingFoils.add(card.getCardId());
            }
            resultingShards = Math.addExact(resultingShards, shardAward);
            results.add(new PackCardResult(
                card.getCardId(),
                duplicate,
                shardAward,
                foil));
        }
        return new GeneratedPack(
            resultingOwned,
            resultingFoils,
            results,
            resultingShards);
    }


    static CardDefinition selectWeightedCard(
        List<CardDefinition> pool,
        Set<String> ownedCardIds,
        int unownedCardWeightPercent,
        Random random)
    {
        return selectWeightedCard(
            pool,
            ownedCardIds,
            unownedCardWeightPercent,
            PackDefinition.NORMAL_SELECTION_WEIGHT,
            PackDefinition.NORMAL_SELECTION_WEIGHT,
            random);
    }

    /**
     * Select a card after rarity has already been resolved. The three weight
     * axes are deliberately multiplicative: missing-card preference, NPC
     * suppression and equipment preference can coexist without changing the
     * rarity roll itself.
     */
    static CardDefinition selectWeightedCard(
        List<CardDefinition> pool,
        Set<String> ownedCardIds,
        int unownedCardWeightPercent,
        int npcCardWeightPercent,
        int equipmentCardWeightPercent,
        Random random)
    {
        Objects.requireNonNull(pool, "pool");
        Objects.requireNonNull(ownedCardIds, "ownedCardIds");
        Objects.requireNonNull(random, "random");
        if (pool.isEmpty())
        {
            throw new IllegalArgumentException(
                "Pack selection pool cannot be empty.");
        }
        if (unownedCardWeightPercent < PackDefinition.NORMAL_SELECTION_WEIGHT)
        {
            throw new IllegalArgumentException(
                "Unowned-card weighting cannot be below normal selection weight.");
        }
        if (npcCardWeightPercent < 1 || equipmentCardWeightPercent < 1)
        {
            throw new IllegalArgumentException(
                "Card-type selection weights must be positive.");
        }

        long totalWeight = 0L;
        for (CardDefinition candidate : pool)
        {
            totalWeight = Math.addExact(
                totalWeight,
                selectionWeight(
                    candidate,
                    ownedCardIds,
                    unownedCardWeightPercent,
                    npcCardWeightPercent,
                    equipmentCardWeightPercent));
        }
        if (totalWeight > Integer.MAX_VALUE)
        {
            // java.util.Random only exposes an int bound. Catalogue pools are
            // currently well below this guard, but fail clearly if that ever
            // changes rather than introducing modulo bias.
            throw new IllegalStateException(
                "Weighted pack-card selection exceeds supported pool weight.");
        }
        int roll = random.nextInt((int) totalWeight);
        long cumulative = 0L;
        for (CardDefinition candidate : pool)
        {
            cumulative += selectionWeight(
                candidate,
                ownedCardIds,
                unownedCardWeightPercent,
                npcCardWeightPercent,
                equipmentCardWeightPercent);
            if (roll < cumulative)
            {
                return candidate;
            }
        }
        throw new IllegalStateException(
            "Weighted pack-card selection did not resolve a candidate.");
    }

    private static int selectionWeight(
        CardDefinition candidate,
        Set<String> ownedCardIds,
        int unownedCardWeightPercent,
        int npcCardWeightPercent,
        int equipmentCardWeightPercent)
    {
        int typeWeight = PackDefinition.NORMAL_SELECTION_WEIGHT;
        if (candidate.getCardType() == CardType.NPC)
        {
            typeWeight = npcCardWeightPercent;
        }
        else if (candidate.getCardType() == CardType.ITEM
            && candidate.getPermissions().contains(ActionType.ITEM_EQUIP))
        {
            typeWeight = equipmentCardWeightPercent;
        }
        int ownershipWeight = ownedCardIds.contains(candidate.getCardId())
            ? PackDefinition.NORMAL_SELECTION_WEIGHT
            : unownedCardWeightPercent;
        return Math.multiplyExact(typeWeight, ownershipWeight);
    }

    private static boolean resolveFoil(
        PackFoilRule foilRule,
        boolean forcedByPack,
        Random random)
    {
        switch (foilRule)
        {
            case GUARANTEED:
                return true;
            case DISABLED:
                return false;
            case RANDOM:
                return forcedByPack || FoilRollPolicy.roll(random);
            default:
                throw new IllegalArgumentException(
                    "Unsupported foil rule: " + foilRule);
        }
    }

    private CollectionState loadCurrentState(String missingMessage)
        throws IOException
    {
        if (sessionCurrentState != null)
        {
            return sessionCurrentState;
        }
        return stateStore.loadHighestValid()
            .orElseThrow(() -> new IllegalStateException(missingMessage));
    }

    private CollectionState loadPurchasableState()
        throws IOException
    {
        CollectionState current = loadCurrentState(
            "A collection must exist before purchasing a pack.");
        if (StarterRewardState.hasPendingStarterPack(current))
        {
            throw new IllegalStateException(
                "Redeem the one-time starter pack before purchasing other packs.");
        }
        if (current.getPendingPackReveal().isPresent())
        {
            throw new PendingRevealException();
        }
        return current;
    }

    private void requireMilestone(
        CollectionState state,
        int requiredUniqueCards,
        String rewardName)
    {
        if (requiredUniqueCards <= 0)
        {
            return;
        }
        int owned = ProgressionMilestonePolicy.uniqueOwnedCardCount(
            catalogue,
            state);
        if (owned < requiredUniqueCards)
        {
            throw new IllegalStateException(
                rewardName + " unlocks at " + requiredUniqueCards
                    + " unique cards; this profile has " + owned + '.');
        }
    }

    private void savePackPurchase(
        CollectionState current,
        CollectionState updated,
        PackDefinition definition,
        PendingPackReveal reveal,
        List<PackCardResult> results,
        String additionalPayload,
        Instant occurredAt)
        throws IOException
    {
        String payload = "openingId=" + reveal.getOpeningId()
            + ";packId=" + definition.getPackId()
            + ";definitionVersion=" + definition.getVersion()
            + ";catalogueVersion=" + catalogue.getCatalogueVersion()
            + ";price=" + definition.getPrice()
            + additionalPayload
            + ";results=" + resultCardIds(results);
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                JournalEventType.PACK_PURCHASED,
                payload,
                occurredAt);
        }
        catch (IOException failure)
        {
            CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
    }

    private static final class GeneratedPack
    {
        private final Set<String> resultingOwned;
        private final Set<String> resultingFoils;
        private final List<PackCardResult> results;
        private final long resultingShards;

        private GeneratedPack(
            Set<String> resultingOwned,
            Set<String> resultingFoils,
            List<PackCardResult> results,
            long resultingShards)
        {
            this.resultingOwned = resultingOwned;
            this.resultingFoils = resultingFoils;
            this.results = results;
            this.resultingShards = resultingShards;
        }
    }

    public synchronized PackRevealResult revealNext(Instant revealedAt)
        throws IOException
    {
        CollectionState current = stateStore.loadHighestValid()
            .orElseThrow(() -> new IllegalStateException(
                "A collection must exist before revealing a pack."));
        PendingPackReveal reveal = current.getPendingPackReveal()
            .orElseThrow(() -> new IllegalStateException(
                "There is no pack waiting to be revealed."));
        return revealCard(
            current,
            reveal.getNextUnrevealedPosition(),
            revealedAt);
    }

    public synchronized PackRevealResult revealCard(
        int cardPosition,
        Instant revealedAt)
        throws IOException
    {
        CollectionState current = stateStore.loadHighestValid()
            .orElseThrow(() -> new IllegalStateException(
                "A collection must exist before revealing a pack."));
        return revealCard(current, cardPosition, revealedAt);
    }

    /**
     * Reveals against an already loaded, session-owned state. save() still
     * validates the expected revision against the durable store, so skipping
     * the redundant pre-save reload does not weaken conflict detection.
     */
    public synchronized PackRevealResult revealCard(
        CollectionState current,
        int cardPosition,
        Instant revealedAt)
        throws IOException
    {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(revealedAt, "revealedAt");
        PendingPackReveal reveal = current.getPendingPackReveal()
            .orElseThrow(() -> new IllegalStateException(
                "There is no pack waiting to be revealed."));
        if (reveal.isRevealed(cardPosition))
        {
            throw new IllegalStateException(
                "That pack card has already been revealed.");
        }
        PackCardResult card = reveal.getCardAt(cardPosition);
        int revealNumber = reveal.getRevealedCount() + 1;
        int totalCards = reveal.getCardResults().size();
        CollectionState updated =
            current.withPackCardRevealed(cardPosition);
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                JournalEventType.PACK_REVEAL_ADVANCED,
                "openingId=" + reveal.getOpeningId()
                    + ";reveal=" + revealNumber
                    + ";position=" + (cardPosition + 1)
                    + ";cardId=" + card.getCardId()
                    + ";foil=" + card.isFoil(),
                revealedAt);
        }
        catch (IOException failure)
        {
            updated = CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
        return new PackRevealResult(
            updated,
            card,
            revealNumber,
            totalCards,
            cardPosition + 1);
    }

    private String resultCardIds(List<PackCardResult> results)
    {
        StringBuilder value = new StringBuilder();
        for (PackCardResult result : results)
        {
            if (value.length() > 0)
            {
                value.append(',');
            }
            value.append(result.getCardId());
            if (result.isDuplicate())
            {
                value.append(":duplicate");
            }
            if (result.isFoil())
            {
                value.append(":foil");
            }
        }
        return value.toString();
    }

    private Map<Rarity, List<CardDefinition>> buildRarityPools(
        PackContentPool contentPool)
    {
        Map<Rarity, List<CardDefinition>> pools =
            new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values())
        {
            pools.put(rarity, new ArrayList<>());
        }
        for (CardDefinition card : catalogue.getCards())
        {
            if (contentPool.includes(card)
                && !ProgressionRewardCardPolicy.isTrackOnlyReward(
                    card.getCardId()))
            {
                pools.get(card.getRarity()).add(card);
            }
        }
        for (List<CardDefinition> pool : pools.values())
        {
            pool.sort(Comparator.comparing(CardDefinition::getCardId));
        }
        return pools;
    }

    private List<CardDefinition> resolvePool(
        Map<Rarity, List<CardDefinition>> pools,
        Rarity rolledRarity,
        Rarity minimumRarity)
    {
        for (int ordinal = rolledRarity.ordinal();
             ordinal >= minimumRarity.ordinal();
             ordinal--)
        {
            List<CardDefinition> pool =
                pools.get(Rarity.values()[ordinal]);
            if (!pool.isEmpty())
            {
                return pool;
            }
        }
        for (int ordinal = rolledRarity.ordinal() + 1;
             ordinal < Rarity.values().length;
             ordinal++)
        {
            List<CardDefinition> pool =
                pools.get(Rarity.values()[ordinal]);
            if (!pool.isEmpty())
            {
                return pool;
            }
        }
        throw new IllegalStateException(
            "The selected pack pool has no cards at or above its rarity floor.");
    }
}
