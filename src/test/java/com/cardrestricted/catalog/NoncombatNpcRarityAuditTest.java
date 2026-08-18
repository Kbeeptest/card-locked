package com.cardrestricted.catalog;

import com.cardrestricted.domain.ActionType;
import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class NoncombatNpcRarityAuditTest
{
    @Test
    public void cl908NoncombatRarityTailRemainsSelective()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values())
        {
            counts.put(rarity, 0);
        }
        int total = 0;
        for (CardDefinition card : catalogue.getCards())
        {
            if (card.getCardType() != CardType.NPC
                || card.getPermissions().contains(ActionType.NPC_ATTACK))
            {
                continue;
            }
            total++;
            counts.put(card.getRarity(), counts.get(card.getRarity()) + 1);
        }
        assertEquals(752, total);
        assertEquals(Integer.valueOf(637), counts.get(Rarity.COMMON));
        assertEquals(Integer.valueOf(112), counts.get(Rarity.UNCOMMON));
        assertEquals(Integer.valueOf(2), counts.get(Rarity.RARE));
        assertEquals(Integer.valueOf(0), counts.get(Rarity.EPIC));
        assertEquals(Integer.valueOf(0), counts.get(Rarity.LEGENDARY));
        assertEquals(Integer.valueOf(0), counts.get(Rarity.MYTHIC));
        assertEquals(Integer.valueOf(1), counts.get(Rarity.GODLY));

        assertEquals(Rarity.RARE, catalogue.requireCard("npc.duradel.13622").getRarity());
        assertEquals(Rarity.RARE, catalogue.requireCard("npc.konar_quo_maten.8623").getRarity());
        assertEquals(Rarity.UNCOMMON, catalogue.requireCard("npc.nieve.6797").getRarity());
        assertEquals(Rarity.UNCOMMON, catalogue.requireCard("npc.steve.6798").getRarity());
        assertEquals(Rarity.UNCOMMON, catalogue.requireCard("npc.krystilia.7663").getRarity());
    }
}
