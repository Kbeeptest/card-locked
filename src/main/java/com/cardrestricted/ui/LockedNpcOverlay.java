package com.cardrestricted.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public final class LockedNpcOverlay extends Overlay
{
    private static final int MAX_LOCK_CACHE_ENTRIES = 1024;
    private final Client client;
    private final LockedEntityVisualIndex index;
    private final Supplier<Set<String>> ownedCardIds;
    private final BooleanSupplier enabled;
    private final BooleanSupplier showLabels;
    private final Map<Integer, CachedNpcLock> lockCache =
        new LinkedHashMap<Integer, CachedNpcLock>(MAX_LOCK_CACHE_ENTRIES, 0.75f, true)
        {
            @Override
            protected boolean removeEldestEntry(
                Map.Entry<Integer, CachedNpcLock> eldest)
            {
                return size() > MAX_LOCK_CACHE_ENTRIES;
            }
        };
    private Set<String> cachedOwnedIdentity;
    private volatile boolean active = true;

    public LockedNpcOverlay(
        Client client,
        LockedEntityVisualIndex index,
        Supplier<Set<String>> ownedCardIds,
        BooleanSupplier enabled,
        BooleanSupplier showLabels)
    {
        this.client = client;
        this.index = index;
        this.ownedCardIds = ownedCardIds;
        this.enabled = enabled;
        this.showLabels = showLabels;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    public void deactivate()
    {
        active = false;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!active || !enabled.getAsBoolean())
        {
            return null;
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
        for (NPC npc : client.getNpcs())
        {
            int npcId = npc.getId();
            String npcName = npc.getName();
            CachedNpcLock cached = lockCache.get(npcId);
            boolean locked;
            if (cached != null && Objects.equals(cached.name, npcName))
            {
                locked = cached.locked;
            }
            else
            {
                locked = index.isNpcLocked(npcId, npcName, owned);
                lockCache.put(npcId, new CachedNpcLock(npcName, locked));
            }
            if (!locked)
            {
                continue;
            }
            Shape hull = npc.getConvexHull();
            if (hull != null)
            {
                OverlayUtil.renderPolygon(
                    graphics,
                    hull,
                    new Color(115, 115, 115, 180),
                    new Color(55, 55, 55, 75),
                    new java.awt.BasicStroke(1.2f));
            }
            if (showLabels.getAsBoolean())
            {
                Point text = npc.getCanvasTextLocation(
                    graphics,
                    "LOCKED",
                    npc.getLogicalHeight() + 18);
                if (text != null)
                {
                    OverlayUtil.renderTextLocation(
                        graphics,
                        text,
                        "LOCKED",
                        new Color(230, 192, 77));
                }
            }
        }
        return null;
    }
    private static final class CachedNpcLock
    {
        private final String name;
        private final boolean locked;

        private CachedNpcLock(String name, boolean locked)
        {
            this.name = name;
            this.locked = locked;
        }
    }

}
