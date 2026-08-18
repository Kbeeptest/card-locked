package com.cardrestricted.collection;

import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.starter.StarterRewardChoice;
import java.util.Objects;

public final class CollectionCreationResult
{
    private final CollectionState state;
    private final StarterRewardChoice starterRewardChoice;

    public CollectionCreationResult(
        CollectionState state,
        StarterRewardChoice starterRewardChoice)
    {
        this.state = Objects.requireNonNull(state, "state");
        this.starterRewardChoice = Objects.requireNonNull(
            starterRewardChoice,
            "starterRewardChoice");
    }

    public CollectionState getState()
    {
        return state;
    }

    public StarterRewardChoice getStarterRewardChoice()
    {
        return starterRewardChoice;
    }
}
