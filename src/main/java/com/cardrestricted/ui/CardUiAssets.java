package com.cardrestricted.ui;

import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CatalogueTextQuality;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.presentation.CardArtworkProvider;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Random;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

final class CardUiAssets
{
    private static final Map<String, BufferedImage> CACHE =
        new ConcurrentHashMap<>();

    private CardUiAssets()
    {
    }

    static ImageIcon icon(String resource, int width, int height)
    {
        BufferedImage source = load(resource);
        if (source == null)
        {
            return new ImageIcon(new BufferedImage(
                Math.max(1, width),
                Math.max(1, height),
                BufferedImage.TYPE_INT_ARGB));
        }
        return new ImageIcon(scale(source, width, height));
    }

    static BufferedImage cardBack(int width, int height)
    {
        BufferedImage source = load("/com/cardrestricted/ui/card-back.png");
        if (source == null)
        {
            return new BufferedImage(
                Math.max(1, width),
                Math.max(1, height),
                BufferedImage.TYPE_INT_ARGB);
        }
        return scale(source, width, height);
    }

    static BufferedImage windowIcon(int size)
    {
        int safeSize = Math.max(16, size);
        BufferedImage icon = new BufferedImage(
            safeSize,
            safeSize,
            BufferedImage.TYPE_INT_ARGB);
        BufferedImage card = cardBack(
            Math.max(10, safeSize * 2 / 3),
            safeSize);
        Graphics2D graphics = icon.createGraphics();
        try
        {
            configure(graphics);
            graphics.drawImage(
                card,
                (safeSize - card.getWidth()) / 2,
                0,
                null);
        }
        finally
        {
            graphics.dispose();
        }
        return icon;
    }

    static BufferedImage cardThumbnail(
        CardDefinition card,
        CardArtworkProvider artworkProvider,
        boolean owned,
        int width,
        int height)
    {
        return cardThumbnail(
            card,
            artworkProvider,
            owned,
            false,
            false,
            width,
            height);
    }

    static BufferedImage cardThumbnail(
        CardDefinition card,
        CardArtworkProvider artworkProvider,
        boolean owned,
        boolean foil,
        int width,
        int height)
    {
        return cardThumbnail(
            card,
            artworkProvider,
            owned,
            foil,
            false,
            width,
            height);
    }

    static BufferedImage cardThumbnail(
        CardDefinition card,
        CardArtworkProvider artworkProvider,
        boolean owned,
        boolean foil,
        boolean foilAccess,
        int width,
        int height)
    {
        String framePath = "/com/cardrestricted/ui/card-frame-"
            + card.getRarity().name().toLowerCase(Locale.ROOT)
            + ".png";
        BufferedImage frame = load(framePath);
        BufferedImage image = frame == null
            ? new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            : scale(frame, width, height);
        Graphics2D g = image.createGraphics();
        try
        {
            configure(g);
            paintOwnedCard(g, image, card, artworkProvider);
        }
        finally
        {
            g.dispose();
        }
        if (!owned)
        {
            applyLockedTreatment(image);
        }
        if (foil)
        {
            applyFoilTreatment(image, card.getCardId());
        }
        else if (foilAccess)
        {
            applyFoilAccessTreatment(image);
        }
        return image;
    }


    private static void applyFoilTreatment(
        BufferedImage image,
        String cardId)
    {
        Graphics2D g = image.createGraphics();
        try
        {
            configure(g);
            Composite previous = g.getComposite();
            int width = image.getWidth();
            int height = image.getHeight();
            int seed = cardId.hashCode();
            int band = Math.max(16, width / 5);
            int offset = Math.floorMod(seed, band * 4);

            g.setComposite(AlphaComposite.SrcOver.derive(0.48f));
            for (int x = -band * 3 + offset;
                 x < width + height + band * 3;
                 x += band)
            {
                float hue = Math.floorMod(x / Math.max(1, band), 14) / 14.0f;
                Color spectrum = Color.getHSBColor(hue, 0.54f, 1.0f);
                g.setColor(new Color(
                    spectrum.getRed(),
                    spectrum.getGreen(),
                    spectrum.getBlue(),
                    72));
                Polygon stripe = new Polygon(
                    new int[]{x, x + band, x - height + band, x - height},
                    new int[]{height, height, 0, 0},
                    4);
                g.fillPolygon(stripe);
            }

            g.setComposite(AlphaComposite.SrcOver.derive(0.24f));
            g.setPaint(new GradientPaint(
                0,
                height,
                new Color(120, 255, 235, 66),
                width,
                0,
                new Color(255, 148, 214, 104),
                true));
            g.fillRect(0, 0, width, height);
            g.setComposite(AlphaComposite.SrcOver.derive(0.20f));
            g.setPaint(new GradientPaint(
                width * 0.08f,
                height,
                new Color(255, 255, 255, 0),
                width * 0.88f,
                height * 0.12f,
                new Color(255, 248, 236, 190),
                true));
            g.fillRect(0, 0, width, height);

            Random random = new Random(seed * 31L + width * 17L + height);
            int sparkleCount = Math.max(18, width / 8);
            for (int index = 0; index < sparkleCount; index++)
            {
                int sparkleX = random.nextInt(Math.max(1, width));
                int sparkleY = random.nextInt(Math.max(1, height));
                int arm = Math.max(2, width / 70) + random.nextInt(Math.max(2, width / 55));
                int alpha = 110 + random.nextInt(100);
                drawTwinkle(g, sparkleX, sparkleY, arm, alpha);
            }

            int border = Math.max(3, width / 46);
            int badgeWidth = Math.max(28, width / 5);
            int badgeHeight = Math.max(12, height / 17);
            int badgeX = width - badgeWidth - border;
            int badgeY = height - badgeHeight - border;
            paintFoilBadge(g, badgeX, badgeY, badgeWidth, badgeHeight);
            g.setComposite(previous);
        }
        finally
        {
            g.dispose();
        }
    }


    private static void applyFoilAccessTreatment(BufferedImage image)
    {
        Graphics2D g = image.createGraphics();
        try
        {
            configure(g);
            Composite previous = g.getComposite();
            int width = image.getWidth();
            int height = image.getHeight();
            int inset = Math.max(2, width / 55);
            int arc = Math.max(12, width / 8);

            for (int layer = 5; layer >= 1; layer--)
            {
                int offset = inset + layer - 1;
                int alpha = 18 + (6 - layer) * 15;
                g.setComposite(AlphaComposite.SrcOver.derive(alpha / 255.0f));
                g.setColor(new Color(91, 205, 255));
                g.setStroke(new BasicStroke(Math.max(1f, layer * 0.9f)));
                g.drawRoundRect(
                    offset,
                    offset,
                    Math.max(1, width - offset * 2 - 1),
                    Math.max(1, height - offset * 2 - 1),
                    arc,
                    arc);
            }

            int border = Math.max(3, width / 46);
            int badgeWidth = Math.max(44, width * 7 / 20);
            int badgeHeight = Math.max(12, height / 17);
            int badgeX = width - badgeWidth - border;
            int badgeY = height - badgeHeight - border;
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(14, 47, 68, 238));
            g.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 8, 8);
            g.setColor(new Color(111, 218, 255));
            g.setStroke(new BasicStroke(Math.max(1f, width / 120f)));
            g.drawRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 8, 8);
            g.setColor(new Color(226, 249, 255));
            g.setFont(new Font(
                Font.SANS_SERIF,
                Font.BOLD,
                Math.max(7, badgeHeight / 2)));
            drawCentredInBox(
                g,
                "USABLE",
                new Rectangle(badgeX, badgeY, badgeWidth, badgeHeight));
            g.setComposite(previous);
        }
        finally
        {
            g.dispose();
        }
    }

    private static void paintFoilBadge(Graphics2D g, int x, int y, int width, int height)
    {
        g.setColor(new Color(39, 18, 58, 228));
        g.fillRoundRect(x, y, width, height, 8, 8);
        g.setColor(new Color(255, 225, 255));
        g.setFont(new Font(
            Font.SANS_SERIF,
            Font.BOLD,
            Math.max(8, height * 3 / 5)));
        drawCentredInBox(g, "FOIL", new Rectangle(x, y, width, height));
    }

    private static void drawTwinkle(Graphics2D g, int x, int y, int arm, int alpha)
    {
        g.setColor(new Color(255, 255, 248, Math.max(0, Math.min(255, alpha))));
        g.drawLine(x - arm, y, x + arm, y);
        g.drawLine(x, y - arm, x, y + arm);
        int diag = Math.max(1, Math.round(arm * 0.58f));
        g.drawLine(x - diag, y - diag, x + diag, y + diag);
        g.drawLine(x - diag, y + diag, x + diag, y - diag);
    }

    private static String presentDisplayName(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        return raw.replace(" (cr)", "").replace("(cr)", "").trim();
    }
    private static void applyLockedTreatment(BufferedImage image)
    {
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                int red = (argb >>> 16) & 0xff;
                int green = (argb >>> 8) & 0xff;
                int blue = argb & 0xff;
                int grey = (red * 54 + green * 183 + blue * 19) >>> 8;
                int dimmed = Math.max(0, Math.min(255, Math.round(grey * 0.78f)));
                image.setRGB(
                    x,
                    y,
                    (alpha << 24) | (dimmed << 16) | (dimmed << 8) | dimmed);
            }
        }
        Graphics2D overlay = image.createGraphics();
        try
        {
            configure(overlay);
            overlay.setColor(new Color(35, 38, 44, 26));
            overlay.fillRoundRect(
                0,
                0,
                image.getWidth(),
                image.getHeight(),
                14,
                14);
        }
        finally
        {
            overlay.dispose();
        }
    }

    private static void paintOwnedCard(
        Graphics2D g,
        BufferedImage image,
        CardDefinition card,
        CardArtworkProvider artworkProvider)
    {
        Rectangle bounds = new Rectangle(0, 0, image.getWidth(), image.getHeight());
        Rectangle titleBox = scaleRect(bounds, 70, 54, 884, 128);
        Rectangle artBox = inset(
            scaleRect(bounds, 69, 218, 886, 690),
            Math.max(2, image.getWidth() / 38),
            Math.max(2, image.getHeight() / 56));
        Rectangle examineBox = inset(
            scaleRect(bounds, 69, 1024, 886, 378),
            Math.max(3, image.getWidth() / 30),
            Math.max(3, image.getHeight() / 50));
        Rectangle typeBox = scaleRect(bounds, 70, 1427, 884, 62);

        String displayName = presentDisplayName(card.getDisplayName());
        int titleMaxWidth = titleBox.width - Math.max(10, image.getWidth() / 12);
        Font title = fitFont(
            g,
            displayName,
            Math.max(11, image.getHeight() / 17),
            Math.max(7, image.getHeight() / 36),
            titleMaxWidth);
        g.setFont(title);
        String paintedDisplayName = ellipsizeToWidth(
            g.getFontMetrics(title),
            displayName,
            titleMaxWidth);
        Color nameColor = titleColor(card.getRarity());
        g.setColor(new Color(0, 0, 0, 205));
        drawCentredInBox(g, paintedDisplayName, translate(titleBox, -1, 0));
        drawCentredInBox(g, paintedDisplayName, translate(titleBox, 1, 0));
        drawCentredInBox(g, paintedDisplayName, translate(titleBox, 0, -1));
        drawCentredInBox(g, paintedDisplayName, translate(titleBox, 0, 1));
        g.setColor(nameColor);
        drawCentredInBox(g, paintedDisplayName, titleBox);

        CardArtworkProvider.Artwork artwork = artworkProvider.getArtwork(card);
        if (artwork != null && artwork.getImage() != null)
        {
            drawArtwork(
                g,
                artwork.getImage(),
                artBox.x,
                artBox.y,
                artBox.width,
                artBox.height,
                artwork.isPixelArt());
        }
        else
        {
            String initials = initials(displayName);
            g.setFont(new Font(
                Font.MONOSPACED,
                Font.BOLD,
                Math.max(18, image.getWidth() / 5)));
            g.setColor(new Color(255, 232, 154));
            drawCentredInBox(g, initials, artBox);
        }

        drawExamineText(
            g,
            CatalogueTextQuality.cardDisplayText(card),
            examineBox,
            image.getHeight() >= 300 ? 5 : 4);

        String type = card.getCardType().name().replace('_', ' ');
        g.setFont(fitFont(
            g,
            type,
            Math.max(8, image.getHeight() / 38),
            Math.max(7, image.getHeight() / 48),
            typeBox.width - Math.max(8, image.getWidth() / 12)));
        g.setColor(new Color(226, 213, 188));
        drawCentredInBox(g, type, typeBox);
    }

    private static void drawExamineText(
        Graphics2D g,
        String text,
        Rectangle bounds,
        int maxLines)
    {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty())
        {
            return;
        }
        int maxWidth = Math.max(20, bounds.width - Math.max(6, bounds.width / 14));
        int maximum = Math.max(9, bounds.height / 4);
        int minimum = Math.max(7, bounds.height / 7);
        for (int size = maximum; size >= minimum; size--)
        {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
            java.util.List<String> lines = wrappedLines(
                g.getFontMetrics(),
                value,
                maxWidth,
                maxLines);
            if (lines.size() * g.getFontMetrics().getHeight() <= bounds.height)
            {
                g.setColor(new Color(48, 37, 28));
                drawWrappedLines(g, lines, bounds);
                return;
            }
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, minimum));
        g.setColor(new Color(48, 37, 28));
        drawWrappedLines(
            g,
            wrappedLines(g.getFontMetrics(), value, maxWidth, maxLines),
            bounds);
    }

    private static java.util.List<String> wrappedLines(
        FontMetrics metrics,
        String value,
        int maxWidth,
        int maxLines)
    {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = value.split("\\s+");
        int consumed = 0;
        for (String word : words)
        {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (metrics.stringWidth(candidate) > maxWidth && line.length() > 0)
            {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
                if (lines.size() == maxLines)
                {
                    break;
                }
            }
            else
            {
                if (line.length() > 0)
                {
                    line.append(' ');
                }
                line.append(word);
            }
            consumed++;
        }
        if (line.length() > 0 && lines.size() < maxLines)
        {
            lines.add(line.toString());
        }
        boolean truncated = consumed < words.length;
        if (!truncated && !lines.isEmpty())
        {
            int represented = 0;
            for (String rendered : lines)
            {
                represented += rendered.split("\\s+").length;
            }
            truncated = represented < words.length;
        }
        if (truncated && !lines.isEmpty())
        {
            int last = lines.size() - 1;
            String lastLine = lines.get(last);
            while (!lastLine.isEmpty()
                && metrics.stringWidth(lastLine + "...") > maxWidth)
            {
                int cut = lastLine.lastIndexOf(' ');
                lastLine = cut < 0 ? lastLine.substring(
                    0,
                    Math.max(0, lastLine.length() - 1)) : lastLine.substring(0, cut);
            }
            lines.set(last, lastLine.isEmpty() ? "..." : lastLine + "...");
        }
        return lines;
    }

    private static void drawWrappedLines(
        Graphics2D g,
        java.util.List<String> lines,
        Rectangle bounds)
    {
        FontMetrics metrics = g.getFontMetrics();
        int totalHeight = lines.size() * metrics.getHeight();
        int baseline = bounds.y
            + Math.max(
                metrics.getAscent(),
                (bounds.height - totalHeight) / 2 + metrics.getAscent());
        for (String line : lines)
        {
            drawCentred(g, line, (int) bounds.getCenterX(), baseline);
            baseline += metrics.getHeight();
        }
    }

    private static Rectangle scaleRect(
        Rectangle bounds,
        int x,
        int y,
        int width,
        int height)
    {
        return new Rectangle(
            bounds.x + Math.round(bounds.width * x / 1024f),
            bounds.y + Math.round(bounds.height * y / 1536f),
            Math.max(1, Math.round(bounds.width * width / 1024f)),
            Math.max(1, Math.round(bounds.height * height / 1536f)));
    }

    private static Rectangle inset(Rectangle bounds, int x, int y)
    {
        return new Rectangle(
            bounds.x + x,
            bounds.y + y,
            Math.max(1, bounds.width - x * 2),
            Math.max(1, bounds.height - y * 2));
    }

    private static Rectangle translate(Rectangle bounds, int x, int y)
    {
        return new Rectangle(
            bounds.x + x,
            bounds.y + y,
            bounds.width,
            bounds.height);
    }

    private static void drawCentredInBox(
        Graphics2D g,
        String text,
        Rectangle bounds)
    {
        java.awt.geom.Rectangle2D visual = g.getFontMetrics()
            .getStringBounds(text, g);
        double drawX = bounds.getCenterX()
            - (visual.getWidth() / 2.0)
            - visual.getX();
        double drawY = bounds.getCenterY()
            - (visual.getHeight() / 2.0)
            - visual.getY();
        g.drawString(text, (float) drawX, (float) drawY);
    }

    private static void drawWrapped(
        Graphics2D g,
        String text,
        int centerX,
        int y,
        int maxWidth,
        int maxHeight,
        int maxLines)
    {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty())
        {
            return;
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        boolean truncated = false;
        for (String word : value.split("\\s+"))
        {
            String candidate = line.length() == 0
                ? word
                : line + " " + word;
            if (g.getFontMetrics().stringWidth(candidate) > maxWidth
                && line.length() > 0)
            {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
                if (lines.size() >= maxLines)
                {
                    truncated = true;
                    break;
                }
            }
            else
            {
                if (line.length() > 0)
                {
                    line.append(' ');
                }
                line.append(word);
            }
        }
        if (line.length() > 0 && lines.size() < maxLines)
        {
            lines.add(line.toString());
        }
        if (truncated && !lines.isEmpty())
        {
            int last = lines.size() - 1;
            String lastLine = lines.get(last);
            while (!lastLine.isEmpty()
                && g.getFontMetrics().stringWidth(lastLine + "...") > maxWidth)
            {
                int cut = lastLine.lastIndexOf(' ');
                lastLine = cut < 0 ? "" : lastLine.substring(0, cut);
            }
            lines.set(last, lastLine.isEmpty() ? "..." : lastLine + "...");
        }
        FontMetrics metrics = g.getFontMetrics();
        int totalHeight = lines.size() * metrics.getHeight();
        int baseline = y + Math.max(
            metrics.getAscent(),
            (maxHeight - totalHeight) / 2 + metrics.getAscent());
        for (String lineValue : lines)
        {
            drawCentred(g, lineValue, centerX, baseline);
            baseline += metrics.getHeight();
        }
    }

    private static void drawArtwork(
        Graphics2D g,
        BufferedImage source,
        int x,
        int y,
        int width,
        int height,
        boolean pixelArt)
    {
        double scale = Math.min(
            width / (double) Math.max(1, source.getWidth()),
            height / (double) Math.max(1, source.getHeight())) * 0.82;
        int drawW = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int drawH = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int drawX = x + (width - drawW) / 2;
        int drawY = y + (height - drawH) / 2;
        Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            pixelArt
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, drawX, drawY, drawW, drawH, null);
        if (previous != null)
        {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
        }
    }

    private static Font fitFont(
        Graphics2D g,
        String text,
        int maximum,
        int minimum,
        int maxWidth)
    {
        for (int size = maximum; size >= minimum; size--)
        {
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, size);
            if (g.getFontMetrics(font).stringWidth(text) <= maxWidth)
            {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, minimum);
    }

    private static String ellipsizeToWidth(
        FontMetrics metrics,
        String text,
        int maxWidth)
    {
        String value = text == null ? "" : text.trim();
        if (metrics.stringWidth(value) <= maxWidth)
        {
            return value;
        }
        String ellipsis = "...";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        if (ellipsisWidth >= maxWidth)
        {
            return ellipsis;
        }
        int end = value.length();
        while (end > 0
            && metrics.stringWidth(value.substring(0, end).trim()) + ellipsisWidth > maxWidth)
        {
            end--;
        }
        return end <= 0 ? ellipsis : value.substring(0, end).trim() + ellipsis;
    }

    private static Color titleColor(Rarity rarity)
    {
        switch (rarity)
        {
            case UNCOMMON:
                return new Color(226, 242, 205);
            case RARE:
                return new Color(220, 234, 255);
            case EPIC:
                return new Color(241, 217, 255);
            case LEGENDARY:
                return new Color(255, 234, 178);
            case MYTHIC:
                return new Color(255, 205, 205);
            case GODLY:
                return new Color(255, 251, 210);
            case COMMON:
            default:
                return new Color(245, 231, 207);
        }
    }

    private static String initials(String value)
    {
        StringBuilder result = new StringBuilder();
        for (String word : value.trim().split("\\s+"))
        {
            if (!word.isEmpty())
            {
                result.append(Character.toUpperCase(word.charAt(0)));
            }
            if (result.length() == 2)
            {
                break;
            }
        }
        return result.length() == 0 ? "?" : result.toString();
    }

    private static void drawCentred(Graphics2D g, String text, int x, int baseline)
    {
        java.awt.geom.Rectangle2D visual = g.getFontMetrics()
            .getStringBounds(text, g);
        float drawX = (float) (x - (visual.getWidth() / 2.0) - visual.getX());
        g.drawString(text, drawX, baseline);
    }

    private static BufferedImage scale(BufferedImage source, int width, int height)
    {
        BufferedImage image = new BufferedImage(
            Math.max(1, width),
            Math.max(1, height),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            configure(g);
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(source, 0, 0, width, height, null);
        }
        finally
        {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage load(String resource)
    {
        return CACHE.computeIfAbsent(resource, key -> {
            try (InputStream stream = CardUiAssets.class.getResourceAsStream(key))
            {
                return stream == null ? null : ImageIO.read(stream);
            }
            catch (IOException ignored)
            {
                return null;
            }
        });
    }

    private static void configure(Graphics2D g)
    {
        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);
    }
}
