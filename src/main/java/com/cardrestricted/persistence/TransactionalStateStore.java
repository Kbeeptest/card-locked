package com.cardrestricted.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TransactionalStateStore
{
    private static final String CURRENT = "current.snapshot";
    private static final String LEGACY_PREVIOUS = "previous.snapshot";
    private static final String PENDING_SNAPSHOT = "pending.snapshot";
    private static final String PENDING_EVENT = "pending.event";
    private static final String RESTORE_SNAPSHOT = "restore.snapshot";
    private static final String JOURNAL_DIRECTORY = "journal";
    private static final String QUARANTINE_DIRECTORY = "recovery-quarantine";
    private static final int RECOVERY_SNAPSHOT_COUNT = 5;
    private static final int HISTORICAL_JOURNAL_AUDIT_INTERVAL = 64;
    private static final long MAX_EXTERNAL_SNAPSHOT_BYTES =
        17L * 1024L * 1024L;
    private static final Pattern JOURNAL_FILE = Pattern.compile(
        "(\\d{20})\\.event");
    private static final ConcurrentMap<Path, Object> DIRECTORY_MONITORS =
        new ConcurrentHashMap<>();

    private final Path directory;
    private final SnapshotCodec codec;
    private final JournalEventCodec journalCodec;
    private final PersistenceFaultInjector faultInjector;
    private ArrayList<StateJournalEvent> journalCache;
    private int fastPathLoadsSinceHistoricalAudit;
    private int historicalJournalAuditIndex;
    private int quarantinedArtifactCount;
    private boolean restoredFromBackup;

    private Path normalizedDirectory()
    {
        return directory.toAbsolutePath().normalize();
    }

    private Object directoryMonitor()
    {
        return DIRECTORY_MONITORS.computeIfAbsent(
            normalizedDirectory(),
            ignored -> new Object());
    }

    private Path transactionLockPath()
    {
        Path normalized = normalizedDirectory();
        Path parent = normalized.getParent();
        String name = normalized.getFileName() == null
            ? "root"
            : normalized.getFileName().toString();
        if (parent == null)
        {
            return normalized.resolve(".card-locked-transaction.lock");
        }
        return parent.resolve("." + name + ".card-locked-transaction.lock");
    }

    private <T> T withDirectoryTransactionLock(IoOperation<T> operation)
        throws IOException
    {
        synchronized (directoryMonitor())
        {
            Path lockPath = transactionLockPath();
            Path parent = lockPath.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }
            try (FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
                 FileLock transactionLock = acquireFileLock(channel))
            {
                if (!transactionLock.isValid())
                {
                    throw new IOException(
                        "The collection state lock was not acquired.");
                }
                return operation.run();
            }
        }
    }

    private FileLock acquireFileLock(FileChannel channel) throws IOException
    {
        while (true)
        {
            try
            {
                FileLock lock = channel.tryLock();
                if (lock != null)
                {
                    return lock;
                }
            }
            catch (OverlappingFileLockException ignored)
            {
                // Another store or test in this JVM owns the same profile lock.
            }
            if (Thread.currentThread().isInterrupted())
            {
                throw new java.io.InterruptedIOException(
                    "Interrupted while waiting for the collection state lock.");
            }
            LockSupport.parkNanos(10_000_000L);
        }
    }

    public TransactionalStateStore(Path directory, SnapshotCodec codec)
    {
        this(
            directory,
            codec,
            new JournalEventCodec(),
            PersistenceFaultInjector.NONE);
    }

    public TransactionalStateStore(
        Path directory,
        SnapshotCodec codec,
        JournalEventCodec journalCodec)
    {
        this(
            directory,
            codec,
            journalCodec,
            PersistenceFaultInjector.NONE);
    }

    public TransactionalStateStore(
        Path directory,
        SnapshotCodec codec,
        JournalEventCodec journalCodec,
        PersistenceFaultInjector faultInjector)
    {
        this.directory = directory;
        this.codec = codec;
        this.journalCodec = journalCodec;
        this.faultInjector = faultInjector;
    }

    public synchronized void deleteAll() throws IOException
    {
        withDirectoryTransactionLock(() -> {
            if (!Files.exists(directory))
            {
                journalCache = null;
                fastPathLoadsSinceHistoricalAudit = 0;
                historicalJournalAuditIndex = 0;
                quarantinedArtifactCount = 0;
                restoredFromBackup = false;
                return null;
            }
            try (java.util.stream.Stream<Path> paths = Files.walk(directory))
            {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try
                    {
                        Files.deleteIfExists(path);
                    }
                    catch (IOException exception)
                    {
                        throw new java.io.UncheckedIOException(exception);
                    }
                });
            }
            catch (java.io.UncheckedIOException exception)
            {
                throw exception.getCause();
            }
            journalCache = null;
            fastPathLoadsSinceHistoricalAudit = 0;
            historicalJournalAuditIndex = 0;
            quarantinedArtifactCount = 0;
            restoredFromBackup = false;
            return null;
        });
    }

    public synchronized Optional<CollectionState> loadHighestValid()
        throws IOException
    {
        return withDirectoryTransactionLock(
            this::loadHighestValidUnderDirectoryLock);
    }

    private Optional<CollectionState> loadHighestValidUnderDirectoryLock()
        throws IOException
    {
        Files.createDirectories(directory);
        boolean hadCommittedArtifacts = hasCommittedPersistenceArtifacts();
        recoverCommittedPendingSnapshot();

        Optional<CollectionState> healthyCurrent =
            tryLoadJournalBoundCurrentSnapshot();
        if (healthyCurrent.isPresent())
        {
            fastPathLoadsSinceHistoricalAudit++;
            return healthyCurrent;
        }

        List<SnapshotCandidate> candidates = loadSnapshotCandidates();
        if (candidates.isEmpty())
        {
            repairJournalWithoutSnapshot();
            cleanupAbandonedPendingFiles();
            fastPathLoadsSinceHistoricalAudit = 0;
            historicalJournalAuditIndex = 0;
            if (hadCommittedArtifacts)
            {
                throw new CorruptSnapshotException(
                    "No valid collection snapshot could be recovered. "
                        + "Original files were preserved in "
                        + QUARANTINE_DIRECTORY + ".");
            }
            return Optional.empty();
        }

        JournalLoad journal = readJournalPrefix();
        SnapshotCandidate selected = selectConsistentSnapshot(
            candidates,
            journal.events);

        if (selected == null)
        {
            // A valid snapshot is more useful than an unusable journal. Preserve
            // the journal for diagnosis, then establish the snapshot as a new
            // local baseline for subsequent mutations.
            quarantinePaths(journal.paths, "unmatched-journal");
            journal = JournalLoad.empty();
            selected = candidates.stream()
                .max(snapshotComparator())
                .orElseThrow();
        }
        else
        {
            journal = truncateJournalAfter(journal, selected.state.getRevision());
        }

        quarantineInconsistentNewerSnapshots(candidates, selected);
        restoreCurrentSnapshot(selected);
        journalCache = new ArrayList<>(journal.events);
        fastPathLoadsSinceHistoricalAudit = 0;
        historicalJournalAuditIndex = 0;
        cleanupAbandonedPendingFiles();
        return Optional.of(selected.state);
    }

    /**
     * Normal mutations only need the healthy current generation, not all five
     * recovery generations. Validate current.snapshot against the already
     * fully-validated in-memory journal tail and the corresponding durable
     * event file. Historical journal files are audited incrementally so
     * long-running profiles retain tamper detection without periodic O(history)
     * stalls. Any mismatch falls through to the exhaustive recovery scan.
     */
    private Optional<CollectionState> tryLoadJournalBoundCurrentSnapshot()
        throws IOException
    {
        if (journalCache == null || journalCache.isEmpty())
        {
            return Optional.empty();
        }

        Path currentPath = directory.resolve(CURRENT);
        if (!Files.isRegularFile(currentPath))
        {
            return Optional.empty();
        }

        byte[] currentBytes;
        CollectionState current;
        try
        {
            currentBytes = Files.readAllBytes(currentPath);
            current = codec.decode(currentBytes);
        }
        catch (CorruptSnapshotException exception)
        {
            return Optional.empty();
        }

        StateJournalEvent cachedTail =
            journalCache.get(journalCache.size() - 1);
        if (cachedTail.getRevision() != current.getRevision()
            || !cachedTail.getCollectionId().equals(current.getCollectionId())
            || !cachedTail.getCharacterKey().equals(current.getCharacterKey()))
        {
            return Optional.empty();
        }

        Path eventPath = directory.resolve(JOURNAL_DIRECTORY).resolve(
            String.format("%020d.event", current.getRevision()));
        if (!Files.isRegularFile(eventPath))
        {
            return Optional.empty();
        }

        StateJournalEvent durableTail;
        try
        {
            durableTail = journalCodec.decode(Files.readAllBytes(eventPath));
        }
        catch (CorruptSnapshotException exception)
        {
            return Optional.empty();
        }
        String currentHash = journalCodec.sha256Hex(currentBytes);
        if (!durableTail.getEventHash().equals(cachedTail.getEventHash())
            || durableTail.getRevision() != current.getRevision()
            || !durableTail.getCollectionId().equals(current.getCollectionId())
            || !durableTail.getCharacterKey().equals(current.getCharacterKey())
            || !durableTail.getStateHash().equals(currentHash))
        {
            return Optional.empty();
        }
        if (fastPathLoadsSinceHistoricalAudit
            >= HISTORICAL_JOURNAL_AUDIT_INTERVAL)
        {
            if (!validateNextHistoricalJournalEvent())
            {
                return Optional.empty();
            }
            fastPathLoadsSinceHistoricalAudit = 0;
        }
        return Optional.of(current);
    }

    private boolean validateNextHistoricalJournalEvent() throws IOException
    {
        if (journalCache == null || journalCache.size() <= 1)
        {
            return true;
        }
        int historicalCount = journalCache.size() - 1;
        int index = Math.floorMod(historicalJournalAuditIndex, historicalCount);
        StateJournalEvent cached = journalCache.get(index);
        Path eventPath = directory.resolve(JOURNAL_DIRECTORY).resolve(
            String.format("%020d.event", cached.getRevision()));
        if (!Files.isRegularFile(eventPath))
        {
            return false;
        }
        StateJournalEvent durable;
        try
        {
            durable = journalCodec.decode(Files.readAllBytes(eventPath));
        }
        catch (CorruptSnapshotException exception)
        {
            return false;
        }
        if (!durable.getEventHash().equals(cached.getEventHash())
            || durable.getRevision() != cached.getRevision()
            || durable.getPreviousRevision() != cached.getPreviousRevision()
            || !durable.getCollectionId().equals(cached.getCollectionId())
            || !durable.getCharacterKey().equals(cached.getCharacterKey())
            || !durable.getPreviousEventHash().equals(cached.getPreviousEventHash())
            || !durable.getStateHash().equals(cached.getStateHash()))
        {
            return false;
        }
        historicalJournalAuditIndex = (index + 1) % historicalCount;
        return true;
    }

    public synchronized Optional<String> consumeRecoveryNotice()
    {
        if (quarantinedArtifactCount == 0 && !restoredFromBackup)
        {
            return Optional.empty();
        }
        StringBuilder message = new StringBuilder();
        if (restoredFromBackup)
        {
            message.append("Collection recovered from a local backup.");
        }
        else
        {
            message.append("Interrupted or damaged save metadata was repaired.");
        }
        if (quarantinedArtifactCount > 0)
        {
            message.append(' ')
                .append(quarantinedArtifactCount)
                .append(quarantinedArtifactCount == 1
                    ? " original file was"
                    : " original files were")
                .append(" preserved in ")
                .append(QUARANTINE_DIRECTORY)
                .append('.');
        }
        quarantinedArtifactCount = 0;
        restoredFromBackup = false;
        return Optional.of(message.toString());
    }

    /** Writes the verified current snapshot to a new user-selected file. */
    public synchronized Path exportCurrentSnapshot(Path destination)
        throws IOException
    {
        Objects.requireNonNull(destination, "destination");
        return withDirectoryTransactionLock(() -> {
            CollectionState current = loadHighestValidUnderDirectoryLock()
                .orElseThrow(() -> new IOException(
                    "No collection snapshot is available to export."));
            Path output = destination.toAbsolutePath().normalize();
            if (Files.exists(output))
            {
                throw new IOException(
                    "The selected backup file already exists.");
            }
            Path parent = output.getParent();
            if (parent == null)
            {
                throw new IOException(
                    "The selected backup location has no parent directory.");
            }
            Files.createDirectories(parent);
            Path pendingExport = parent.resolve(
                "." + output.getFileName() + "." + UUID.randomUUID()
                    + ".pending");
            try
            {
                byte[] encoded = codec.encode(current);
                writeAndFlush(pendingExport, encoded);
                byte[] written = Files.readAllBytes(pendingExport);
                CollectionState verified = codec.decode(written);
                if (!Arrays.equals(encoded, written)
                    || !sameStateIdentityAndRevision(current, verified))
                {
                    throw new CorruptSnapshotException(
                        "The exported snapshot failed verification.");
                }
                moveWithoutReplacing(pendingExport, output);
                return output;
            }
            finally
            {
                Files.deleteIfExists(pendingExport);
            }
        });
    }

    /**
     * Imports a validated snapshot. Manual recovery always forfeits integrity;
     * on an existing profile it also preserves every recorded one-time claim
     * and the highest skill watermark so rollback cannot duplicate rewards.
     */
    public synchronized CollectionState importSnapshot(
        Path source,
        String expectedCharacterKey,
        int maximumCatalogueVersion,
        String integrityForfeitedMarker)
        throws IOException
    {
        Objects.requireNonNull(source, "source");
        String characterKey = requireText(
            expectedCharacterKey,
            "expectedCharacterKey");
        String forfeitedMarker = requireText(
            integrityForfeitedMarker,
            "integrityForfeitedMarker");
        if (maximumCatalogueVersion < 1)
        {
            throw new IllegalArgumentException(
                "maximumCatalogueVersion must be positive");
        }
        return withDirectoryTransactionLock(() -> {
            CollectionState imported = readExternalSnapshot(source);
            validateImportedSnapshot(
                imported,
                characterKey,
                maximumCatalogueVersion);

            Optional<CollectionState> current;
            try
            {
                current = loadHighestValidUnderDirectoryLock();
            }
            catch (CorruptSnapshotException unrecoverableCurrent)
            {
                // loadHighestValid has already preserved every invalid
                // committed artifact in recovery-quarantine.
                current = Optional.empty();
            }

            CollectionState recovery;
            long expectedRevision;
            JournalEventType eventType;
            if (current.isPresent())
            {
                CollectionState existing = current.orElseThrow();
                if (!existing.getCollectionId().equals(
                    imported.getCollectionId()))
                {
                    throw new IOException(
                        "The backup belongs to a different collection lineage.");
                }
                recovery = manualRecoveryState(
                    existing,
                    imported,
                    forfeitedMarker);
                expectedRevision = existing.getRevision();
                eventType = JournalEventType.STATE_UPDATED;
            }
            else
            {
                recovery = baselineImportState(
                    imported,
                    characterKey,
                    forfeitedMarker);
                expectedRevision = -1L;
                eventType = JournalEventType.COLLECTION_CREATED;
            }
            CollectionState committed = persistRecoveryState(
                recovery,
                expectedRevision,
                eventType,
                "manualBackupImport=true");
            restoredFromBackup = true;
            return committed;
        });
    }

    /** Restores the newest older automatic snapshot as a new casual revision. */
    public synchronized CollectionState restorePreviousSnapshot(
        int maximumCatalogueVersion,
        String integrityForfeitedMarker)
        throws IOException
    {
        String forfeitedMarker = requireText(
            integrityForfeitedMarker,
            "integrityForfeitedMarker");
        return withDirectoryTransactionLock(() -> {
            CollectionState current = loadHighestValidUnderDirectoryLock()
                .orElseThrow(() -> new IOException(
                    "No current collection is available to restore."));
            CollectionState previous = newestPreviousSnapshot(current)
                .orElseThrow(() -> new IOException(
                    "No valid earlier automatic backup is available."));
            validateImportedSnapshot(
                previous,
                current.getCharacterKey(),
                maximumCatalogueVersion);
            CollectionState recovery = manualRecoveryState(
                current,
                previous,
                forfeitedMarker);
            CollectionState committed = persistRecoveryState(
                recovery,
                current.getRevision(),
                JournalEventType.STATE_UPDATED,
                "automaticBackupRestore=true;sourceRevision="
                    + previous.getRevision());
            restoredFromBackup = true;
            return committed;
        });
    }

    private CollectionState readExternalSnapshot(Path source)
        throws IOException
    {
        Path input = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(input))
        {
            throw new IOException("The selected backup is not a regular file.");
        }
        long size = Files.size(input);
        if (size < 1L || size > MAX_EXTERNAL_SNAPSHOT_BYTES)
        {
            throw new IOException("The selected backup size is invalid.");
        }
        return codec.decode(Files.readAllBytes(input));
    }

    private void validateImportedSnapshot(
        CollectionState imported,
        String expectedCharacterKey,
        int maximumCatalogueVersion)
        throws IOException
    {
        if (!expectedCharacterKey.equals(imported.getCharacterKey()))
        {
            throw new IOException(
                "The backup belongs to a different character.");
        }
        if (imported.getCatalogueVersion() > maximumCatalogueVersion)
        {
            throw new IOException(
                "The backup requires a newer card catalogue.");
        }
    }

    private Optional<CollectionState> newestPreviousSnapshot(
        CollectionState current)
        throws IOException
    {
        CollectionState selected = null;
        for (int generation = 1;
             generation <= RECOVERY_SNAPSHOT_COUNT;
             generation++)
        {
            Path path = recoveryPath(generation);
            if (!Files.isRegularFile(path))
            {
                continue;
            }
            try
            {
                CollectionState candidate = codec.decode(
                    Files.readAllBytes(path));
                if (candidate.getRevision() >= current.getRevision()
                    || !candidate.getCollectionId().equals(
                        current.getCollectionId())
                    || !candidate.getCharacterKey().equals(
                        current.getCharacterKey()))
                {
                    continue;
                }
                if (selected == null
                    || candidate.getRevision() > selected.getRevision())
                {
                    selected = candidate;
                }
            }
            catch (CorruptSnapshotException corrupt)
            {
                quarantine(path, "corrupt-manual-recovery-candidate");
            }
        }
        return Optional.ofNullable(selected);
    }

    private CollectionState manualRecoveryState(
        CollectionState current,
        CollectionState backup,
        String forfeitedMarker)
    {
        Set<String> claimed = new HashSet<>(
            backup.getClaimedPointSourceIds());
        claimed.addAll(current.getClaimedPointSourceIds());
        claimed.add(forfeitedMarker);
        Map<String, Long> watermarks = new HashMap<>(
            backup.getNoncombatXpWatermarks());
        current.getNoncombatXpWatermarks().forEach((skill, watermark) ->
            watermarks.merge(skill, watermark, Math::max));
        return new CollectionState(
            current.getCollectionId(),
            current.getCharacterKey(),
            current.getDisplayName(),
            current.getEconomyMode(),
            com.cardrestricted.domain.IntegrityMode.CASUAL,
            current.getCreatedAt(),
            Math.max(current.getSchemaVersion(), backup.getSchemaVersion()),
            backup.getCatalogueVersion(),
            Math.max(current.getRuleSetVersion(), backup.getRuleSetVersion()),
            current.getRevision() + 1L,
            backup.getPoints(),
            backup.getShards(),
            backup.getOwnedCardIds(),
            backup.getFoilCardIds(),
            claimed,
            backup.getNoncombatRewardRemainderUnits(),
            watermarks,
            backup.getPendingPackReveal().orElse(null));
    }

    private CollectionState baselineImportState(
        CollectionState backup,
        String expectedCharacterKey,
        String forfeitedMarker)
    {
        Set<String> claimed = new HashSet<>(
            backup.getClaimedPointSourceIds());
        claimed.add(forfeitedMarker);
        return new CollectionState(
            backup.getCollectionId(),
            expectedCharacterKey,
            backup.getDisplayName(),
            backup.getEconomyMode(),
            com.cardrestricted.domain.IntegrityMode.CASUAL,
            backup.getCreatedAt(),
            backup.getSchemaVersion(),
            backup.getCatalogueVersion(),
            backup.getRuleSetVersion(),
            0L,
            backup.getPoints(),
            backup.getShards(),
            backup.getOwnedCardIds(),
            backup.getFoilCardIds(),
            claimed,
            backup.getNoncombatRewardRemainderUnits(),
            backup.getNoncombatXpWatermarks(),
            backup.getPendingPackReveal().orElse(null));
    }

    private CollectionState persistRecoveryState(
        CollectionState recovery,
        long expectedRevision,
        JournalEventType eventType,
        String payload)
        throws IOException
    {
        try
        {
            saveUnderDirectoryLock(
                recovery,
                expectedRevision,
                eventType,
                payload,
                Instant.now());
            return recovery;
        }
        catch (IOException failure)
        {
            try
            {
                Optional<CollectionState> committed =
                    loadHighestValidUnderDirectoryLock();
                if (committed.isPresent()
                    && sameStateIdentityAndRevision(
                        recovery,
                        committed.orElseThrow()))
                {
                    return committed.orElseThrow();
                }
            }
            catch (IOException recoveryFailure)
            {
                failure.addSuppressed(recoveryFailure);
            }
            throw failure;
        }
    }

    private static boolean sameStateIdentityAndRevision(
        CollectionState expected,
        CollectionState actual)
    {
        return expected.getCollectionId().equals(actual.getCollectionId())
            && expected.getCharacterKey().equals(actual.getCharacterKey())
            && expected.getRevision() == actual.getRevision();
    }

    private static String requireText(String value, String name)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public synchronized List<StateJournalEvent> loadJournal()
        throws IOException
    {
        return withDirectoryTransactionLock(
            this::loadJournalUnderDirectoryLock);
    }

    /**
     * Returns only events newer than the supplied revision. The private journal
     * cache is revision ordered, so long-lived sessions can advance derived
     * activity state without copying/replaying the complete lifetime journal.
     */
    public synchronized List<StateJournalEvent> loadJournalAfterRevision(
        long revision) throws IOException
    {
        return withDirectoryTransactionLock(() -> {
            if (journalCache == null)
            {
                JournalLoad load = readJournalPrefix();
                journalCache = new ArrayList<>(load.events);
            }
            int low = 0;
            int high = journalCache.size();
            while (low < high)
            {
                int middle = (low + high) >>> 1;
                if (journalCache.get(middle).getRevision() <= revision)
                {
                    low = middle + 1;
                }
                else
                {
                    high = middle;
                }
            }
            if (low >= journalCache.size())
            {
                return List.of();
            }
            return List.copyOf(journalCache.subList(low, journalCache.size()));
        });
    }

    private List<StateJournalEvent> loadJournalUnderDirectoryLock()
        throws IOException
    {
        if (journalCache == null)
        {
            JournalLoad load = readJournalPrefix();
            journalCache = new ArrayList<>(load.events);
        }
        // Callers performing activity/history calculations receive a stable
        // immutable snapshot. Normal saves use the private appendable cache
        // directly and therefore do not copy the entire journal per event.
        return List.copyOf(journalCache);
    }

    public synchronized void save(
        CollectionState state,
        long expectedPreviousRevision)
        throws IOException
    {
        save(
            state,
            expectedPreviousRevision,
            expectedPreviousRevision == -1
                ? JournalEventType.COLLECTION_CREATED
                : JournalEventType.STATE_UPDATED,
            "",
            Instant.now());
    }

    public synchronized void save(
        CollectionState state,
        long expectedPreviousRevision,
        JournalEventType eventType,
        String payload,
        Instant occurredAt)
        throws IOException
    {
        withDirectoryTransactionLock(() -> {
            saveUnderDirectoryLock(
                state,
                expectedPreviousRevision,
                eventType,
                payload,
                occurredAt);
            return null;
        });
    }

    private void saveUnderDirectoryLock(
        CollectionState state,
        long expectedPreviousRevision,
        JournalEventType eventType,
        String payload,
        Instant occurredAt)
        throws IOException
    {
        Files.createDirectories(directory);
        Optional<CollectionState> current =
            loadHighestValidUnderDirectoryLock();
        long actualRevision = current
            .map(CollectionState::getRevision)
            .orElse(-1L);

        if (actualRevision != expectedPreviousRevision)
        {
            throw new RevisionConflictException(
                expectedPreviousRevision, actualRevision);
        }
        if (state.getRevision() != expectedPreviousRevision + 1)
        {
            throw new RevisionConflictException(
                expectedPreviousRevision + 1, state.getRevision());
        }
        if (current.isPresent()
            && (!current.get().getCollectionId().equals(
                state.getCollectionId())
                || !current.get().getCharacterKey().equals(
                    state.getCharacterKey())))
        {
            throw new IllegalArgumentException(
                "A mutation cannot change collection identity.");
        }

        byte[] encodedState = codec.encode(state);
        if (journalCache == null)
        {
            JournalLoad load = readJournalPrefix();
            journalCache = new ArrayList<>(load.events);
        }
        String previousEventHash = journalCache.isEmpty()
            ? ""
            : journalCache.get(journalCache.size() - 1).getEventHash();
        StateJournalEvent event = journalCodec.create(
            state,
            expectedPreviousRevision,
            eventType,
            payload,
            occurredAt,
            previousEventHash,
            encodedState);

        Path pendingSnapshot = directory.resolve(PENDING_SNAPSHOT);
        Path pendingEvent = directory.resolve(PENDING_EVENT);
        writeAndFlush(pendingSnapshot, encodedState);
        faultInjector.checkpoint(
            PersistenceCommitStage.AFTER_PENDING_SNAPSHOT_FLUSH);
        writeAndFlush(pendingEvent, journalCodec.encode(event));
        faultInjector.checkpoint(
            PersistenceCommitStage.AFTER_PENDING_EVENT_FLUSH);
        verifyPending(state, event, pendingSnapshot, pendingEvent);

        Path journalDirectory = directory.resolve(JOURNAL_DIRECTORY);
        Files.createDirectories(journalDirectory);
        Path committedEvent = journalDirectory.resolve(
            String.format("%020d.event", state.getRevision()));
        if (Files.exists(committedEvent))
        {
            throw new IOException(
                "A journal event already exists for revision "
                    + state.getRevision() + ".");
        }

        moveWithoutReplacing(pendingEvent, committedEvent);
        // The event is durable at this point. Update the in-memory append-only
        // view before the crash checkpoint so a handled I/O failure cannot
        // leave this store using a stale previous-event hash.
        journalCache.add(event);
        faultInjector.checkpoint(
            PersistenceCommitStage.AFTER_EVENT_COMMIT);
        recoverCommittedPendingSnapshot();
    }

    private List<SnapshotCandidate> loadSnapshotCandidates()
        throws IOException
    {
        List<Path> paths = new ArrayList<>();
        paths.add(directory.resolve(CURRENT));
        for (int index = 1; index <= RECOVERY_SNAPSHOT_COUNT; index++)
        {
            paths.add(recoveryPath(index));
        }
        paths.add(directory.resolve(LEGACY_PREVIOUS));

        List<SnapshotCandidate> candidates = new ArrayList<>();
        for (Path path : paths)
        {
            if (!Files.isRegularFile(path))
            {
                continue;
            }
            byte[] bytes = Files.readAllBytes(path);
            try
            {
                CollectionState state = codec.decode(bytes);
                candidates.add(new SnapshotCandidate(
                    path,
                    state,
                    bytes,
                    journalCodec.sha256Hex(bytes)));
            }
            catch (CorruptSnapshotException exception)
            {
                quarantine(path, "corrupt-snapshot");
            }
        }
        return candidates;
    }

    private SnapshotCandidate selectConsistentSnapshot(
        List<SnapshotCandidate> candidates,
        List<StateJournalEvent> journal)
    {
        if (journal.isEmpty())
        {
            return candidates.stream()
                .max(snapshotComparator())
                .orElse(null);
        }

        Map<Long, StateJournalEvent> eventsByRevision = new HashMap<>();
        for (StateJournalEvent event : journal)
        {
            eventsByRevision.put(event.getRevision(), event);
        }
        return candidates.stream()
            .filter(candidate -> {
                StateJournalEvent event = eventsByRevision.get(
                    candidate.state.getRevision());
                return event != null
                    && event.getCollectionId().equals(
                        candidate.state.getCollectionId())
                    && event.getCharacterKey().equals(
                        candidate.state.getCharacterKey())
                    && event.getStateHash().equals(candidate.stateHash);
            })
            .max(snapshotComparator())
            .orElse(null);
    }

    private Comparator<SnapshotCandidate> snapshotComparator()
    {
        return Comparator
            .comparingLong((SnapshotCandidate candidate) ->
                candidate.state.getRevision())
            .thenComparingInt(candidate ->
                candidate.path.getFileName().toString().equals(CURRENT)
                    ? 1
                    : 0);
    }

    private void quarantineInconsistentNewerSnapshots(
        List<SnapshotCandidate> candidates,
        SnapshotCandidate selected)
        throws IOException
    {
        for (SnapshotCandidate candidate : candidates)
        {
            if (candidate.path.equals(selected.path))
            {
                continue;
            }
            boolean newer = candidate.state.getRevision()
                > selected.state.getRevision();
            boolean conflictingSameRevision =
                candidate.state.getRevision() == selected.state.getRevision()
                    && (!candidate.state.getCollectionId().equals(
                        selected.state.getCollectionId())
                        || !candidate.stateHash.equals(selected.stateHash));
            if (newer || conflictingSameRevision)
            {
                quarantine(candidate.path, "uncommitted-snapshot");
            }
        }
    }

    private void restoreCurrentSnapshot(SnapshotCandidate selected)
        throws IOException
    {
        Path current = directory.resolve(CURRENT);
        if (selected.path.equals(current))
        {
            return;
        }
        if (Files.isRegularFile(current))
        {
            quarantine(current, "replaced-current");
        }
        Path restore = directory.resolve(RESTORE_SNAPSHOT);
        writeAndFlush(restore, selected.bytes);
        CollectionState verified = codec.decode(Files.readAllBytes(restore));
        if (verified.getRevision() != selected.state.getRevision()
            || !verified.getCollectionId().equals(
                selected.state.getCollectionId()))
        {
            throw new CorruptSnapshotException(
                "The recovery snapshot failed restoration verification.");
        }
        moveReplacing(restore, current);
        restoredFromBackup = true;
    }

    private JournalLoad truncateJournalAfter(
        JournalLoad journal,
        long revision)
        throws IOException
    {
        List<StateJournalEvent> retainedEvents = new ArrayList<>();
        List<Path> retainedPaths = new ArrayList<>();
        List<Path> discardedPaths = new ArrayList<>();
        for (int index = 0; index < journal.events.size(); index++)
        {
            StateJournalEvent event = journal.events.get(index);
            Path path = journal.paths.get(index);
            if (event.getRevision() <= revision)
            {
                retainedEvents.add(event);
                retainedPaths.add(path);
            }
            else
            {
                discardedPaths.add(path);
            }
        }
        quarantinePaths(discardedPaths, "rolled-back-event");
        return new JournalLoad(retainedEvents, retainedPaths);
    }

    private void repairJournalWithoutSnapshot() throws IOException
    {
        JournalLoad journal = readJournalPrefix();
        if (!journal.events.isEmpty())
        {
            quarantinePaths(journal.paths, "orphaned-journal");
        }
        journalCache = new ArrayList<>();
    }

    private JournalLoad readJournalPrefix() throws IOException
    {
        Path journalDirectory = directory.resolve(JOURNAL_DIRECTORY);
        if (!Files.isDirectory(journalDirectory))
        {
            return JournalLoad.empty();
        }

        List<Path> allEventFiles = new ArrayList<>();
        try (java.util.stream.Stream<Path> paths =
                 Files.list(journalDirectory))
        {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString()
                    .endsWith(".event"))
                .sorted()
                .forEach(allEventFiles::add);
        }

        List<StateJournalEvent> events = new ArrayList<>();
        List<Path> validPaths = new ArrayList<>();
        List<Path> tail = new ArrayList<>();
        boolean chainBroken = false;
        for (Path path : allEventFiles)
        {
            if (chainBroken)
            {
                tail.add(path);
                continue;
            }

            Matcher matcher = JOURNAL_FILE.matcher(
                path.getFileName().toString());
            if (!matcher.matches())
            {
                quarantine(path, "invalid-event-name");
                continue;
            }

            try
            {
                StateJournalEvent event = journalCodec.decode(
                    Files.readAllBytes(path));
                long fileRevision = Long.parseLong(matcher.group(1));
                if (event.getRevision() != fileRevision
                    || !continuesJournal(events, event))
                {
                    chainBroken = true;
                    tail.add(path);
                    continue;
                }
                events.add(event);
                validPaths.add(path);
            }
            catch (IOException | IllegalArgumentException exception)
            {
                chainBroken = true;
                tail.add(path);
            }
        }
        quarantinePaths(tail, "invalid-journal-tail");
        return new JournalLoad(events, validPaths);
    }

    private boolean continuesJournal(
        List<StateJournalEvent> events,
        StateJournalEvent current)
    {
        if (events.isEmpty())
        {
            return true;
        }
        StateJournalEvent previous = events.get(events.size() - 1);
        return current.getRevision() == previous.getRevision() + 1
            && current.getPreviousRevision() == previous.getRevision()
            && current.getPreviousEventHash().equals(
                previous.getEventHash())
            && current.getCollectionId().equals(
                previous.getCollectionId())
            && current.getCharacterKey().equals(
                previous.getCharacterKey());
    }

    private void recoverCommittedPendingSnapshot() throws IOException
    {
        Path pending = directory.resolve(PENDING_SNAPSHOT);
        Path pendingEvent = directory.resolve(PENDING_EVENT);
        if (!Files.isRegularFile(pending))
        {
            if (Files.isRegularFile(pendingEvent))
            {
                quarantine(pendingEvent, "orphaned-pending-event");
            }
            return;
        }

        byte[] pendingBytes = Files.readAllBytes(pending);
        CollectionState pendingState;
        try
        {
            pendingState = codec.decode(pendingBytes);
        }
        catch (CorruptSnapshotException exception)
        {
            quarantine(pending, "corrupt-pending-snapshot");
            if (Files.isRegularFile(pendingEvent))
            {
                quarantine(pendingEvent, "abandoned-pending-event");
            }
            return;
        }

        Path eventPath = directory.resolve(JOURNAL_DIRECTORY).resolve(
            String.format("%020d.event", pendingState.getRevision()));
        if (!Files.isRegularFile(eventPath))
        {
            quarantine(pending, "uncommitted-pending-snapshot");
            if (Files.isRegularFile(pendingEvent))
            {
                quarantine(pendingEvent, "uncommitted-pending-event");
            }
            return;
        }

        StateJournalEvent event;
        try
        {
            event = journalCodec.decode(Files.readAllBytes(eventPath));
        }
        catch (CorruptSnapshotException exception)
        {
            quarantine(pending, "event-missing-for-pending");
            return;
        }
        String pendingHash = journalCodec.sha256Hex(pendingBytes);
        if (!event.getCollectionId().equals(
            pendingState.getCollectionId())
            || !event.getCharacterKey().equals(
                pendingState.getCharacterKey())
            || !event.getStateHash().equals(pendingHash))
        {
            quarantine(pending, "mismatched-pending-snapshot");
            return;
        }

        if (currentAlreadyContains(pendingState, pendingHash))
        {
            Files.deleteIfExists(pending);
            Files.deleteIfExists(pendingEvent);
            return;
        }
        try
        {
            if (requiresRecoveryRotation(event, pendingState))
            {
                rotateRecoverySnapshots();
            }
        }
        catch (CorruptSnapshotException exception)
        {
            quarantine(pending, "unsafe-pending-promotion");
            return;
        }
        faultInjector.checkpoint(
            PersistenceCommitStage.AFTER_RECOVERY_ROTATION);
        moveReplacing(pending, directory.resolve(CURRENT));
        Files.deleteIfExists(pendingEvent);
        faultInjector.checkpoint(
            PersistenceCommitStage.AFTER_SNAPSHOT_PROMOTION);
    }

    private boolean currentAlreadyContains(
        CollectionState pendingState,
        String pendingHash)
        throws IOException
    {
        Path currentPath = directory.resolve(CURRENT);
        if (!Files.isRegularFile(currentPath))
        {
            return false;
        }
        try
        {
            byte[] currentBytes = Files.readAllBytes(currentPath);
            CollectionState current = codec.decode(currentBytes);
            return current.getRevision() == pendingState.getRevision()
                && current.getCollectionId().equals(
                    pendingState.getCollectionId())
                && current.getCharacterKey().equals(
                    pendingState.getCharacterKey())
                && journalCodec.sha256Hex(currentBytes).equals(pendingHash);
        }
        catch (CorruptSnapshotException ignored)
        {
            return false;
        }
    }

    private boolean requiresRecoveryRotation(
        StateJournalEvent event,
        CollectionState pendingState)
        throws IOException
    {
        Path currentPath = directory.resolve(CURRENT);
        if (Files.isRegularFile(currentPath))
        {
            return true;
        }

        if (event.getPreviousRevision() == -1)
        {
            return false;
        }

        Path newestRecovery = recoveryPath(1);
        if (Files.isRegularFile(newestRecovery))
        {
            try
            {
                CollectionState recovery =
                    codec.decode(Files.readAllBytes(newestRecovery));
                if (recovery.getRevision() == event.getPreviousRevision()
                    && recovery.getCollectionId().equals(
                        pendingState.getCollectionId())
                    && recovery.getCharacterKey().equals(
                        pendingState.getCharacterKey()))
                {
                    return false;
                }
            }
            catch (CorruptSnapshotException ignored)
            {
                // The expected prior generation is not recoverable.
            }
        }

        throw new CorruptSnapshotException(
            "Snapshot rotation state does not match the committed event.");
    }

    private void rotateRecoverySnapshots() throws IOException
    {
        Files.deleteIfExists(recoveryPath(RECOVERY_SNAPSHOT_COUNT));
        for (int index = RECOVERY_SNAPSHOT_COUNT - 1;
             index >= 1;
             index--)
        {
            Path source = recoveryPath(index);
            if (Files.isRegularFile(source))
            {
                moveReplacing(source, recoveryPath(index + 1));
            }
        }
        Path current = directory.resolve(CURRENT);
        if (Files.isRegularFile(current))
        {
            moveReplacing(current, recoveryPath(1));
        }
    }

    private Path recoveryPath(int generation)
    {
        return directory.resolve(
            "recovery-" + generation + ".snapshot");
    }

    private void verifyPending(
        CollectionState state,
        StateJournalEvent event,
        Path pendingSnapshot,
        Path pendingEvent)
        throws IOException
    {
        byte[] pendingSnapshotBytes = Files.readAllBytes(pendingSnapshot);
        byte[] pendingEventBytes = Files.readAllBytes(pendingEvent);
        CollectionState reloaded = codec.decode(pendingSnapshotBytes);
        StateJournalEvent reloadedEvent =
            journalCodec.decode(pendingEventBytes);
        String stateHash =
            journalCodec.sha256Hex(pendingSnapshotBytes);
        if (reloaded.getRevision() != state.getRevision()
            || !reloaded.getCollectionId().equals(state.getCollectionId())
            || !reloaded.getCharacterKey().equals(state.getCharacterKey())
            || !reloadedEvent.getEventHash().equals(event.getEventHash())
            || !reloadedEvent.getStateHash().equals(stateHash))
        {
            throw new CorruptSnapshotException(
                "Pending transaction failed verification.");
        }
    }

    private void cleanupAbandonedPendingFiles() throws IOException
    {
        Path pending = directory.resolve(PENDING_SNAPSHOT);
        Path pendingEvent = directory.resolve(PENDING_EVENT);
        if (Files.isRegularFile(pending))
        {
            quarantine(pending, "abandoned-pending-snapshot");
        }
        if (Files.isRegularFile(pendingEvent))
        {
            quarantine(pendingEvent, "abandoned-pending-event");
        }
        Files.deleteIfExists(directory.resolve(RESTORE_SNAPSHOT));
    }

    private void quarantinePaths(List<Path> paths, String reason)
        throws IOException
    {
        for (Path path : paths)
        {
            if (Files.exists(path))
            {
                quarantine(path, reason);
            }
        }
    }

    private void quarantine(Path path, String reason) throws IOException
    {
        if (!Files.exists(path))
        {
            return;
        }
        Path quarantine = directory.resolve(QUARANTINE_DIRECTORY);
        Files.createDirectories(quarantine);
        String safeReason = reason.replaceAll("[^a-zA-Z0-9._-]", "-");
        Path target = quarantine.resolve(
            UUID.randomUUID() + "-" + safeReason + "-"
                + path.getFileName());
        moveWithoutReplacing(path, target);
        quarantinedArtifactCount++;
    }

    private boolean hasCommittedPersistenceArtifacts()
        throws IOException
    {
        if (Files.isRegularFile(directory.resolve(CURRENT))
            || Files.isRegularFile(directory.resolve(LEGACY_PREVIOUS)))
        {
            return true;
        }
        for (int index = 1; index <= RECOVERY_SNAPSHOT_COUNT; index++)
        {
            if (Files.isRegularFile(recoveryPath(index)))
            {
                return true;
            }
        }
        Path journalDirectory = directory.resolve(JOURNAL_DIRECTORY);
        if (!Files.isDirectory(journalDirectory))
        {
            return false;
        }
        try (java.util.stream.Stream<Path> paths =
                 Files.list(journalDirectory))
        {
            return paths.anyMatch(path -> Files.isRegularFile(path)
                && path.getFileName().toString().endsWith(".event"));
        }
    }

    private void writeAndFlush(Path path, byte[] bytes) throws IOException
    {
        try (FileChannel channel = FileChannel.open(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE))
        {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining())
            {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private void moveWithoutReplacing(Path source, Path target)
        throws IOException
    {
        try
        {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException ignored)
        {
            Files.move(source, target);
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException
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
            Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    private interface IoOperation<T>
    {
        T run() throws IOException;
    }

    private static final class SnapshotCandidate
    {
        private final Path path;
        private final CollectionState state;
        private final byte[] bytes;
        private final String stateHash;

        private SnapshotCandidate(
            Path path,
            CollectionState state,
            byte[] bytes,
            String stateHash)
        {
            this.path = path;
            this.state = state;
            this.bytes = bytes;
            this.stateHash = stateHash;
        }
    }

    private static final class JournalLoad
    {
        private final List<StateJournalEvent> events;
        private final List<Path> paths;

        private JournalLoad(
            List<StateJournalEvent> events,
            List<Path> paths)
        {
            this.events = List.copyOf(events);
            this.paths = List.copyOf(paths);
        }

        private static JournalLoad empty()
        {
            return new JournalLoad(List.of(), List.of());
        }
    }
}
