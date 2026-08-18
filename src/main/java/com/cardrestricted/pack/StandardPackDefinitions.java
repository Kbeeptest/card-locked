package com.cardrestricted.pack;

import com.cardrestricted.catalog.Rarity;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class StandardPackDefinitions
{
    private StandardPackDefinitions()
    {
    }

    /** Historical Phase 0.28 definition retained for compatibility. */
    public static PackDefinition version2()
    {
        RarityRollTable standard = randomAllRarities("roll.standard.v1");
        RarityRollTable uncommonPlus = randomUncommonPlus(
            "roll.uncommon-plus.v1");
        return new PackDefinition(
            "pack.standard.v2",
            2,
            3_000L,
            PackContentPool.ITEMS,
            List.of(
                new PackSlotDefinition("standard-1", standard),
                new PackSlotDefinition("standard-2", standard),
                new PackSlotDefinition("standard-3", standard),
                new PackSlotDefinition("standard-4", standard),
                new PackSlotDefinition("uncommon-plus", uncommonPlus)));
    }

    /** Three Common, one Uncommon and one unrestricted rarity roll. */
    public static PackDefinition version3()
    {
        RarityRollTable common = exactRarity(
            "roll.standard.common.v1", Rarity.COMMON);
        RarityRollTable uncommon = exactRarity(
            "roll.standard.uncommon.v1", Rarity.UNCOMMON);
        RarityRollTable random = randomAllRarities(
            "roll.standard.random.v1");
        return new PackDefinition(
            "pack.standard.v3",
            3,
            3_000L,
            PackContentPool.ALL,
            List.of(
                new PackSlotDefinition("common-1", common),
                new PackSlotDefinition("common-2", common),
                new PackSlotDefinition("common-3", common),
                new PackSlotDefinition("uncommon", uncommon),
                new PackSlotDefinition("random", random)));
    }

    /**
     * General-purpose Standard Pack. Rarity odds are unchanged from v3,
     * but selection within the rolled rarity now de-emphasises NPCs because
     * Explorer/Adventure provide dedicated NPC targeting. Equipment receives
     * a mild 1.15x preference so early/mid progression pulls feel more useful
     * without guaranteeing gear or changing rarity odds.
     */
    public static PackDefinition version4()
    {
        RarityRollTable common = exactRarity(
            "roll.standard.common.v1", Rarity.COMMON);
        RarityRollTable uncommon = exactRarity(
            "roll.standard.uncommon.v1", Rarity.UNCOMMON);
        RarityRollTable random = randomAllRarities(
            "roll.standard.random.v1");
        return new PackDefinition(
            "pack.standard.v4",
            4,
            3_000L,
            PackContentPool.ALL,
            PackDefinition.NORMAL_SELECTION_WEIGHT,
            40,
            115,
            List.of(
                new PackSlotDefinition("common-1", common),
                new PackSlotDefinition("common-2", common),
                new PackSlotDefinition("common-3", common),
                new PackSlotDefinition("uncommon", uncommon),
                new PackSlotDefinition("random", random)));
    }

    /** Two Uncommon, two Uncommon+ and one unrestricted rarity roll. */
    public static PackDefinition uncommonPlusPack()
    {
        RarityRollTable uncommon = exactRarity(
            "roll.uncommon-pack.uncommon.v1", Rarity.UNCOMMON);
        RarityRollTable uncommonPlus = randomUncommonPlus(
            "roll.uncommon-pack.uncommon-plus.v1");
        RarityRollTable random = randomAllRarities(
            "roll.uncommon-pack.random.v1");
        return new PackDefinition(
            "pack.uncommon-plus.v1",
            1,
            4_000L,
            PackContentPool.ALL,
            List.of(
                new PackSlotDefinition("uncommon-1", uncommon),
                new PackSlotDefinition("uncommon-2", uncommon),
                new PackSlotDefinition("uncommon-plus-1", uncommonPlus),
                new PackSlotDefinition("uncommon-plus-2", uncommonPlus),
                new PackSlotDefinition("random", random)));
    }

    /**
     * Non-combat NPC completion pack. The pool is intentionally dominated by
     * Common/Uncommon cards, so value comes from targeting and a 2.5x weight
     * for currently unowned cards rather than artificial premium-tier rolls.
     */
    public static PackDefinition explorerPack()
    {
        RarityRollTable common = exactRarity(
            "roll.explorer.common.v2", Rarity.COMMON);
        RarityRollTable uncommon = exactRarity(
            "roll.explorer.uncommon.v2", Rarity.UNCOMMON);
        RarityRollTable weightedLow = new RarityRollTable(
            "roll.explorer.weighted-low.v2",
            2,
            Rarity.COMMON,
            weights(35_000, 65_000, 0, 0, 0, 0, 0));
        RarityRollTable discovery = new RarityRollTable(
            "roll.explorer.discovery.v2",
            2,
            Rarity.COMMON,
            weights(25_000, 70_000, 5_000, 0, 0, 0, 0));
        return new PackDefinition(
            "pack.explorer.v2",
            2,
            4_000L,
            PackContentPool.NONCOMBAT_NPCS,
            250,
            List.of(
                new PackSlotDefinition("explorer-common-1", common),
                new PackSlotDefinition("explorer-common-2", common),
                new PackSlotDefinition("explorer-uncommon", uncommon),
                new PackSlotDefinition("explorer-weighted-low", weightedLow),
                new PackSlotDefinition("explorer-discovery", discovery)));
    }

    /**
     * Combat-NPC progression pack. Adventure replaces the retired attackable
     * NPC store role and deliberately reaches through the full boss/end-game
     * rarity ladder while retaining a 1.5x preference for unowned cards.
     */
    public static PackDefinition adventurePack()
    {
        RarityRollTable uncommonPlus = randomUncommonPlus(
            "roll.adventure.uncommon-plus.v2");
        RarityRollTable rarePlus = randomRarePlus(
            "roll.adventure.rare-plus.v2");
        RarityRollTable epicPlus = randomAdventureEpicPlus(
            "roll.adventure.epic-plus.v2");
        return new PackDefinition(
            "pack.adventure.v2",
            2,
            7_500L,
            PackContentPool.ATTACKABLE_NPCS,
            150,
            List.of(
                new PackSlotDefinition("adventure-uncommon-plus", uncommonPlus),
                new PackSlotDefinition("adventure-rare-plus-1", rarePlus),
                new PackSlotDefinition("adventure-rare-plus-2", rarePlus),
                new PackSlotDefinition("adventure-rare-plus-3", rarePlus),
                new PackSlotDefinition("adventure-epic-plus", epicPlus)));
    }

    public static PackDefinition rarePlusItemPack()
    {
        RarityRollTable guaranteedRare = exactRarity(
            "roll.rare-plus.guaranteed-rare.v2", Rarity.RARE);
        RarityRollTable randomRarePlus = randomRarePlus(
            "roll.rare-plus.random.v2");
        return new PackDefinition(
            "pack.rare-plus.v4",
            4,
            6_000L,
            PackContentPool.ALL,
            List.of(
                new PackSlotDefinition("rare-1", guaranteedRare),
                new PackSlotDefinition("rare-2", guaranteedRare),
                new PackSlotDefinition("rare-3", guaranteedRare),
                new PackSlotDefinition("rare-plus-random-1", randomRarePlus),
                new PackSlotDefinition("rare-plus-random-2", randomRarePlus)));
    }

    public static PackDefinition collectorPack()
    {
        return new PackDefinition(
            "pack.collector.v1",
            1,
            25_000L,
            PackContentPool.ALL,
            List.of(
                new PackSlotDefinition("rare", exactRarity(
                    "roll.collector.rare.v1", Rarity.RARE)),
                new PackSlotDefinition("epic", exactRarity(
                    "roll.collector.epic.v1", Rarity.EPIC)),
                new PackSlotDefinition("legendary", exactRarity(
                    "roll.collector.legendary.v1", Rarity.LEGENDARY)),
                new PackSlotDefinition("mythic", exactRarity(
                    "roll.collector.mythic.v1", Rarity.MYTHIC)),
                new PackSlotDefinition("godly", exactRarity(
                    "roll.collector.godly.v1", Rarity.GODLY))));
    }

    public static PackDefinition initiateFoilPack()
    {
        RarityRollTable commonUncommon = randomCommonUncommon(
            "roll.milestone.initiate.common-uncommon.v1");
        return new PackDefinition(
            "pack.milestone.initiate-foil.v1",
            1,
            0L,
            PackContentPool.ALL,
            List.of(
                guaranteedFoil("foil-1", commonUncommon),
                guaranteedFoil("foil-2", commonUncommon),
                guaranteedFoil("foil-3", commonUncommon),
                guaranteedFoil("foil-4", commonUncommon),
                guaranteedFoil("foil-5", commonUncommon)));
    }

    public static PackDefinition heroPack()
    {
        return tierMilestonePack(
            "pack.milestone.hero.v1",
            "hero",
            Rarity.RARE);
    }

    public static PackDefinition noblePack()
    {
        return tierMilestonePack(
            "pack.milestone.noble.v1",
            "noble",
            Rarity.EPIC);
    }

    public static PackDefinition legendPack()
    {
        return tierMilestonePack(
            "pack.milestone.legend.v1",
            "legend",
            Rarity.LEGENDARY);
    }

    public static PackDefinition mythicalPack()
    {
        return tierMilestonePack(
            "pack.milestone.mythical.v1",
            "mythical",
            Rarity.MYTHIC);
    }

    public static PackDefinition godsPack()
    {
        return tierMilestonePack(
            "pack.milestone.gods.v1",
            "gods",
            Rarity.GODLY);
    }

    /** Legacy definitions retained for save/activity compatibility. */
    public static PackDefinition noncombatNpcPack()
    {
        return legacyFiveCardPack(
            "pack.noncombat-npc.v2",
            2,
            3_000L,
            PackContentPool.NONCOMBAT_NPCS,
            "noncombat-npc");
    }

    public static PackDefinition attackableNpcPack()
    {
        return legacyFiveCardPack(
            "pack.attackable-npc.v2",
            2,
            5_000L,
            PackContentPool.ATTACKABLE_NPCS,
            "attackable-npc");
    }

    public static PackDefinition foilTestPack()
    {
        return new PackDefinition(
            "pack.foil-test.v1",
            1,
            1_000L,
            PackContentPool.ALL,
            List.of(
                new PackSlotDefinition("foil-test-common", exactRarity(
                    "roll.foil-test.common.v1", Rarity.COMMON)),
                new PackSlotDefinition("foil-test-uncommon", exactRarity(
                    "roll.foil-test.uncommon.v1", Rarity.UNCOMMON)),
                new PackSlotDefinition("foil-test-rare", exactRarity(
                    "roll.foil-test.rare.v1", Rarity.RARE)),
                new PackSlotDefinition("foil-test-epic", exactRarity(
                    "roll.foil-test.epic.v1", Rarity.EPIC)),
                new PackSlotDefinition("foil-test-legendary", exactRarity(
                    "roll.foil-test.legendary.v1", Rarity.LEGENDARY))));
    }

    public static PackDefinition premiumFoilTestPack()
    {
        RarityRollTable legendaryPlus = randomLegendaryPlus(
            "roll.premium-foil-test.legendary-plus.v1");
        return new PackDefinition(
            "pack.premium-foil-test.v1",
            1,
            1_000L,
            PackContentPool.ALL,
            List.of(
                new PackSlotDefinition("premium-foil-test-1", legendaryPlus),
                new PackSlotDefinition("premium-foil-test-2", legendaryPlus),
                new PackSlotDefinition("premium-foil-test-3", legendaryPlus),
                new PackSlotDefinition("premium-foil-test-4", legendaryPlus),
                new PackSlotDefinition("premium-foil-test-5", legendaryPlus)));
    }

    /** Compatibility alias retained while older call sites are phased out. */
    public static PackDefinition rareHunterTestPack()
    {
        return rarePlusItemPack();
    }

    private static PackDefinition structuredGeneralPack(
        String packId,
        int version,
        long price,
        PackContentPool contentPool,
        String prefix)
    {
        RarityRollTable common = exactRarity(
            "roll." + prefix + ".common.v1", Rarity.COMMON);
        RarityRollTable uncommon = exactRarity(
            "roll." + prefix + ".uncommon.v1", Rarity.UNCOMMON);
        RarityRollTable random = randomAllRarities(
            "roll." + prefix + ".random.v1");
        return new PackDefinition(
            packId,
            version,
            price,
            contentPool,
            List.of(
                new PackSlotDefinition(prefix + "-common-1", common),
                new PackSlotDefinition(prefix + "-common-2", common),
                new PackSlotDefinition(prefix + "-common-3", common),
                new PackSlotDefinition(prefix + "-uncommon", uncommon),
                new PackSlotDefinition(prefix + "-random", random)));
    }

    private static PackDefinition tierMilestonePack(
        String packId,
        String prefix,
        Rarity rarity)
    {
        RarityRollTable exact = exactRarity(
            "roll.milestone." + prefix + "." + rarity.name().toLowerCase()
                + ".v1",
            rarity);
        return new PackDefinition(
            packId,
            1,
            0L,
            PackContentPool.ALL,
            List.of(
                guaranteedFoil("foil-1", exact),
                guaranteedFoil("foil-2", exact),
                guaranteedFoil("foil-3", exact),
                normalCard("normal-1", exact),
                normalCard("normal-2", exact)));
    }

    private static PackSlotDefinition guaranteedFoil(
        String slotId,
        RarityRollTable table)
    {
        return new PackSlotDefinition(
            slotId,
            table,
            PackFoilRule.GUARANTEED);
    }

    private static PackSlotDefinition normalCard(
        String slotId,
        RarityRollTable table)
    {
        return new PackSlotDefinition(
            slotId,
            table,
            PackFoilRule.DISABLED);
    }

    private static PackDefinition legacyFiveCardPack(
        String packId,
        int version,
        long price,
        PackContentPool contentPool,
        String rollPrefix)
    {
        RarityRollTable standard = randomAllRarities(
            "roll." + rollPrefix + ".standard.v1");
        RarityRollTable uncommonPlus = randomUncommonPlus(
            "roll." + rollPrefix + ".uncommon-plus.v1");
        return new PackDefinition(
            packId,
            version,
            price,
            contentPool,
            List.of(
                new PackSlotDefinition(rollPrefix + "-1", standard),
                new PackSlotDefinition(rollPrefix + "-2", standard),
                new PackSlotDefinition(rollPrefix + "-3", standard),
                new PackSlotDefinition(rollPrefix + "-4", standard),
                new PackSlotDefinition(
                    rollPrefix + "-uncommon-plus", uncommonPlus)));
    }

    private static RarityRollTable randomCommonUncommon(String tableId)
    {
        return new RarityRollTable(
            tableId,
            1,
            Rarity.COMMON,
            weights(60_000, 40_000, 0, 0, 0, 0, 0));
    }

    private static RarityRollTable randomLegendaryPlus(String tableId)
    {
        return new RarityRollTable(
            tableId,
            1,
            Rarity.LEGENDARY,
            weights(0, 0, 0, 0, 60_000, 30_000, 10_000));
    }

    private static RarityRollTable exactRarity(
        String tableId,
        Rarity rarity)
    {
        int common = rarity == Rarity.COMMON ? 100_000 : 0;
        int uncommon = rarity == Rarity.UNCOMMON ? 100_000 : 0;
        int rare = rarity == Rarity.RARE ? 100_000 : 0;
        int epic = rarity == Rarity.EPIC ? 100_000 : 0;
        int legendary = rarity == Rarity.LEGENDARY ? 100_000 : 0;
        int mythic = rarity == Rarity.MYTHIC ? 100_000 : 0;
        int godly = rarity == Rarity.GODLY ? 100_000 : 0;
        return new RarityRollTable(
            tableId,
            1,
            rarity,
            weights(common, uncommon, rare, epic, legendary, mythic, godly));
    }

    private static RarityRollTable randomAllRarities(String tableId)
    {
        return new RarityRollTable(
            tableId,
            1,
            Rarity.COMMON,
            weights(45_000, 38_000, 13_000, 3_500, 450, 45, 5));
    }

    private static RarityRollTable randomUncommonPlus(String tableId)
    {
        return new RarityRollTable(
            tableId,
            1,
            Rarity.UNCOMMON,
            weights(0, 65_000, 25_000, 8_000, 1_700, 270, 30));
    }

    private static RarityRollTable randomRarePlus(String tableId)
    {
        return new RarityRollTable(
            tableId,
            1,
            Rarity.RARE,
            weights(0, 0, 42_000, 31_000, 16_000, 8_000, 3_000));
    }

    private static RarityRollTable randomAdventureEpicPlus(String tableId)
    {
        return new RarityRollTable(
            tableId,
            1,
            Rarity.EPIC,
            weights(0, 0, 0, 62_000, 23_000, 10_000, 5_000));
    }

    private static Map<Rarity, Integer> weights(
        int common,
        int uncommon,
        int rare,
        int epic,
        int legendary,
        int mythic,
        int godly)
    {
        EnumMap<Rarity, Integer> weights = new EnumMap<>(Rarity.class);
        weights.put(Rarity.COMMON, common);
        weights.put(Rarity.UNCOMMON, uncommon);
        weights.put(Rarity.RARE, rare);
        weights.put(Rarity.EPIC, epic);
        weights.put(Rarity.LEGENDARY, legendary);
        weights.put(Rarity.MYTHIC, mythic);
        weights.put(Rarity.GODLY, godly);
        return weights;
    }
}
