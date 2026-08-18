package com.cardrestricted.collection.achievement;

import com.cardrestricted.persistence.CollectionState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AchievementReconciliationResult
{
    private final CollectionState state;
    private final boolean baselineInitialised;
    private final List<AchievementDefinition> newlyCompleted;

    AchievementReconciliationResult(
        CollectionState state,
        boolean baselineInitialised,
        List<AchievementDefinition> newlyCompleted)
    {
        this.state = Objects.requireNonNull(state, "state");
        this.baselineInitialised = baselineInitialised;
        this.newlyCompleted = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(
                newlyCompleted,
                "newlyCompleted")));
    }

    public CollectionState getState()
    {
        return state;
    }

    public boolean isBaselineInitialised()
    {
        return baselineInitialised;
    }

    public List<AchievementDefinition> getNewlyCompleted()
    {
        return newlyCompleted;
    }
}
