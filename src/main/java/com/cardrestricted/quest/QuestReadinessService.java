package com.cardrestricted.quest;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.foil.FoilEntitlementResolver;
import com.cardrestricted.foil.FoilEntitlementSnapshot;
import com.cardrestricted.persistence.CollectionState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class QuestReadinessService
{
    private final CardCatalogue catalogue;
    private final QuestRequirementRegistry registry;
    private final FoilEntitlementResolver foilEntitlementResolver;
    private final Map<String, String> searchTextByQuestKey;

    public QuestReadinessService(
        CardCatalogue catalogue,
        QuestRequirementRegistry registry)
    {
        this(catalogue, registry, null);
    }

    public QuestReadinessService(
        CardCatalogue catalogue,
        QuestRequirementRegistry registry,
        FoilEntitlementResolver foilEntitlementResolver)
    {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.foilEntitlementResolver = foilEntitlementResolver;
        this.searchTextByQuestKey = indexSearchText(catalogue, registry);
    }

    public QuestReadinessSnapshot calculate(CollectionState state)
    {
        return calculate(state, Collections.emptySet());
    }

    public QuestReadinessSnapshot calculate(
        CollectionState state,
        Set<String> completedQuestKeys)
    {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(completedQuestKeys, "completedQuestKeys");
        Set<String> owned = catalogue.canonicalizeCardIds(
            state.getOwnedCardIds());
        Set<String> foilUnlocked = Collections.emptySet();
        Set<String> available = owned;
        if (foilEntitlementResolver != null)
        {
            FoilEntitlementSnapshot entitlements =
                foilEntitlementResolver.resolve(
                    state.getOwnedCardIds(),
                    state.getFoilCardIds());
            owned = entitlements.getOwnedCardIds();
            foilUnlocked = entitlements.getDerivedCardIds();
            available = entitlements.getUsableCardIds();
        }

        List<QuestReadinessEntry> entries = new ArrayList<>();
        int completeCount = 0;
        int readyCount = 0;
        int blockedCount = 0;
        for (QuestTrackerDefinition quest : registry.getQuests())
        {
            RequirementEvaluation items = evaluate(
                quest.getItemRequirementGroups(),
                owned,
                foilUnlocked,
                available);
            RequirementEvaluation combat = evaluate(
                quest.getCombatRequirementGroups(),
                owned,
                foilUnlocked,
                available);
            QuestStatus status;
            if (completedQuestKeys.contains(quest.getQuestKey()))
            {
                status = QuestStatus.COMPLETE;
                completeCount++;
            }
            else if (items.ready && combat.ready)
            {
                status = QuestStatus.READY;
                readyCount++;
            }
            else
            {
                status = QuestStatus.BLOCKED;
                blockedCount++;
            }
            entries.add(new QuestReadinessEntry(
                quest,
                status,
                items.ready,
                combat.ready,
                items.ownedCardIds,
                items.foilUnlockedCardIds,
                items.missingCardIds,
                items.allCardIds.size(),
                combat.ownedCardIds,
                combat.foilUnlockedCardIds,
                combat.missingCardIds,
                combat.allCardIds.size(),
                searchTextByQuestKey.getOrDefault(
                    quest.getQuestKey(),
                    quest.getQuestName().toLowerCase(Locale.ROOT))));
        }
        return new QuestReadinessSnapshot(
            entries, completeCount, readyCount, blockedCount);
    }

    private static Map<String, String> indexSearchText(
        CardCatalogue catalogue,
        QuestRequirementRegistry registry)
    {
        Map<String, String> indexed = new LinkedHashMap<>();
        for (QuestTrackerDefinition quest : registry.getQuests())
        {
            StringBuilder text = new StringBuilder(
                quest.getQuestName().toLowerCase(Locale.ROOT));
            Set<String> cardIds = new LinkedHashSet<>();
            for (QuestRequirementGroup group : quest.getRequirementGroups())
            {
                cardIds.addAll(group.getCardIds());
            }
            for (String cardId : cardIds)
            {
                text.append('\n').append(
                    catalogue.requireCard(cardId).getDisplayName()
                        .toLowerCase(Locale.ROOT));
            }
            indexed.put(quest.getQuestKey(), text.toString());
        }
        return Collections.unmodifiableMap(indexed);
    }

    private static RequirementEvaluation evaluate(
        List<QuestRequirementGroup> groups,
        Set<String> owned,
        Set<String> foilUnlocked,
        Set<String> available)
    {
        Set<String> allCards = new LinkedHashSet<>();
        Set<String> missing = new LinkedHashSet<>();
        boolean ready = true;
        for (QuestRequirementGroup group : groups)
        {
            allCards.addAll(group.getCardIds());
            if (group.getMode() == QuestRequirementMode.ANY)
            {
                boolean groupReady = group.getCardIds().stream()
                    .anyMatch(available::contains);
                if (!groupReady)
                {
                    ready = false;
                    missing.addAll(group.getCardIds());
                }
            }
            else
            {
                for (String cardId : group.getCardIds())
                {
                    if (!available.contains(cardId))
                    {
                        ready = false;
                        missing.add(cardId);
                    }
                }
            }
        }
        List<String> ownedCards = new ArrayList<>();
        List<String> foilCards = new ArrayList<>();
        for (String cardId : allCards)
        {
            if (owned.contains(cardId))
            {
                ownedCards.add(cardId);
            }
            else if (foilUnlocked.contains(cardId))
            {
                foilCards.add(cardId);
            }
        }
        return new RequirementEvaluation(
            ready,
            new ArrayList<>(allCards),
            ownedCards,
            foilCards,
            new ArrayList<>(missing));
    }

    private static final class RequirementEvaluation
    {
        private final boolean ready;
        private final List<String> allCardIds;
        private final List<String> ownedCardIds;
        private final List<String> foilUnlockedCardIds;
        private final List<String> missingCardIds;

        private RequirementEvaluation(
            boolean ready,
            List<String> allCardIds,
            List<String> ownedCardIds,
            List<String> foilUnlockedCardIds,
            List<String> missingCardIds)
        {
            this.ready = ready;
            this.allCardIds = allCardIds;
            this.ownedCardIds = ownedCardIds;
            this.foilUnlockedCardIds = foilUnlockedCardIds;
            this.missingCardIds = missingCardIds;
        }
    }
}
