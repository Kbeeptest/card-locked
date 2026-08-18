package com.cardrestricted.ui;

import java.awt.Color;
import java.awt.Font;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CardUiThemeTest
{
    @Test
    public void semanticStatesRemainVisuallyDistinct()
    {
        assertEquals(6, new HashSet<>(Arrays.asList(
            CardUiTheme.OWNED,
            CardUiTheme.COMPLETE,
            CardUiTheme.AVAILABLE,
            CardUiTheme.BLOCKED,
            CardUiTheme.LOCKED,
            CardUiTheme.FOIL_ACCESS)).size());
    }

    @Test
    public void bodyAndStateTextKeepReadableDarkSurfaceContrast()
    {
        assertTrue(contrast(
            CardUiTheme.MUTED_TEXT,
            CardUiTheme.BACKGROUND) >= 4.5d);
        assertTrue(contrast(
            CardUiTheme.AVAILABLE,
            CardUiTheme.BACKGROUND) >= 4.5d);
        assertTrue(contrast(
            CardUiTheme.BLOCKED,
            CardUiTheme.BACKGROUND) >= 4.5d);
        assertTrue(contrast(
            CardUiTheme.FOIL_ACCESS,
            CardUiTheme.BACKGROUND) >= 4.5d);
    }

    @Test
    public void highDpiFontScalingIsBoundedAndProportional()
    {
        Font scaled = CardUiTheme.scaled(CardUiTheme.BODY, 2f);
        assertEquals(
            CardUiTheme.BODY.getSize2D() * 2f,
            scaled.getSize2D(),
            0.01f);
        assertEquals(CardUiTheme.BODY.getFamily(), scaled.getFamily());
    }

    private static double contrast(Color foreground, Color background)
    {
        double light = luminance(foreground);
        double dark = luminance(background);
        return (Math.max(light, dark) + 0.05d)
            / (Math.min(light, dark) + 0.05d);
    }

    private static double luminance(Color color)
    {
        double red = channel(color.getRed());
        double green = channel(color.getGreen());
        double blue = channel(color.getBlue());
        return 0.2126d * red + 0.7152d * green + 0.0722d * blue;
    }

    private static double channel(int value)
    {
        double normalised = value / 255d;
        return normalised <= 0.03928d
            ? normalised / 12.92d
            : Math.pow((normalised + 0.055d) / 1.055d, 2.4d);
    }
}
