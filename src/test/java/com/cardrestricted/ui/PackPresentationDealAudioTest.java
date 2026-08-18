package com.cardrestricted.ui;

import com.cardrestricted.CardRestrictedAccountConfig;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.pack.PendingPackReveal;
import com.cardrestricted.presentation.CardArtworkProvider;
import com.cardrestricted.presentation.PackPresentationController;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.api.Client;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class PackPresentationDealAudioTest
{
    @Test
    public void dealCuesFireOnceInVisualLandingOrder()
    {
        PackPresentationController controller = controllerWithPack();
        List<Integer> cues = new CopyOnWriteArrayList<>();
        PackPresentationOverlay overlay = overlay(controller, cues);

        controller.advance(PackPresentationController.PACK_ENTER_MILLIS);
        controller.openPack();
        overlay.emitDealPlacementCuesForTesting(controller.snapshot());
        assertEquals(0, cues.size());

        // Eased travel reaches visible table contact well before raw local=1.0.
        controller.advance(340L);
        overlay.emitDealPlacementCuesForTesting(controller.snapshot());
        assertEquals(0, cues.size());
        controller.advance(20L);
        overlay.emitDealPlacementCuesForTesting(controller.snapshot());
        assertEquals(Arrays.asList(0), cues);

        for (int expected = 2; expected <= 5; expected++)
        {
            controller.advance(53L);
            overlay.emitDealPlacementCuesForTesting(controller.snapshot());
            assertEquals(expected, cues.size());
        }
        assertEquals(Arrays.asList(0, 1, 2, 3, 4), cues);

        // Re-rendering the same progress must not duplicate placement audio.
        overlay.emitDealPlacementCuesForTesting(controller.snapshot());
        assertEquals(Arrays.asList(0, 1, 2, 3, 4), cues);
        overlay.stopAnimationLoop();
    }

    @Test
    public void skippingDealDoesNotEmitOutstandingPlacementCues()
    {
        PackPresentationController controller = controllerWithPack();
        List<Integer> cues = new CopyOnWriteArrayList<>();
        PackPresentationOverlay overlay = overlay(controller, cues);

        controller.advance(PackPresentationController.PACK_ENTER_MILLIS);
        controller.openPack();
        controller.advance(300L);
        overlay.emitDealPlacementCuesForTesting(controller.snapshot());
        assertEquals(0, cues.size());

        controller.skipTransition();
        overlay.emitDealPlacementCuesForTesting(controller.snapshot());
        assertEquals(0, cues.size());
        overlay.stopAnimationLoop();
    }

    private static PackPresentationController controllerWithPack()
    {
        PackPresentationController controller = new PackPresentationController();
        controller.synchronise(Optional.of(new PendingPackReveal(
            UUID.randomUUID(),
            "pack.standard.v4",
            Instant.now(),
            Arrays.asList(
                new PackCardResult("test.card.1", false, 0),
                new PackCardResult("test.card.2", false, 0),
                new PackCardResult("test.card.3", false, 0),
                new PackCardResult("test.card.4", false, 0),
                new PackCardResult("test.card.5", false, 0)),
            0)));
        return controller;
    }

    private static PackPresentationOverlay overlay(
        PackPresentationController controller,
        List<Integer> cues)
    {
        return new PackPresentationOverlay(
            proxy(Client.class),
            proxy(CardRestrictedAccountConfig.class),
            MembersCatalogue.create(),
            controller,
            CardArtworkProvider.none(),
            Runnable::run,
            cues::add);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type)
    {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (instance, method, args) -> {
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class)
                {
                    return false;
                }
                if (returnType == int.class)
                {
                    return 0;
                }
                if (returnType == long.class)
                {
                    return 0L;
                }
                if (returnType == double.class)
                {
                    return 0.0;
                }
                return null;
            });
    }
}
