package com.cardrestricted.ui;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.presentation.CardArtworkProvider;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Headless visual invariants for album and sidebar card rendering. */
public final class CardPresentationVerification
{
    private CardPresentationVerification()
    {
    }

    public static void main(String[] args)
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        CardDefinition card = catalogue.getCards().stream()
            .filter(candidate -> candidate.getDisplayName().length() >= 12)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing presentation card."));
        CardArtworkProvider provider = ignored -> new CardArtworkProvider.Artwork(
            sampleArtwork(),
            false,
            CardArtworkProvider.ArtworkSource.OTHER);

        BufferedImage owned = CardUiAssets.cardThumbnail(
            card, provider, true, 150, 225);
        BufferedImage locked = CardUiAssets.cardThumbnail(
            card, provider, false, 150, 225);
        BufferedImage sidebar = CardUiAssets.cardThumbnail(
            card, provider, true, 44, 66);
        BufferedImage detail = CardUiAssets.cardThumbnail(
            card, provider, true, 240, 360);
        BufferedImage foil = CardUiAssets.cardThumbnail(
            card, provider, true, true, 150, 225);
        BufferedImage foilAccess = CardUiAssets.cardThumbnail(
            card, provider, false, false, true, 150, 225);

        require(owned.getWidth() == 150 && owned.getHeight() == 225,
            "Album card dimensions changed.");
        require(sidebar.getWidth() == 44 && sidebar.getHeight() == 66,
            "Sidebar card dimensions changed.");
        require(detail.getWidth() == 240 && detail.getHeight() == 360,
            "Detail card dimensions changed.");

        require(colourVariation(owned, 15, 137, 135, 40) > 12,
            "Album description panel appears blank or unpainted.");
        require(colourVariation(detail, 24, 220, 192, 65) > 12,
            "Detail description panel appears blank or unpainted.");
        require(averageSaturation(locked) < averageSaturation(owned) * 0.35,
            "Locked album cards are not visibly greyscaled.");
        require(nonTransparentPixels(sidebar) > sidebar.getWidth() * sidebar.getHeight() / 2,
            "Sidebar thumbnail is unexpectedly blank.");
        require(pixelDifference(owned, foil)
                > owned.getWidth() * owned.getHeight() * 9 / 10,
            "Foil thumbnails are not unmistakably distinct from normal cards.");
        require(averageSaturation(foilAccess) < averageSaturation(owned) * 0.50,
            "Foil-access cards must remain visibly unowned and greyscaled.");
        require(pixelDifference(locked, foilAccess)
                > locked.getWidth() * locked.getHeight() / 20,
            "Foil-access cards are not visibly distinct from locked cards.");
        require(cyanPixels(foilAccess) > 80,
            "Foil-access cards are missing the cyan glow or USABLE badge.");

        System.out.println("Card presentation rendering verification passed.");
    }

    private static BufferedImage sampleArtwork()
    {
        BufferedImage image = new BufferedImage(
            96, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(new Color(40, 110, 180));
            graphics.fillRect(0, 0, 96, 96);
            graphics.setColor(new Color(235, 190, 60));
            graphics.fillOval(16, 10, 64, 76);
            graphics.setColor(new Color(110, 35, 120));
            graphics.fillRect(36, 24, 24, 48);
        }
        finally
        {
            graphics.dispose();
        }
        return image;
    }


    private static int cyanPixels(BufferedImage image)
    {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                Color colour = new Color(image.getRGB(x, y), true);
                if (colour.getAlpha() > 80
                    && colour.getBlue() > 150
                    && colour.getGreen() > 120
                    && colour.getBlue() > colour.getRed() + 35)
                {
                    count++;
                }
            }
        }
        return count;
    }

    private static int pixelDifference(
        BufferedImage left,
        BufferedImage right)
    {
        int difference = 0;
        for (int y = 0; y < Math.min(left.getHeight(), right.getHeight()); y++)
        {
            for (int x = 0; x < Math.min(left.getWidth(), right.getWidth()); x++)
            {
                if (left.getRGB(x, y) != right.getRGB(x, y))
                {
                    difference++;
                }
            }
        }
        return difference;
    }

    private static int colourVariation(
        BufferedImage image,
        int x,
        int y,
        int width,
        int height)
    {
        java.util.Set<Integer> colours = new java.util.HashSet<>();
        int maxX = Math.min(image.getWidth(), x + width);
        int maxY = Math.min(image.getHeight(), y + height);
        for (int py = Math.max(0, y); py < maxY; py++)
        {
            for (int px = Math.max(0, x); px < maxX; px++)
            {
                colours.add(image.getRGB(px, py));
            }
        }
        return colours.size();
    }

    private static double averageSaturation(BufferedImage image)
    {
        double total = 0.0;
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                int argb = image.getRGB(x, y);
                if (((argb >>> 24) & 0xff) == 0)
                {
                    continue;
                }
                Color colour = new Color(argb, true);
                float[] hsb = Color.RGBtoHSB(
                    colour.getRed(),
                    colour.getGreen(),
                    colour.getBlue(),
                    null);
                total += hsb[1];
                count++;
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    private static int nonTransparentPixels(BufferedImage image)
    {
        int result = 0;
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                if (((image.getRGB(x, y) >>> 24) & 0xff) != 0)
                {
                    result++;
                }
            }
        }
        return result;
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
