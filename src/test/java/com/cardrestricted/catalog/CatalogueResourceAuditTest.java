package com.cardrestricted.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Whole-catalogue and packaged-resource invariants for the public-beta gate. */
public final class CatalogueResourceAuditTest
{
    private static final String ROOT =
        "com/cardrestricted/catalog/members/";
    private static final String ART =
        "com/cardrestricted/artwork/wiki/";

    @Test
    public void activeCatalogueFamiliesAliasesAndRetirementsAreConsistent()
        throws Exception
    {
        List<Map<String, String>> cards = rows(ROOT + "cards.tsv");
        List<Map<String, String>> families = rows(ROOT + "families.tsv");
        List<Map<String, String>> aliases = rows(ROOT + "aliases.tsv");
        List<Map<String, String>> historical = rows(
            ROOT + "historical-cards.tsv");
        List<Map<String, String>> retired = rows(ROOT + "retired-cards.tsv");

        assertEquals(7588, cards.size());
        Map<String, Map<String, String>> cardById = unique(
            cards,
            "card_id");
        Map<String, Map<String, String>> familyById = unique(
            families,
            "family_id");
        Map<String, Map<String, String>> historicalById = unique(
            historical,
            "card_id");
        unique(retired, "card_id");
        Map<String, Map<String, String>> aliasByLegacy = unique(
            aliases,
            "legacy_card_id");

        Set<String> activeIds = cardById.keySet();
        assertTrue(Collections.disjoint(
            activeIds,
            historicalById.keySet()));
        for (Map<String, String> card : cards)
        {
            assertTrue(familyById.containsKey(card.get("entity_family_id")));
            assertFalse(card.get("display_name").trim().isEmpty());
            assertFalse(card.get("examine_text").trim().isEmpty());
        }

        Map<String, String> assignedEntityIds = new HashMap<>();
        for (Map<String, String> family : families)
        {
            List<String> ids = new ArrayList<>();
            ids.add(family.get("canonical_id"));
            if (!family.get("variant_ids").isEmpty())
            {
                ids.addAll(Arrays.asList(
                    family.get("variant_ids").split(",")));
            }
            for (String id : ids)
            {
                String key = family.get("entity_type") + ":" + id;
                assertFalse(
                    "Entity ID appears in multiple families: " + key,
                    assignedEntityIds.containsKey(key));
                assignedEntityIds.put(key, family.get("family_id"));
            }
        }

        for (Map<String, String> alias : aliases)
        {
            String legacy = alias.get("legacy_card_id");
            String canonical = alias.get("canonical_card_id");
            assertFalse(activeIds.contains(legacy));
            assertTrue(activeIds.contains(canonical)
                || historicalById.containsKey(canonical));
            Set<String> visited = new HashSet<>();
            while (aliasByLegacy.containsKey(canonical))
            {
                assertTrue("Alias cycle at " + canonical,
                    visited.add(canonical));
                canonical = aliasByLegacy.get(canonical)
                    .get("canonical_card_id");
            }
        }

        Set<String> activeEntityIds = assignedEntityIds.keySet();
        for (Map<String, String> row : retired)
        {
            String type = row.get("card_type");
            String canonicalId = row.get("canonical_id");
            if (!canonicalId.isEmpty())
            {
                assertFalse(
                    "Retired identity remains assigned: " + row.get("card_id"),
                    activeEntityIds.contains(type + ":" + canonicalId));
            }
        }

        String banned =
            "deadman|temporary leagues|trailblazer|shattered relics|"
                + "raging echoes|demonic pacts|grid master|speedrun|"
                + "technical test|failed poll beta";
        for (Map<String, String> card : cards)
        {
            String searchable = String.join(" ", card.values())
                .toLowerCase(Locale.ROOT);
            for (String token : banned.split("\\|"))
            {
                assertFalse(
                    "Temporary-mode content remains active: "
                        + card.get("card_id") + " matched " + token,
                    searchable.contains(token));
            }
        }
    }

    @Test
    public void textAndArtworkGapsAreExplicitAndComplete()
        throws Exception
    {
        List<Map<String, String>> cards = rows(ROOT + "cards.tsv");
        List<Map<String, String>> families = rows(ROOT + "families.tsv");
        List<Map<String, String>> examines = rows(
            ROOT + "examine-overrides.tsv");
        List<Map<String, String>> exceptions = rows(
            ROOT + "catalogue-audit-exceptions.tsv");
        List<Map<String, String>> manifest = rows(ART + "manifest.tsv");

        Map<String, Map<String, String>> cardById = unique(cards, "card_id");
        Map<String, Map<String, String>> familyById = unique(
            families,
            "family_id");
        Set<String> official = unique(examines, "card_id").keySet();
        Map<String, Map<String, String>> exceptionById = unique(
            exceptions,
            "card_id");
        Set<String> mapped = unique(manifest, "card_id").keySet();

        assertEquals(9, exceptions.size());
        Set<String> pendingText = new TreeSet<>();
        Set<String> builtInNpcFallback = new TreeSet<>();
        for (Map<String, String> exception : exceptions)
        {
            assertTrue(cardById.containsKey(exception.get("card_id")));
            if ("PENDING_OFFICIAL_EXAMINE".equals(
                exception.get("text_status")))
            {
                pendingText.add(exception.get("card_id"));
            }
            if ("BUILT_IN_FALLBACK".equals(
                exception.get("artwork_status")))
            {
                builtInNpcFallback.add(exception.get("card_id"));
            }
        }

        Set<String> missingOfficial = new TreeSet<>(cardById.keySet());
        missingOfficial.removeAll(official);
        assertEquals(pendingText, missingOfficial);
        assertEquals(9, missingOfficial.size());

        Set<String> missingNpcMappings = new TreeSet<>();
        for (Map<String, String> card : cards)
        {
            if (mapped.contains(card.get("card_id")))
            {
                continue;
            }
            if ("NPC".equals(card.get("card_type")))
            {
                missingNpcMappings.add(card.get("card_id"));
            }
            else
            {
                Map<String, String> family = familyById.get(
                    card.get("entity_family_id"));
                assertNotNull(family);
                assertEquals("ITEM", family.get("entity_type"));
                assertTrue(Integer.parseInt(family.get("canonical_id")) >= 0);
            }
        }
        assertEquals(builtInNpcFallback, missingNpcMappings);
        assertEquals(5, missingNpcMappings.size());
        assertTrue(mapped.stream().allMatch(cardById::containsKey));
    }

    @Test
    public void runtimeEnrichmentContainsNoOrphanedCardRows()
        throws Exception
    {
        Set<String> active = unique(rows(ROOT + "cards.tsv"), "card_id")
            .keySet();
        assertOnlyActive("com/cardrestricted/catalog/card-details.tsv", active);
        assertOnlyActive(
            "com/cardrestricted/catalog/runtime/item-actions.tsv",
            active);
        assertOnlyActive(
            "com/cardrestricted/catalog/runtime/npc-combat-levels.tsv",
            active);
        assertOnlyActive(
            "com/cardrestricted/catalog/quest-review.tsv",
            active);
        assertOnlyActive(
            "com/cardrestricted/catalog/unique-drop-review.tsv",
            active);
    }

    @Test
    public void publicBuildRetainsManifestAndBundlesVerifiedWikiMedia()
        throws Exception
    {
        List<Map<String, String>> manifest = rows(ART + "manifest.tsv");
        assertEquals(7144, manifest.size());
        for (Map<String, String> row : manifest)
        {
            String filename = row.get("filename");
            assertTrue(filename.matches("assets/[0-9a-f]{64}\\.png"));
            assertTrue(filename.contains(row.get("sha256")));
        }
        assertNotNull(CatalogueResourceAuditTest.class.getClassLoader()
            .getResource(ART + "offline-assets-v1.zip"));
        assertNotNull(CatalogueResourceAuditTest.class.getClassLoader()
            .getResource(ART + "offline-assets-v1.sha256"));
        String properties = text(ART + "offline-content-pack.properties");
        assertTrue(properties.contains("redistributed=true"));
        assertTrue(properties.contains("complete=true"));
        assertTrue(properties.contains("uniqueAssets=6667"));
    }

    private static void assertOnlyActive(
        String resource,
        Set<String> active)
        throws Exception
    {
        List<Map<String, String>> data = rows(resource);
        Set<String> ids = unique(data, "card_id").keySet();
        Set<String> orphaned = new TreeSet<>(ids);
        orphaned.removeAll(active);
        assertTrue(resource + " has orphaned rows " + orphaned, orphaned.isEmpty());
    }

    private static Map<String, Map<String, String>> unique(
        List<Map<String, String>> data,
        String key)
    {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map<String, String> row : data)
        {
            String value = row.get(key);
            assertNotNull(value);
            assertFalse(value.trim().isEmpty());
            assertFalse("Duplicate " + key + ": " + value,
                result.containsKey(value));
            result.put(value, row);
        }
        return result;
    }

    private static List<Map<String, String>> rows(String resource)
        throws IOException
    {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream(resource), StandardCharsets.UTF_8)))
        {
            String headerLine = reader.readLine();
            assertNotNull("Missing header for " + resource, headerLine);
            String[] headers = headerLine.split("\\t", -1);
            List<Map<String, String>> result = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.trim().isEmpty())
                {
                    continue;
                }
                String[] values = line.split("\\t", -1);
                assertEquals("Wrong column count in " + resource,
                    headers.length,
                    values.length);
                Map<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < headers.length; index++)
                {
                    row.put(headers[index], values[index]);
                }
                result.add(row);
            }
            return result;
        }
    }

    private static String text(String resource) throws IOException
    {
        return new String(bytes(resource), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String resource) throws IOException
    {
        try (InputStream input = stream(resource);
             java.io.ByteArrayOutputStream output =
                 new java.io.ByteArrayOutputStream())
        {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0)
            {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static InputStream stream(String resource)
    {
        InputStream input = CatalogueResourceAuditTest.class
            .getClassLoader().getResourceAsStream(resource);
        assertNotNull("Missing resource " + resource, input);
        return input;
    }

    private static String hex(byte[] value)
    {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte current : value)
        {
            result.append(String.format("%02x", current & 0xff));
        }
        return result.toString();
    }
}
