package com.cardrestricted.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class WikiArtworkManifest
{
    static final String RESOURCE =
        "com/cardrestricted/artwork/wiki/manifest.tsv";

    private WikiArtworkManifest()
    {
    }

    static Map<String, WikiArtworkEntry> load(ClassLoader loader)
    {
        Map<String, WikiArtworkEntry> entries = new LinkedHashMap<>();
        try (InputStream stream = loader.getResourceAsStream(RESOURCE))
        {
            if (stream == null)
            {
                return Collections.emptyMap();
            }
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)))
            {
                String line = reader.readLine();
                while ((line = reader.readLine()) != null)
                {
                    parseLine(line, entries);
                }
            }
        }
        catch (IOException ignored)
        {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(entries);
    }

    private static void parseLine(
        String line,
        Map<String, WikiArtworkEntry> entries)
    {
        if (line.trim().isEmpty() || line.startsWith("#"))
        {
            return;
        }
        String[] columns = line.split("\\t", -1);
        if (columns.length < 12)
        {
            return;
        }
        String cardId = columns[0].trim();
        String runtimeFilename = columns[1].trim();
        String sourceUrl = columns[5].trim();
        String runtimeDigest = columns[7].trim().toLowerCase(Locale.ROOT);
        String sourceDigest = columns[10].trim().toLowerCase(Locale.ROOT);
        if (cardId.isEmpty()
            || runtimeFilename.isEmpty()
            || !runtimeDigest.matches("[0-9a-f]{64}")
            || !sourceDigest.matches("[0-9a-f]{64}"))
        {
            return;
        }
        try
        {
            URI uri = new URI(sourceUrl);
            if (!isApprovedManifestReference(uri))
            {
                return;
            }
            entries.put(cardId, new WikiArtworkEntry(
                cardId,
                runtimeFilename,
                uri,
                sourceDigest,
                runtimeDigest,
                Boolean.parseBoolean(columns[9].trim()),
                WikiArtworkDiskCache.isApprovedSource(uri),
                Boolean.parseBoolean(columns[11].trim())));
        }
        catch (URISyntaxException ignored)
        {
            // Invalid rows are excluded from the runtime manifest.
        }
    }

    private static boolean isApprovedManifestReference(URI uri)
    {
        if (uri == null
            || !"https".equalsIgnoreCase(uri.getScheme())
            || !"oldschool.runescape.wiki".equalsIgnoreCase(uri.getHost())
            || uri.getPath() == null)
        {
            return false;
        }
        return uri.getPath().startsWith("/images/")
            || uri.getPath().startsWith("/w/Category:");
    }
}
