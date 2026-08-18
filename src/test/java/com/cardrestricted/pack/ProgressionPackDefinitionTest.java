package com.cardrestricted.pack;

import com.cardrestricted.catalog.Rarity;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ProgressionPackDefinitionTest
{
    @Test
    public void standardPackHasFourLowFloorSlotsAndOneWildcard()
    {
        PackDefinition definition = StandardPackDefinitions.version4();
        assertEquals("pack.standard.v4", definition.getPackId());
        assertEquals(PackContentPool.ALL, definition.getContentPool());
        assertEquals(40, definition.getNpcCardWeightPercent());
        assertEquals(115, definition.getEquipmentCardWeightPercent());
        assertEquals(5, definition.getSlots().size());
        assertExact(definition.getSlots().get(0), Rarity.COMMON);
        assertExact(definition.getSlots().get(1), Rarity.COMMON);
        assertExact(definition.getSlots().get(2), Rarity.COMMON);
        assertExact(definition.getSlots().get(3), Rarity.UNCOMMON);
        assertEquals(Rarity.COMMON,
            definition.getSlots().get(4).getRollTable().getMinimumRarity());
        assertTrue(definition.getSlots().get(4).getRollTable()
            .getWeight(Rarity.RARE) > 0);
    }

    @Test
    public void rarePlusPackHasThreeRareAndTwoRarePlusSlots()
    {
        PackDefinition definition = StandardPackDefinitions.rarePlusItemPack();
        assertEquals(PackContentPool.ALL, definition.getContentPool());
        List<PackSlotDefinition> slots = definition.getSlots();
        assertEquals(5, slots.size());
        assertExact(slots.get(0), Rarity.RARE);
        assertExact(slots.get(1), Rarity.RARE);
        assertExact(slots.get(2), Rarity.RARE);
        assertEquals(Rarity.RARE,
            slots.get(3).getRollTable().getMinimumRarity());
        assertEquals(Rarity.RARE,
            slots.get(4).getRollTable().getMinimumRarity());
        assertEquals(42_000, slots.get(3).getRollTable().getWeight(Rarity.RARE));
        assertEquals(31_000, slots.get(3).getRollTable().getWeight(Rarity.EPIC));
        assertEquals(16_000, slots.get(3).getRollTable().getWeight(Rarity.LEGENDARY));
        assertEquals(8_000, slots.get(3).getRollTable().getWeight(Rarity.MYTHIC));
        assertEquals(3_000, slots.get(3).getRollTable().getWeight(Rarity.GODLY));
    }

    @Test
    public void explorerPackTargetsNoncombatNpcCompletion()
    {
        PackDefinition definition = StandardPackDefinitions.explorerPack();
        assertEquals("pack.explorer.v2", definition.getPackId());
        assertEquals(4_000L, definition.getPrice());
        assertEquals(PackContentPool.NONCOMBAT_NPCS, definition.getContentPool());
        assertEquals(250, definition.getUnownedCardWeightPercent());
        List<PackSlotDefinition> slots = definition.getSlots();
        assertEquals(5, slots.size());
        assertExact(slots.get(0), Rarity.COMMON);
        assertExact(slots.get(1), Rarity.COMMON);
        assertExact(slots.get(2), Rarity.UNCOMMON);
        assertEquals(35_000, slots.get(3).getRollTable().getWeight(Rarity.COMMON));
        assertEquals(65_000, slots.get(3).getRollTable().getWeight(Rarity.UNCOMMON));
        assertEquals(25_000, slots.get(4).getRollTable().getWeight(Rarity.COMMON));
        assertEquals(70_000, slots.get(4).getRollTable().getWeight(Rarity.UNCOMMON));
        assertEquals(5_000, slots.get(4).getRollTable().getWeight(Rarity.RARE));
    }

    @Test
    public void adventurePackTargetsCombatNpcsAcrossPremiumTiers()
    {
        PackDefinition definition = StandardPackDefinitions.adventurePack();
        assertEquals("pack.adventure.v2", definition.getPackId());
        assertEquals(7_500L, definition.getPrice());
        assertEquals(PackContentPool.ATTACKABLE_NPCS, definition.getContentPool());
        assertEquals(150, definition.getUnownedCardWeightPercent());
        List<PackSlotDefinition> slots = definition.getSlots();
        assertEquals(5, slots.size());
        assertEquals(Rarity.UNCOMMON, slots.get(0).getRollTable().getMinimumRarity());
        for (int index = 1; index <= 3; index++)
        {
            assertEquals(Rarity.RARE, slots.get(index).getRollTable().getMinimumRarity());
            assertEquals(42_000, slots.get(index).getRollTable().getWeight(Rarity.RARE));
            assertEquals(31_000, slots.get(index).getRollTable().getWeight(Rarity.EPIC));
            assertEquals(16_000, slots.get(index).getRollTable().getWeight(Rarity.LEGENDARY));
            assertEquals(8_000, slots.get(index).getRollTable().getWeight(Rarity.MYTHIC));
            assertEquals(3_000, slots.get(index).getRollTable().getWeight(Rarity.GODLY));
        }
        assertEquals(Rarity.EPIC, slots.get(4).getRollTable().getMinimumRarity());
        assertEquals(62_000, slots.get(4).getRollTable().getWeight(Rarity.EPIC));
        assertEquals(23_000, slots.get(4).getRollTable().getWeight(Rarity.LEGENDARY));
        assertEquals(10_000, slots.get(4).getRollTable().getWeight(Rarity.MYTHIC));
        assertEquals(5_000, slots.get(4).getRollTable().getWeight(Rarity.GODLY));
    }

    @Test
    public void milestoneTierPacksUseThreeFoilAndTwoNormalSlots()
    {
        for (PackDefinition definition : List.of(
            StandardPackDefinitions.heroPack(),
            StandardPackDefinitions.noblePack(),
            StandardPackDefinitions.legendPack(),
            StandardPackDefinitions.mythicalPack(),
            StandardPackDefinitions.godsPack()))
        {
            assertEquals(PackFoilRule.GUARANTEED,
                definition.getSlots().get(0).getFoilRule());
            assertEquals(PackFoilRule.GUARANTEED,
                definition.getSlots().get(1).getFoilRule());
            assertEquals(PackFoilRule.GUARANTEED,
                definition.getSlots().get(2).getFoilRule());
            assertEquals(PackFoilRule.DISABLED,
                definition.getSlots().get(3).getFoilRule());
            assertEquals(PackFoilRule.DISABLED,
                definition.getSlots().get(4).getFoilRule());
        }
    }

    private static void assertExact(PackSlotDefinition slot, Rarity rarity)
    {
        assertEquals(rarity, slot.getRollTable().getMinimumRarity());
        for (Rarity candidate : Rarity.values())
        {
            assertEquals(candidate == rarity ? RarityRollTable.TOTAL_WEIGHT : 0,
                slot.getRollTable().getWeight(candidate));
        }
    }
}
