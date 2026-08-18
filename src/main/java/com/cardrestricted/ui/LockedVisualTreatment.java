package com.cardrestricted.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

final class LockedVisualTreatment
{
    private static final Color ITEM_DIM = new Color(112, 118, 126, 120);
    private static final Color ITEM_EDGE = new Color(70, 76, 84, 130);
    private static final Color BADGE_RING = new Color(220, 45, 45, 200);

    private LockedVisualTreatment()
    {
    }

    static void paintSoftItemOverlay(Graphics2D graphics, Rectangle bounds)
    {
        graphics.setColor(ITEM_DIM);
        graphics.fillRoundRect(
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            6,
            6);
        graphics.setColor(ITEM_EDGE);
        graphics.drawRoundRect(
            bounds.x,
            bounds.y,
            bounds.width - 1,
            bounds.height - 1,
            6,
            6);
    }

    static void paintBlockedBadge(Graphics2D graphics, int x, int y, int size)
    {
        Object oldAntialias = graphics.getRenderingHint(
            RenderingHints.KEY_ANTIALIASING);
        java.awt.Stroke oldStroke = graphics.getStroke();
        try
        {
            graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(BADGE_RING);
            graphics.setStroke(new BasicStroke(Math.max(1.4f, size / 6.5f)));
            int margin = Math.max(2, size / 5);
            graphics.drawLine(
                x + margin,
                y + margin,
                x + size - margin,
                y + size - margin);
            graphics.drawLine(
                x + size - margin,
                y + margin,
                x + margin,
                y + size - margin);
        }
        finally
        {
            graphics.setStroke(oldStroke);
            graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                oldAntialias);
        }
    }
}
