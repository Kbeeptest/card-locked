package com.cardrestricted.ui;

import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.presentation.CardArtworkProvider.Artwork;
import com.cardrestricted.presentation.CardArtworkProvider.ArtworkSource;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Deterministic packaged artwork used only when a reviewed card has no Wiki
 * image or RuneLite item sprite. It prevents a catalogue entry from silently
 * appearing as missing while keeping the fallback clearly distinguishable
 * from sourced game artwork.
 */
final class BuiltInCardArtwork
{
    private static final int WIDTH = 420;
    private static final int HEIGHT = 280;

    private BuiltInCardArtwork()
    {
    }

    static Artwork create(CardDefinition card)
    {
        BufferedImage image = new BufferedImage(
            WIDTH,
            HEIGHT,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int hueSeed = Math.floorMod(card.getCardId().hashCode(), 90);
            Color upper = new Color(42 + hueSeed / 5, 36, 48 + hueSeed / 4);
            Color lower = new Color(18, 20 + hueSeed / 8, 28);
            graphics.setPaint(new GradientPaint(
                0,
                0,
                upper,
                WIDTH,
                HEIGHT,
                lower));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            graphics.setColor(new Color(238, 215, 155, 65));
            for (int offset = -HEIGHT; offset < WIDTH; offset += 42)
            {
                graphics.drawLine(offset, HEIGHT, offset + HEIGHT, 0);
            }

            graphics.setColor(new Color(232, 209, 142, 190));
            graphics.drawRoundRect(11, 11, WIDTH - 23, HEIGHT - 23, 26, 26);
            graphics.setColor(new Color(0, 0, 0, 95));
            graphics.fillOval(105, 38, 210, 166);

            String initials = initials(card.getDisplayName());
            Font initialsFont = new Font(Font.SERIF, Font.BOLD, 90);
            graphics.setFont(initialsFont);
            FontMetrics initialsMetrics = graphics.getFontMetrics();
            int initialsX = (WIDTH - initialsMetrics.stringWidth(initials)) / 2;
            int initialsY = 148;
            graphics.setColor(new Color(0, 0, 0, 180));
            graphics.drawString(initials, initialsX + 3, initialsY + 3);
            graphics.setColor(new Color(244, 224, 169));
            graphics.drawString(initials, initialsX, initialsY);

            String label = fitLabel(
                graphics,
                card.getDisplayName(),
                WIDTH - 54);
            Font labelFont = new Font(Font.SANS_SERIF, Font.BOLD, 24);
            graphics.setFont(labelFont);
            FontMetrics labelMetrics = graphics.getFontMetrics();
            int labelX = (WIDTH - labelMetrics.stringWidth(label)) / 2;
            graphics.setColor(new Color(0, 0, 0, 185));
            graphics.drawString(label, labelX + 2, 239 + 2);
            graphics.setColor(new Color(238, 226, 198));
            graphics.drawString(label, labelX, 239);
        }
        finally
        {
            graphics.dispose();
        }
        return new Artwork(image, false, ArtworkSource.BUILT_IN_FALLBACK);
    }

    private static String initials(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return "?";
        }
        StringBuilder result = new StringBuilder(3);
        for (String word : value.trim().split("\\s+"))
        {
            if (!word.isEmpty() && Character.isLetterOrDigit(word.charAt(0)))
            {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (result.length() == 3)
                {
                    break;
                }
            }
        }
        return result.length() == 0 ? "?" : result.toString();
    }

    private static String fitLabel(
        Graphics2D graphics,
        String value,
        int maximumWidth)
    {
        String label = value == null ? "Unknown card" : value.trim();
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        FontMetrics metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(label) <= maximumWidth)
        {
            return label;
        }
        String suffix = "…";
        while (label.length() > 1
            && metrics.stringWidth(label + suffix) > maximumWidth)
        {
            label = label.substring(0, label.length() - 1).trim();
        }
        return label + suffix;
    }
}
