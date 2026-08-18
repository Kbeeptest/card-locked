package com.cardrestricted.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Persistent exact-ID cache for RuneLite item sprites used only when a card has
 * no packaged Wiki artwork. This mirrors the stable local-image-cache model
 * used by OSRS TCG: album painting reads completed local PNGs and never relies
 * on an in-progress AsyncBufferedImage.
 */
final class PersistentItemSpriteCache
{
    private final Path directory;
    private final Set<Integer> presentItemIds = ConcurrentHashMap.newKeySet();

    PersistentItemSpriteCache(Path directory)
    {
        this.directory = directory;
        try
        {
            Files.createDirectories(directory);
        }
        catch (IOException ignored)
        {
            // Cache remains optional. RuneLite sprite fallback still works.
        }
        discoverExistingSprites();
    }

    Optional<BufferedImage> load(int itemId)
    {
        if (!presentItemIds.contains(itemId))
        {
            return Optional.empty();
        }
        Path path = path(itemId);
        if (!Files.isRegularFile(path))
        {
            presentItemIds.remove(itemId);
            return Optional.empty();
        }
        try
        {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1)
            {
                Files.deleteIfExists(path);
                presentItemIds.remove(itemId);
                return Optional.empty();
            }
            return Optional.of(image);
        }
        catch (IOException exception)
        {
            presentItemIds.remove(itemId);
            return Optional.empty();
        }
    }

    void save(int itemId, BufferedImage image)
    {
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1)
        {
            return;
        }
        try
        {
            Files.createDirectories(directory);
            Path target = path(itemId);
            Path temporary = Files.createTempFile(directory, itemId + "-", ".tmp");
            try
            {
                if (!ImageIO.write(image, "png", temporary.toFile()))
                {
                    return;
                }
                try
                {
                    Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored)
                {
                    Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
                }
                presentItemIds.add(itemId);
            }
            finally
            {
                Files.deleteIfExists(temporary);
            }
        }
        catch (IOException ignored)
        {
            // A failed disk write must never break plugin rendering.
        }
    }

    boolean contains(int itemId)
    {
        return presentItemIds.contains(itemId);
    }

    int indexedEntryCountForTesting()
    {
        return presentItemIds.size();
    }

    private void discoverExistingSprites()
    {
        if (!Files.isDirectory(directory))
        {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.list(directory))
        {
            paths.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.matches("[0-9]+\\.png"))
                .map(name -> name.substring(0, name.length() - 4))
                .forEach(name -> {
                    try
                    {
                        presentItemIds.add(Integer.parseInt(name));
                    }
                    catch (NumberFormatException ignored)
                    {
                        // Ignore filenames outside the item-ID range.
                    }
                });
        }
        catch (IOException ignored)
        {
            presentItemIds.clear();
        }
    }

    private Path path(int itemId)
    {
        return directory.resolve(itemId + ".png");
    }
}
