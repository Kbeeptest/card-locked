package com.cardrestricted.catalog;

import com.cardrestricted.domain.ActionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CardDefinition
{
    private static final String DEFAULT_EXAMINE =
        "No examine text is available for this card.";

    private final String cardId;
    private final String displayName;
    private final String examineText;
    private final CardType cardType;
    private final Rarity rarity;
    private final Set<CardCategory> categories;
    private final String entityFamilyId;
    private final Set<ActionType> permissions;
    private final List<CardPermissionGrant> additionalPermissionGrants;
    private final boolean freeToPlay;
    private final int catalogueVersionIntroduced;

    public CardDefinition(
        String cardId,
        String displayName,
        CardType cardType,
        Rarity rarity,
        Set<CardCategory> categories,
        String entityFamilyId,
        Set<ActionType> permissions,
        boolean freeToPlay,
        int catalogueVersionIntroduced)
    {
        this(
            cardId,
            displayName,
            DEFAULT_EXAMINE,
            cardType,
            rarity,
            categories,
            entityFamilyId,
            permissions,
            Collections.emptyList(),
            freeToPlay,
            catalogueVersionIntroduced);
    }

    public CardDefinition(
        String cardId,
        String displayName,
        String examineText,
        CardType cardType,
        Rarity rarity,
        Set<CardCategory> categories,
        String entityFamilyId,
        Set<ActionType> permissions,
        boolean freeToPlay,
        int catalogueVersionIntroduced)
    {
        this(
            cardId,
            displayName,
            examineText,
            cardType,
            rarity,
            categories,
            entityFamilyId,
            permissions,
            Collections.emptyList(),
            freeToPlay,
            catalogueVersionIntroduced);
    }

    public CardDefinition(
        String cardId,
        String displayName,
        CardType cardType,
        Rarity rarity,
        Set<CardCategory> categories,
        String entityFamilyId,
        Set<ActionType> permissions,
        List<CardPermissionGrant> additionalPermissionGrants,
        boolean freeToPlay,
        int catalogueVersionIntroduced)
    {
        this(
            cardId,
            displayName,
            DEFAULT_EXAMINE,
            cardType,
            rarity,
            categories,
            entityFamilyId,
            permissions,
            additionalPermissionGrants,
            freeToPlay,
            catalogueVersionIntroduced);
    }

    public CardDefinition(
        String cardId,
        String displayName,
        String examineText,
        CardType cardType,
        Rarity rarity,
        Set<CardCategory> categories,
        String entityFamilyId,
        Set<ActionType> permissions,
        List<CardPermissionGrant> additionalPermissionGrants,
        boolean freeToPlay,
        int catalogueVersionIntroduced)
    {
        this.cardId = requireText(cardId, "cardId");
        this.displayName = requireText(displayName, "displayName");
        this.examineText = requireText(examineText, "examineText");
        this.cardType = Objects.requireNonNull(cardType, "cardType");
        this.rarity = Objects.requireNonNull(rarity, "rarity");
        this.categories = immutableEnumSet(categories, CardCategory.class);
        this.entityFamilyId = requireText(entityFamilyId, "entityFamilyId");
        this.permissions = immutableEnumSet(permissions, ActionType.class);
        this.additionalPermissionGrants = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(
                additionalPermissionGrants,
                "additionalPermissionGrants")));
        if (this.additionalPermissionGrants.stream()
            .anyMatch(Objects::isNull))
        {
            throw new IllegalArgumentException(
                "Additional permission grants cannot contain null.");
        }
        this.freeToPlay = freeToPlay;
        this.catalogueVersionIntroduced = catalogueVersionIntroduced;

        if (this.categories.isEmpty())
        {
            throw new IllegalArgumentException(
                "A card must have at least one category.");
        }
        if (this.permissions.isEmpty())
        {
            throw new IllegalArgumentException(
                "A card must grant at least one permission.");
        }
        if (catalogueVersionIntroduced < 1)
        {
            throw new IllegalArgumentException(
                "catalogueVersionIntroduced must be positive.");
        }
    }

    public String getCardId()
    {
        return cardId;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public String getExamineText()
    {
        return examineText;
    }

    public CardType getCardType()
    {
        return cardType;
    }

    public Rarity getRarity()
    {
        return rarity;
    }

    public Set<CardCategory> getCategories()
    {
        return categories;
    }

    public String getEntityFamilyId()
    {
        return entityFamilyId;
    }

    public Set<ActionType> getPermissions()
    {
        return permissions;
    }

    public List<CardPermissionGrant> getAdditionalPermissionGrants()
    {
        return additionalPermissionGrants;
    }

    public boolean isFreeToPlay()
    {
        return freeToPlay;
    }

    public int getCatalogueVersionIntroduced()
    {
        return catalogueVersionIntroduced;
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

    private static <E extends Enum<E>> Set<E> immutableEnumSet(
        Set<E> source,
        Class<E> type)
    {
        Objects.requireNonNull(source, "source");
        if (source.isEmpty())
        {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(source));
    }
}
