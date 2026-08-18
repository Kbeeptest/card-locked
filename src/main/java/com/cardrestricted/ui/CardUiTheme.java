package com.cardrestricted.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.border.Border;

/** Shared visual tokens for the sidebar and auxiliary windows. */
final class CardUiTheme
{
    static final Color BACKGROUND = new Color(24, 25, 28);
    static final Color SURFACE = new Color(32, 33, 37);
    static final Color ACTIVE_SURFACE = new Color(45, 39, 28);
    static final Color MUTED_TEXT = new Color(184, 186, 191);
    static final Color GOLD = new Color(238, 178, 66);
    static final Color GOLD_HOVER = new Color(224, 185, 86);
    static final Color OWNED = new Color(104, 190, 112);
    static final Color COMPLETE = new Color(112, 202, 122);
    static final Color AVAILABLE = new Color(224, 171, 75);
    static final Color BLOCKED = new Color(226, 104, 96);
    static final Color LOCKED = new Color(125, 125, 125);
    static final Color FOIL_ACCESS = new Color(118, 178, 224);

    static final int SPACE_XS = 4;
    static final int SPACE_SM = 8;
    static final int SPACE_MD = 12;
    static final int SPACE_LG = 16;

    static final Font TITLE = font(Font.BOLD, 16f);
    static final Font HEADING = font(Font.BOLD, 12f);
    static final Font BODY = font(Font.PLAIN, 11f);
    static final Font META = font(Font.PLAIN, 10f);
    static final Font META_BOLD = font(Font.BOLD, 9f);

    private CardUiTheme()
    {
    }

    static Font font(int style, float size)
    {
        if (size <= 0f || Float.isNaN(size) || Float.isInfinite(size))
        {
            throw new IllegalArgumentException("Font size must be positive.");
        }
        return new Font(Font.DIALOG, style, Math.round(size))
            .deriveFont(size);
    }

    static Font scaled(Font font, float scale)
    {
        if (font == null)
        {
            throw new NullPointerException("font");
        }
        if (scale < 1f || scale > 3f
            || Float.isNaN(scale) || Float.isInfinite(scale))
        {
            throw new IllegalArgumentException(
                "UI font scale must be from 1 through 3.");
        }
        return font.deriveFont(font.getSize2D() * scale);
    }

    static Border insetBorder(Color accent)
    {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
            BorderFactory.createEmptyBorder(
                SPACE_SM - 2,
                SPACE_SM,
                SPACE_SM - 2,
                SPACE_SM));
    }

    static void styleCompactButton(AbstractButton button)
    {
        button.setFont(META);
        button.setMargin(new java.awt.Insets(3, 5, 3, 5));
    }
}
