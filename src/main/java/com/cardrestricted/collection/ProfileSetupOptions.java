package com.cardrestricted.collection;

import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.domain.RestrictionPreset;
import com.cardrestricted.starter.StarterRewardChoice;
import java.util.Objects;

public final class ProfileSetupOptions
{
    private final EconomyMode economyMode;
    private final StarterRewardChoice starterRewardChoice;
    private final RestrictionPreset restrictionPreset;
    private final boolean lockedVisuals;
    private final IntegrityMode integrityMode;
    private final boolean allowUnverifiedActions;

    public ProfileSetupOptions(
        EconomyMode economyMode,
        StarterRewardChoice starterRewardChoice,
        RestrictionPreset restrictionPreset,
        boolean lockedVisuals,
        IntegrityMode integrityMode)
    {
        this(
            economyMode,
            starterRewardChoice,
            restrictionPreset,
            lockedVisuals,
            integrityMode,
            false);
    }

    public ProfileSetupOptions(
        EconomyMode economyMode,
        StarterRewardChoice starterRewardChoice,
        RestrictionPreset restrictionPreset,
        boolean lockedVisuals,
        IntegrityMode integrityMode,
        boolean allowUnverifiedActions)
    {
        this.economyMode = Objects.requireNonNull(economyMode, "economyMode");
        this.starterRewardChoice = Objects.requireNonNull(starterRewardChoice, "starterRewardChoice");
        this.restrictionPreset = Objects.requireNonNull(restrictionPreset, "restrictionPreset");
        this.lockedVisuals = lockedVisuals;
        this.integrityMode = Objects.requireNonNull(integrityMode, "integrityMode");
        this.allowUnverifiedActions = allowUnverifiedActions
            && integrityMode != IntegrityMode.INTEGRITY;
    }

    public EconomyMode getEconomyMode() { return economyMode; }
    public StarterRewardChoice getStarterRewardChoice() { return starterRewardChoice; }
    public RestrictionPreset getRestrictionPreset() { return restrictionPreset; }
    public boolean isLockedVisuals() { return lockedVisuals; }
    public IntegrityMode getIntegrityMode() { return integrityMode; }
    public boolean isAllowUnverifiedActions() { return allowUnverifiedActions; }
}
