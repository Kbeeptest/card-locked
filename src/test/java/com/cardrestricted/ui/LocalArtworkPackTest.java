package com.cardrestricted.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import org.junit.Test;

public class LocalArtworkPackTest
{
    @Test
    public void bundledArtworkPackMaterialisesAndServesCurrentManifest()
        throws Exception
    {
        Path directory = Files.createTempDirectory("cl-artwork-bundled-");
        try (WikiArtworkDiskCache cache = new WikiArtworkDiskCache(
            directory,
            () -> false))
        {
            cache.prepareAsync(Runnable::run);
            assertTrue(cache.isBundledArchivePreparedForTesting());
            assertEquals(6667, cache.localPackEntryCount());

            Map<String, WikiArtworkEntry> manifest = WikiArtworkManifest.load(
                LocalArtworkPackTest.class.getClassLoader());
            assertEquals(7144, manifest.size());
            WikiArtworkEntry sample = manifest.values().iterator().next();
            assertTrue(cache.hasLocalPackArtwork(sample));
            assertTrue(cache.loadLocalPackArtwork(sample).isPresent());

            try (java.util.stream.Stream<Path> paths = Files.list(directory))
            {
                assertEquals(1L, paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                        .matches("offline-assets-v1-[0-9a-f]{16}\\.zip"))
                    .count());
            }
        }
        finally
        {
            deleteTree(directory);
        }
    }

    @Test
    public void lookupBeforeBackgroundPreparationDoesNotMaterialiseArchive()
        throws Exception
    {
        Path directory = Files.createTempDirectory("cl-artwork-idle-");
        try (WikiArtworkDiskCache cache = new WikiArtworkDiskCache(
            directory,
            () -> false))
        {
            Map<String, WikiArtworkEntry> manifest = WikiArtworkManifest.load(
                LocalArtworkPackTest.class.getClassLoader());
            WikiArtworkEntry sample = manifest.values().iterator().next();
            assertFalse(cache.hasLocalPackArtwork(sample));
            assertEquals(0, cache.localPackEntryCount());
            try (java.util.stream.Stream<Path> paths = Files.list(directory))
            {
                assertEquals(0L, paths.count());
            }
        }
        finally
        {
            deleteTree(directory);
        }
    }

    @Test
    public void manifestDigestMismatchStillRejectsBundledEntry() throws Exception
    {
        Path directory = Files.createTempDirectory("cl-artwork-digest-");
        try (WikiArtworkDiskCache cache = new WikiArtworkDiskCache(
            directory,
            () -> false))
        {
            cache.prepareAsync(Runnable::run);
            Map<String, WikiArtworkEntry> manifest = WikiArtworkManifest.load(
                LocalArtworkPackTest.class.getClassLoader());
            WikiArtworkEntry source = manifest.values().iterator().next();
            WikiArtworkEntry mismatched = new WikiArtworkEntry(
                source.getCardId(),
                source.getRuntimeFilename(),
                source.getSourceUri(),
                source.getSourceSha256(),
                "0".repeat(64),
                source.isPixelArt(),
                source.isDownloadable(),
                source.isBundledFallback());
            assertTrue(cache.hasLocalPackArtwork(mismatched));
            assertFalse(cache.loadLocalPackArtwork(mismatched).isPresent());
        }
        finally
        {
            deleteTree(directory);
        }
    }

    private static void deleteTree(Path root) throws Exception
    {
        if (!Files.exists(root))
        {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root))
        {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))
            {
                Files.deleteIfExists(path);
            }
        }
    }
}
