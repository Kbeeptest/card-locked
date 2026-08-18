package com.cardrestricted.foil;

import java.util.Objects;

/** Explains which owned foil directly supplies a derived use entitlement. */
public final class FoilUnlockProvenance
{
    private final String sourceCardId;
    private final String targetCardId;
    private final FoilRewardKind kind;
    private final String ruleId;
    private final String description;

    public FoilUnlockProvenance(FoilRewardGrant grant)
    {
        Objects.requireNonNull(grant, "grant");
        sourceCardId = grant.getSourceCardId();
        targetCardId = grant.getTargetCardId();
        kind = grant.getKind();
        ruleId = grant.getRuleId();
        description = grant.getDescription();
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
}
