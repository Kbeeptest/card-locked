package com.cardrestricted.ui;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.foil.FoilRewardKind;
import com.cardrestricted.foil.FoilRewardRegistry;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AlbumReleasePolishTest
{
    @Test
    public void windowIconUsesTheCardLockedCardBack()
    {
        BufferedImage icon = CardUiAssets.windowIcon(32);

        assertEquals(32, icon.getWidth());
        assertEquals(32, icon.getHeight());
        int opaquePixels = 0;
        int warmPixels = 0;
        for (int y = 0; y < icon.getHeight(); y++)
        {
            for (int x = 0; x < icon.getWidth(); x++)
            {
                int argb = icon.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha > 0)
                {
                    opaquePixels++;
                    int red = (argb >>> 16) & 0xff;
                    int green = (argb >>> 8) & 0xff;
                    if (red > green && red > 70)
                    {
                        warmPixels++;
                    }
                }
            }
        }
        assertTrue(opaquePixels > 250);
        assertTrue(warmPixels > 20);
    }

    @Test
    public void combatAndNonCombatNpcClassificationIsDataBacked()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        CardDetailMetadata metadata = CardDetailMetadata.load(
            getClass().getClassLoader());
        List<CardDefinition> npcs = catalogue.getCards().stream()
            .filter(card -> card.getCardType() == CardType.NPC)
            .collect(Collectors.toList());
        long combat = npcs.stream().filter(metadata::hasCombatLevel).count();
        long nonCombat = npcs.size() - combat;

        assertTrue(combat >= 1_150);
        assertTrue(nonCombat >= 500);
        assertTrue(metadata.hasCombatLevel(
            catalogue.requireCard("npc.abhorrent_spectre.7402")));
        assertFalse(metadata.hasCombatLevel(
            catalogue.requireCard("npc.elnock_inquisitor.5734")));
    }

    @Test
    public void combatNpcPanelsHaveBroadStructuredCoverage()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        CardDetailMetadata metadata = CardDetailMetadata.load(
            getClass().getClassLoader());
        List<CardDefinition> combat = catalogue.getCards().stream()
            .filter(card -> card.getCardType() == CardType.NPC)
            .filter(metadata::hasCombatLevel)
            .collect(Collectors.toList());

        assertTrue(count(combat, metadata, Field.HITPOINTS) >= 1_140);
        assertTrue(count(combat, metadata, Field.MAX_HIT) >= 1_110);
        assertTrue(count(combat, metadata, Field.ATTACK_STYLES) >= 1_120);
        assertTrue(count(combat, metadata, Field.AGGRESSION) >= 1_130);
        assertTrue(count(combat, metadata, Field.ATTACK_SPEED) >= 1_120);
    }

    @Test
    public void reviewedBossSourcesExposeTheirExclusiveDrops()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        FoilRewardRegistry registry = FoilRewardRegistry.load(
            getClass().getClassLoader(), catalogue);

        assertEquals(37, registry.getSourceCardIdsForKind(
            FoilRewardKind.SOURCE_UNIQUES).size());
        assertTrue(registry.getTargetCardIdsForSource("npc.giant_mole.5779")
            .containsAll(List.of("item.mole_claw.7416", "item.mole_skin.7418")));
        assertTrue(registry.getTargetCardIdsForSource("npc.dusk.7851")
            .contains("item.black_tourmaline_core.21730"));
        assertTrue(registry.getTargetCardIdsForSource("npc.bryophyta.8195")
            .contains("item.bryophyta_s_essence.22372"));
        assertFalse(registry.getTargetCardIdsForSource("npc.bryophyta.8195")
            .contains("item.bryophyta_s_staff.22370"));
    }

    private static long count(
        List<CardDefinition> cards,
        CardDetailMetadata metadata,
        Field field)
    {
        return cards.stream()
            .map(metadata::detail)
            .map(field::value)
            .filter(value -> !value.isEmpty())
            .count();
    }

    private enum Field
    {
        HITPOINTS { @Override String value(CardDetailMetadata.Detail d) { return d.getHitpoints(); } },
        MAX_HIT { @Override String value(CardDetailMetadata.Detail d) { return d.getMaxHit(); } },
        ATTACK_STYLES { @Override String value(CardDetailMetadata.Detail d) { return d.getAttackStyles(); } },
        AGGRESSION { @Override String value(CardDetailMetadata.Detail d) { return d.getAggression(); } },
        ATTACK_SPEED { @Override String value(CardDetailMetadata.Detail d) { return d.getAttackSpeed(); } };

        abstract String value(CardDetailMetadata.Detail detail);
    }
}
