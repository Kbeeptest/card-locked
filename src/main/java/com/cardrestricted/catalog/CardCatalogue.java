package com.cardrestricted.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CardCatalogue
{
    private final int catalogueVersion;
    private final ContentBoundary contentBoundary;
    private final Map<String, EntityFamily> familiesById;
    private final Map<String, CardDefinition> cardsById;
    private final CardIdAliasIndex cardIdAliases;
    private final Map<String, HistoricalCardDefinition> historicalCardsById;
    private final Map<Rarity, List<CardDefinition>> cardsByRarity;
    private final Map<CardType, List<CardDefinition>> cardsByType;
    private final Map<CardCategory, List<CardDefinition>> cardsByCategory;
    private final Map<Integer, List<CardDefinition>> cardsByIntroducedVersion;
    private final List<CardDefinition> freeToPlayCards;
    private final List<CardDefinition> membersCards;
    private final Map<String, String> searchTextByCardId;

    public CardCatalogue(
        int catalogueVersion,
        ContentBoundary contentBoundary,
        Collection<EntityFamily> families,
        Collection<CardDefinition> cards)
    {
        this(
            catalogueVersion,
            contentBoundary,
            families,
            cards,
            Collections.emptyMap(),
            Collections.emptyList());
    }

    public CardCatalogue(
        int catalogueVersion,
        ContentBoundary contentBoundary,
        Collection<EntityFamily> families,
        Collection<CardDefinition> cards,
        Map<String, String> cardAliases)
    {
        this(
            catalogueVersion,
            contentBoundary,
            families,
            cards,
            cardAliases,
            Collections.emptyList());
    }

    public CardCatalogue(
        int catalogueVersion,
        ContentBoundary contentBoundary,
        Collection<EntityFamily> families,
        Collection<CardDefinition> cards,
        Map<String, String> cardAliases,
        Collection<HistoricalCardDefinition> historicalCards)
    {
        if (catalogueVersion < 1)
        {
            throw new IllegalArgumentException(
                "catalogueVersion must be positive.");
        }
        this.catalogueVersion = catalogueVersion;
        this.contentBoundary =
            Objects.requireNonNull(contentBoundary, "contentBoundary");
        validateUniqueDisplayNames(cards);
        this.familiesById = indexFamilies(families);
        this.cardsById = indexCards(cards);
        this.cardIdAliases = new CardIdAliasIndex(
            cardAliases,
            this.cardsById.keySet());
        this.historicalCardsById = indexHistoricalCards(historicalCards);
        this.cardsByRarity = indexByRarity(this.cardsById.values());
        this.cardsByType = indexByType(this.cardsById.values());
        this.cardsByCategory = indexByCategory(this.cardsById.values());
        this.cardsByIntroducedVersion =
            indexByIntroducedVersion(this.cardsById.values());
        this.freeToPlayCards = membershipCards(true);
        this.membersCards = membershipCards(false);
        this.searchTextByCardId = indexSearchText(this.cardsById.values());
    }

    public int getCatalogueVersion()
    {
        return catalogueVersion;
    }

    public ContentBoundary getContentBoundary()
    {
        return contentBoundary;
    }

    public Collection<EntityFamily> getFamilies()
    {
        return familiesById.values();
    }

    public Collection<CardDefinition> getCards()
    {
        return cardsById.values();
    }

    public List<CardDefinition> getCards(Rarity rarity)
    {
        return cardsByRarity.get(Objects.requireNonNull(rarity, "rarity"));
    }

    public List<CardDefinition> getCards(CardType cardType)
    {
        return cardsByType.get(Objects.requireNonNull(cardType, "cardType"));
    }

    public List<CardDefinition> getCards(CardCategory category)
    {
        return cardsByCategory.get(Objects.requireNonNull(category, "category"));
    }

    public List<CardDefinition> getCardsIntroducedInVersion(int version)
    {
        if (version < 1)
        {
            throw new IllegalArgumentException(
                "Introduced catalogue version must be positive.");
        }
        return cardsByIntroducedVersion.getOrDefault(
            version,
            Collections.emptyList());
    }

    public Set<Integer> getIntroducedVersions()
    {
        return cardsByIntroducedVersion.keySet();
    }

    public List<CardDefinition> getFreeToPlayCards()
    {
        return freeToPlayCards;
    }

    public List<CardDefinition> getMembersCards()
    {
        return membersCards;
    }

    public EntityFamily requireFamily(String familyId)
    {
        EntityFamily family = familiesById.get(familyId);
        if (family == null)
        {
            throw new IllegalArgumentException(
                "Unknown entity family " + familyId + ".");
        }
        return family;
    }

    public CardDefinition requireCard(String cardId)
    {
        return findCard(cardId).orElseThrow(() ->
            new IllegalArgumentException("Unknown card " + cardId + "."));
    }

    public Optional<CardDefinition> findCard(String cardId)
    {
        return Optional.ofNullable(cardsById.get(resolveCardId(cardId)));
    }

    public boolean containsCard(String cardId)
    {
        return findCard(cardId).isPresent();
    }

    public CardSearchQuery prepareSearch(String query)
    {
        return CardSearchQuery.parse(query);
    }

    public boolean matchesSearch(CardDefinition card, String query)
    {
        return matchesSearch(card, prepareSearch(query));
    }

    public boolean matchesSearch(
        CardDefinition card,
        CardSearchQuery query)
    {
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(query, "query");
        String searchable = searchTextByCardId.get(card.getCardId());
        if (searchable == null)
        {
            throw new IllegalArgumentException(
                "Card is not part of this catalogue: " + card.getCardId());
        }
        return query.matches(searchable);
    }

    public String resolveCardId(String cardId)
    {
        return cardIdAliases.canonicalize(cardId);
    }

    public Set<String> canonicalizeCardIds(Set<String> cardIds)
    {
        return cardIdAliases.canonicalizeAll(cardIds);
    }

    public Optional<HistoricalCardDefinition> findHistoricalCard(
        String cardId)
    {
        Objects.requireNonNull(cardId, "cardId");
        return Optional.ofNullable(historicalCardsById.get(cardId));
    }

    public Collection<HistoricalCardDefinition> getHistoricalCards()
    {
        return historicalCardsById.values();
    }

    public Map<String, String> getCardAliases()
    {
        return cardIdAliases.asMap();
    }

    public int getCardAliasCount()
    {
        return cardIdAliases.size();
    }

    private static void validateUniqueDisplayNames(
        Collection<CardDefinition> cards)
    {
        Objects.requireNonNull(cards, "cards");
        Map<String, String> cardByTypeAndName = new LinkedHashMap<>();
        for (CardDefinition card : cards)
        {
            Objects.requireNonNull(card, "cards cannot contain null");
            String key = card.getCardType().name() + "\u0000"
                + card.getDisplayName().trim().toLowerCase(
                    java.util.Locale.ROOT);
            String previous = cardByTypeAndName.putIfAbsent(
                key,
                card.getCardId());
            if (previous != null)
            {
                throw new IllegalArgumentException(
                    "Duplicate collectible display name for "
                        + card.getCardType() + ": "
                        + card.getDisplayName() + " ("
                        + previous + " and " + card.getCardId() + ").");
            }
        }
    }

    private Map<String, EntityFamily> indexFamilies(
        Collection<EntityFamily> families)
    {
        Objects.requireNonNull(families, "families");
        Map<String, EntityFamily> indexed = new LinkedHashMap<>();
        for (EntityFamily family : families)
        {
            EntityFamily previous = indexed.put(family.getFamilyId(), family);
            if (previous != null)
            {
                throw new IllegalArgumentException(
                    "Duplicate family ID " + family.getFamilyId() + ".");
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    private Map<String, CardDefinition> indexCards(
        Collection<CardDefinition> cards)
    {
        Objects.requireNonNull(cards, "cards");
        Map<String, CardDefinition> indexed = new LinkedHashMap<>();
        for (CardDefinition card : cards)
        {
            CardDefinition previous = indexed.put(card.getCardId(), card);
            if (previous != null)
            {
                throw new IllegalArgumentException(
                    "Duplicate card ID " + card.getCardId() + ".");
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    private Map<String, HistoricalCardDefinition> indexHistoricalCards(
        Collection<HistoricalCardDefinition> historicalCards)
    {
        Objects.requireNonNull(historicalCards, "historicalCards");
        Map<String, HistoricalCardDefinition> indexed = new LinkedHashMap<>();
        for (HistoricalCardDefinition historicalCard : historicalCards)
        {
            Objects.requireNonNull(
                historicalCard,
                "historicalCards cannot contain null");
            String cardId = historicalCard.getCardId();
            if (cardsById.containsKey(cardId))
            {
                throw new IllegalArgumentException(
                    "Historical card remains active: " + cardId + ".");
            }
            if (cardIdAliases.asMap().containsKey(cardId))
            {
                throw new IllegalArgumentException(
                    "Historical card must not also be an alias: "
                        + cardId + ".");
            }
            if (indexed.put(cardId, historicalCard) != null)
            {
                throw new IllegalArgumentException(
                    "Duplicate historical card ID " + cardId + ".");
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    private Map<Rarity, List<CardDefinition>> indexByRarity(
        Collection<CardDefinition> cards)
    {
        Map<Rarity, List<CardDefinition>> indexed =
            new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values())
        {
            indexed.put(rarity, new ArrayList<>());
        }
        for (CardDefinition card : cards)
        {
            indexed.get(card.getRarity()).add(card);
        }
        return immutableEnumLists(indexed, Rarity.class);
    }

    private Map<CardType, List<CardDefinition>> indexByType(
        Collection<CardDefinition> cards)
    {
        Map<CardType, List<CardDefinition>> indexed =
            new EnumMap<>(CardType.class);
        for (CardType cardType : CardType.values())
        {
            indexed.put(cardType, new ArrayList<>());
        }
        for (CardDefinition card : cards)
        {
            indexed.get(card.getCardType()).add(card);
        }
        return immutableEnumLists(indexed, CardType.class);
    }

    private Map<CardCategory, List<CardDefinition>> indexByCategory(
        Collection<CardDefinition> cards)
    {
        Map<CardCategory, List<CardDefinition>> indexed =
            new EnumMap<>(CardCategory.class);
        for (CardCategory category : CardCategory.values())
        {
            indexed.put(category, new ArrayList<>());
        }
        for (CardDefinition card : cards)
        {
            for (CardCategory category : card.getCategories())
            {
                indexed.get(category).add(card);
            }
        }
        return immutableEnumLists(indexed, CardCategory.class);
    }

    private Map<Integer, List<CardDefinition>> indexByIntroducedVersion(
        Collection<CardDefinition> cards)
    {
        Map<Integer, List<CardDefinition>> indexed = new LinkedHashMap<>();
        for (CardDefinition card : cards)
        {
            indexed.computeIfAbsent(
                card.getCatalogueVersionIntroduced(),
                ignored -> new ArrayList<>()).add(card);
        }
        Map<Integer, List<CardDefinition>> immutable = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<CardDefinition>> entry
            : indexed.entrySet())
        {
            immutable.put(
                entry.getKey(),
                Collections.unmodifiableList(
                    new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(immutable);
    }

    private List<CardDefinition> membershipCards(boolean freeToPlay)
    {
        List<CardDefinition> cards = new ArrayList<>();
        for (CardDefinition card : cardsById.values())
        {
            if (card.isFreeToPlay() == freeToPlay)
            {
                cards.add(card);
            }
        }
        return Collections.unmodifiableList(cards);
    }

    private Map<String, String> indexSearchText(
        Collection<CardDefinition> cards)
    {
        Map<String, String> indexed = new LinkedHashMap<>();
        for (CardDefinition card : cards)
        {
            StringBuilder searchable = new StringBuilder();
            searchable.append(card.getCardId()).append(' ')
                .append(card.getDisplayName()).append(' ')
                .append(card.getExamineText()).append(' ')
                .append(card.getCardType().name()).append(' ')
                .append(card.getRarity().name()).append(' ')
                .append(card.getEntityFamilyId()).append(' ')
                .append(card.isFreeToPlay() ? "free to play f2p" : "members p2p");
            for (CardCategory category : card.getCategories())
            {
                searchable.append(' ').append(category.name());
            }
            indexed.put(
                card.getCardId(),
                CardSearchQuery.normalize(searchable.toString()));
        }
        return Collections.unmodifiableMap(indexed);
    }

    private static <E extends Enum<E>> Map<E, List<CardDefinition>>
        immutableEnumLists(
            Map<E, List<CardDefinition>> source,
            Class<E> enumType)
    {
        Map<E, List<CardDefinition>> immutable = new EnumMap<>(enumType);
        for (Map.Entry<E, List<CardDefinition>> entry : source.entrySet())
        {
            immutable.put(
                entry.getKey(),
                Collections.unmodifiableList(
                    new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(immutable);
    }
}
