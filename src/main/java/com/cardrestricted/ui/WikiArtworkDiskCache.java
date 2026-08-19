package com.cardrestricted.ui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Verified lazy reader for Card Locked's offline OSRS Wiki artwork pack.
 *
 * <p>The reviewed artwork archive is hosted as a versioned GitHub Release asset
 * so the Plugin Hub source package stays within RuneLite's size limit. On a
 * clean installation the archive is downloaded once on Card Locked's
 * low-priority artwork worker, verified against the packaged SHA-256, cached
 * inside RuneLite's local data directory, and then read lazily. No collection
 * or gameplay data is included in the request.</p>
 */
public final class WikiArtworkDiskCache implements AutoCloseable
{
    public static final String LOCAL_PACK_FILENAME =
        "card-locked-artwork-v1.zip";
    private static final String PACK_HASH_RESOURCE =
        "com/cardrestricted/artwork/wiki/offline-assets-v1.sha256";
    private static final URI REMOTE_PACK_URI = URI.create(
        "https://github.com/Kbeeptest/card-locked/releases/download/"
            + "artwork-v1/card-locked-artwork-v1.zip");
    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 60_000;
    private static final int MAX_IMAGE_BYTES = 1024 * 1024;
    private static final long MAX_ARCHIVE_BYTES = 96L * 1024L * 1024L;

    private final Path directory;
    private final Set<String> presentDigests = Collections.synchronizedSet(
        new HashSet<>());
    private final Set<String> verifiedDigests = Collections.synchronizedSet(
        new HashSet<>());
    private final Set<String> verifiedArchiveDigests =
        Collections.synchronizedSet(new HashSet<>());
    private volatile Set<String> localPackEntries = Collections.emptySet();
    private volatile ZipFile localPackArchive;
    private volatile boolean archivePreparationAttempted;
    private volatile boolean closed;

    public WikiArtworkDiskCache(Path directory, BooleanSupplier enabled)
    {
        this(directory, enabled, Clock.systemUTC());
    }

    WikiArtworkDiskCache(
        Path directory,
        BooleanSupplier enabled,
        Clock clock)
    {
        this.directory = Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(clock, "clock");
        discoverExistingImages();
    }

    Optional<BufferedImage> load(WikiArtworkEntry entry)
    {
        Objects.requireNonNull(entry, "entry");
        Path path = imagePath(entry);
        if (!presentDigests.contains(entry.getSourceSha256())
            || !Files.isRegularFile(path))
        {
            presentDigests.remove(entry.getSourceSha256());
            return Optional.empty();
        }
        try
        {
            BufferedImage image = ImageIO.read(path.toFile());
            if (!isUsable(image))
            {
                Files.deleteIfExists(path);
                presentDigests.remove(entry.getSourceSha256());
                verifiedDigests.remove(entry.getSourceSha256());
                return Optional.empty();
            }
            verifiedDigests.add(entry.getSourceSha256());
            return Optional.of(image);
        }
        catch (IOException exception)
        {
            verifiedDigests.remove(entry.getSourceSha256());
            return Optional.empty();
        }
    }

    Optional<BufferedImage> loadLocalPackArtwork(
        WikiArtworkEntry entry)
    {
        Objects.requireNonNull(entry, "entry");
        if (!hasLocalPackArtwork(entry) || localPackArchive == null)
        {
            return Optional.empty();
        }
        ZipEntry zipEntry = localPackArchive.getEntry(
            entry.getRuntimeFilename());
        if (zipEntry == null
            || zipEntry.isDirectory()
            || zipEntry.getSize() < 0
            || zipEntry.getSize() > MAX_IMAGE_BYTES)
        {
            return Optional.empty();
        }
        try (InputStream input = localPackArchive.getInputStream(zipEntry))
        {
            byte[] bytes = readBounded(input, MAX_IMAGE_BYTES);
            if (!verifiedArchiveDigests.contains(entry.getRuntimeSha256()))
            {
                if (!entry.getRuntimeSha256().equals(sha256(bytes)))
                {
                    return Optional.empty();
                }
                verifiedArchiveDigests.add(entry.getRuntimeSha256());
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            return isUsable(image) ? Optional.of(image) : Optional.empty();
        }
        catch (IOException exception)
        {
            return Optional.empty();
        }
    }

    /** Compatibility alias retained for older focused verifiers. */
    Optional<BufferedImage> loadBundledFallback(
        WikiArtworkEntry entry)
    {
        return loadLocalPackArtwork(entry);
    }

    boolean contains(WikiArtworkEntry entry)
    {
        return presentDigests.contains(entry.getSourceSha256());
    }

    boolean hasLocalPackArtwork(WikiArtworkEntry entry)
    {
        return entry.isBundledFallback()
            && localPackEntries.contains(entry.getRuntimeFilename());
    }

    /** Compatibility alias retained for older source consumers. */
    boolean hasBundledFallback(WikiArtworkEntry entry)
    {
        return hasLocalPackArtwork(entry);
    }

    void request(
        WikiArtworkEntry entry,
        Consumer<String> onAvailable)
    {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(onAvailable, "onAvailable");
        // Individual artwork downloads are deliberately disabled. The only
        // network path is the versioned, checksum-verified release archive
        // prepared by prepareAsync().
    }

    public int size()
    {
        return presentDigests.size();
    }

    public int localPackEntryCount()
    {
        return localPackEntries.size();
    }

    /** Compatibility alias retained for older UI/report callers. */
    public int bundledFallbackCount()
    {
        return localPackEntryCount();
    }

    /** Prepares the verified local/release archive away from the Swing EDT. */
    public void prepareAsync(Executor executor)
    {
        prepareAsync(executor, () -> { });
    }

    public void prepareAsync(Executor executor, Runnable onPrepared)
    {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(onPrepared, "onPrepared");
        executor.execute(() -> {
            ensureLocalPackPrepared();
            onPrepared.run();
        });
    }

    boolean isBundledArchivePreparedForTesting()
    {
        synchronized (this)
        {
            return archivePreparationAttempted;
        }
    }

    public Path getDirectory()
    {
        return directory;
    }

    public Path getLocalPackPath()
    {
        return directory.resolve(LOCAL_PACK_FILENAME);
    }

    private void discoverExistingImages()
    {
        if (!Files.isDirectory(directory))
        {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.list(directory))
        {
            paths.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.matches("[0-9a-f]{64}\\.png"))
                .map(name -> name.substring(0, 64))
                .forEach(presentDigests::add);
        }
        catch (IOException ignored)
        {
            presentDigests.clear();
        }
    }

    private void ensureLocalPackPrepared()
    {
        synchronized (this)
        {
            if (archivePreparationAttempted || closed)
            {
                return;
            }
            archivePreparationAttempted = true;
        }
        prepareLocalPack();
    }

    private void prepareLocalPack()
    {
        String expectedHash = readExpectedPackHash();
        Path archivePath = findVerifiedLocalPack(expectedHash);
        if (archivePath == null)
        {
            archivePath = downloadVerifiedPack(expectedHash);
        }
        if (archivePath == null)
        {
            return;
        }
        try
        {
            long size = Files.size(archivePath);
            if (size <= 0L || size > MAX_ARCHIVE_BYTES)
            {
                return;
            }
            ZipFile archive = new ZipFile(archivePath.toFile());
            Set<String> entries = new HashSet<>();
            boolean invalid = archive.stream()
                .filter(zipEntry -> !zipEntry.isDirectory())
                .anyMatch(zipEntry -> !isSafeArtworkEntry(zipEntry, entries));
            if (invalid || entries.isEmpty())
            {
                archive.close();
                return;
            }
            synchronized (this)
            {
                if (closed)
                {
                    archive.close();
                    return;
                }
                localPackArchive = archive;
                localPackEntries = Collections.unmodifiableSet(entries);
            }
        }
        catch (IOException | RuntimeException ignored)
        {
            closeLocalPack();
        }
    }

    private String readExpectedPackHash()
    {
        ClassLoader loader = WikiArtworkDiskCache.class.getClassLoader();
        try (InputStream hashInput = loader.getResourceAsStream(
            PACK_HASH_RESOURCE))
        {
            if (hashInput == null)
            {
                return null;
            }
            String expectedHash = new String(
                readBounded(hashInput, 256),
                StandardCharsets.US_ASCII).trim().toLowerCase(
                    java.util.Locale.ROOT);
            return expectedHash.matches("[0-9a-f]{64}")
                ? expectedHash
                : null;
        }
        catch (IOException | RuntimeException ignored)
        {
            return null;
        }
    }

    private Path findVerifiedLocalPack(String expectedHash)
    {
        if (expectedHash == null)
        {
            return null;
        }
        try
        {
            Files.createDirectories(directory);
            Path versioned = versionedPackPath(expectedHash);
            if (isExpectedPack(versioned, expectedHash))
            {
                removeStaleBundledArchives(versioned);
                return versioned;
            }
            Files.deleteIfExists(versioned);

            Path preferred = directory.resolve(LOCAL_PACK_FILENAME);
            if (isExpectedPack(preferred, expectedHash))
            {
                moveOrCopyVerifiedPack(preferred, versioned);
                removeStaleBundledArchives(versioned);
                return versioned;
            }
            return null;
        }
        catch (IOException | RuntimeException ignored)
        {
            return null;
        }
    }

    private Path downloadVerifiedPack(String expectedHash)
    {
        if (expectedHash == null || closed)
        {
            return null;
        }
        try
        {
            Files.createDirectories(directory);
            Path target = versionedPackPath(expectedHash);
            Path temporary = Files.createTempFile(
                directory, "offline-assets-v1-download-", ".tmp");
            try
            {
                try (InputStream input = openVerifiedRemoteStream(
                    REMOTE_PACK_URI))
                {
                    writeAndFlushBounded(input, temporary, MAX_ARCHIVE_BYTES);
                }
                if (!expectedHash.equals(sha256(temporary)))
                {
                    throw new IOException(
                        "Downloaded artwork archive failed verification.");
                }
                moveAtomicallyReplacing(temporary, target);
            }
            finally
            {
                Files.deleteIfExists(temporary);
            }
            removeStaleBundledArchives(target);
            return target;
        }
        catch (IOException | RuntimeException ignored)
        {
            return null;
        }
    }

    private Path versionedPackPath(String expectedHash)
    {
        return directory.resolve(
            "offline-assets-v1-" + expectedHash.substring(0, 16) + ".zip");
    }

    private static boolean isExpectedPack(Path path, String expectedHash)
        throws IOException
    {
        return Files.isRegularFile(path)
            && Files.size(path) > 0L
            && Files.size(path) <= MAX_ARCHIVE_BYTES
            && expectedHash.equals(sha256(path));
    }

    private static void moveOrCopyVerifiedPack(Path source, Path target)
        throws IOException
    {
        try
        {
            moveAtomicallyReplacing(source, target);
        }
        catch (IOException moveFailed)
        {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static InputStream openVerifiedRemoteStream(URI initialUri)
        throws IOException
    {
        URI current = initialUri;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++)
        {
            if (!isApprovedBundleUri(current))
            {
                throw new IOException("Unapproved artwork download host.");
            }
            URLConnection rawConnection = current.toURL().openConnection();
            if (!(rawConnection instanceof HttpURLConnection))
            {
                throw new IOException("Artwork download requires HTTPS.");
            }
            HttpURLConnection connection =
                (HttpURLConnection) rawConnection;
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty(
                "User-Agent", "Card-Locked/0.81.04 RuneLite");
            connection.setRequestProperty("Accept", "application/zip");

            int status = connection.getResponseCode();
            if (status >= 300 && status < 400)
            {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.trim().isEmpty())
                {
                    throw new IOException(
                        "Artwork download redirect had no destination.");
                }
                current = current.resolve(location);
                continue;
            }
            if (status != HttpURLConnection.HTTP_OK)
            {
                connection.disconnect();
                throw new IOException(
                    "Artwork download failed with HTTP " + status + ".");
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_ARCHIVE_BYTES)
            {
                connection.disconnect();
                throw new IOException("Artwork archive exceeds size limit.");
            }
            InputStream stream = connection.getInputStream();
            return new java.io.FilterInputStream(stream)
            {
                @Override
                public void close() throws IOException
                {
                    try
                    {
                        super.close();
                    }
                    finally
                    {
                        connection.disconnect();
                    }
                }
            };
        }
        throw new IOException("Too many artwork download redirects.");
    }

    static boolean isApprovedBundleUri(URI uri)
    {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()))
        {
            return false;
        }
        String host = uri.getHost();
        return "github.com".equalsIgnoreCase(host)
            || "objects.githubusercontent.com".equalsIgnoreCase(host)
            || "release-assets.githubusercontent.com".equalsIgnoreCase(host);
    }

    private void removeStaleBundledArchives(Path current)
    {
        if (!Files.isDirectory(directory))
        {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.list(directory))
        {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString()
                    .matches("offline-assets-v1-[0-9a-f]{16}\\.zip"))
                .filter(path -> !path.equals(current))
                .forEach(path -> {
                    try
                    {
                        Files.deleteIfExists(path);
                    }
                    catch (IOException ignored)
                    {
                        // Stale cache cleanup is opportunistic.
                    }
                });
        }
        catch (IOException ignored)
        {
            // Stale cache cleanup is opportunistic.
        }
    }

    private static boolean isSafeArtworkEntry(
        ZipEntry entry,
        Set<String> entries)
    {
        String name = entry.getName();
        return name != null
            && name.matches("assets/[0-9a-f]{64}\\.png")
            && entry.getSize() >= 0
            && entry.getSize() <= MAX_IMAGE_BYTES
            && entries.add(name);
    }

    static boolean isApprovedSource(URI uri)
    {
        return uri != null
            && "https".equalsIgnoreCase(uri.getScheme())
            && "oldschool.runescape.wiki".equalsIgnoreCase(uri.getHost())
            && uri.getPath() != null
            && uri.getPath().startsWith("/images/");
    }

    private Path imagePath(WikiArtworkEntry entry)
    {
        return directory.resolve(entry.getSourceSha256() + ".png");
    }

    private static boolean isUsable(BufferedImage image)
    {
        return image != null && image.getWidth() > 0 && image.getHeight() > 0;
    }

    private static byte[] readBounded(InputStream input, int maximumBytes)
        throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0)
        {
            if (read == 0)
            {
                continue;
            }
            total += read;
            if (total > maximumBytes)
            {
                throw new IOException("Resource exceeds the size limit.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void writeAndFlushBounded(
        InputStream input,
        Path target,
        long maximumBytes)
        throws IOException
    {
        try (FileChannel channel = FileChannel.open(
            target,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING))
        {
            byte[] bytes = new byte[8192];
            long total = 0L;
            int read;
            while ((read = input.read(bytes)) >= 0)
            {
                if (read == 0)
                {
                    continue;
                }
                total += read;
                if (total > maximumBytes)
                {
                    throw new IOException("Resource exceeds the size limit.");
                }
                ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, read);
                while (buffer.hasRemaining())
                {
                    channel.write(buffer);
                }
            }
            channel.force(true);
        }
    }

    private static void moveAtomicallyReplacing(Path source, Path target)
        throws IOException
    {
        try
        {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException ignored)
        {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path path) throws IOException
    {
        try (InputStream input = Files.newInputStream(path))
        {
            return sha256(input);
        }
    }

    private static String sha256(byte[] bytes) throws IOException
    {
        return toHex(newDigest().digest(bytes));
    }

    private static String sha256(InputStream input) throws IOException
    {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0)
        {
            if (read > 0)
            {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static MessageDigest newDigest() throws IOException
    {
        try
        {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IOException("SHA-256 is unavailable.", impossible);
        }
    }

    private static String toHex(byte[] bytes)
    {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
        {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private synchronized void closeLocalPack()
    {
        if (localPackArchive != null)
        {
            try
            {
                localPackArchive.close();
            }
            catch (IOException ignored)
            {
                // Cache shutdown is best effort.
            }
        }
        localPackArchive = null;
        localPackEntries = Collections.emptySet();
    }

    @Override
    public synchronized void close()
    {
        closed = true;
        archivePreparationAttempted = true;
        verifiedArchiveDigests.clear();
        closeLocalPack();
    }
}
