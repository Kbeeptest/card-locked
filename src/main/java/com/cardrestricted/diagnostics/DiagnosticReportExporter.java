package com.cardrestricted.diagnostics;

import com.cardrestricted.PluginBuildInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Writes a bounded local report without collection data or telemetry. */
public final class DiagnosticReportExporter
{
    public static final int MAX_REPORTS = 5;
    private static final int MAX_REPORT_BYTES = 64 * 1024;
    private static final DateTimeFormatter FILE_TIME =
        DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);

    private final Path directory;
    private final Clock clock;

    public DiagnosticReportExporter(Path directory)
    {
        this(directory, Clock.systemUTC());
    }

    DiagnosticReportExporter(Path directory, Clock clock)
    {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Path export(
        LocalDiagnosticLog log,
        DiagnosticRuntimeSnapshot runtime)
        throws IOException
    {
        return export(log, new IntegrityTraceLog(1, clock), runtime);
    }

    public Path export(
        LocalDiagnosticLog log,
        IntegrityTraceLog integrityTrace,
        DiagnosticRuntimeSnapshot runtime)
        throws IOException
    {
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(integrityTrace, "integrityTrace");
        Objects.requireNonNull(runtime, "runtime");
        Files.createDirectories(directory);
        Instant generatedAt = Instant.now(clock);
        byte[] bytes = render(log, integrityTrace, runtime, generatedAt)
            .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_REPORT_BYTES)
        {
            throw new IOException("Diagnostic report exceeded its size limit.");
        }

        Path target = uniqueTarget(generatedAt);
        Path temporary = Files.createTempFile(
            directory,
            ".card-locked-diagnostics-",
            ".tmp");
        boolean moved = false;
        try
        {
            Files.write(
                temporary,
                bytes,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            try
            {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException exception)
            {
                Files.move(temporary, target);
            }
            moved = true;
        }
        finally
        {
            if (!moved)
            {
                Files.deleteIfExists(temporary);
            }
        }
        pruneOldReports();
        return target;
    }

    private String render(
        LocalDiagnosticLog log,
        IntegrityTraceLog integrityTrace,
        DiagnosticRuntimeSnapshot runtime,
        Instant generatedAt)
    {
        StringBuilder output = new StringBuilder(8192);
        output.append("Card Locked local diagnostic report\n");
        output.append("schema=2\n");
        output.append("generated_utc=").append(generatedAt).append('\n');
        output.append("build_version=").append(PluginBuildInfo.VERSION).append('\n');
        output.append("build_channel=").append(safe(PluginBuildInfo.CHANNEL)).append('\n');
        output.append("privacy=No account name or hash, collection identifier, owned-card list, balances, target names, free-form chat, file paths, exception messages or telemetry are included. The bounded interaction trace contains only technical action classes, numeric client references and allow/block reason codes.\n");
        output.append("network_transmission=none\n\n");

        output.append("[environment]\n");
        property(output, "java_version", "java.version");
        property(output, "java_vendor", "java.vendor");
        property(output, "os_name", "os.name");
        property(output, "os_version", "os.version");
        property(output, "os_arch", "os.arch");
        output.append('\n');

        output.append("[runtime]\n");
        output.append("game_state=").append(runtime.getGameState()).append('\n');
        output.append("session_status=").append(runtime.getSessionStatus()).append('\n');
        flag(output, "startup_complete", runtime.isStartupComplete());
        flag(output, "session_suspended", runtime.isSessionSuspended());
        flag(output, "collection_runtime_active", runtime.isCollectionRuntimeActive());
        flag(output, "restriction_runtime_active", runtime.isRestrictionRuntimeActive());
        flag(output, "panel_present", runtime.isPanelPresent());
        flag(output, "overlays_present", runtime.isOverlaysPresent());
        flag(output, "artwork_executor_active", runtime.isArtworkExecutorActive());
        flag(output, "task_scope_accepting", runtime.isTaskScopeAccepting());
        output.append("tracked_tasks=").append(runtime.getTrackedTasks()).append('\n');
        flag(output, "restriction_state_pending", runtime.isRestrictionStatePending());
        flag(output, "autocast_verified", runtime.isAutocastVerified());
        flag(output, "shop_open", runtime.isShopOpen());
        flag(output, "shop_authorized", runtime.isShopAuthorized());
        flag(output, "storage_open", runtime.isStorageOpen());
        flag(output, "storage_authorized", runtime.isStorageAuthorized());
        flag(output, "exchange_open", runtime.isExchangeOpen());
        flag(output, "exchange_authorized", runtime.isExchangeAuthorized());
        flag(output, "service_open", runtime.isServiceOpen());
        flag(output, "service_authorized", runtime.isServiceAuthorized());
        output.append("integrity_trace_events=")
            .append(runtime.getIntegrityTraceEvents()).append('\n');
        output.append('\n');

        output.append("[events]\n");
        output.append("sequence\ttimestamp_utc\tseverity\tevent\toperation\treference\tfailure_type\n");
        for (DiagnosticEvent event : log.snapshot())
        {
            DiagnosticOperation operation = event.getOperation();
            output.append(event.getSequence()).append('\t')
                .append(event.getTimestamp()).append('\t')
                .append(event.getEventCode().getSeverity()).append('\t')
                .append(event.getEventCode()).append('\t')
                .append(operation == null ? "" : operation.name()).append('\t')
                .append(operation == null ? "" : operation.getReferenceCode()).append('\t')
                .append(safe(event.getFailureType())).append('\n');
        }
        output.append('\n');

        output.append("[interaction_trace]\n");
        output.append("sequence\ttimestamp_utc\ttick\taction\tsurface\trisk\toption_class\tdecision\treason\twidget_group\tentity_id\n");
        for (IntegrityTraceEvent event : integrityTrace.snapshot())
        {
            output.append(event.getSequence()).append('\t')
                .append(event.getTimestamp()).append('\t')
                .append(event.getClientTick()).append('\t')
                .append(event.getMenuAction()).append('\t')
                .append(event.getSurface()).append('\t')
                .append(event.getRiskClass()).append('\t')
                .append(event.getOptionClass()).append('\t')
                .append(event.getDecision()).append('\t')
                .append(event.getReason()).append('\t')
                .append(event.getWidgetGroup()).append('\t')
                .append(event.getEntityId()).append('\n');
        }
        return output.toString();
    }

    private Path uniqueTarget(Instant generatedAt)
        throws IOException
    {
        String base = "card-locked-diagnostics-" + FILE_TIME.format(generatedAt);
        for (int attempt = 0; attempt < 100; attempt++)
        {
            String suffix = attempt == 0 ? "" : "-" + attempt;
            Path candidate = directory.resolve(base + suffix + ".txt");
            if (!Files.exists(candidate))
            {
                return candidate;
            }
        }
        throw new IOException("Unable to allocate a diagnostic report filename.");
    }

    private void pruneOldReports()
        throws IOException
    {
        List<Path> reports = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(
            directory,
            "card-locked-diagnostics-*.txt"))
        {
            for (Path path : stream)
            {
                if (Files.isRegularFile(path))
                {
                    reports.add(path);
                }
            }
        }
        reports.sort(Comparator.comparingLong(this::lastModifiedSafe).reversed());
        for (int index = MAX_REPORTS; index < reports.size(); index++)
        {
            Files.deleteIfExists(reports.get(index));
        }
    }

    private long lastModifiedSafe(Path path)
    {
        try
        {
            return Files.getLastModifiedTime(path).toMillis();
        }
        catch (IOException exception)
        {
            return Long.MIN_VALUE;
        }
    }

    private static void property(
        StringBuilder output,
        String key,
        String property)
    {
        output.append(key).append('=')
            .append(safe(System.getProperty(property, "unknown")))
            .append('\n');
    }

    private static void flag(StringBuilder output, String key, boolean value)
    {
        output.append(key).append('=').append(value).append('\n');
    }

    private static String safe(String value)
    {
        if (value == null)
        {
            return "";
        }
        String safe = value.replace('\r', '_').replace('\n', '_').replace('\t', '_');
        return safe.length() <= 160 ? safe : safe.substring(0, 160);
    }
}
