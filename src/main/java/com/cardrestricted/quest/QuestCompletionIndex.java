package com.cardrestricted.quest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Maps RuneLite quest display names to the Quest Tracker's stable quest keys. */
public final class QuestCompletionIndex
{
    private static final String DEFINITIONS_RESOURCE =
        "com/cardrestricted/quest/quest-definitions.tsv";

    private final Map<String, String> questKeysByNormalisedName;

    private QuestCompletionIndex(Map<String, String> questKeysByNormalisedName)
    {
        this.questKeysByNormalisedName = Collections.unmodifiableMap(
            new LinkedHashMap<>(questKeysByNormalisedName));
    }

    public static QuestCompletionIndex load(ClassLoader classLoader)
    {
        Objects.requireNonNull(classLoader, "classLoader");
        Map<String, String> names = new LinkedHashMap<>();
        InputStream stream = classLoader.getResourceAsStream(DEFINITIONS_RESOURCE);
        if (stream == null)
        {
            throw new IllegalStateException("Missing runtime resource "
                + DEFINITIONS_RESOURCE + ".");
        }
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null)
            {
                if (header)
                {
                    header = false;
                    continue;
                }
                if (line.trim().isEmpty())
                {
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length < 3)
                {
                    throw new IllegalStateException("Invalid quest definition row: " + line);
                }
                putUnique(names, normalise(fields[2]), fields[1]);
            }
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Unable to load quest completion index.", exception);
        }

        // RuneLite display-name differences from the certified tracker names.
        alias(names, "Dragon Slayer I", "Dragon Slayer");
        alias(names, "Desert Treasure II - The Fallen Empire", "Desert Treasure II");
        alias(names, "Ratcatchers", "Rat Catchers");
        alias(names, "Romeo & Juliet", "Romeo and Juliet");
        return new QuestCompletionIndex(names);
    }

    public Optional<String> findQuestKey(String runeLiteQuestName)
    {
        if (runeLiteQuestName == null)
        {
            return Optional.empty();
        }
        return Optional.ofNullable(
            questKeysByNormalisedName.get(normalise(runeLiteQuestName)));
    }

    public int size()
    {
        return questKeysByNormalisedName.size();
    }

    static String normalise(String value)
    {
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(java.util.Locale.ROOT)
            .replace("&", " and ")
            .replace("'", "")
            .replace("’", "")
            .replaceAll("[^a-z0-9]+", " ")
            .trim();
        return ascii.replaceAll("\\s+", " ");
    }

    private static void alias(
        Map<String, String> names,
        String alias,
        String canonicalTrackerName)
    {
        String questKey = names.get(normalise(canonicalTrackerName));
        if (questKey == null)
        {
            throw new IllegalStateException("Missing canonical tracker quest "
                + canonicalTrackerName + ".");
        }
        putUnique(names, normalise(alias), questKey);
    }

    private static void putUnique(
        Map<String, String> names,
        String normalisedName,
        String questKey)
    {
        String previous = names.put(normalisedName, questKey);
        if (previous != null && !previous.equals(questKey))
        {
            throw new IllegalStateException("Ambiguous quest completion name "
                + normalisedName + ".");
        }
    }
}
