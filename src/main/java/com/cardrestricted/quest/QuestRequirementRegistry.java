package com.cardrestricted.quest;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.domain.ActionType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class QuestRequirementRegistry
{
    private static final String DEFINITIONS_RESOURCE =
        "com/cardrestricted/quest/quest-definitions.tsv";
    private static final String ITEM_REQUIREMENTS_RESOURCE =
        "com/cardrestricted/quest/quest-requirements.tsv";
    private static final String COMBAT_REQUIREMENTS_RESOURCE =
        "com/cardrestricted/quest/quest-combat-requirements.tsv";

    private final List<QuestTrackerDefinition> quests;
    private final Map<String, QuestTrackerDefinition> questsByKey;

    private QuestRequirementRegistry(List<QuestTrackerDefinition> quests)
    {
        this.quests = Collections.unmodifiableList(new ArrayList<>(quests));
        Map<String, QuestTrackerDefinition> byKey = new LinkedHashMap<>();
        for (QuestTrackerDefinition quest : quests)
        {
            byKey.put(quest.getQuestKey(), quest);
        }
        this.questsByKey = Collections.unmodifiableMap(byKey);
    }

    public static QuestRequirementRegistry load(
        ClassLoader classLoader,
        CardCatalogue catalogue)
    {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(catalogue, "catalogue");

        Map<String, DefinitionBuilder> builders = loadDefinitions(classLoader);
        loadRequirements(
            classLoader,
            catalogue,
            builders,
            ITEM_REQUIREMENTS_RESOURCE,
            QuestRequirementType.ITEM);
        loadRequirements(
            classLoader,
            catalogue,
            builders,
            COMBAT_REQUIREMENTS_RESOURCE,
            QuestRequirementType.COMBAT);
        List<QuestTrackerDefinition> definitions = new ArrayList<>();
        for (DefinitionBuilder builder : builders.values())
        {
            definitions.add(builder.build());
        }
        definitions.sort(Comparator.comparing(
            QuestTrackerDefinition::getQuestName,
            String.CASE_INSENSITIVE_ORDER));
        return new QuestRequirementRegistry(definitions);
    }

    public List<QuestTrackerDefinition> getQuests()
    {
        return quests;
    }

    public Optional<QuestTrackerDefinition> findQuest(String questKey)
    {
        return Optional.ofNullable(questsByKey.get(questKey));
    }

    private static Map<String, DefinitionBuilder> loadDefinitions(
        ClassLoader classLoader)
    {
        Map<String, DefinitionBuilder> builders = new LinkedHashMap<>();
        try (BufferedReader reader = reader(classLoader, DEFINITIONS_RESOURCE))
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
                DefinitionBuilder previous = builders.put(
                    fields[1], new DefinitionBuilder(fields[0], fields[1], fields[2]));
                if (previous != null)
                {
                    throw new IllegalStateException("Duplicate quest key " + fields[1] + ".");
                }
            }
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Unable to load quest definitions.", exception);
        }
        return builders;
    }

    private static void loadRequirements(
        ClassLoader classLoader,
        CardCatalogue catalogue,
        Map<String, DefinitionBuilder> builders,
        String resource,
        QuestRequirementType type)
    {
        try (BufferedReader reader = reader(classLoader, resource))
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
                if (fields.length < 4)
                {
                    throw new IllegalStateException("Invalid quest requirement row: " + line);
                }
                DefinitionBuilder builder = builders.get(fields[0]);
                if (builder == null)
                {
                    throw new IllegalStateException("Unknown quest key " + fields[0] + ".");
                }
                String canonicalCardId = catalogue.resolveCardId(fields[3]);
                validateRequirementCard(catalogue.requireCard(canonicalCardId), type);
                builder.addRequirement(
                    fields[1],
                    type,
                    QuestRequirementMode.valueOf(fields[2]),
                    canonicalCardId);
            }
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Unable to load quest requirements from "
                + resource + ".", exception);
        }
    }

    private static void validateRequirementCard(
        CardDefinition card,
        QuestRequirementType type)
    {
        if (type == QuestRequirementType.ITEM)
        {
            if (card.getCardType() != CardType.ITEM)
            {
                throw new IllegalStateException(
                    "Quest item registry contains non-item card " + card.getCardId() + ".");
            }
            return;
        }
        if (card.getCardType() != CardType.NPC
            || !card.getPermissions().contains(ActionType.NPC_ATTACK))
        {
            throw new IllegalStateException(
                "Quest combat registry contains non-attackable NPC card "
                    + card.getCardId() + ".");
        }
    }

    private static BufferedReader reader(
        ClassLoader classLoader,
        String resource)
    {
        InputStream stream = classLoader.getResourceAsStream(resource);
        if (stream == null)
        {
            throw new IllegalStateException("Missing runtime resource " + resource + ".");
        }
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static final class DefinitionBuilder
    {
        private final String scope;
        private final String key;
        private final String name;
        private final Map<String, GroupBuilder> groups = new LinkedHashMap<>();

        private DefinitionBuilder(String scope, String key, String name)
        {
            this.scope = scope;
            this.key = key;
            this.name = name;
        }

        private void addRequirement(
            String groupId,
            QuestRequirementType type,
            QuestRequirementMode mode,
            String cardId)
        {
            String typedGroupId = type.name() + ":" + groupId;
            GroupBuilder group = groups.computeIfAbsent(
                typedGroupId,
                ignored -> new GroupBuilder(groupId, type, mode));
            if (group.mode != mode || group.type != type)
            {
                throw new IllegalStateException("Mixed modes or types for requirement group "
                    + groupId + ".");
            }
            if (!group.cardIds.contains(cardId))
            {
                group.cardIds.add(cardId);
            }
        }

        private QuestTrackerDefinition build()
        {
            List<QuestRequirementGroup> builtGroups = new ArrayList<>();
            for (GroupBuilder group : groups.values())
            {
                builtGroups.add(new QuestRequirementGroup(
                    group.groupId, group.type, group.mode, group.cardIds));
            }
            return new QuestTrackerDefinition(scope, key, name, builtGroups);
        }
    }

    private static final class GroupBuilder
    {
        private final String groupId;
        private final QuestRequirementType type;
        private final QuestRequirementMode mode;
        private final List<String> cardIds = new ArrayList<>();

        private GroupBuilder(
            String groupId,
            QuestRequirementType type,
            QuestRequirementMode mode)
        {
            this.groupId = groupId;
            this.type = type;
            this.mode = mode;
        }
    }
}
