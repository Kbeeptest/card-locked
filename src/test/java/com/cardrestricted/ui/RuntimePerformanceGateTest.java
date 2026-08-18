package com.cardrestricted.ui;

import com.cardrestricted.CardRestrictedAccountConfig;
import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.foil.FoilEntitlementResolver;
import com.cardrestricted.foil.FoilRewardRegistry;
import com.cardrestricted.presentation.CardArtworkProvider;
import com.cardrestricted.presentation.PackPresentationController;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Performance-sensitive construction and cache invariants for public beta. */
public final class RuntimePerformanceGateTest
{
    @Test
    public void offlineArtworkArchivePreparationIsLazy()
        throws Exception
    {
        Path directory = Files.createTempDirectory("cl-artwork-lazy-");
        try (WikiArtworkDiskCache cache =
            new WikiArtworkDiskCache(directory, () -> true))
        {
            assertFalse(cache.isBundledArchivePreparedForTesting());
            assertFalse(Files.exists(directory.resolve("offline-assets-v1.zip")));
        }
        finally
        {
            deleteTree(directory);
        }
    }

    @Test
    public void persistentItemSpriteCacheUsesAndMaintainsItsIndex()
        throws Exception
    {
        Path directory = Files.createTempDirectory("cl-sprite-index-");
        try
        {
            BufferedImage existing = new BufferedImage(
                2,
                2,
                BufferedImage.TYPE_INT_ARGB);
            ImageIO.write(existing, "png", directory.resolve("42.png").toFile());

            PersistentItemSpriteCache cache =
                new PersistentItemSpriteCache(directory);
            assertTrue(cache.contains(42));
            assertEquals(1, cache.indexedEntryCountForTesting());

            cache.save(99, existing);
            assertTrue(cache.contains(99));
            assertEquals(2, cache.indexedEntryCountForTesting());

            Files.delete(directory.resolve("42.png"));
            assertFalse(cache.load(42).isPresent());
            assertFalse(cache.contains(42));
            assertEquals(1, cache.indexedEntryCountForTesting());
        }
        finally
        {
            deleteTree(directory);
        }
    }

    @Test
    public void packRendererIsNotAllocatedUntilPresentationBegins()
    {
        Client client = proxy(Client.class);
        CardRestrictedAccountConfig config = proxy(
            CardRestrictedAccountConfig.class);
        PackPresentationOverlay overlay = new PackPresentationOverlay(
            client,
            config,
            MembersCatalogue.create(),
            new PackPresentationController(),
            CardArtworkProvider.none());

        assertFalse(overlay.isRendererInitializedForTesting());
        overlay.stopAnimationLoop();
    }

    @Test
    public void albumWindowTreesAreNotAllocatedWithTheSidePanel()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        AtomicReference<CardRestrictedAccountPanel> panelRef =
            new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(
            new CardRestrictedAccountPanel(
                catalogue,
                new FoilEntitlementResolver(
                    catalogue,
                    FoilRewardRegistry.load(
                        getClass().getClassLoader(), catalogue)),
                new NoOpSetupHandler(),
                new NoOpPackHandler(),
                CardArtworkProvider.none())));

        CardRestrictedAccountPanel panel = panelRef.get();
        try
        {
            assertFalse(panel.areAlbumWindowsInitializedForTesting());
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::closeAuxiliaryWindows);
        }
    }

    @Test
    public void catalogueConstructionStaysWithinAConservativeStartupBudget()
    {
        long started = System.nanoTime();
        CardCatalogue catalogue = MembersCatalogue.create();
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(7588, catalogue.getCards().size());
        assertTrue(
            "Catalogue construction exceeded 10 seconds: " + elapsedMillis,
            elapsedMillis < 10_000L);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type)
    {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[]{type},
            (ignored, method, arguments) -> defaultValue(
                method.getReturnType()));
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
            return 0.0F;
        }
        if (type == double.class)
        {
            return 0.0D;
        }
        return null;
    }

    private static void deleteTree(Path root)
        throws Exception
    {
        if (!Files.exists(root))
        {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root))
        {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try
                {
                    Files.deleteIfExists(path);
                }
                catch (Exception exception)
                {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    private static final class NoOpSetupHandler
        implements CollectionSetupHandler
    {
        @Override
        public void createCollection(
            com.cardrestricted.collection.ProfileSetupOptions options)
        {
        }

        @Override
        public void disableIntegrity()
        {
        }

        @Override
        public void resetProfile()
        {
        }

        @Override
        public void exportDiagnostics()
        {
        }
    }

    private static final class NoOpPackHandler implements PackActionHandler
    {
        @Override
        public void redeemStarterPack()
        {
        }

        @Override
        public void purchaseStandardPack()
        {
        }

        @Override
        public void purchaseRareHunterPack()
        {
        }

        @Override
        public void purchaseNoncombatNpcPack()
        {
        }

        @Override
        public void purchaseAttackableNpcPack()
        {
        }

        @Override
        public void exchangeNexusCard(Rarity rarity)
        {
        }
    }
}
