package com.cardrestricted.ui;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.presentation.CardArtworkProvider.Artwork;
import com.cardrestricted.presentation.CardArtworkProvider.ArtworkSource;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class BuiltInCardArtworkTest
{
    @Test
    public void pendingNpcMappingsReceiveDeterministicPackagedFallback()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        for (String cardId : Arrays.asList(
            "npc.evil_creature.1241",
            "npc.koschei_the_deathless.3897",
            "npc.loar_shade.1277",
            "npc.naiatli.13838",
            "npc.solus_dellagar.4962"))
        {
            CardDefinition card = catalogue.findCard(cardId)
                .orElseThrow(AssertionError::new);
            Artwork first = BuiltInCardArtwork.create(card);
            Artwork second = BuiltInCardArtwork.create(card);
            assertEquals(ArtworkSource.BUILT_IN_FALLBACK, first.getSource());
            assertNotNull(first.getImage());
            assertEquals(imageHash(first.getImage()), imageHash(second.getImage()));
        }
    }

    private static long imageHash(BufferedImage image)
    {
        long hash = 1125899906842597L;
        for (int y = 0; y < image.getHeight(); y += 7)
        {
            for (int x = 0; x < image.getWidth(); x += 7)
            {
                hash = 31L * hash + image.getRGB(x, y);
            }
        }
        return hash;
    }
}
