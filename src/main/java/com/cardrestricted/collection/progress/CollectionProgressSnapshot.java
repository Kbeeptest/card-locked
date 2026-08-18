package com.cardrestricted.collection.progress;

import com.cardrestricted.catalog.CardCategory;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.Rarity;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CollectionProgressSnapshot
{
    private final CollectionProgress overall;
    private final Map<Rarity, CollectionProgress> byRarity;
    private final Map<CardType, CollectionProgress> byCardType;
    private final Map<CardCategory, CollectionProgress> byCategory;
    private final Map<Integer, CollectionProgress> byIntroducedVersion;
    private final CollectionProgress freeToPlay;
    private final CollectionProgress members;
    private final Set<String> unknownOwnedCardIds;
    private final Set<String> unknownFoilCardIds;

    CollectionProgressSnapshot(
        CollectionProgress overall,
        Map<Rarity, CollectionProgress> byRarity,
        Map<CardType, CollectionProgress> byCardType,
        Map<CardCategory, CollectionProgress> byCategory,
        Map<Integer, CollectionProgress> byIntroducedVersion,
        CollectionProgress freeToPlay,
        CollectionProgress members,
        Set<String> unknownOwnedCardIds,
        Set<String> unknownFoilCardIds)
    {
        this.overall = Objects.requireNonNull(overall, "overall");
        this.byRarity = immutableEnumMap(byRarity, Rarity.class);
        this.byCardType = immutableEnumMap(byCardType, CardType.class);
        this.byCategory = immutableEnumMap(byCategory, CardCategory.class);
        this.byIntroducedVersion = Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(
                byIntroducedVersion,
                "byIntroducedVersion")));
        this.freeToPlay = Objects.requireNonNull(freeToPlay, "freeToPlay");
        this.members = Objects.requireNonNull(members, "members");
        this.unknownOwnedCardIds = Set.copyOf(Objects.requireNonNull(
            unknownOwnedCardIds,
            "unknownOwnedCardIds"));
        this.unknownFoilCardIds = Set.copyOf(Objects.requireNonNull(
            unknownFoilCardIds,
            "unknownFoilCardIds"));
    }

    public CollectionProgress getOverall()
    {
        return overall;
    }

    public CollectionProgress getProgress(Rarity rarity)
    {
        return requireProgress(byRarity, rarity, "rarity");
    }

    public CollectionProgress getProgress(CardType cardType)
    {
        return requireProgress(byCardType, cardType, "cardType");
    }

    public CollectionProgress getProgress(CardCategory category)
    {
        return requireProgress(byCategory, category, "category");
    }

    public CollectionProgress getProgressForIntroducedVersion(int version)
    {
        if (version < 1)
        {
            throw new IllegalArgumentException(
                "Introduced catalogue version must be positive.");
        }
        return byIntroducedVersion.getOrDefault(
            version,
            new CollectionProgress(0, 0, 0));
    }

    public Map<Integer, CollectionProgress> getIntroducedVersionProgress()
    {
        return byIntroducedVersion;
    }

    public CollectionProgress getFreeToPlay()
    {
        return freeToPlay;
    }

    public CollectionProgress getMembers()
    {
        return members;
    }

    public Set<String> getUnknownOwnedCardIds()
    {
        return unknownOwnedCardIds;
    }

    public Set<String> getUnknownFoilCardIds()
    {
        return unknownFoilCardIds;
    }

    public boolean hasUnknownCardIds()
    {
        return !unknownOwnedCardIds.isEmpty() || !unknownFoilCardIds.isEmpty();
    }

    private static <E extends Enum<E>> Map<E, CollectionProgress>
        immutableEnumMap(
            Map<E, CollectionProgress> source,
            Class<E> enumType)
    {
        Objects.requireNonNull(source, "source");
        Map<E, CollectionProgress> copy = new EnumMap<>(enumType);
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }

    private static <E extends Enum<E>> CollectionProgress requireProgress(
        Map<E, CollectionProgress> values,
        E key,
        String field)
    {
        Objects.requireNonNull(key, field);
        CollectionProgress progress = values.get(key);
        if (progress == null)
        {
            throw new IllegalStateException(
                "Progress was not calculated for " + key + ".");
        }
        return progress;
    }
}
