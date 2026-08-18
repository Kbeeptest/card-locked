package com.cardrestricted.collection.achievement;

import com.cardrestricted.persistence.CollectionState;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class AchievementCompletionState
{
    public static final String TRACKING_MARKER =
        "achievement.tracking.v1";
    private static final String COMPLETION_PREFIX =
        "achievement.completed.";
    private static final String COMPLETION_SUFFIX = ".v1";

    private AchievementCompletionState()
    {
    }

    public static boolean isTrackingInitialised(CollectionState state)
    {
        Objects.requireNonNull(state, "state");
        return state.getClaimedPointSourceIds().contains(TRACKING_MARKER);
    }

    public static boolean isAchievementMarker(String sourceId)
    {
        return TRACKING_MARKER.equals(sourceId)
            || (sourceId != null
                && sourceId.startsWith(COMPLETION_PREFIX)
                && sourceId.endsWith(COMPLETION_SUFFIX));
    }

    public static String completionMarker(String achievementId)
    {
        String id = requireAchievementId(achievementId);
        return COMPLETION_PREFIX + id + COMPLETION_SUFFIX;
    }

    public static boolean isCompleted(
        CollectionState state,
        String achievementId)
    {
        Objects.requireNonNull(state, "state");
        return state.getClaimedPointSourceIds().contains(
            completionMarker(achievementId));
    }

    public static Set<String> completedAchievementIds(
        CollectionState state)
    {
        Objects.requireNonNull(state, "state");
        Set<String> result = new LinkedHashSet<>();
        for (String marker : state.getClaimedPointSourceIds())
        {
            if (marker.startsWith(COMPLETION_PREFIX)
                && marker.endsWith(COMPLETION_SUFFIX))
            {
                result.add(marker.substring(
                    COMPLETION_PREFIX.length(),
                    marker.length() - COMPLETION_SUFFIX.length()));
            }
        }
        return Set.copyOf(result);
    }

    private static String requireAchievementId(String value)
    {
        Objects.requireNonNull(value, "achievementId");
        String id = value.trim();
        if (id.isEmpty())
        {
            throw new IllegalArgumentException(
                "achievementId cannot be blank.");
        }
        return id;
    }
}
