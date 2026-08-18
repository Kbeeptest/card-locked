package com.cardrestricted.points;

import com.cardrestricted.persistence.CollectionState;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class F2pNpcKillRewardPolicy
{
    public static final int LUMBRIDGE_REGION_ID = 12850;
    public static final int GOBLIN_NPC_ID = 3028;
    public static final String GOBLIN_CARD_ID = "npc.goblin";
    public static final int GOBLIN_COMBAT_LEVEL = 2;
    public static final long GOBLIN_POINTS = GOBLIN_COMBAT_LEVEL;

    public Optional<PointAward> createAward(
        CollectionState state,
        NpcKillObservation observation)
    {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(observation, "observation");
        if (observation.getCombatLevel() <= 0)
        {
            return Optional.empty();
        }

        String sourceId = String.format(
            Locale.ROOT,
            "npc-kill:v2:%s:%d:%d:%d:%d",
            observation.getGameSessionId(),
            observation.getWorld(),
            observation.getTick(),
            observation.getNpcIndex(),
            observation.getNpcId());
        return Optional.of(new PointAward(
            sourceId,
            PointSourceType.NPC_KILL,
            observation.getCombatLevel(),
            observation.getOccurredAt()));
    }
}
