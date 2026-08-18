package com.cardrestricted.collection.achievement;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardCategory;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.collection.progress.CollectionProgress;
import com.cardrestricted.collection.progress.CollectionProgressService;
import com.cardrestricted.collection.progress.CollectionProgressSnapshot;
import com.cardrestricted.persistence.CollectionState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AchievementService
{
    private final CardCatalogue catalogue;
    private final AchievementRegistry registry;
    private final List<AchievementDefinition> activeDefinitions;
    private final CollectionProgressService progressService;

    public AchievementService(
        CardCatalogue catalogue,
        AchievementRegistry registry)
    {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.progressService = new CollectionProgressService(catalogue);
        this.activeDefinitions = activeDefinitions();
    }

    public AchievementSnapshot calculate(CollectionState state)
    {
        Objects.requireNonNull(state, "state");
        return calculate(state, progressService.calculate(state));
    }

    public AchievementSnapshot calculate(
        CollectionState state,
        CollectionProgressSnapshot progressSnapshot)
    {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(progressSnapshot, "progressSnapshot");
        List<AchievementProgress> values = new ArrayList<>();
        for (AchievementDefinition definition : activeDefinitions)
        {
            CollectionProgress progress = progressFor(
                definition,
                progressSnapshot);
            int current;
            int required;
            switch (definition.getMeasure())
            {
                case FOIL_COUNT:
                    current = progress.getFoil();
                    required = definition.getTarget();
                    break;
                case COMPLETION_PERCENT:
                    current = progress.getOwned();
                    required = requiredForPercent(
                        progress.getTotal(),
                        definition.getTarget());
                    break;
                case OWNED_COUNT:
                default:
                    current = progress.getOwned();
                    required = definition.getTarget();
                    break;
            }
            values.add(new AchievementProgress(
                definition,
                progress.getTotal(),
                current,
                required));
        }
        return new AchievementSnapshot(values);
    }

    public List<AchievementDefinition> getDefinitions()
    {
        return activeDefinitions;
    }

    public List<CardDefinition> getTargetCards(
        AchievementDefinition definition)
    {
        Objects.requireNonNull(definition, "definition");
        switch (definition.getScope())
        {
            case OVERALL:
                return immutableCopy(catalogue.getCards());
            case RARITY:
                return catalogue.getCards(Rarity.valueOf(
                    definition.getScopeKey()));
            case CARD_TYPE:
                return catalogue.getCards(CardType.valueOf(
                    definition.getScopeKey()));
            case CATEGORY:
                return catalogue.getCards(CardCategory.valueOf(
                    definition.getScopeKey()));
            case ACCESS:
                return "F2P".equals(definition.getScopeKey())
                    ? catalogue.getFreeToPlayCards()
                    : catalogue.getMembersCards();
            case CATALOGUE_VERSION:
                return catalogue.getCardsIntroducedInVersion(
                    Integer.parseInt(definition.getScopeKey()));
            default:
                throw new IllegalStateException(
                    "Unsupported achievement scope "
                        + definition.getScope() + ".");
        }
    }

    public Set<String> getTargetCardIds(AchievementDefinition definition)
    {
        Set<String> cardIds = new HashSet<>();
        for (CardDefinition card : getTargetCards(definition))
        {
            cardIds.add(card.getCardId());
        }
        return Collections.unmodifiableSet(cardIds);
    }

    public Set<String> getMissingTargetCardIds(
        AchievementDefinition definition,
        CollectionState state)
    {
        Objects.requireNonNull(state, "state");
        Set<String> missing = new HashSet<>(getTargetCardIds(definition));
        missing.removeAll(catalogue.canonicalizeCardIds(
            state.getOwnedCardIds()));
        return Collections.unmodifiableSet(missing);
    }

    private CollectionProgress progressFor(
        AchievementDefinition definition,
        CollectionProgressSnapshot snapshot)
    {
        switch (definition.getScope())
        {
            case OVERALL:
                return snapshot.getOverall();
            case RARITY:
                return snapshot.getProgress(Rarity.valueOf(
                    definition.getScopeKey()));
            case CARD_TYPE:
                return snapshot.getProgress(CardType.valueOf(
                    definition.getScopeKey()));
            case CATEGORY:
                return snapshot.getProgress(CardCategory.valueOf(
                    definition.getScopeKey()));
            case ACCESS:
                return "F2P".equals(definition.getScopeKey())
                    ? snapshot.getFreeToPlay()
                    : snapshot.getMembers();
            case CATALOGUE_VERSION:
                return snapshot.getProgressForIntroducedVersion(
                    Integer.parseInt(definition.getScopeKey()));
            default:
                throw new IllegalStateException(
                    "Unsupported achievement scope "
                        + definition.getScope() + ".");
        }
    }

    private List<AchievementDefinition> activeDefinitions()
    {
        List<AchievementDefinition> active = new ArrayList<>();
        for (AchievementDefinition definition : registry.getDefinitions())
        {
            int total = getTargetCards(definition).size();
            if (total == 0)
            {
                continue;
            }
            if (definition.getMeasure()
                    != AchievementMeasure.COMPLETION_PERCENT
                && definition.getTarget() > total)
            {
                continue;
            }
            active.add(definition);
        }
        if (active.isEmpty())
        {
            throw new IllegalArgumentException(
                "The active catalogue does not support any achievements.");
        }
        return Collections.unmodifiableList(active);
    }

    private static int requiredForPercent(int total, int targetPercent)
    {
        return (total * targetPercent + 99) / 100;
    }

    private static List<CardDefinition> immutableCopy(
        java.util.Collection<CardDefinition> cards)
    {
        return Collections.unmodifiableList(new ArrayList<>(cards));
    }
}
