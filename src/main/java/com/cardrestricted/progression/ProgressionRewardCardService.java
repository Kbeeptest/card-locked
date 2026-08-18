package com.cardrestricted.progression;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.CommittedStateRecovery;
import com.cardrestricted.persistence.JournalEventType;
import com.cardrestricted.persistence.TransactionalStateStore;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Persists one-time card grants earned from the progression track. */
public final class ProgressionRewardCardService
{
    private final CardCatalogue catalogue;
    private final TransactionalStateStore stateStore;

    public ProgressionRewardCardService(
        CardCatalogue catalogue,
        TransactionalStateStore stateStore)
    {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
    }

    public CollectionState reconcile(
        CollectionState suppliedState,
        Instant occurredAt)
        throws IOException
    {
        Objects.requireNonNull(suppliedState, "suppliedState");
        Objects.requireNonNull(occurredAt, "occurredAt");
        CollectionState current = stateStore.loadHighestValid()
            .orElseThrow(() -> new IllegalStateException(
                "Progression reward reconciliation requires a collection."));
        if (!current.getCollectionId().equals(suppliedState.getCollectionId())
            || current.getRevision() != suppliedState.getRevision())
        {
            throw new IllegalStateException(
                "Progression reward reconciliation requires the current state.");
        }

        String cardId = ProgressionRewardCardPolicy.COINS_CARD_ID;
        catalogue.requireCard(cardId);
        if (current.getOwnedCardIds().contains(cardId)
            || !ProgressionMilestonePolicy.hasReached(
                catalogue,
                current,
                ProgressionMilestonePolicy.COINS))
        {
            return current;
        }

        Set<String> owned = new HashSet<>(current.getOwnedCardIds());
        owned.add(cardId);
        CollectionState updated = current.withProgress(
            current.getRevision() + 1,
            current.getPoints(),
            current.getShards(),
            owned,
            current.getFoilCardIds());
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                JournalEventType.PROGRESSION_REWARD_GRANTED,
                "cardId=" + cardId
                    + ";threshold=" + ProgressionMilestonePolicy.COINS,
                occurredAt);
        }
        catch (IOException failure)
        {
            updated = CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
        return updated;
    }
}
