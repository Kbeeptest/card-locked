package com.cardrestricted.starter;

import com.cardrestricted.persistence.CollectionState;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class StarterRewardState
{
    public static final String PACK_CHOICE_MARKER =
        "starter.reward.pack.v1";
    public static final String POINTS_CHOICE_MARKER =
        "starter.reward.points.v1";
    public static final String PACK_REDEEMED_MARKER =
        "starter.reward.pack.redeemed.v1";
    public static final long POINTS_BONUS = 3_000L;

    private StarterRewardState()
    {
    }

    public static Set<String> initialMarkers(StarterRewardChoice choice)
    {
        Objects.requireNonNull(choice, "choice");
        return Collections.singleton(
            choice == StarterRewardChoice.RANDOMISED_PACK
                ? PACK_CHOICE_MARKER
                : POINTS_CHOICE_MARKER);
    }


    public static boolean isStarterRewardMarker(String sourceId)
    {
        return PACK_CHOICE_MARKER.equals(sourceId)
            || POINTS_CHOICE_MARKER.equals(sourceId)
            || PACK_REDEEMED_MARKER.equals(sourceId);
    }

    public static boolean choseRandomisedPack(CollectionState state)
    {
        return hasMarker(state, PACK_CHOICE_MARKER);
    }

    public static boolean chosePoints(CollectionState state)
    {
        return hasMarker(state, POINTS_CHOICE_MARKER);
    }

    public static boolean isStarterPackRedeemed(CollectionState state)
    {
        return hasMarker(state, PACK_REDEEMED_MARKER);
    }

    public static boolean hasPendingStarterPack(CollectionState state)
    {
        return choseRandomisedPack(state)
            && !isStarterPackRedeemed(state);
    }

    public static boolean isLegacyCollection(CollectionState state)
    {
        Objects.requireNonNull(state, "state");
        return !choseRandomisedPack(state) && !chosePoints(state);
    }

    private static boolean hasMarker(
        CollectionState state,
        String marker)
    {
        Objects.requireNonNull(state, "state");
        return state.getClaimedPointSourceIds().contains(marker);
    }
}
