package com.cardrestricted.points;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Corroborates local-player NPC kills without relying on loot events, while
 * retaining a deduplicated loot fallback for encounters with unusual death
 * signalling.
 */
public final class NpcKillCreditTracker
{
    static final int CREDIT_WINDOW_TICKS = 7;
    private static final int RETENTION_TICKS = 20;

    private final Map<Long, Evidence> evidenceByNpc = new HashMap<>();
    private final Map<Long, Integer> creditedAtTickByNpc = new HashMap<>();

    public synchronized void observeInteraction(
        int npcIndex,
        int npcId,
        int tick)
    {
        evidence(npcIndex, npcId).interactionTick = tick;
    }

    public synchronized void observePlayerHitsplat(
        int npcIndex,
        int npcId,
        int tick)
    {
        evidence(npcIndex, npcId).playerHitTick = tick;
    }

    public synchronized boolean qualifyDeath(
        int npcIndex,
        int npcId,
        int tick)
    {
        long key = key(npcIndex, npcId);
        if (recentlyCredited(key, tick))
        {
            return false;
        }
        Evidence evidence = evidenceByNpc.remove(key);
        if (evidence == null
            || !withinWindow(evidence.interactionTick, tick)
            || !withinWindow(evidence.playerHitTick, tick))
        {
            return false;
        }
        creditedAtTickByNpc.put(key, tick);
        return true;
    }

    public synchronized boolean qualifyLootFallback(
        int npcIndex,
        int npcId,
        int tick)
    {
        long key = key(npcIndex, npcId);
        if (recentlyCredited(key, tick))
        {
            return false;
        }
        evidenceByNpc.remove(key);
        creditedAtTickByNpc.put(key, tick);
        return true;
    }

    public synchronized void prune(int tick)
    {
        removeExpiredEvidence(tick);
        Iterator<Map.Entry<Long, Integer>> credited =
            creditedAtTickByNpc.entrySet().iterator();
        while (credited.hasNext())
        {
            if (tick - credited.next().getValue() > RETENTION_TICKS)
            {
                credited.remove();
            }
        }
    }

    public synchronized void clear()
    {
        evidenceByNpc.clear();
        creditedAtTickByNpc.clear();
    }

    private Evidence evidence(int npcIndex, int npcId)
    {
        return evidenceByNpc.computeIfAbsent(
            key(npcIndex, npcId),
            ignored -> new Evidence());
    }

    private boolean recentlyCredited(long key, int tick)
    {
        Integer creditedAt = creditedAtTickByNpc.get(key);
        return creditedAt != null
            && tick - creditedAt <= RETENTION_TICKS;
    }

    private void removeExpiredEvidence(int tick)
    {
        Iterator<Map.Entry<Long, Evidence>> evidence =
            evidenceByNpc.entrySet().iterator();
        while (evidence.hasNext())
        {
            Evidence value = evidence.next().getValue();
            int latest = Math.max(value.interactionTick, value.playerHitTick);
            if (latest == Integer.MIN_VALUE
                || tick - latest > RETENTION_TICKS)
            {
                evidence.remove();
            }
        }
    }

    private static boolean withinWindow(int observedTick, int currentTick)
    {
        int elapsed = currentTick - observedTick;
        return observedTick != Integer.MIN_VALUE
            && elapsed >= 0
            && elapsed <= CREDIT_WINDOW_TICKS;
    }

    private static long key(int npcIndex, int npcId)
    {
        return ((long) npcIndex << 32) ^ (npcId & 0xffffffffL);
    }

    private static final class Evidence
    {
        private int interactionTick = Integer.MIN_VALUE;
        private int playerHitTick = Integer.MIN_VALUE;
    }
}
