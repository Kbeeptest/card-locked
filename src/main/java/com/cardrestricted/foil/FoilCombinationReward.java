package com.cardrestricted.foil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** A non-recursive entitlement that requires several genuinely owned foil sources. */
public final class FoilCombinationReward
{
    private final String ruleId;
    private final String description;
    private final Set<String> requiredSourceCardIds;
    private final Set<String> targetCardIds;

    public FoilCombinationReward(
        String ruleId,
        String description,
        Set<String> requiredSourceCardIds,
        Set<String> targetCardIds)
    {
        this.ruleId = requireText(ruleId, "ruleId");
        this.description = requireText(description, "description");
        this.requiredSourceCardIds = immutableNonEmpty(
            requiredSourceCardIds, "requiredSourceCardIds");
        this.targetCardIds = immutableNonEmpty(targetCardIds, "targetCardIds");
        if (this.requiredSourceCardIds.size() < 2)
        {
            throw new IllegalArgumentException(
                "A combination reward requires at least two foil sources.");
        }
    }

    public String getRuleId()
    {
        return ruleId;
    }

    public String getDescription()
    {
        return description;
    }

    public Set<String> getRequiredSourceCardIds()
    {
        return requiredSourceCardIds;
    }

    public Set<String> getTargetCardIds()
    {
        return targetCardIds;
    }

    public FoilRewardGrant asGrant(String sourceCardId, String targetCardId)
    {
        if (!requiredSourceCardIds.contains(sourceCardId))
        {
            throw new IllegalArgumentException(
                "Source is not part of combination " + ruleId + '.');
        }
        if (!targetCardIds.contains(targetCardId))
        {
            throw new IllegalArgumentException(
                "Target is not part of combination " + ruleId + '.');
        }
        return new FoilRewardGrant(
            sourceCardId,
            targetCardId,
            FoilRewardKind.MULTI_SOURCE_COMPLETION,
            ruleId,
            description);
    }

    private static Set<String> immutableNonEmpty(Set<String> values, String field)
    {
        Objects.requireNonNull(values, field);
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        for (String value : values)
        {
            copy.add(requireText(value, field));
        }
        if (copy.isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be empty.");
        }
        return Collections.unmodifiableSet(copy);
    }

    private static String requireText(String value, String field)
    {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return trimmed;
    }
}
