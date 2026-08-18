package com.cardrestricted.foil;

import java.util.Objects;

/** One direct, non-recursive use entitlement supplied by a foil source card. */
public final class FoilRewardGrant
{
    private final String sourceCardId;
    private final String targetCardId;
    private final FoilRewardKind kind;
    private final String ruleId;
    private final String description;

    public FoilRewardGrant(
        String sourceCardId,
        String targetCardId,
        FoilRewardKind kind,
        String ruleId,
        String description)
    {
        this.sourceCardId = requireText(sourceCardId, "sourceCardId");
        this.targetCardId = requireText(targetCardId, "targetCardId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.ruleId = requireText(ruleId, "ruleId");
        this.description = requireText(description, "description");
        if (this.sourceCardId.equals(this.targetCardId))
        {
            throw new IllegalArgumentException(
                "A foil reward cannot target its own source card.");
        }
    }

    public String getSourceCardId()
    {
        return sourceCardId;
    }

    public String getTargetCardId()
    {
        return targetCardId;
    }

    public FoilRewardKind getKind()
    {
        return kind;
    }

    public String getRuleId()
    {
        return ruleId;
    }

    public String getDescription()
    {
        return description;
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
