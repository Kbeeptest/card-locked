package com.cardrestricted.collection;

import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.domain.RestrictionPreset;
import com.cardrestricted.persistence.CollectionState;
import java.util.HashSet;
import java.util.Set;

public final class ProfileStateMarkers
{
    public static final String INTEGRITY_ELIGIBLE = "profile.integrity.eligible.v1";
    public static final String INTEGRITY_FORFEITED = "profile.integrity.forfeited.v1";
    public static final String LOCKED_VISUALS = "profile.visual.locked-markers.v1";
    private static final String PRESET_PREFIX = "profile.restriction-preset.";

    private ProfileStateMarkers() { }

    public static Set<String> initialMarkers(ProfileSetupOptions options)
    {
        Set<String> markers = new HashSet<>();
        markers.add(PRESET_PREFIX + options.getRestrictionPreset().name().toLowerCase());
        if (options.isLockedVisuals())
        {
            markers.add(LOCKED_VISUALS);
        }
        if (options.getIntegrityMode() == IntegrityMode.INTEGRITY)
        {
            markers.add(INTEGRITY_ELIGIBLE);
        }
        return markers;
    }

    public static RestrictionPreset restrictionPreset(CollectionState state)
    {
        for (RestrictionPreset preset : RestrictionPreset.values())
        {
            if (state.getClaimedPointSourceIds().contains(
                PRESET_PREFIX + preset.name().toLowerCase()))
            {
                return preset;
            }
        }
        return RestrictionPreset.BALANCED;
    }

    public static boolean isIntegrityProfile(CollectionState state)
    {
        return state.getIntegrityMode() == IntegrityMode.INTEGRITY
            && state.getClaimedPointSourceIds().contains(INTEGRITY_ELIGIBLE)
            && !state.getClaimedPointSourceIds().contains(INTEGRITY_FORFEITED);
    }

    public static boolean isIntegrityForfeited(CollectionState state)
    {
        return state.getClaimedPointSourceIds().contains(INTEGRITY_FORFEITED);
    }
}
