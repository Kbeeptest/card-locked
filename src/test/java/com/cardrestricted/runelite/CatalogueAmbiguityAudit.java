package com.cardrestricted.runelite;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact-name ambiguity inventory using the same normalisers as runtime fallback. */
final class CatalogueAmbiguityAudit
{
    private CatalogueAmbiguityAudit()
    {
    }

    static Result analyse(CardCatalogue catalogue)
    {
        Map<Key, Set<String>> families = new LinkedHashMap<>();
        Map<Key, Set<String>> cards = new LinkedHashMap<>();
        for (CardDefinition card : catalogue.getCards())
        {
            if (card.getCardType() != CardType.ITEM
                && card.getCardType() != CardType.NPC)
            {
                continue;
            }
            String normalised = card.getCardType() == CardType.ITEM
                ? InteractionNameNormalizer.normaliseItemName(
                    card.getDisplayName())
                : InteractionNameNormalizer.normaliseEntityName(
                    card.getDisplayName());
            if (normalised.isEmpty())
            {
                continue;
            }
            Key key = new Key(card.getCardType(), normalised);
            families.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                .add(card.getEntityFamilyId());
            cards.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                .add(card.getCardId());
        }

        List<Entry> unique = new ArrayList<>();
        List<Entry> ambiguous = new ArrayList<>();
        for (Map.Entry<Key, Set<String>> entry : families.entrySet())
        {
            Entry result = new Entry(
                entry.getKey().type,
                entry.getKey().normalisedName,
                entry.getValue(),
                cards.getOrDefault(entry.getKey(), Collections.emptySet()));
            if (entry.getValue().size() == 1)
            {
                unique.add(result);
            }
            else
            {
                ambiguous.add(result);
            }
        }
        Comparator<Entry> order = Comparator
            .comparing((Entry entry) -> entry.type.name())
            .thenComparing(entry -> entry.normalisedName);
        unique.sort(order);
        ambiguous.sort(order);
        return new Result(unique, ambiguous);
    }

    static final class Result
    {
        final List<Entry> unique;
        final List<Entry> ambiguous;

        Result(List<Entry> unique, List<Entry> ambiguous)
        {
            this.unique = List.copyOf(unique);
            this.ambiguous = List.copyOf(ambiguous);
        }
    }

    static final class Entry
    {
        final CardType type;
        final String normalisedName;
        final Set<String> familyIds;
        final Set<String> cardIds;

        Entry(
            CardType type,
            String normalisedName,
            Set<String> familyIds,
            Set<String> cardIds)
        {
            this.type = type;
            this.normalisedName = normalisedName;
            this.familyIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(familyIds));
            this.cardIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(cardIds));
        }
    }

    private static final class Key
    {
        private final CardType type;
        private final String normalisedName;

        private Key(CardType type, String normalisedName)
        {
            this.type = type;
            this.normalisedName = normalisedName;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }
            if (!(other instanceof Key))
            {
                return false;
            }
            Key key = (Key) other;
            return type == key.type
                && normalisedName.equals(key.normalisedName);
        }

        @Override
        public int hashCode()
        {
            return 31 * type.hashCode() + normalisedName.hashCode();
        }
    }
}
