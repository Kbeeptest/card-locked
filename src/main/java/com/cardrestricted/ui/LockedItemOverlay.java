package com.cardrestricted.ui;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

public final class LockedItemOverlay extends WidgetItemOverlay
{
    private static final int MAX_LOCK_CACHE_ENTRIES = 4096;
    private final LockedEntityVisualIndex index;
    private final Supplier<Set<String>> ownedCardIds;
    private final BooleanSupplier enabled;
    private final IntFunction<String> itemNameResolver;
    private final Map<Integer, Boolean> lockCache =
        new LinkedHashMap<Integer, Boolean>(MAX_LOCK_CACHE_ENTRIES, 0.75f, true)
        {
            @Override
            protected boolean removeEldestEntry(
                Map.Entry<Integer, Boolean> eldest)
            {
                return size() > MAX_LOCK_CACHE_ENTRIES;
            }
        };
    private Set<String> cachedOwnedIdentity;
    private volatile boolean active = true;

    public LockedItemOverlay(
        LockedEntityVisualIndex index,
        Supplier<Set<String>> ownedCardIds,
        BooleanSupplier enabled)
    {
        this(index, ownedCardIds, enabled, ignored -> "");
    }

    public LockedItemOverlay(
        LockedEntityVisualIndex index,
        Supplier<Set<String>> ownedCardIds,
        BooleanSupplier enabled,
        IntFunction<String> itemNameResolver)
    {
        this.index = index;
        this.ownedCardIds = ownedCardIds;
        this.enabled = enabled;
        this.itemNameResolver = itemNameResolver == null
            ? ignored -> ""
            : itemNameResolver;
        showOnInventory();
        showOnBank();
        showOnEquipment();
    }

    public void deactivate()
    {
        active = false;
    }

    @Override
    public void renderItemOverlay(
        Graphics2D graphics,
        int itemId,
        WidgetItem widgetItem)
    {
        if (!active || !enabled.getAsBoolean())
        {
            return;
        }
        Set<String> owned = ownedCardIds.get();
        if (owned == null)
        {
            owned = Collections.emptySet();
        }
        if (owned != cachedOwnedIdentity)
        {
            cachedOwnedIdentity = owned;
            lockCache.clear();
        }
        Boolean locked = lockCache.get(itemId);
        if (locked == null)
        {
            locked = index.isItemLocked(
                itemId,
                itemNameResolver.apply(itemId),
                owned);
            lockCache.put(itemId, locked);
        }
        if (!locked)
        {
            return;
        }
        Rectangle bounds = widgetItem.getCanvasBounds();
        Rectangle visible = visibleBounds(widgetItem, bounds);
        if (visible.isEmpty())
        {
            return;
        }
        LockedVisualTreatment.paintSoftItemOverlay(graphics, visible);
        int size = Math.max(8, Math.min(13, Math.min(
            visible.width,
            visible.height) / 3));
        int x = visible.x + (visible.width - size) / 2;
        int y = visible.y + (visible.height - size) / 2;
        LockedVisualTreatment.paintBlockedBadge(graphics, x, y, size);
    }
    static Rectangle visibleBounds(
        WidgetItem widgetItem,
        Rectangle itemBounds)
    {
        if (itemBounds == null)
        {
            return new Rectangle();
        }
        if (widgetItem == null || widgetItem.getWidget() == null)
        {
            return new Rectangle(itemBounds);
        }
        Rectangle visible = new Rectangle(itemBounds);
        net.runelite.api.widgets.Widget ancestor =
            widgetItem.getWidget().getParent();
        int depth = 0;
        while (ancestor != null && depth++ < 32 && !visible.isEmpty())
        {
            Rectangle ancestorBounds = ancestor.getBounds();
            if (ancestorBounds != null
                && ancestorBounds.width > 0
                && ancestorBounds.height > 0)
            {
                visible = visible.intersection(ancestorBounds);
            }
            ancestor = ancestor.getParent();
        }
        return visible;
    }


    static Rectangle intersectVisibleBounds(
        Rectangle itemBounds,
        Iterable<Rectangle> ancestorBounds)
    {
        Rectangle visible = itemBounds == null
            ? new Rectangle()
            : new Rectangle(itemBounds);
        if (ancestorBounds == null)
        {
            return visible;
        }
        for (Rectangle bounds : ancestorBounds)
        {
            if (bounds != null && bounds.width > 0 && bounds.height > 0)
            {
                visible = visible.intersection(bounds);
                if (visible.isEmpty())
                {
                    break;
                }
            }
        }
        return visible;
    }

    static Rectangle intersectVisibleBounds(
        Rectangle itemBounds,
        Rectangle parentBounds)
    {
        if (itemBounds == null)
        {
            return new Rectangle();
        }
        if (parentBounds == null)
        {
            return new Rectangle(itemBounds);
        }
        return itemBounds.intersection(parentBounds);
    }

}
