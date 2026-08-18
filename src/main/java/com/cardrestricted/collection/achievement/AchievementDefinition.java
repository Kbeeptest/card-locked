package com.cardrestricted.collection.achievement;

import com.cardrestricted.catalog.CardCategory;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.Rarity;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class AchievementDefinition
{
    private static final Pattern ID_PATTERN =
        Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final String achievementId;
    private final String displayName;
    private final String description;
    private final AchievementScope scope;
    private final String scopeKey;
    private final AchievementMeasure measure;
    private final int target;

    public AchievementDefinition(
        String achievementId,
        String displayName,
        String description,
        AchievementScope scope,
        String scopeKey,
        AchievementMeasure measure,
        int target)
    {
        this.achievementId = requireText(achievementId, "achievementId");
        this.displayName = requireText(displayName, "displayName");
        this.description = requireText(description, "description");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.scopeKey = normaliseScopeKey(scopeKey);
        this.measure = Objects.requireNonNull(measure, "measure");
        this.target = target;

        if (!ID_PATTERN.matcher(this.achievementId).matches())
        {
            throw new IllegalArgumentException(
                "Achievement IDs must use lowercase letters, numbers and "
                    + "hyphens: " + this.achievementId);
        }
        if (target <= 0)
        {
            throw new IllegalArgumentException(
                "Achievement targets must be positive.");
        }
        if (measure == AchievementMeasure.COMPLETION_PERCENT && target > 100)
        {
            throw new IllegalArgumentException(
                "Completion percentage targets cannot exceed 100.");
        }
        validateScopeKey();
    }

    public String getAchievementId()
    {
        return achievementId;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public String getDescription()
    {
        return description;
    }

    public AchievementScope getScope()
    {
        return scope;
    }

    public String getScopeKey()
    {
        return scopeKey;
    }

    public AchievementMeasure getMeasure()
    {
        return measure;
    }

    public int getTarget()
    {
        return target;
    }

    private void validateScopeKey()
    {
        switch (scope)
        {
            case OVERALL:
                if (!"*".equals(scopeKey))
                {
                    throw new IllegalArgumentException(
                        "Overall achievements must use '*' as their scope key.");
                }
                return;
            case RARITY:
                Rarity.valueOf(scopeKey);
                return;
            case CARD_TYPE:
                CardType.valueOf(scopeKey);
                return;
            case CATEGORY:
                CardCategory.valueOf(scopeKey);
                return;
            case ACCESS:
                if (!"F2P".equals(scopeKey) && !"MEMBERS".equals(scopeKey))
                {
                    throw new IllegalArgumentException(
                        "Access achievements must use F2P or MEMBERS.");
                }
                return;
            case CATALOGUE_VERSION:
                int version = Integer.parseInt(scopeKey);
                if (version < 1)
                {
                    throw new IllegalArgumentException(
                        "Catalogue-version achievement keys must be positive.");
                }
                return;
            default:
                throw new IllegalStateException(
                    "Unsupported achievement scope " + scope + ".");
        }
    }

    private static String normaliseScopeKey(String value)
    {
        String text = requireText(value, "scopeKey").trim();
        return "*".equals(text)
            ? text
            : text.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String requireText(String value, String field)
    {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value.trim();
    }
}
