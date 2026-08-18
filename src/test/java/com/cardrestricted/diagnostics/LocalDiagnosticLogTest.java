package com.cardrestricted.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LocalDiagnosticLogTest
{
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-03T19:30:00Z"),
        ZoneOffset.UTC);

    @Test
    public void ringBufferRetainsOnlyTheNewestEvents()
    {
        LocalDiagnosticLog log = new LocalDiagnosticLog(3, CLOCK);
        log.record(DiagnosticEventCode.STARTUP_BEGIN, DiagnosticOperation.STARTUP);
        log.record(DiagnosticEventCode.STARTUP_COMPLETE, DiagnosticOperation.STARTUP);
        log.record(DiagnosticEventCode.SHUTDOWN_BEGIN, DiagnosticOperation.SHUTDOWN);
        log.record(DiagnosticEventCode.SHUTDOWN_COMPLETE, DiagnosticOperation.SHUTDOWN);

        List<DiagnosticEvent> events = log.snapshot();
        assertEquals(3, events.size());
        assertEquals(2L, events.get(0).getSequence());
        assertEquals(
            DiagnosticEventCode.SHUTDOWN_COMPLETE,
            events.get(2).getEventCode());
    }

    @Test
    public void exceptionMessagesAreNeverRetained()
    {
        LocalDiagnosticLog log = new LocalDiagnosticLog(4, CLOCK);
        log.recordFailure(
            DiagnosticEventCode.TASK_FAILED,
            DiagnosticOperation.SESSION_OPEN,
            new IOException(
                "account=123456 path=C:\\Users\\Private\\collection.snapshot"));

        DiagnosticEvent event = log.snapshot().get(0);
        assertEquals("IOException", event.getFailureType());
        assertFalse(event.getFailureType().contains("123456"));
        assertFalse(event.getFailureType().contains("Private"));
    }

    @Test
    public void capacityIsStrictlyBounded()
    {
        LocalDiagnosticLog log = new LocalDiagnosticLog(8, CLOCK);
        for (int index = 0; index < 1000; index++)
        {
            log.record(
                DiagnosticEventCode.TASK_REJECTED,
                DiagnosticOperation.PACK_PURCHASE);
        }
        assertEquals(8, log.size());
        assertEquals(8, log.capacity());
    }

    @Test(expected = IllegalArgumentException.class)
    public void excessiveCapacityIsRejected()
    {
        new LocalDiagnosticLog(513, CLOCK);
    }

    @Test
    public void reportContainsOnlyTheWhitelistedRuntimeShape()
        throws Exception
    {
        Path directory = Files.createTempDirectory("cl-diagnostic-report-");
        try
        {
            LocalDiagnosticLog log = new LocalDiagnosticLog(4, CLOCK);
            log.recordFailure(
                DiagnosticEventCode.SESSION_FAILURE,
                DiagnosticOperation.SESSION_OPEN,
                new IOException("SECRET-ACCOUNT SECRET-CARD SECRET-PATH"));
            DiagnosticRuntimeSnapshot runtime = new DiagnosticRuntimeSnapshot(
                "LOGGED_IN",
                "ERROR",
                true,
                false,
                false,
                false,
                true,
                true,
                true,
                true,
                2);
            Path report = new DiagnosticReportExporter(directory, CLOCK)
                .export(log, runtime);
            String text = Files.readString(report, StandardCharsets.UTF_8);

            assertTrue(text.contains("network_transmission=none"));
            assertTrue(text.contains("reference\tfailure_type"));
            assertTrue(text.contains("CL-SESSION-001"));
            assertTrue(text.contains("IOException"));
            assertFalse(text.contains("SECRET-ACCOUNT"));
            assertFalse(text.contains("SECRET-CARD"));
            assertFalse(text.contains("SECRET-PATH"));
            assertFalse(text.contains(System.getProperty("user.home", "never")));
            assertFalse(text.contains(System.getProperty("user.name", "never")));
            assertTrue(Files.size(report) < 64 * 1024);
        }
        finally
        {
            deleteTree(directory);
        }
    }

    @Test
    public void reportRetentionIsBoundedAndLeavesNoTemporaryFiles()
        throws Exception
    {
        Path directory = Files.createTempDirectory("cl-diagnostic-retention-");
        try
        {
            LocalDiagnosticLog log = new LocalDiagnosticLog(2, CLOCK);
            DiagnosticRuntimeSnapshot runtime = new DiagnosticRuntimeSnapshot(
                "LOGIN_SCREEN",
                "LOGGED_OUT",
                true,
                false,
                false,
                false,
                true,
                true,
                false,
                true,
                0);
            DiagnosticReportExporter exporter =
                new DiagnosticReportExporter(directory, CLOCK);
            for (int index = 0; index < 12; index++)
            {
                exporter.export(log, runtime);
            }
            try (java.util.stream.Stream<Path> files = Files.list(directory))
            {
                List<Path> all = files.collect(java.util.stream.Collectors.toList());
                assertEquals(DiagnosticReportExporter.MAX_REPORTS, all.size());
                assertTrue(all.stream().allMatch(path ->
                    path.getFileName().toString().endsWith(".txt")));
            }
        }
        finally
        {
            deleteTree(directory);
        }
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
}
