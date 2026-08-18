package com.cardrestricted.domain;

import java.util.Objects;

/** Immutable runtime configuration derived from a profile restriction preset. */
public final class RestrictionPresetSettings
{
    private final RestrictionMode restrictionMode;
    private final boolean restrictLockedItems;
    private final boolean restrictNpcCombat;
    private final boolean allowLockedItemBanking;

    private RestrictionPresetSettings(
        RestrictionMode restrictionMode,
        boolean restrictLockedItems,
        boolean restrictNpcCombat,
        boolean allowLockedItemBanking)
    {
        this.restrictionMode = restrictionMode;
        this.restrictLockedItems = restrictLockedItems;
        this.restrictNpcCombat = restrictNpcCombat;
        this.allowLockedItemBanking = allowLockedItemBanking;
    }

    public static RestrictionPresetSettings fromConfiguration(
        RestrictionMode restrictionMode,
        boolean restrictLockedItems,
        boolean restrictNpcCombat,
        boolean allowLockedItemBanking)
    {
        return new RestrictionPresetSettings(
            Objects.requireNonNull(restrictionMode, "restrictionMode"),
            restrictLockedItems,
            restrictNpcCombat,
            allowLockedItemBanking);
    }

    public static RestrictionPresetSettings forPreset(
        RestrictionPreset preset)
    {
        RestrictionPreset value = Objects.requireNonNull(preset, "preset");
        boolean collectionOnly = value == RestrictionPreset.COLLECTION_ONLY;
        boolean strict = value == RestrictionPreset.STRICT;
        return new RestrictionPresetSettings(
            collectionOnly ? RestrictionMode.AUDIT_ONLY : RestrictionMode.ENFORCE,
            !collectionOnly,
            !collectionOnly,
            !strict);
    }

    public RestrictionMode getRestrictionMode()
    {
        return restrictionMode;
    }

    public boolean isRestrictLockedItems()
    {
        return restrictLockedItems;
    }

    public boolean isRestrictNpcCombat()
    {
        return restrictNpcCombat;
    }

    public boolean isAllowLockedItemBanking()
    {
        return allowLockedItemBanking;
    }
}
