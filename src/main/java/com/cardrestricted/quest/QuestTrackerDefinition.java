package com.cardrestricted.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class QuestTrackerDefinition
{
    private final String scope;
    private final String questKey;
    private final String questName;
    private final List<QuestRequirementGroup> requirementGroups;
    private final List<QuestRequirementGroup> itemRequirementGroups;
    private final List<QuestRequirementGroup> combatRequirementGroups;

    QuestTrackerDefinition(
        String scope,
        String questKey,
        String questName,
        List<QuestRequirementGroup> requirementGroups)
    {
        this.scope = requireText(scope, "scope");
        this.questKey = requireText(questKey, "questKey");
        this.questName = requireText(questName, "questName");
        this.requirementGroups = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(requirementGroups, "requirementGroups")));
        this.itemRequirementGroups = groupsOfType(QuestRequirementType.ITEM);
        this.combatRequirementGroups = groupsOfType(QuestRequirementType.COMBAT);
    }

    private List<QuestRequirementGroup> groupsOfType(QuestRequirementType type)
    {
        return Collections.unmodifiableList(requirementGroups.stream()
            .filter(group -> group.getType() == type)
            .collect(Collectors.toList()));
    }

    public String getScope()
    {
        return scope;
    }

    public String getQuestKey()
    {
        return questKey;
    }

    public String getQuestName()
    {
        return questName;
    }

    /** Compatibility accessor for all requirement groups. */
    public List<QuestRequirementGroup> getRequirementGroups()
    {
        return requirementGroups;
    }

    public List<QuestRequirementGroup> getItemRequirementGroups()
    {
        return itemRequirementGroups;
    }

    public List<QuestRequirementGroup> getCombatRequirementGroups()
    {
        return combatRequirementGroups;
    }

    private static String requireText(String value, String field)
    {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value;
    }
}
