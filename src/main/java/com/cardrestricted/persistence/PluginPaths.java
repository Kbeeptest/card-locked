package com.cardrestricted.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/** Centralised filesystem layout and conservative legacy-data migration. */
public final class PluginPaths
{
    private static final String CURRENT_ROOT = "card-locked";
    private static final String LEGACY_ROOT = "card-restricted-account";

    private final Path runeLiteDirectory;
    private final Path rootDirectory;

    public PluginPaths(Path runeLiteDirectory)
    {
        this.runeLiteDirectory = Objects.requireNonNull(
            runeLiteDirectory,
            "runeLiteDirectory");
        this.rootDirectory = runeLiteDirectory.resolve(CURRENT_ROOT);
    }

    public Path rootDirectory()
    {
        return rootDirectory;
    }

    public Path charactersDirectory()
    {
        return rootDirectory.resolve("characters");
    }

    public Path interactionDiagnosticsFile()
    {
        return rootDirectory.resolve("interaction-diagnostics.tsv");
    }

    public Path diagnosticsDirectory()
    {
        return rootDirectory.resolve("diagnostics");
    }

    public Path migrationBackupsDirectory()
    {
        return rootDirectory.resolve("migration-backups");
    }

    public Path wikiArtworkDirectory()
    {
        return rootDirectory.resolve("artwork").resolve("offline-wiki-v1");
    }

    public void prepareAndMigrate() throws IOException
    {
        Files.createDirectories(rootDirectory);
        Path legacyCharacters = runeLiteDirectory.resolve(LEGACY_ROOT)
            .resolve("characters");
        backupLegacyCharacters(legacyCharacters);
        mergeDirectory(legacyCharacters, charactersDirectory());
        Files.createDirectories(charactersDirectory());
        Files.createDirectories(wikiArtworkDirectory());
        Files.createDirectories(diagnosticsDirectory());
        deleteTree(rootDirectory.resolve("artwork").resolve("wiki-v1"));
        deleteTree(rootDirectory.resolve("artwork").resolve("npc-captures"));
        deleteTree(rootDirectory.resolve("npc-artwork-cache"));
        removeEmptyTree(runeLiteDirectory.resolve(LEGACY_ROOT));
    }

    private void backupLegacyCharacters(Path source) throws IOException
    {
        if (!Files.isDirectory(source))
        {
            return;
        }
        Path backups = migrationBackupsDirectory();
        Path destination = backups.resolve("legacy-characters-v1");
        if (Files.exists(destination))
        {
            if (!Files.isDirectory(destination))
            {
                throw new IOException(
                    "The legacy migration backup path is not a directory.");
            }
            return;
        }
        Files.createDirectories(backups);
        Path pending = backups.resolve(
            ".legacy-characters-v1-" + UUID.randomUUID() + ".pending");
        try
        {
            copyTree(source, pending);
            verifyTreeCopy(source, pending);
            try
            {
                Files.move(pending, destination,
                    StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException ignored)
            {
                Files.move(pending, destination);
            }
        }
        finally
        {
            deleteTree(pending);
        }
    }

    private static void copyTree(Path source, Path target)
        throws IOException
    {
        try (Stream<Path> paths = Files.walk(source))
        {
            paths.sorted(Comparator.naturalOrder()).forEach(path -> {
                Path destination = target.resolve(source.relativize(path));
                try
                {
                    if (Files.isSymbolicLink(path))
                    {
                        throw new IOException(
                            "Legacy profile migration does not follow symbolic links.");
                    }
                    if (Files.isDirectory(path))
                    {
                        Files.createDirectories(destination);
                    }
                    else if (Files.isRegularFile(path))
                    {
                        Files.createDirectories(destination.getParent());
                        Files.copy(path, destination);
                    }
                }
                catch (IOException exception)
                {
                    throw new MigrationFailure(exception);
                }
            });
        }
        catch (MigrationFailure failure)
        {
            throw failure.getCause();
        }
    }

    private static void verifyTreeCopy(Path source, Path target)
        throws IOException
    {
        long sourceFiles;
        long targetFiles;
        try (Stream<Path> paths = Files.walk(source))
        {
            Path[] files = paths.filter(Files::isRegularFile)
                .toArray(Path[]::new);
            sourceFiles = files.length;
            for (Path file : files)
            {
                Path copy = target.resolve(source.relativize(file));
                if (!Files.isRegularFile(copy)
                    || Files.size(file) != Files.size(copy)
                    || !MessageDigest.isEqual(
                        sha256(file),
                        sha256(copy)))
                {
                    throw new IOException(
                        "The legacy profile migration backup failed verification.");
                }
            }
        }
        try (Stream<Path> paths = Files.walk(target))
        {
            targetFiles = paths.filter(Files::isRegularFile).count();
        }
        if (sourceFiles != targetFiles)
        {
            throw new IOException(
                "The legacy profile migration backup file count differs.");
        }
    }

    private static byte[] sha256(Path path) throws IOException
    {
        MessageDigest digest;
        try
        {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
        try (InputStream input = Files.newInputStream(path))
        {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0)
            {
                if (read > 0)
                {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return digest.digest();
    }

    private static void mergeDirectory(Path source, Path target)
        throws IOException
    {
        if (!Files.isDirectory(source))
        {
            return;
        }
        Files.createDirectories(target);
        try (Stream<Path> paths = Files.walk(source))
        {
            paths.sorted(Comparator.naturalOrder()).forEach(path -> {
                if (path.equals(source))
                {
                    return;
                }
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                try
                {
                    if (Files.isDirectory(path))
                    {
                        Files.createDirectories(destination);
                    }
                    else if (!Files.exists(destination))
                    {
                        Files.createDirectories(destination.getParent());
                        moveOrCopy(path, destination);
                    }
                }
                catch (IOException exception)
                {
                    throw new MigrationFailure(exception);
                }
            });
        }
        catch (MigrationFailure failure)
        {
            throw failure.getCause();
        }
        removeEmptyTree(source);
    }

    private static void moveOrCopy(Path source, Path target)
        throws IOException
    {
        try
        {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException moveFailure)
        {
            Files.copy(source, target);
            Files.delete(source);
        }
    }

    private static void deleteTree(Path root) throws IOException
    {
        if (!Files.exists(root))
        {
            return;
        }
        try (Stream<Path> paths = Files.walk(root))
        {
            Path[] ordered = paths.sorted(Comparator.reverseOrder())
                .toArray(Path[]::new);
            for (Path path : ordered)
            {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void removeEmptyTree(Path root) throws IOException
    {
        if (!Files.exists(root))
        {
            return;
        }
        try (Stream<Path> paths = Files.walk(root))
        {
            Path[] ordered = paths.sorted(Comparator.reverseOrder())
                .toArray(Path[]::new);
            for (Path path : ordered)
            {
                if (Files.isDirectory(path))
                {
                    try (Stream<Path> children = Files.list(path))
                    {
                        if (!children.findAny().isPresent())
                        {
                            Files.deleteIfExists(path);
                        }
                    }
                }
            }
        }
    }

    private static final class MigrationFailure extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private MigrationFailure(IOException cause)
        {
            super(cause);
        }

        @Override
        public synchronized IOException getCause()
        {
            return (IOException) super.getCause();
        }
    }
}
