package com.cardrestricted.selftest;

import com.cardrestricted.CardRestrictedAccountPlugin;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.eventbus.Subscribe;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Ensures every RuneLite subscriber is explicitly inventoried and risk-owned. */
public final class SubscriberBoundaryPolicyTest
{
    @Test
    public void everySubscriberHasACompleteCoverageDeclaration()
        throws Exception
    {
        Map<String, String> actual = new LinkedHashMap<>();
        for (Method method : CardRestrictedAccountPlugin.class
            .getDeclaredMethods())
        {
            if (!method.isAnnotationPresent(Subscribe.class))
            {
                continue;
            }
            assertEquals(method.getName(), 1, method.getParameterCount());
            actual.put(method.getName(),
                method.getParameterTypes()[0].getSimpleName());
        }
        assertFalse(actual.isEmpty());

        Map<String, Declaration> declared = loadPolicy();
        assertEquals(actual.keySet(), declared.keySet());
        for (Map.Entry<String, String> entry : actual.entrySet())
        {
            Declaration declaration = declared.get(entry.getKey());
            assertEquals(entry.getKey(), entry.getValue(),
                declaration.eventType);
            assertTrue(entry.getKey(),
                declaration.risk.startsWith("critical_")
                    || declaration.risk.startsWith("high_")
                    || declaration.risk.startsWith("medium_"));
            assertEquals(entry.getKey(), "delegated",
                declaration.coverageMode);
            assertTrue(entry.getKey(), declaration.testFiles.contains("Test.java"));
            assertTrue(entry.getKey(), declaration.notes.length() >= 40);
        }
        assertEquals(13, actual.size());
    }

    private static Map<String, Declaration> loadPolicy() throws Exception
    {
        String resource = "/com/cardrestricted/selftest/subscriber-boundaries.tsv";
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                SubscriberBoundaryPolicyTest.class.getResourceAsStream(resource),
                StandardCharsets.UTF_8)))
        {
            Map<String, Declaration> result = new LinkedHashMap<>();
            String header = reader.readLine();
            assertEquals("handler\tevent_type\trisk\tcoverage_mode\ttest_files\tnotes",
                header);
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.isBlank())
                {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                assertEquals(line, 6, columns.length);
                Declaration previous = result.put(columns[0],
                    new Declaration(columns[1], columns[2], columns[3],
                        columns[4], columns[5]));
                assertEquals("Duplicate handler " + columns[0], null, previous);
            }
            return result;
        }
    }

    private static final class Declaration
    {
        private final String eventType;
        private final String risk;
        private final String coverageMode;
        private final String testFiles;
        private final String notes;

        private Declaration(
            String eventType,
            String risk,
            String coverageMode,
            String testFiles,
            String notes)
        {
            this.eventType = eventType;
            this.risk = risk;
            this.coverageMode = coverageMode;
            this.testFiles = testFiles;
            this.notes = notes;
        }
    }
}
