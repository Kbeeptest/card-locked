package com.cardrestricted.presentation;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.pack.PendingPackReveal;
import java.time.Instant;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class PackPresentationCacheTest
{
    @Test
    public void cardFrontsAreComposedOnceAndBounded()
        throws ReflectiveOperationException
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        PackPresentationRenderer renderer = new PackPresentationRenderer(catalogue);
        Method surfaceMethod = PackPresentationRenderer.class.getDeclaredMethod(
            "cardFrontSurface", PackCardResult.class);
        surfaceMethod.setAccessible(true);

        List<String> cardIds = new ArrayList<>();
        catalogue.getCards().stream().limit(20)
            .forEach(card -> cardIds.add(card.getCardId()));
        PackCardResult firstResult = new PackCardResult(cardIds.get(0), false, 0L);
        BufferedImage first = (BufferedImage) surfaceMethod.invoke(renderer, firstResult);
        BufferedImage repeated = (BufferedImage) surfaceMethod.invoke(renderer, firstResult);

        assertSame(first, repeated);
        assertEquals(512, first.getWidth());
        assertEquals(768, first.getHeight());

        for (String cardId : cardIds)
        {
            surfaceMethod.invoke(renderer, new PackCardResult(cardId, false, 0L));
        }
        Field cacheField = PackPresentationRenderer.class.getDeclaredField(
            "cardFrontSurfaceCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, BufferedImage> cache =
            (Map<String, BufferedImage>) cacheField.get(renderer);
        assertTrue(cache.size() <= 12);
    }

    @Test
    public void pendingPackFrontsArePrewarmedOutsideRenderPath()
        throws ReflectiveOperationException
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        List<PackCardResult> cards = new ArrayList<>();
        catalogue.getCards().stream().limit(5).forEach(card ->
            cards.add(new PackCardResult(card.getCardId(), false, 0L)));
        PackPresentationController controller = new PackPresentationController();
        controller.synchronise(java.util.Optional.of(new PendingPackReveal(
            UUID.randomUUID(),
            "standard",
            Instant.parse("2026-08-06T20:00:00Z"),
            cards,
            java.util.Collections.emptySet())));
        PackPresentationRenderer renderer = new PackPresentationRenderer(catalogue);

        renderer.prewarmCardFronts(controller.snapshot());

        Field cacheField = PackPresentationRenderer.class.getDeclaredField(
            "cardFrontSurfaceCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, BufferedImage> cache =
            (Map<String, BufferedImage>) cacheField.get(renderer);
        assertEquals(5, cache.size());
    }
}
