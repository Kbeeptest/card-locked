package com.cardrestricted.points;

import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.CommittedStateRecovery;
import com.cardrestricted.persistence.JournalEventType;
import com.cardrestricted.persistence.TransactionalStateStore;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;

public final class PointsLedgerService
{
    private final TransactionalStateStore stateStore;

    public PointsLedgerService(TransactionalStateStore stateStore)
    {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
    }

    public synchronized CollectionState award(PointAward award)
        throws IOException
    {
        Objects.requireNonNull(award, "award");
        CollectionState current = stateStore.loadHighestValid()
            .orElseThrow(() -> new IllegalStateException(
                "A collection must exist before points can be awarded."));
        return award(current, award);
    }


    public synchronized CollectionState awardAll(
        CollectionState current,
        List<PointAward> awards)
        throws IOException
    {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(awards, "awards");
        if (awards.isEmpty())
        {
            return current;
        }

        Set<String> sourceIds = new LinkedHashSet<>();
        long totalAmount = 0L;
        PointAward lastApplied = null;
        for (PointAward award : awards)
        {
            Objects.requireNonNull(award, "award");
            if (current.getClaimedPointSourceIds().contains(award.getSourceId())
                || !sourceIds.add(award.getSourceId()))
            {
                continue;
            }
            totalAmount = Math.addExact(totalAmount, award.getAmount());
            lastApplied = award;
        }
        if (sourceIds.isEmpty())
        {
            throw new DuplicatePointAwardException(
                awards.get(0).getSourceId());
        }

        CollectionState updated = current.withPointsAwardedBatch(
            sourceIds,
            totalAmount);
        String payload = "sourceId=batch:" + sourceIds.size()
            + ";sourceType=" + (lastApplied == null
                ? PointSourceType.NPC_KILL.name()
                : lastApplied.getSourceType().name())
            + ";amount=" + totalAmount
            + ";count=" + sourceIds.size();
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                JournalEventType.POINTS_AWARDED,
                payload,
                lastApplied == null
                    ? java.time.Instant.now()
                    : lastApplied.getOccurredAt());
        }
        catch (IOException failure)
        {
            updated = CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
        return updated;
    }

    public synchronized CollectionState award(
        CollectionState current,
        PointAward award)
        throws IOException
    {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(award, "award");
        if (current.getClaimedPointSourceIds()
            .contains(award.getSourceId()))
        {
            throw new DuplicatePointAwardException(award.getSourceId());
        }

        CollectionState updated = current.withPointsAwarded(
            award.getSourceId(),
            award.getAmount());
        String payload = "sourceId=" + award.getSourceId()
            + ";sourceType=" + award.getSourceType().name()
            + ";amount=" + award.getAmount();
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                JournalEventType.POINTS_AWARDED,
                payload,
                award.getOccurredAt());
        }
        catch (IOException failure)
        {
            updated = CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
        return updated;
    }
}
