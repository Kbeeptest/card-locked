package com.cardrestricted.collection.achievement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AchievementRegistry
{
    public static final String RESOURCE =
        "com/cardrestricted/collection/achievements.tsv";
    private static final String HEADER =
        "achievement_id\tdisplay_name\tdescription\tscope\tscope_key"
            + "\tmeasure\ttarget";

    private final List<AchievementDefinition> definitions;
    private final Map<String, AchievementDefinition> definitionsById;

    private AchievementRegistry(List<AchievementDefinition> definitions)
    {
        this.definitions = Collections.unmodifiableList(
            new ArrayList<>(definitions));
        Map<String, AchievementDefinition> indexed = new LinkedHashMap<>();
        for (AchievementDefinition definition : definitions)
        {
            AchievementDefinition previous = indexed.put(
                definition.getAchievementId(),
                definition);
            if (previous != null)
            {
                throw new IllegalArgumentException(
                    "Duplicate achievement ID "
                        + definition.getAchievementId() + ".");
            }
        }
        if (indexed.isEmpty())
        {
            throw new IllegalArgumentException(
                "At least one achievement must be defined.");
        }
        definitionsById = Collections.unmodifiableMap(indexed);
    }

    public static AchievementRegistry load(ClassLoader classLoader)
    {
        Objects.requireNonNull(classLoader, "classLoader");
        InputStream input = classLoader.getResourceAsStream(RESOURCE);
        if (input == null)
        {
            throw new IllegalStateException(
                "Missing achievement resource " + RESOURCE + ".");
        }
        return parse(input);
    }

    public static AchievementRegistry parse(InputStream input)
    {
        Objects.requireNonNull(input, "input");
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, StandardCharsets.UTF_8)))
        {
            String header = reader.readLine();
            if (!HEADER.equals(header))
            {
                throw new IllegalArgumentException(
                    "Unexpected achievement header. Expected: " + HEADER);
            }
            List<AchievementDefinition> definitions = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null)
            {
                lineNumber++;
                if (line.trim().isEmpty() || line.startsWith("#"))
                {
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length != 7)
                {
                    throw new IllegalArgumentException(
                        "Achievement line " + lineNumber
                            + " must contain exactly seven columns.");
                }
                try
                {
                    definitions.add(new AchievementDefinition(
                        fields[0],
                        fields[1],
                        fields[2],
                        AchievementScope.valueOf(fields[3]),
                        fields[4],
                        AchievementMeasure.valueOf(fields[5]),
                        Integer.parseInt(fields[6])));
                }
                catch (RuntimeException exception)
                {
                    throw new IllegalArgumentException(
                        "Invalid achievement on line " + lineNumber + ": "
                            + exception.getMessage(),
                        exception);
                }
            }
            return new AchievementRegistry(definitions);
        }
        catch (IOException exception)
        {
            throw new IllegalStateException(
                "Unable to read achievement definitions.",
                exception);
        }
    }

    public List<AchievementDefinition> getDefinitions()
    {
        return definitions;
    }

    public AchievementDefinition require(String achievementId)
    {
        AchievementDefinition definition = definitionsById.get(
            Objects.requireNonNull(achievementId, "achievementId"));
        if (definition == null)
        {
            throw new IllegalArgumentException(
                "Unknown achievement " + achievementId + ".");
        }
        return definition;
    }
}
