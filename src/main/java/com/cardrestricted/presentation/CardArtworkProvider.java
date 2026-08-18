package com.cardrestricted.presentation;

import com.cardrestricted.catalog.CardDefinition;
import java.awt.image.BufferedImage;

public interface CardArtworkProvider
{
    Artwork getArtwork(CardDefinition card);

    /**
     * Returns artwork that is fully packaged with the plugin. Runtime-only
     * fallbacks such as asynchronous RuneLite item sprites must not be used.
     */
    default Artwork getPackagedArtwork(CardDefinition card)
    {
        return getArtwork(card);
    }

    default ArtworkSource getPackagedArtworkSource(CardDefinition card)
    {
        Artwork artwork = getPackagedArtwork(card);
        return artwork == null
            ? ArtworkSource.NONE
            : artwork.getSource();
    }

    default ArtworkSource getArtworkSource(CardDefinition card)
    {
        Artwork artwork = getArtwork(card);
        return artwork == null
            ? ArtworkSource.NONE
            : artwork.getSource();
    }

    /** Queues artwork for a bounded visible set without requiring it immediately. */
    default void prefetch(Iterable<CardDefinition> cards)
    {
        // Providers without asynchronous artwork have nothing to prefetch.
    }

    /**
     * Returns the number of catalogue cards with an explicit artwork mapping,
     * or {@code -1} when the provider does not expose mapping statistics.
     */
    default int mappedArtworkCount()
    {
        return -1;
    }

    static CardArtworkProvider none()
    {
        return card -> null;
    }

    enum ArtworkSource
    {
        NONE,
        OSRS_WIKI,
        ITEM_SPRITE,
        BUILT_IN_FALLBACK,
        OTHER
    }

    final class Artwork
    {
        private final BufferedImage image;
        private final boolean pixelArt;
        private final ArtworkSource source;

        public Artwork(BufferedImage image, boolean pixelArt)
        {
            this(image, pixelArt, ArtworkSource.OTHER);
        }

        public Artwork(
            BufferedImage image,
            boolean pixelArt,
            ArtworkSource source)
        {
            this.image = image;
            this.pixelArt = pixelArt;
            this.source = source == null
                ? ArtworkSource.OTHER
                : source;
        }

        public BufferedImage getImage()
        {
            return image;
        }

        public boolean isPixelArt()
        {
            return pixelArt;
        }

        public ArtworkSource getSource()
        {
            return source;
        }
    }
}
