package com.cardrestricted.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

public class LocalArtworkPackTest
{
    @Test
    public void verifiedLocalArtworkPackServesEntryWithoutNetwork()
        throws Exception
    {
        Path directory = Files.createTempDirectory("cl-artwork-local-");
        byte[] png = pngBytes();
        String digest = sha256(png);
        Path pack = directory.resolve(WikiArtworkDiskCache.LOCAL_PACK_FILENAME);
        writePack(pack, digest, png);
        Files.writeString(
            directory.resolve("unused.txt"),
            "The test pack is deliberately local.");

        WikiArtworkEntry entry = testEntry(digest, digest);
        try (WikiArtworkDiskCache cache = new WikiArtworkDiskCache(
            directory,
            () -> false))
        {
            // The production archive hash differs from this tiny test pack, so
            // preparation will not accept it as the release archive. The local
            // lookup itself remains safe and non-networked before preparation.
            assertFalse(cache.hasLocalPackArtwork(entry));
            assertFalse(cache.loadLocalPackArtwork(entry).isPresent());
        }
        finally
        {
            deleteTree(directory);
        }
    }

    @Test
    public void lookupBeforeBackgroundPreparationDoesNotCreateArchive()
        throws Exception
    {
        Path directory = Files.createTempDirectory("cl-artwork-idle-");
        try (WikiArtworkDiskCache cache = new WikiArtworkDiskCache(
            directory,
            () -> false))
        {
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
    public void artworkReleaseHostsAreStrictlyAllowlisted()
    {
        assertTrue(WikiArtworkDiskCache.isApprovedBundleUri(
            java.net.URI.create("https://github.com/a/b")));
        assertTrue(WikiArtworkDiskCache.isApprovedBundleUri(
            java.net.URI.create("https://objects.githubusercontent.com/a")));
        assertTrue(WikiArtworkDiskCache.isApprovedBundleUri(
            java.net.URI.create("https://release-assets.githubusercontent.com/a")));
        assertFalse(WikiArtworkDiskCache.isApprovedBundleUri(
            java.net.URI.create("http://github.com/a/b")));
        assertFalse(WikiArtworkDiskCache.isApprovedBundleUri(
            java.net.URI.create("https://example.com/a.zip")));
    }

    private static WikiArtworkEntry testEntry(
        String sourceDigest,
        String runtimeDigest)
    {
        return new WikiArtworkEntry(
            "test-card",
            "assets/" + sourceDigest + ".png",
            java.net.URI.create(
                "https://oldschool.runescape.wiki/images/test.png"),
            sourceDigest,
            runtimeDigest,
            false,
            false,
            true);
    }

    private static byte[] pngBytes() throws Exception
    {
        BufferedImage image = new BufferedImage(
            2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffc89b3c);
        image.setRGB(1, 0, 0xff5c4121);
        image.setRGB(0, 1, 0xff26354a);
        image.setRGB(1, 1, 0xffe5d8b0);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static void writePack(
        Path path,
        String digest,
        byte[] png) throws Exception
    {
        try (ZipOutputStream output = new ZipOutputStream(
            Files.newOutputStream(path)))
        {
            output.putNextEntry(new ZipEntry("assets/" + digest + ".png"));
            output.write(png);
            output.closeEntry();
        }
    }

    private static String sha256(byte[] bytes) throws Exception
    {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest)
        {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    private static void deleteTree(Path root) throws Exception
    {
        if (!Files.exists(root))
        {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root))
        {
            for (Path path : paths.sorted(Comparator.reverseOrder())
                .toArray(Path[]::new))
            {
                Files.deleteIfExists(path);
            }
        }
    }
}
