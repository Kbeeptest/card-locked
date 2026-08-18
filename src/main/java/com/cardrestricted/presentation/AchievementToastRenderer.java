package com.cardrestricted.presentation;

import com.cardrestricted.collection.achievement.AchievementDefinition;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AchievementToastRenderer
{
    private static final Color PANEL_TOP = new Color(43, 39, 31, 244);
    private static final Color PANEL_BOTTOM = new Color(18, 20, 25, 246);
    private static final Color BORDER = new Color(214, 169, 73);
    private static final Color PALE_GOLD = new Color(247, 224, 156);
    private static final Color TEXT = new Color(241, 241, 241);
    private static final Color MUTED = new Color(184, 187, 194);
    private static final Color SHADOW = new Color(0, 0, 0, 150);

    private final Font regularFont;
    private final Font boldFont;
    private final Font smallFont;

    public AchievementToastRenderer(
        Font regularFont,
        Font boldFont,
        Font smallFont)
    {
        this.regularFont = Objects.requireNonNull(
            regularFont,
            "regularFont");
        this.boldFont = Objects.requireNonNull(boldFont, "boldFont");
        this.smallFont = Objects.requireNonNull(smallFont, "smallFont");
    }

    public Rectangle render(
        Graphics2D graphics,
        Rectangle viewportBounds,
        AchievementToastSnapshot snapshot)
    {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(viewportBounds, "viewportBounds");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.isActive()
            || viewportBounds.width < 120
            || viewportBounds.height < 80)
        {
            return new Rectangle();
        }

        AchievementDefinition achievement = snapshot.getAchievement()
            .orElseThrow();
        int width = Math.min(430, Math.max(280, viewportBounds.width - 28));
        int height = 102;
        double visibility = visibility(snapshot);
        int travel = (int) Math.round((1.0 - visibility) * 26.0);
        int x = viewportBounds.x + (viewportBounds.width - width) / 2;
        int y = viewportBounds.y + 18 - travel;
        Rectangle bounds = new Rectangle(x, y, width, height);

        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            configure(g);
            g.setComposite(java.awt.AlphaComposite.SrcOver.derive(
                (float) Math.max(0.0, Math.min(1.0, visibility))));
            g.setColor(SHADOW);
            g.fill(new RoundRectangle2D.Double(
                x + 3,
                y + 5,
                width,
                height,
                18,
                18));
            g.setPaint(new GradientPaint(
                x,
                y,
                PANEL_TOP,
                x,
                y + height,
                PANEL_BOTTOM));
            g.fill(new RoundRectangle2D.Double(
                x,
                y,
                width,
                height,
                18,
                18));
            g.setColor(BORDER);
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new RoundRectangle2D.Double(
                x + 1,
                y + 1,
                width - 2,
                height - 2,
                18,
                18));

            int badgeCenterX = x + 48;
            int badgeCenterY = y + 48;
            drawBadge(g, badgeCenterX, badgeCenterY);

            int textX = x + 88;
            int maxTextWidth = width - 108;
            g.setFont(boldFont.deriveFont(Font.BOLD, 12f));
            g.setColor(PALE_GOLD);
            g.drawString("COLLECTION GOAL COMPLETE", textX, y + 23);

            g.setFont(boldFont.deriveFont(Font.BOLD, 17f));
            g.setColor(TEXT);
            g.drawString(
                ellipsise(
                    g.getFontMetrics(),
                    achievement.getDisplayName(),
                    maxTextWidth),
                textX,
                y + 48);

            g.setFont(regularFont.deriveFont(Font.PLAIN, 12f));
            g.setColor(MUTED);
            List<String> description = wrap(
                g.getFontMetrics(),
                achievement.getDescription(),
                maxTextWidth,
                2);
            int baseline = y + 67;
            for (String line : description)
            {
                g.drawString(line, textX, baseline);
                baseline += 14;
            }

            if (snapshot.getQueuedCount() > 0)
            {
                g.setFont(smallFont.deriveFont(Font.BOLD, 10f));
                g.setColor(PALE_GOLD);
                String queued = "+" + snapshot.getQueuedCount();
                int queuedWidth = g.getFontMetrics().stringWidth(queued);
                g.drawString(queued, x + width - queuedWidth - 12, y + 20);
            }

            int progressWidth = width - 24;
            int progressX = x + 12;
            int progressY = y + height - 8;
            g.setColor(new Color(255, 255, 255, 35));
            g.fillRoundRect(progressX, progressY, progressWidth, 3, 3, 3);
            g.setColor(BORDER);
            g.fillRoundRect(
                progressX,
                progressY,
                Math.max(0, (int) Math.round(
                    progressWidth * (1.0 - snapshot.getHoldProgress()))),
                3,
                3,
                3);
            return bounds;
        }
        finally
        {
            g.dispose();
        }
    }

    private static double visibility(AchievementToastSnapshot snapshot)
    {
        switch (snapshot.getState())
        {
            case ENTERING:
                return easeOutCubic(snapshot.getTransitionProgress());
            case EXITING:
                return 1.0 - easeInCubic(snapshot.getTransitionProgress());
            case HOLDING:
                return 1.0;
            case IDLE:
            default:
                return 0.0;
        }
    }

    private static void drawBadge(Graphics2D g, int centerX, int centerY)
    {
        g.setColor(new Color(11, 12, 16, 215));
        g.fillOval(centerX - 27, centerY - 27, 54, 54);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(centerX - 27, centerY - 27, 54, 54);
        Polygon star = new Polygon();
        for (int point = 0; point < 10; point++)
        {
            double angle = -Math.PI / 2.0 + point * Math.PI / 5.0;
            double radius = point % 2 == 0 ? 17.0 : 7.5;
            star.addPoint(
                centerX + (int) Math.round(Math.cos(angle) * radius),
                centerY + (int) Math.round(Math.sin(angle) * radius));
        }
        g.setColor(PALE_GOLD);
        g.fillPolygon(star);
    }

    private static List<String> wrap(
        FontMetrics metrics,
        String value,
        int maxWidth,
        int maxLines)
    {
        String text = value == null ? "" : value.trim();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+"))
        {
            String candidate = line.length() == 0
                ? word
                : line + " " + word;
            if (metrics.stringWidth(candidate) > maxWidth
                && line.length() > 0)
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
        }
        if (line.length() > 0 && lines.size() < maxLines)
        {
            lines.add(ellipsise(metrics, line.toString(), maxWidth));
        }
        return lines;
    }

    private static String ellipsise(
        FontMetrics metrics,
        String value,
        int maxWidth)
    {
        if (metrics.stringWidth(value) <= maxWidth)
        {
            return value;
        }
        String suffix = "…";
        String result = value;
        while (!result.isEmpty()
            && metrics.stringWidth(result + suffix) > maxWidth)
        {
            result = result.substring(0, result.length() - 1);
        }
        return result.trim() + suffix;
    }

    private static void configure(Graphics2D g)
    {
        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static double easeOutCubic(double value)
    {
        double inverse = 1.0 - value;
        return 1.0 - inverse * inverse * inverse;
    }

    private static double easeInCubic(double value)
    {
        return value * value * value;
    }
}
