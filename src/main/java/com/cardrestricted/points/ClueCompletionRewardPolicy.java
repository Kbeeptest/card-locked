package com.cardrestricted.points;

import java.util.Locale;
import java.util.Objects;

public final class ClueCompletionRewardPolicy
{
    public static final String SOURCE_PREFIX = "clue-completion:v1:";

    public PointAward createAward(ClueCompletionObservation observation)
    {
        Objects.requireNonNull(observation, "observation");
        String sourceId = String.format(
            Locale.ROOT,
            "%s%s:%d",
            SOURCE_PREFIX,
            observation.getTier().key(),
            observation.getCompletionCount());
        return new PointAward(
            sourceId,
            PointSourceType.CLUE_COMPLETION,
            observation.getTier().getPoints(),
            observation.getOccurredAt());
    }
}
