package com.cardrestricted.collection;

import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.domain.RestrictionPreset;
import com.cardrestricted.starter.StarterRewardChoice;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProfileSetupCompatibilityTest
{
    @Test
    public void casualProfileCanOptIntoBetaCompatibility()
    {
        ProfileSetupOptions options = new ProfileSetupOptions(
            EconomyMode.STANDARD,
            StarterRewardChoice.POINTS,
            RestrictionPreset.BALANCED,
            true,
            IntegrityMode.CASUAL,
            true);
        assertTrue(options.isAllowUnverifiedActions());
    }

    @Test
    public void integrityProfileAlwaysFailsClosed()
    {
        ProfileSetupOptions options = new ProfileSetupOptions(
            EconomyMode.STANDARD,
            StarterRewardChoice.POINTS,
            RestrictionPreset.BALANCED,
            true,
            IntegrityMode.INTEGRITY,
            true);
        assertFalse(options.isAllowUnverifiedActions());
    }
}
