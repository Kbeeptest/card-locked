package com.cardrestricted.catalog;

import com.cardrestricted.domain.ActionType;
import com.cardrestricted.domain.EntityType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public final class CatalogueDataLoader
{
    private static final String PROPERTIES_FILE = "catalogue.properties";
    private static final String FAMILIES_FILE = "families.tsv";
    private static final String CARDS_FILE = "cards.tsv";
    private static final String GRANTS_FILE = "grants.tsv";
    private static final String ALIASES_FILE = "aliases.tsv";
    private static final String EXAMINE_OVERRIDES_FILE =
        "examine-overrides.tsv";
    private static final String HISTORICAL_CARDS_FILE =
        "historical-cards.tsv";

    public CardCatalogue load(ClassLoader classLoader, String resourceRoot)
    {
        Objects.requireNonNull(classLoader, "classLoader");
        String root = normalizeRoot(resourceRoot);
        Properties metadata = readProperties(
            classLoader,
            root + PROPERTIES_FILE);
        int catalogueVersion = parsePositiveInt(
            metadata.getProperty("catalogueVersion"),
            PROPERTIES_FILE,
            "catalogueVersion");
        ContentBoundary boundary = parseEnum(
            ContentBoundary.class,
            metadata.getProperty("contentBoundary"),
            PROPERTIES_FILE,
            "contentBoundary");

        List<EntityFamily> families = readFamilies(
            classLoader,
            root + FAMILIES_FILE);
        Map<String, List<CardPermissionGrant>> grantsByCard =
            readGrants(classLoader, root + GRANTS_FILE);
        Map<String, String> examineOverrides = readExamineOverrides(
            classLoader,
            root + EXAMINE_OVERRIDES_FILE);
        List<CardDefinition> cards = readCards(
            classLoader,
            root + CARDS_FILE,
            grantsByCard,
            examineOverrides);
        Set<String> loadedCardIds = new HashSet<>();
        for (CardDefinition card : cards)
        {
            loadedCardIds.add(card.getCardId());
        }
        for (String overrideCardId : examineOverrides.keySet())
        {
            if (!loadedCardIds.contains(overrideCardId))
            {
                throw dataError(
                    EXAMINE_OVERRIDES_FILE,
                    "Examine override references missing card "
                        + overrideCardId + ".");
            }
        }
        for (String grantedCardId : grantsByCard.keySet())
        {
            if (!loadedCardIds.contains(grantedCardId))
            {
                throw dataError(
                    GRANTS_FILE,
                    "Grant references missing card " + grantedCardId + ".");
            }
        }

        Map<String, String> cardAliases = readAliases(
            classLoader,
            root + ALIASES_FILE);
        List<HistoricalCardDefinition> historicalCards =
            readHistoricalCards(
                classLoader,
                root + HISTORICAL_CARDS_FILE);
        CardCatalogue catalogue = new CardCatalogue(
            catalogueVersion,
            boundary,
            families,
            cards,
            cardAliases,
            historicalCards);
        new CatalogueValidator().validate(catalogue);
        return catalogue;
    }

    private List<EntityFamily> readFamilies(
        ClassLoader classLoader,
        String path)
    {
        List<String[]> rows = readTsv(
            classLoader,
            path,
            new String[] {
                "family_id",
                "entity_type",
                "canonical_id",
                "variant_ids",
                "family_version",
                "free_to_play"
            });
        List<EntityFamily> families = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++)
        {
            String[] row = rows.get(index);
            int line = index + 2;
            families.add(new EntityFamily(
                requireText(row[0], path, line, "family_id"),
                parseEnum(
                    EntityType.class,
                    row[1],
                    path,
                    line,
                    "entity_type"),
                parseNonNegativeInt(
                    row[2],
                    path,
                    line,
                    "canonical_id"),
                parseIntegerSet(
                    row[3],
                    path,
                    line,
                    "variant_ids"),
                parsePositiveInt(
                    row[4],
                    path,
                    line,
                    "family_version"),
                parseBoolean(
                    row[5],
                    path,
                    line,
                    "free_to_play")));
        }
        return families;
    }

    private List<CardDefinition> readCards(
        ClassLoader classLoader,
        String path,
        Map<String, List<CardPermissionGrant>> grantsByCard,
        Map<String, String> examineOverrides)
    {
        List<String[]> rows = readTsv(
            classLoader,
            path,
            new String[] {
                "card_id",
                "display_name",
                "examine_text",
                "card_type",
                "rarity",
                "categories",
                "entity_family_id",
                "permissions",
                "free_to_play",
                "introduced_version"
            });
        List<CardDefinition> cards = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++)
        {
            String[] row = rows.get(index);
            int line = index + 2;
            String cardId = requireText(
                row[0],
                path,
                line,
                "card_id");
            cards.add(new CardDefinition(
                cardId,
                requireText(row[1], path, line, "display_name"),
                examineOverrides.getOrDefault(
                    cardId,
                    requireText(row[2], path, line, "examine_text")),
                parseEnum(
                    CardType.class,
                    row[3],
                    path,
                    line,
                    "card_type"),
                parseEnum(
                    Rarity.class,
                    row[4],
                    path,
                    line,
                    "rarity"),
                parseEnumSet(
                    CardCategory.class,
                    row[5],
                    path,
                    line,
                    "categories"),
                requireText(
                    row[6],
                    path,
                    line,
                    "entity_family_id"),
                parseEnumSet(
                    ActionType.class,
                    row[7],
                    path,
                    line,
                    "permissions"),
                grantsByCard.getOrDefault(
                    cardId,
                    Collections.emptyList()),
                parseBoolean(
                    row[8],
                    path,
                    line,
                    "free_to_play"),
                parsePositiveInt(
                    row[9],
                    path,
                    line,
                    "introduced_version")));
        }
        return cards;
    }

    private Map<String, List<CardPermissionGrant>> readGrants(
        ClassLoader classLoader,
        String path)
    {
        List<String[]> rows = readTsv(
            classLoader,
            path,
            new String[] {
                "card_id",
                "entity_family_id",
                "permissions"
            });
        Map<String, List<CardPermissionGrant>> grantsByCard =
            new HashMap<>();
        for (int index = 0; index < rows.size(); index++)
        {
            String[] row = rows.get(index);
            int line = index + 2;
            String cardId = requireText(
                row[0],
                path,
                line,
                "card_id");
            grantsByCard.computeIfAbsent(
                cardId,
                ignored -> new ArrayList<>()).add(
                    new CardPermissionGrant(
                        requireText(
                            row[1],
                            path,
                            line,
                            "entity_family_id"),
                        parseEnumSet(
                            ActionType.class,
                            row[2],
                            path,
                            line,
                            "permissions")));
        }
        return grantsByCard;
    }

    private Map<String, String> readExamineOverrides(
        ClassLoader classLoader,
        String path)
    {
        if (classLoader.getResource(path) == null)
        {
            return Collections.emptyMap();
        }
        List<String[]> rows = readTsv(
            classLoader,
            path,
            new String[] {
                "card_id",
                "examine_text",
                "source",
                "reference"
            });
        Map<String, String> overrides = new HashMap<>();
        for (int index = 0; index < rows.size(); index++)
        {
            String[] row = rows.get(index);
            int line = index + 2;
            String cardId = requireText(
                row[0], path, line, "card_id");
            String text = requireText(
                row[1], path, line, "examine_text");
            requireText(row[2], path, line, "source");
            requireText(row[3], path, line, "reference");
            if (overrides.put(cardId, text) != null)
            {
                throw dataError(
                    path,
                    line,
                    "Duplicate examine override " + cardId + ".");
            }
        }
        return overrides;
    }

    private List<HistoricalCardDefinition> readHistoricalCards(
        ClassLoader classLoader,
        String path)
    {
        if (classLoader.getResource(path) == null)
        {
            return Collections.emptyList();
        }
        List<String[]> rows = readTsv(
            classLoader,
            path,
            new String[] {
                "card_id",
                "display_name",
                "card_type",
                "rarity",
                "retired_version",
                "reason"
            });
        List<HistoricalCardDefinition> historicalCards =
            new ArrayList<>();
        for (int index = 0; index < rows.size(); index++)
        {
            String[] row = rows.get(index);
            int line = index + 2;
            historicalCards.add(new HistoricalCardDefinition(
                requireText(row[0], path, line, "card_id"),
                requireText(row[1], path, line, "display_name"),
                parseEnum(
                    CardType.class,
                    row[2],
                    path,
                    line,
                    "card_type"),
                parseEnum(
                    Rarity.class,
                    row[3],
                    path,
                    line,
                    "rarity"),
                parsePositiveInt(
                    row[4],
                    path,
                    line,
                    "retired_version"),
                requireText(row[5], path, line, "reason")));
        }
        return historicalCards;
    }

    private Map<String, String> readAliases(
        ClassLoader classLoader,
        String path)
    {
        if (classLoader.getResource(path) == null)
        {
            return Collections.emptyMap();
        }
        List<String[]> rows = readTsv(
            classLoader,
            path,
            new String[] {
                "legacy_card_id",
                "canonical_card_id",
                "introduced_version",
                "reason"
            });
        Map<String, String> aliases = new HashMap<>();
        for (int index = 0; index < rows.size(); index++)
        {
            String[] row = rows.get(index);
            int line = index + 2;
            String legacy = requireText(
                row[0], path, line, "legacy_card_id");
            String canonical = requireText(
                row[1], path, line, "canonical_card_id");
            parsePositiveInt(
                row[2], path, line, "introduced_version");
            requireText(row[3], path, line, "reason");
            if (aliases.put(legacy, canonical) != null)
            {
                throw dataError(
                    path,
                    line,
                    "Duplicate legacy card alias " + legacy + ".");
            }
        }
        return aliases;
    }

    private Properties readProperties(
        ClassLoader classLoader,
        String path)
    {
        Properties properties = new Properties();
        try (InputStream input = open(classLoader, path))
        {
            properties.load(input);
        }
        catch (IOException exception)
        {
            throw dataError(path, "Could not read catalogue metadata.");
        }
        return properties;
    }

    private List<String[]> readTsv(
        ClassLoader classLoader,
        String path,
        String[] expectedHeader)
    {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                open(classLoader, path),
                StandardCharsets.UTF_8)))
        {
            String header = reader.readLine();
            if (header == null)
            {
                throw dataError(path, "The data file is empty.");
            }
            String expected = String.join("\t", expectedHeader);
            if (!expected.equals(header))
            {
                throw dataError(
                    path,
                    "Unexpected header. Expected: " + expected + ".");
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null)
            {
                lineNumber++;
                if (line.trim().isEmpty() || line.startsWith("#"))
                {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                if (columns.length != expectedHeader.length)
                {
                    throw dataError(
                        path,
                        lineNumber,
                        "Expected " + expectedHeader.length
                            + " columns but found " + columns.length + ".");
                }
                rows.add(columns);
            }
        }
        catch (IOException exception)
        {
            throw dataError(path, "Could not read catalogue data.");
        }
        return rows;
    }

    private InputStream open(ClassLoader classLoader, String path)
    {
        InputStream input = classLoader.getResourceAsStream(path);
        if (input == null)
        {
            throw dataError(path, "Required resource is missing.");
        }
        return input;
    }

    private String normalizeRoot(String resourceRoot)
    {
        Objects.requireNonNull(resourceRoot, "resourceRoot");
        String root = resourceRoot.startsWith("/")
            ? resourceRoot.substring(1)
            : resourceRoot;
        return root.endsWith("/") ? root : root + "/";
    }

    private Set<Integer> parseIntegerSet(
        String value,
        String path,
        int line,
        String field)
    {
        if (value.trim().isEmpty())
        {
            return Collections.emptySet();
        }
        Set<Integer> values = new HashSet<>();
        for (String part : value.split(","))
        {
            int parsed = parseNonNegativeInt(part, path, line, field);
            if (!values.add(parsed))
            {
                throw dataError(
                    path,
                    line,
                    "Duplicate value " + parsed + " in " + field + ".");
            }
        }
        return values;
    }

    private <E extends Enum<E>> Set<E> parseEnumSet(
        Class<E> type,
        String value,
        String path,
        int line,
        String field)
    {
        if (value.trim().isEmpty())
        {
            return Collections.emptySet();
        }
        Set<E> values = EnumSet.noneOf(type);
        for (String part : value.split(","))
        {
            E parsed = parseEnum(type, part, path, line, field);
            if (!values.add(parsed))
            {
                throw dataError(
                    path,
                    line,
                    "Duplicate value " + parsed + " in " + field + ".");
            }
        }
        return values;
    }

    private <E extends Enum<E>> E parseEnum(
        Class<E> type,
        String value,
        String path,
        int line,
        String field)
    {
        try
        {
            return Enum.valueOf(
                type,
                requireText(value, path, line, field));
        }
        catch (IllegalArgumentException exception)
        {
            throw dataError(
                path,
                line,
                "Invalid " + field + " value " + value + ".");
        }
    }

    private <E extends Enum<E>> E parseEnum(
        Class<E> type,
        String value,
        String path,
        String field)
    {
        return parseEnum(type, value, path, 0, field);
    }

    private boolean parseBoolean(
        String value,
        String path,
        int line,
        String field)
    {
        if ("true".equals(value))
        {
            return true;
        }
        if ("false".equals(value))
        {
            return false;
        }
        throw dataError(
            path,
            line,
            "Invalid " + field + " value " + value + ".");
    }

    private int parsePositiveInt(
        String value,
        String path,
        String field)
    {
        return parsePositiveInt(value, path, 0, field);
    }

    private int parsePositiveInt(
        String value,
        String path,
        int line,
        String field)
    {
        int parsed = parseInteger(value, path, line, field);
        if (parsed < 1)
        {
            throw dataError(
                path,
                line,
                field + " must be positive.");
        }
        return parsed;
    }

    private int parseNonNegativeInt(
        String value,
        String path,
        int line,
        String field)
    {
        int parsed = parseInteger(value, path, line, field);
        if (parsed < 0)
        {
            throw dataError(
                path,
                line,
                field + " cannot be negative.");
        }
        return parsed;
    }

    private int parseInteger(
        String value,
        String path,
        int line,
        String field)
    {
        try
        {
            return Integer.parseInt(
                requireText(value, path, line, field));
        }
        catch (NumberFormatException exception)
        {
            throw dataError(
                path,
                line,
                "Invalid integer for " + field + ": " + value + ".");
        }
    }

    private String requireText(
        String value,
        String path,
        int line,
        String field)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw dataError(
                path,
                line,
                field + " cannot be blank.");
        }
        return value;
    }

    private CatalogueValidationException dataError(
        String path,
        String message)
    {
        return new CatalogueValidationException(path + ": " + message);
    }

    private CatalogueValidationException dataError(
        String path,
        int line,
        String message)
    {
        if (line < 1)
        {
            return dataError(path, message);
        }
        return new CatalogueValidationException(
            path + ":" + line + ": " + message);
    }
}
