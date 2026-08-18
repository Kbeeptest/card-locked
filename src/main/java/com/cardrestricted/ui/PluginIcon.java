package com.cardrestricted.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class PluginIcon
{
    private PluginIcon()
    {
    }

    public static BufferedImage create()
    {
        BufferedImage image =
            new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(43, 34, 24));
            graphics.fillRoundRect(2, 1, 13, 16, 3, 3);
            graphics.setColor(new Color(203, 162, 79));
            graphics.setStroke(new BasicStroke(2f));
            graphics.drawRoundRect(3, 2, 11, 14, 3, 3);
            graphics.setColor(new Color(120, 186, 101));
            graphics.fillOval(6, 6, 5, 5);
        }
        finally
        {
            graphics.dispose();
        }
        return image;
    }
}
