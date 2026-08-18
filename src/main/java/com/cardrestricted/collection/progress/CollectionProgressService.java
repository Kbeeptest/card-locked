package com.cardrestricted.collection.progress;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardCategory;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.persistence.CollectionState;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CollectionProgressService
{
    private final CardCatalogue catalogue;

    public CollectionProgressService(CardCatalogue catalogue)
    {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
    }

    public CollectionProgressSnapshot calculate(CollectionState state)
    {
        Objects.requireNonNull(state, "state");
        return calculate(state.getOwnedCardIds(), state.getFoilCardIds());
    }

    public CollectionProgressSnapshot calculate(
        Set<String> ownedCardIds,
        Set<String> foilCardIds)
    {
        Objects.requireNonNull(ownedCardIds, "ownedCardIds");
        Objects.requireNonNull(foilCardIds, "foilCardIds");

        Set<String> canonicalOwned = catalogue.canonicalizeCardIds(
            ownedCardIds);
        Set<String> canonicalFoils = catalogue.canonicalizeCardIds(
            foilCardIds);
        Set<String> unknownOwned = unknownIds(canonicalOwned);
        Set<String> unknownFoils = unknownIds(canonicalFoils);
        canonicalOwned.removeAll(unknownOwned);
        canonicalFoils.removeAll(unknownFoils);
        canonicalFoils.retainAll(canonicalOwned);

        MutableProgress overall = new MutableProgress();
        Map<Rarity, MutableProgress> rarity = mutableEnumMap(Rarity.class);
        Map<CardType, MutableProgress> cardType = mutableEnumMap(CardType.class);
        Map<CardCategory, MutableProgress> category =
            mutableEnumMap(CardCategory.class);
        Map<Integer, MutableProgress> introducedVersion =
            new LinkedHashMap<>();
        MutableProgress freeToPlay = new MutableProgress();
        MutableProgress members = new MutableProgress();

        for (CardDefinition card : catalogue.getCards())
        {
            boolean owned = canonicalOwned.contains(card.getCardId());
            boolean foil = canonicalFoils.contains(card.getCardId());
            overall.record(owned, foil);
            rarity.get(card.getRarity()).record(owned, foil);
            cardType.get(card.getCardType()).record(owned, foil);
            for (CardCategory value : card.getCategories())
            {
                category.get(value).record(owned, foil);
            }
            introducedVersion.computeIfAbsent(
                card.getCatalogueVersionIntroduced(),
                ignored -> new MutableProgress()).record(owned, foil);
            (card.isFreeToPlay() ? freeToPlay : members).record(owned, foil);
        }

        return new CollectionProgressSnapshot(
            overall.freeze(),
            freezeEnumMap(rarity, Rarity.class),
            freezeEnumMap(cardType, CardType.class),
            freezeEnumMap(category, CardCategory.class),
            freezeVersionMap(introducedVersion),
            freeToPlay.freeze(),
            members.freeze(),
            unknownOwned,
            unknownFoils);
    }

    private Set<String> unknownIds(Set<String> cardIds)
    {
        Set<String> unknown = new HashSet<>();
        for (String cardId : cardIds)
        {
            if (!catalogue.containsCard(cardId))
            {
                unknown.add(cardId);
            }
        }
        return unknown;
    }

    private static <E extends Enum<E>> Map<E, MutableProgress>
        mutableEnumMap(Class<E> enumType)
    {
        Map<E, MutableProgress> values = new EnumMap<>(enumType);
        for (E value : enumType.getEnumConstants())
        {
            values.put(value, new MutableProgress());
        }
        return values;
    }

    private static <E extends Enum<E>> Map<E, CollectionProgress>
        freezeEnumMap(
            Map<E, MutableProgress> values,
            Class<E> enumType)
    {
        Map<E, CollectionProgress> frozen = new EnumMap<>(enumType);
        for (Map.Entry<E, MutableProgress> entry : values.entrySet())
        {
            frozen.put(entry.getKey(), entry.getValue().freeze());
        }
        return frozen;
    }

    private static Map<Integer, CollectionProgress> freezeVersionMap(
        Map<Integer, MutableProgress> values)
    {
        Map<Integer, CollectionProgress> frozen = new LinkedHashMap<>();
        for (Map.Entry<Integer, MutableProgress> entry : values.entrySet())
        {
            frozen.put(entry.getKey(), entry.getValue().freeze());
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static final class MutableProgress
    {
        private int total;
        private int owned;
        private int foil;

        private void record(boolean isOwned, boolean isFoil)
        {
            total++;
            if (isOwned)
            {
                owned++;
            }
            if (isFoil)
            {
                foil++;
            }
        }

        private CollectionProgress freeze()
        {
            return new CollectionProgress(total, owned, foil);
        }
    }
}
