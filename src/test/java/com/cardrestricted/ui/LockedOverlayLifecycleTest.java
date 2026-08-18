package com.cardrestricted.ui;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.EntityFamily;
import com.cardrestricted.catalog.MembersCatalogue;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.widgets.WidgetItem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LockedOverlayLifecycleTest
{
    @Test
    public void deactivatedItemOverlayNeverReadsOwnershipAgain()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        int itemId = firstEntityId(catalogue, CardType.ITEM);
        LockedEntityVisualIndex index = new LockedEntityVisualIndex(catalogue);
        AtomicInteger reads = new AtomicInteger();
        LockedItemOverlay overlay = new LockedItemOverlay(
            index,
            () -> {
                reads.incrementAndGet();
                return Collections.emptySet();
            },
            () -> true);
        WidgetItem widgetItem = new WidgetItem(
            itemId,
            1,
            new Rectangle(0, 0, 32, 32),
            null,
            new Rectangle(0, 0, 32, 32));
        BufferedImage image = new BufferedImage(
            40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            overlay.renderItemOverlay(graphics, itemId, widgetItem);
            assertEquals(1, reads.get());
            overlay.deactivate();
            overlay.renderItemOverlay(graphics, itemId, widgetItem);
            assertEquals(1, reads.get());
        }
        finally
        {
            graphics.dispose();
        }
    }

    @Test
    public void disabledItemOverlayDoesNotReadOwnership()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        int itemId = firstEntityId(catalogue, CardType.ITEM);
        AtomicInteger reads = new AtomicInteger();
        LockedItemOverlay overlay = new LockedItemOverlay(
            new LockedEntityVisualIndex(catalogue),
            () -> {
                reads.incrementAndGet();
                return Collections.emptySet();
            },
            () -> false);
        BufferedImage image = new BufferedImage(
            40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            overlay.renderItemOverlay(
                graphics,
                itemId,
                new WidgetItem(
                    itemId,
                    1,
                    new Rectangle(0, 0, 32, 32),
                    null,
                    new Rectangle(0, 0, 32, 32)));
            assertEquals(0, reads.get());
        }
        finally
        {
            graphics.dispose();
        }
    }

    @Test
    public void deactivatedNpcOverlayNeverReadsSceneAgain()
    {
        AtomicInteger sceneReads = new AtomicInteger();
        Client client = (Client) Proxy.newProxyInstance(
            Client.class.getClassLoader(),
            new Class<?>[]{Client.class},
            (proxy, method, arguments) -> {
                if ("getNpcs".equals(method.getName()))
                {
                    sceneReads.incrementAndGet();
                    return List.of();
                }
                return defaultValue(method.getReturnType());
            });
        LockedNpcOverlay overlay = new LockedNpcOverlay(
            client,
            new LockedEntityVisualIndex(MembersCatalogue.create()),
            Collections::emptySet,
            () -> true,
            () -> false);
        BufferedImage image = new BufferedImage(
            40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            overlay.render(graphics);
            assertEquals(1, sceneReads.get());
            overlay.deactivate();
            overlay.render(graphics);
            assertEquals(1, sceneReads.get());
        }
        finally
        {
            graphics.dispose();
        }
    }

    private static int firstEntityId(
        CardCatalogue catalogue,
        CardType type)
    {
        for (CardDefinition card : catalogue.getCards())
        {
            if (card.getCardType() != type)
            {
                continue;
            }
            EntityFamily family = catalogue.requireFamily(
                card.getEntityFamilyId());
            return family.getCanonicalEntityId();
        }
        throw new AssertionError("No entity found for " + type);
    }

    private static Object defaultValue(Class<?> type)
    {
        if (!type.isPrimitive())
        {
            return null;
        }
        if (type == boolean.class)
        {
            return false;
        }
        if (type == char.class)
        {
            return '\0';
        }
        if (type == byte.class)
        {
            return (byte) 0;
        }
        if (type == short.class)
        {
            return (short) 0;
        }
        if (type == int.class)
        {
            return 0;
        }
        if (type == long.class)
        {
            return 0L;
        }
        if (type == float.class)
        {
            return 0.0f;
        }
        if (type == double.class)
        {
            return 0.0d;
        }
        return null;
    }
}
