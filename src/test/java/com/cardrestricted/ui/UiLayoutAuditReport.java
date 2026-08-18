package com.cardrestricted.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Emits the deterministic CL897 sidebar geometry/paint fingerprint. */
public final class UiLayoutAuditReport
{
    private UiLayoutAuditReport()
    {
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length != 1)
        {
            throw new IllegalArgumentException("Pass one JSON output path.");
        }
        UiLayoutAuditHarness.Result result = UiLayoutAuditHarness.run();
        StringBuilder json = new StringBuilder(4_096);
        json.append("{\n")
            .append("  \"schema_version\": 1,\n")
            .append("  \"project\": \"Card Locked\",\n")
            .append("  \"build\": \"CL897\",\n")
            .append("  \"status\": \"")
            .append(result.violations.isEmpty() ? "passed" : "failed")
            .append("\",\n")
            .append("  \"captures\": ").append(result.captures)
            .append(",\n")
            .append("  \"paint_cycles\": ").append(result.paintCycles)
            .append(",\n")
            .append("  \"tab_states\": ").append(result.tabStates)
            .append(",\n")
            .append("  \"components_inspected\": ")
            .append(result.componentsInspected).append(",\n")
            .append("  \"structural_sha256\": \"")
            .append(result.structuralSha256).append("\",\n")
            .append("  \"pixel_sha256\": \"")
            .append(result.pixelSha256).append("\",\n")
            .append("  \"violations\": [");
        for (int index = 0; index < result.violations.size(); index++)
        {
            if (index > 0)
            {
                json.append(',');
            }
            json.append("\n    \"")
                .append(escape(result.violations.get(index))).append('"');
        }
        if (!result.violations.isEmpty())
        {
            json.append('\n');
        }
        json.append("  ]\n}\n");
        Path output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        Files.writeString(output, json, StandardCharsets.UTF_8);
        if (!result.violations.isEmpty())
        {
            throw new IllegalStateException(
                "UI audit found " + result.violations.size()
                    + " violations.");
        }
        System.out.println("UI layout audit passed: " + result.captures
            + " captures, " + result.componentsInspected
            + " components inspected.");
    }

    private static String escape(String value)
    {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
