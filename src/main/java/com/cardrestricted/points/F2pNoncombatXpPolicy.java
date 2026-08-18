package com.cardrestricted.points;

import com.cardrestricted.persistence.CollectionState;
import java.util.Objects;

public final class F2pNoncombatXpPolicy
{
    public boolean isEligible(
        CollectionState state,
        NoncombatSkill skill)
    {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(skill, "skill");
        return true;
    }
}
