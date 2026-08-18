package com.cardrestricted.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class QuestRequirementGroup
{
    private final String groupId;
    private final QuestRequirementType type;
    private final QuestRequirementMode mode;
    private final List<String> cardIds;

    QuestRequirementGroup(
        String groupId,
        QuestRequirementType type,
        QuestRequirementMode mode,
        List<String> cardIds)
    {
        this.groupId = requireText(groupId, "groupId");
        this.type = Objects.requireNonNull(type, "type");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.cardIds = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(cardIds, "cardIds")));
        if (this.cardIds.isEmpty() || this.cardIds.stream().anyMatch(Objects::isNull))
        {
            throw new IllegalArgumentException("Requirement group must contain card IDs.");
        }
    }

    public String getGroupId()
    {
        return groupId;
    }

    public QuestRequirementType getType()
    {
        return type;
    }

    public QuestRequirementMode getMode()
    {
        return mode;
    }

    public List<String> getCardIds()
    {
        return cardIds;
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
