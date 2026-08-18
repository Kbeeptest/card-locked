package com.cardrestricted.catalog;

import java.util.Objects;

/**
 * Minimal immutable metadata retained for activity records that reference a
 * card removed from the active collection catalogue.
 */
public final class HistoricalCardDefinition
{
    private final String cardId;
    private final String displayName;
    private final CardType cardType;
    private final Rarity rarity;
    private final int retiredVersion;
    private final String reason;

    public HistoricalCardDefinition(
        String cardId,
        String displayName,
        CardType cardType,
        Rarity rarity,
        int retiredVersion,
        String reason)
    {
        this.cardId = requireText(cardId, "cardId");
        this.displayName = requireText(displayName, "displayName");
        this.cardType = Objects.requireNonNull(cardType, "cardType");
        this.rarity = Objects.requireNonNull(rarity, "rarity");
        if (retiredVersion < 1)
        {
            throw new IllegalArgumentException(
                "retiredVersion must be positive.");
        }
        this.retiredVersion = retiredVersion;
        this.reason = requireText(reason, "reason");
    }

    public String getCardId()
    {
        return cardId;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public CardType getCardType()
    {
        return cardType;
    }

    public Rarity getRarity()
    {
        return rarity;
    }

    public int getRetiredVersion()
    {
        return retiredVersion;
    }

    public String getReason()
    {
        return reason;
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value.trim();
    }
}
