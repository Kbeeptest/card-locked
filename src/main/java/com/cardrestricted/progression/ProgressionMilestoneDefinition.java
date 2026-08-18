package com.cardrestricted.progression;

import java.util.Objects;

/** Immutable presentation definition for one collection progression milestone. */
public final class ProgressionMilestoneDefinition
{
    public enum Kind
    {
        PERMANENT_UNLOCK,
        ONE_TIME_REWARD
    }

    private final int requiredCards;
    private final String title;
    private final String rewardSummary;
    private final String detail;
    private final Kind kind;
    private final String claimedMarker;

    public ProgressionMilestoneDefinition(
        int requiredCards,
        String title,
        String rewardSummary,
        String detail,
        Kind kind,
        String claimedMarker)
    {
        if (requiredCards < 0)
        {
            throw new IllegalArgumentException("requiredCards cannot be negative.");
        }
        this.requiredCards = requiredCards;
        this.title = requireText(title, "title");
        this.rewardSummary = requireText(rewardSummary, "rewardSummary");
        this.detail = requireText(detail, "detail");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.claimedMarker = claimedMarker == null ? "" : claimedMarker.trim();
        if (kind == Kind.ONE_TIME_REWARD && this.claimedMarker.isEmpty())
        {
            throw new IllegalArgumentException(
                "One-time milestone rewards require a claimed marker.");
        }
        if (kind == Kind.PERMANENT_UNLOCK && !this.claimedMarker.isEmpty())
        {
            throw new IllegalArgumentException(
                "Permanent milestone unlocks cannot use a claimed marker.");
        }
    }

    public int getRequiredCards()
    {
        return requiredCards;
    }

    public String getTitle()
    {
        return title;
    }

    public String getRewardSummary()
    {
        return rewardSummary;
    }

    public String getDetail()
    {
        return detail;
    }

    public Kind getKind()
    {
        return kind;
    }

    public String getClaimedMarker()
    {
        return claimedMarker;
    }

    private static String requireText(String value, String label)
    {
        Objects.requireNonNull(value, label);
        String trimmed = value.trim();
        if (trimmed.isEmpty())
        {
            throw new IllegalArgumentException(label + " cannot be blank.");
        }
        return trimmed;
    }
}
