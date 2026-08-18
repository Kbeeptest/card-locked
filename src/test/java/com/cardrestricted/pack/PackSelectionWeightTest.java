package com.cardrestricted.pack;

import com.cardrestricted.catalog.CardCategory;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.domain.ActionType;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class PackSelectionWeightTest
{
    @Test
    public void explorerWeightMateriallyFavoursMissingCardWithoutGuaranteeingIt()
    {
        CardDefinition owned = card("npc.owned", "Owned");
        CardDefinition missing = card("npc.missing", "Missing");
        List<CardDefinition> pool = List.of(owned, missing);
        Set<String> ownedIds = Set.of(owned.getCardId());
        Random random = new Random(908L);
        int missingHits = 0;
        int trials = 20_000;
        for (int index = 0; index < trials; index++)
        {
            if (StandardPackService.selectWeightedCard(
                pool, ownedIds, 250, random) == missing)
            {
                missingHits++;
            }
        }
        double rate = missingHits / (double) trials;
        assertTrue("2.5x weighting should yield about 71% in a 1-owned/1-missing pool",
            rate > 0.69 && rate < 0.74);
        assertTrue("Weighting must not become a guarantee", missingHits < trials);
    }

    @Test
    public void adventureWeightProvidesMilderMissingCardPreference()
    {
        CardDefinition owned = card("npc.owned", "Owned");
        CardDefinition missing = card("npc.missing", "Missing");
        List<CardDefinition> pool = List.of(owned, missing);
        Set<String> ownedIds = Set.of(owned.getCardId());
        Random random = new Random(909L);
        int missingHits = 0;
        int trials = 20_000;
        for (int index = 0; index < trials; index++)
        {
            if (StandardPackService.selectWeightedCard(
                pool, ownedIds, 150, random) == missing)
            {
                missingHits++;
            }
        }
        double rate = missingHits / (double) trials;
        assertTrue("1.5x weighting should yield about 60% in a 1-owned/1-missing pool",
            rate > 0.58 && rate < 0.62);
    }

    @Test
    public void standardTypeWeightSuppressesNpcAndMildlyFavoursEquipment()
    {
        CardDefinition npc = card("npc.standard", "NPC");
        CardDefinition utility = item(
            "item.utility", "Utility", ActionType.ITEM_ACTIVATE);
        CardDefinition equipment = item(
            "item.equipment", "Equipment", ActionType.ITEM_EQUIP);
        List<CardDefinition> pool = List.of(npc, utility, equipment);
        Random random = new Random(9091L);
        int npcHits = 0;
        int utilityHits = 0;
        int equipmentHits = 0;
        int trials = 100_000;
        for (int index = 0; index < trials; index++)
        {
            CardDefinition selected = StandardPackService.selectWeightedCard(
                pool,
                Set.of(),
                100,
                40,
                115,
                random);
            if (selected == npc)
            {
                npcHits++;
            }
            else if (selected == utility)
            {
                utilityHits++;
            }
            else if (selected == equipment)
            {
                equipmentHits++;
            }
        }
        double npcRate = npcHits / (double) trials;
        double utilityRate = utilityHits / (double) trials;
        double equipmentRate = equipmentHits / (double) trials;
        assertTrue("NPC weighting should materially suppress general-pack NPCs",
            npcRate > 0.145 && npcRate < 0.170);
        assertTrue("Neutral utility item should remain near its expected share",
            utilityRate > 0.375 && utilityRate < 0.410);
        assertTrue("Equipment should receive the intended mild preference",
            equipmentRate > 0.435 && equipmentRate < 0.465);
    }

    private static CardDefinition card(String id, String name)
    {
        return new CardDefinition(
            id,
            name,
            CardType.NPC,
            Rarity.COMMON,
            Set.of(CardCategory.NPC_TARGET),
            "family." + id,
            Set.of(ActionType.NPC_SERVICE),
            false,
            1);
    }

    private static CardDefinition item(
        String id,
        String name,
        ActionType permission)
    {
        return new CardDefinition(
            id,
            name,
            CardType.ITEM,
            Rarity.COMMON,
            Set.of(CardCategory.SUPPORT),
            "family." + id,
            Set.of(permission),
            false,
            1);
    }
}
