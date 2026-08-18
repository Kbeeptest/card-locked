package com.cardrestricted.points;

import java.time.Instant;
import java.util.Objects;

public final class NoncombatXpObservation
{
    private final NoncombatSkill skill;
    private final long sessionBaselineXp;
    private final long totalXp;
    private final Instant occurredAt;

    public NoncombatXpObservation(
        NoncombatSkill skill,
        long sessionBaselineXp,
        long totalXp,
        Instant occurredAt)
    {
        this.skill = Objects.requireNonNull(skill, "skill");
        if (sessionBaselineXp < 0 || totalXp < sessionBaselineXp)
        {
            throw new IllegalArgumentException(
                "Noncombat XP totals are invalid.");
        }
        this.sessionBaselineXp = sessionBaselineXp;
        this.totalXp = totalXp;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public NoncombatSkill getSkill()
    {
        return skill;
    }

    public long getSessionBaselineXp()
    {
        return sessionBaselineXp;
    }

    public long getTotalXp()
    {
        return totalXp;
    }

    public Instant getOccurredAt()
    {
        return occurredAt;
    }
}
