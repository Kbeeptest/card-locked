package com.cardrestricted.diagnostics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.MenuAction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class IntegrityTraceLogTest
{
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-05T02:20:00Z"),
        ZoneOffset.UTC);

    @Test
    public void traceIsBoundedAndRetainsOnlyFixedTechnicalFields()
    {
        IntegrityTraceLog trace = new IntegrityTraceLog(3, CLOCK);
        trace.record(1, MenuAction.NPC_FIRST_OPTION, "Talk-to",
            IntegrityTraceDecision.ALLOWED,
            IntegrityTraceReason.POLICY_ALLOWED,
            219 << 16,
            123);
        trace.record(2, MenuAction.NPC_SECOND_OPTION, "Pickpocket",
            IntegrityTraceDecision.BLOCKED,
            IntegrityTraceReason.POLICY_BLOCKED,
            -1,
            456);
        trace.record(3, MenuAction.CC_OP, "Buy-10",
            IntegrityTraceDecision.BLOCKED,
            IntegrityTraceReason.POLICY_BLOCKED,
            300 << 16,
            789);
        trace.record(4, MenuAction.WALK, "Walk here",
            IntegrityTraceDecision.ALLOWED,
            IntegrityTraceReason.POLICY_ALLOWED,
            -1,
            -1);

        List<IntegrityTraceEvent> events = trace.snapshot();
        assertEquals(3, events.size());
        assertEquals(2L, events.get(0).getSequence());
        assertEquals(IntegrityOptionClass.PICKPOCKET,
            events.get(0).getOptionClass());
        assertEquals(IntegrityOptionClass.MOVEMENT,
            events.get(2).getOptionClass());
        assertEquals(300, events.get(1).getWidgetGroup());
        assertEquals(789, events.get(1).getEntityId());
    }

    @Test
    public void optionClassificationHandlesFormattingAndDoesNotRetainText()
    {
        assertEquals(IntegrityOptionClass.ATTACK,
            IntegrityOptionClass.classify("<col=ff0000>Attack</col>"));
        assertEquals(IntegrityOptionClass.TRADE,
            IntegrityOptionClass.classify("Trade-with"));
        assertEquals(IntegrityOptionClass.CAST,
            IntegrityOptionClass.classify("Defensive Autocast"));
        assertEquals(IntegrityOptionClass.UNEQUIP,
            IntegrityOptionClass.classify("Remove"));
        assertEquals(IntegrityOptionClass.EMPTY,
            IntegrityOptionClass.classify(null));
    }

    @Test
    public void exportedTraceIsLocalBoundedAndContainsNoFreeFormTarget()
        throws Exception
    {
        Path directory = Files.createTempDirectory("cl-integrity-trace-");
        try
        {
            LocalDiagnosticLog log = new LocalDiagnosticLog(4, CLOCK);
            IntegrityTraceLog trace = new IntegrityTraceLog(8, CLOCK);
            trace.record(99, MenuAction.NPC_THIRD_OPTION,
                "<col=ff0000>Trade Secret Merchant Name</col>",
                IntegrityTraceDecision.BLOCKED,
                IntegrityTraceReason.POLICY_BLOCKED,
                300 << 16,
                12345);
            DiagnosticRuntimeSnapshot runtime = new DiagnosticRuntimeSnapshot(
                "LOGGED_IN", "READY", true, false, true, true,
                true, true, true, true, 1,
                false, true,
                true, false,
                false, false,
                false, false,
                false, false,
                trace.size());
            Path report = new DiagnosticReportExporter(directory, CLOCK)
                .export(log, trace, runtime);
            String text = Files.readString(report, StandardCharsets.UTF_8);

            assertTrue(text.contains("schema=2"));
            assertTrue(text.contains("[interaction_trace]"));
            assertTrue(text.contains("POLICY_BLOCKED"));
            assertTrue(text.contains("widget_group"));
            assertTrue(text.contains("shop_open=true"));
            assertFalse(text.contains("Secret Merchant Name"));
            assertTrue(Files.size(report) < 64 * 1024);
        }
        finally
        {
            deleteTree(directory);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void excessiveTraceCapacityIsRejected()
    {
        new IntegrityTraceLog(IntegrityTraceLog.MAX_CAPACITY + 1, CLOCK);
    }

    private static void deleteTree(Path root) throws Exception
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
