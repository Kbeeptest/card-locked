package com.cardrestricted.ui;

import java.awt.Rectangle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LockedItemOverlayBoundsTest
{
    @Test
    public void partiallyObscuredTopRowRetainsVisibleOverlayArea()
    {
        Rectangle item = new Rectangle(100, 90, 32, 32);
        Rectangle viewport = new Rectangle(80, 100, 200, 200);
        Rectangle visible = LockedItemOverlay.intersectVisibleBounds(
            item,
            viewport);
        assertEquals(new Rectangle(100, 100, 32, 22), visible);
        assertTrue(visible.height > 0);
    }

    @Test
    public void fullyVisibleItemKeepsOriginalBounds()
    {
        Rectangle item = new Rectangle(100, 120, 32, 32);
        Rectangle viewport = new Rectangle(80, 100, 200, 200);
        assertEquals(item, LockedItemOverlay.intersectVisibleBounds(
            item,
            viewport));
    }
}
