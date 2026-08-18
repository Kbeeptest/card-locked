package com.cardrestricted.collection.achievement;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.CommittedStateRecovery;
import com.cardrestricted.persistence.JournalEventType;
import com.cardrestricted.persistence.TransactionalStateStore;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class AchievementReconciliationService
{
    private final AchievementService achievementService;
    private final TransactionalStateStore stateStore;

    public AchievementReconciliationService(
        CardCatalogue catalogue,
        AchievementRegistry registry,
        TransactionalStateStore stateStore)
    {
        this.achievementService = new AchievementService(
            Objects.requireNonNull(catalogue, "catalogue"),
            Objects.requireNonNull(registry, "registry"));
        this.stateStore = Objects.requireNonNull(
            stateStore,
            "stateStore");
    }

    public AchievementReconciliationResult reconcile(
        CollectionState suppliedState,
        Instant occurredAt)
        throws IOException
    {
        Objects.requireNonNull(suppliedState, "suppliedState");
        Objects.requireNonNull(occurredAt, "occurredAt");
        CollectionState current = stateStore.loadHighestValid()
            .orElseThrow(() -> new IllegalStateException(
                "Achievement reconciliation requires a collection."));
        if (!current.getCollectionId().equals(
                suppliedState.getCollectionId())
            || current.getRevision() != suppliedState.getRevision())
        {
            throw new IllegalStateException(
                "Achievement reconciliation requires the current state.");
        }

        AchievementSnapshot snapshot = achievementService.calculate(current);
        List<AchievementDefinition> completed = snapshot.getProgress().stream()
            .filter(AchievementProgress::isCompleted)
            .map(AchievementProgress::getDefinition)
            .sorted(Comparator.comparing(
                AchievementDefinition::getAchievementId))
            .collect(Collectors.toList());

        boolean baseline = !AchievementCompletionState
            .isTrackingInitialised(current);
        List<AchievementDefinition> missing = completed.stream()
            .filter(definition -> !AchievementCompletionState.isCompleted(
                current,
                definition.getAchievementId()))
            .collect(Collectors.toList());

        if (!baseline && missing.isEmpty())
        {
            return new AchievementReconciliationResult(
                current,
                false,
                List.of());
        }

        Set<String> markers = new LinkedHashSet<>();
        if (baseline)
        {
            markers.add(AchievementCompletionState.TRACKING_MARKER);
        }
        for (AchievementDefinition definition : missing)
        {
            markers.add(AchievementCompletionState.completionMarker(
                definition.getAchievementId()));
        }

        CollectionState updated = current.withMarkersAdded(markers);
        String completedIds = missing.stream()
            .map(AchievementDefinition::getAchievementId)
            .collect(Collectors.joining(","));
        String payload = "mode=" + (baseline ? "baseline" : "earned")
            + ";completed=" + completedIds;
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                JournalEventType.ACHIEVEMENTS_RECONCILED,
                payload,
                occurredAt);
        }
        catch (IOException failure)
        {
            updated = CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
        return new AchievementReconciliationResult(
            updated,
            baseline,
            baseline ? List.of() : new ArrayList<>(missing));
    }
}
