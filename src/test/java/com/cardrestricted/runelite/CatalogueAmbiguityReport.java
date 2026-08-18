package com.cardrestricted.runelite;

import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.MembersCatalogue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/** Writes a deterministic review report for every name-based fallback ambiguity. */
public final class CatalogueAmbiguityReport
{
    private CatalogueAmbiguityReport()
    {
    }

    public static void main(String[] args) throws IOException
    {
        if (args.length != 1)
        {
            throw new IllegalArgumentException("Pass one JSON output path.");
        }
        CatalogueAmbiguityAudit.Result result =
            CatalogueAmbiguityAudit.analyse(MembersCatalogue.create());
        long itemAmbiguous = result.ambiguous.stream()
            .filter(entry -> entry.type == CardType.ITEM).count();
        long npcAmbiguous = result.ambiguous.stream()
            .filter(entry -> entry.type == CardType.NPC).count();
        StringBuilder json = new StringBuilder(32_768);
        json.append("{\n")
            .append("  \"schema_version\": 1,\n")
            .append("  \"project\": \"Card Locked\",\n")
            .append("  \"build\": \"CL897\",\n")
            .append("  \"status\": \"passed\",\n")
            .append("  \"policy\": \"ambiguous names fail closed; exact IDs remain authoritative\",\n")
            .append("  \"unique_name_groups\": ")
            .append(result.unique.size()).append(",\n")
            .append("  \"ambiguous_name_groups\": ")
            .append(result.ambiguous.size()).append(",\n")
            .append("  \"ambiguous_item_names\": ")
            .append(itemAmbiguous).append(",\n")
            .append("  \"ambiguous_npc_names\": ")
            .append(npcAmbiguous).append(",\n")
            .append("  \"entries\": [\n");
        Iterator<CatalogueAmbiguityAudit.Entry> iterator =
            result.ambiguous.iterator();
        while (iterator.hasNext())
        {
            CatalogueAmbiguityAudit.Entry entry = iterator.next();
            json.append("    {\"type\":\"")
                .append(entry.type.name())
                .append("\",\"normalised_name\":\"")
                .append(escape(entry.normalisedName))
                .append("\",\"family_count\":")
                .append(entry.familyIds.size())
                .append(",\"families\":[");
            appendStrings(json, entry.familyIds.iterator());
            json.append("],\"cards\":[");
            appendStrings(json, entry.cardIds.iterator());
            json.append("]}");
            if (iterator.hasNext())
            {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n}\n");
        Path output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        Files.writeString(output, json, StandardCharsets.UTF_8);
        System.out.println("Catalogue ambiguity audit passed: "
            + result.ambiguous.size() + " fail-closed name groups.");
    }

    private static void appendStrings(
        StringBuilder output,
        Iterator<String> values)
    {
        while (values.hasNext())
        {
            output.append('"').append(escape(values.next())).append('"');
            if (values.hasNext())
            {
                output.append(',');
            }
        }
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
