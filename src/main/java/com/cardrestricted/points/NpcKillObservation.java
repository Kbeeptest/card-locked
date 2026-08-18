package com.cardrestricted.points;

import java.time.Instant;
import java.util.Objects;

public final class NpcKillObservation
{
    private final String gameSessionId;
    private final int world;
    private final int tick;
    private final int npcIndex;
    private final int npcId;
    private final int combatLevel;
    private final int regionId;
    private final Instant occurredAt;

    public NpcKillObservation(
        String gameSessionId,
        int world,
        int tick,
        int npcIndex,
        int npcId,
        int combatLevel,
        int regionId,
        Instant occurredAt)
    {
        Objects.requireNonNull(gameSessionId, "gameSessionId");
        if (gameSessionId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                "gameSessionId cannot be blank.");
        }
        if (world <= 0 || tick < 0 || npcIndex < 0 || npcId < 0
            || combatLevel <= 0
            || regionId < 0)
        {
            throw new IllegalArgumentException(
                "NPC kill observation values are invalid.");
        }
        this.gameSessionId = gameSessionId;
        this.world = world;
        this.tick = tick;
        this.npcIndex = npcIndex;
        this.npcId = npcId;
        this.combatLevel = combatLevel;
        this.regionId = regionId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public String getGameSessionId()
    {
        return gameSessionId;
    }

    public int getWorld()
    {
        return world;
    }

    public int getTick()
    {
        return tick;
    }

    public int getNpcIndex()
    {
        return npcIndex;
    }

    public int getNpcId()
    {
        return npcId;
    }

    public int getCombatLevel()
    {
        return combatLevel;
    }

    public int getRegionId()
    {
        return regionId;
    }

    public Instant getOccurredAt()
    {
        return occurredAt;
    }
}
