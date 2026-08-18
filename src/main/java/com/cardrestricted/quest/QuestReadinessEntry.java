package com.cardrestricted.quest;

import com.cardrestricted.catalog.CardCatalogue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class QuestReadinessEntry
{
    private final QuestTrackerDefinition definition;
    private final QuestStatus status;
    private final boolean itemReady;
    private final boolean combatReady;
    private final List<String> ownedItemCardIds;
    private final List<String> foilUnlockedItemCardIds;
    private final List<String> missingItemCardIds;
    private final List<String> ownedCombatCardIds;
    private final List<String> foilUnlockedCombatCardIds;
    private final List<String> missingCombatCardIds;
    private final int totalItemCards;
    private final int totalCombatCards;
    private final String searchText;

    QuestReadinessEntry(
        QuestTrackerDefinition definition,
        QuestStatus status,
        boolean itemReady,
        boolean combatReady,
        List<String> ownedItemCardIds,
        List<String> foilUnlockedItemCardIds,
        List<String> missingItemCardIds,
        int totalItemCards,
        List<String> ownedCombatCardIds,
        List<String> foilUnlockedCombatCardIds,
        List<String> missingCombatCardIds,
        int totalCombatCards,
        String searchText)
    {
        this.definition = definition;
        this.status = status;
        this.itemReady = itemReady;
        this.combatReady = combatReady;
        this.ownedItemCardIds = immutableCopy(ownedItemCardIds);
        this.foilUnlockedItemCardIds = immutableCopy(foilUnlockedItemCardIds);
        this.missingItemCardIds = immutableCopy(missingItemCardIds);
        this.ownedCombatCardIds = immutableCopy(ownedCombatCardIds);
        this.foilUnlockedCombatCardIds = immutableCopy(
            foilUnlockedCombatCardIds);
        this.missingCombatCardIds = immutableCopy(missingCombatCardIds);
        this.totalItemCards = totalItemCards;
        this.totalCombatCards = totalCombatCards;
        this.searchText = searchText == null ? "" : searchText;
    }

    private static List<String> immutableCopy(List<String> values)
    {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public QuestTrackerDefinition getDefinition()
    {
        return definition;
    }

    public QuestStatus getStatus()
    {
        return status;
    }

    public boolean isComplete()
    {
        return status == QuestStatus.COMPLETE;
    }

    public boolean isCardReady()
    {
        return itemReady && combatReady;
    }

    public boolean isItemReady()
    {
        return itemReady;
    }

    public boolean isCombatReady()
    {
        return combatReady;
    }

    /** Actual collection ownership only. */
    public int getOwnedItemCards()
    {
        return ownedItemCardIds.size();
    }

    public int getFoilUnlockedItemCards()
    {
        return foilUnlockedItemCardIds.size();
    }

    public int getAvailableItemCards()
    {
        return getOwnedItemCards() + getFoilUnlockedItemCards();
    }

    public int getTotalItemCards()
    {
        return totalItemCards;
    }

    /** Actual collection ownership only. */
    public int getOwnedCombatCards()
    {
        return ownedCombatCardIds.size();
    }

    public int getFoilUnlockedCombatCards()
    {
        return foilUnlockedCombatCardIds.size();
    }

    public int getAvailableCombatCards()
    {
        return getOwnedCombatCards() + getFoilUnlockedCombatCards();
    }

    public int getTotalCombatCards()
    {
        return totalCombatCards;
    }

    /** Compatibility count for actual ownership across both types. */
    public int getOwnedCards()
    {
        return getOwnedItemCards() + getOwnedCombatCards();
    }

    public int getFoilUnlockedCards()
    {
        return getFoilUnlockedItemCards() + getFoilUnlockedCombatCards();
    }

    public int getAvailableCards()
    {
        return getAvailableItemCards() + getAvailableCombatCards();
    }

    public int getTotalCards()
    {
        return totalItemCards + totalCombatCards;
    }

    public List<String> getOwnedItemCardIds()
    {
        return ownedItemCardIds;
    }

    public List<String> getFoilUnlockedItemCardIds()
    {
        return foilUnlockedItemCardIds;
    }

    public List<String> getAvailableItemCardIds()
    {
        return merge(ownedItemCardIds, foilUnlockedItemCardIds);
    }

    public List<String> getMissingItemCardIds()
    {
        return missingItemCardIds;
    }

    public List<String> getOwnedCombatCardIds()
    {
        return ownedCombatCardIds;
    }

    public List<String> getFoilUnlockedCombatCardIds()
    {
        return foilUnlockedCombatCardIds;
    }

    public List<String> getAvailableCombatCardIds()
    {
        return merge(ownedCombatCardIds, foilUnlockedCombatCardIds);
    }

    public List<String> getMissingCombatCardIds()
    {
        return missingCombatCardIds;
    }

    public List<String> getOwnedCardIds()
    {
        return merge(ownedItemCardIds, ownedCombatCardIds);
    }

    public List<String> getFoilUnlockedCardIds()
    {
        return merge(foilUnlockedItemCardIds, foilUnlockedCombatCardIds);
    }

    public List<String> getAvailableCardIds()
    {
        return merge(getAvailableItemCardIds(), getAvailableCombatCardIds());
    }

    public List<String> getMissingCardIds()
    {
        return merge(missingItemCardIds, missingCombatCardIds);
    }

    private static List<String> merge(List<String> first, List<String> second)
    {
        LinkedHashSet<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return Collections.unmodifiableList(new ArrayList<>(result));
    }

    public boolean matches(String query, CardCatalogue catalogue)
    {
        java.util.Objects.requireNonNull(catalogue, "catalogue");
        String lowered = query == null
            ? ""
            : query.trim().toLowerCase(java.util.Locale.ROOT);
        return matchesNormalized(lowered);
    }

    public boolean matchesNormalized(String loweredQuery)
    {
        String lowered = loweredQuery == null ? "" : loweredQuery;
        return lowered.isEmpty() || searchText.contains(lowered);
    }
}
