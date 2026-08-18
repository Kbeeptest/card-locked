package com.cardrestricted.points;

import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.CommittedStateRecovery;
import com.cardrestricted.persistence.JournalEventType;
import com.cardrestricted.persistence.TransactionalStateStore;
import java.io.IOException;
import java.util.Objects;

public final class NoncombatXpLedgerService
{
    public static final long REWARD_UNITS_PER_XP = 100L;
    public static final long XP_BATCH_SIZE = 1_000L;

    private final TransactionalStateStore stateStore;
    private final F2pNoncombatXpPolicy policy;

    public NoncombatXpLedgerService(
        TransactionalStateStore stateStore,
        F2pNoncombatXpPolicy policy)
    {
        this.stateStore = Objects.requireNonNull(
            stateStore, "stateStore");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public synchronized NoncombatXpProcessResult process(
        NoncombatXpObservation observation)
        throws IOException
    {
        Objects.requireNonNull(observation, "observation");
        CollectionState current = stateStore.loadHighestValid()
            .orElseThrow(() -> new IllegalStateException(
                "A collection must exist before XP can be processed."));
        return process(current, observation);
    }

    public synchronized NoncombatXpProcessResult process(
        CollectionState current,
        NoncombatXpObservation observation)
        throws IOException
    {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(observation, "observation");
        String skillKey = observation.getSkill().name();
        long watermark = current.getNoncombatXpWatermarks()
            .getOrDefault(
                skillKey,
                observation.getSessionBaselineXp());
        long effectivePreviousXp = Math.max(
            watermark,
            observation.getSessionBaselineXp());
        if (observation.getTotalXp() <= effectivePreviousXp)
        {
            return new NoncombatXpProcessResult(
                current,
                NoncombatXpResultStatus.DUPLICATE,
                0,
                0);
        }

        long xpDelta = observation.getTotalXp() - effectivePreviousXp;
        boolean eligible = policy.isEligible(
            current, observation.getSkill());
        long processedXp = (xpDelta / XP_BATCH_SIZE) * XP_BATCH_SIZE;
        if (processedXp == 0L)
        {
            return new NoncombatXpProcessResult(
                current,
                eligible
                    ? NoncombatXpResultStatus.ACCUMULATED
                    : NoncombatXpResultStatus.INELIGIBLE,
                0L,
                0L);
        }
        long processedTotalXp = effectivePreviousXp + processedXp;
        long eligibleXp = eligible ? processedXp : 0;
        CollectionState updated = current.withNoncombatXpProcessed(
            skillKey,
            processedTotalXp,
            eligibleXp,
            REWARD_UNITS_PER_XP);
        long awardedPoints = updated.getPoints() - current.getPoints();
        NoncombatXpResultStatus status;
        if (!eligible)
        {
            status = NoncombatXpResultStatus.INELIGIBLE;
        }
        else if (awardedPoints > 0)
        {
            status = NoncombatXpResultStatus.AWARDED;
        }
        else
        {
            status = NoncombatXpResultStatus.ACCUMULATED;
        }

        String payload = "skill=" + skillKey
            + ";fromXp=" + effectivePreviousXp
            + ";toXp=" + processedTotalXp
            + ";observedXp=" + observation.getTotalXp()
            + ";eligibleXp=" + eligibleXp
            + ";points=" + awardedPoints
            + ";status=" + status.name();
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                JournalEventType.NONCOMBAT_XP_PROCESSED,
                payload,
                observation.getOccurredAt());
        }
        catch (IOException failure)
        {
            updated = CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
        return new NoncombatXpProcessResult(
            updated,
            status,
            processedXp,
            awardedPoints);
    }
}
