package com.cardrestricted.points;

import java.util.Objects;

public final class F2pQuestCompletionRewardPolicy
{
    public static final String SOURCE_PREFIX =
        "f2p-quest-completion:v1:";
    public static final long REWARD_POINTS = 500L;

    public PointAward createAward(
        QuestCompletionObservation observation)
    {
        Objects.requireNonNull(observation, "observation");
        return new PointAward(
            sourceId(observation.getQuestKey()),
            PointSourceType.MILESTONE,
            REWARD_POINTS,
            observation.getCompletedAt());
    }

    public String sourceId(String questKey)
    {
        Objects.requireNonNull(questKey, "questKey");
        if (questKey.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                "questKey cannot be blank.");
        }
        return SOURCE_PREFIX + questKey;
    }
}
