package com.cardrestricted.runelite;

import com.cardrestricted.domain.IntegrityMode;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DeveloperTestingRulesTest
{
    @Test
    public void integrityProfilesCannotReceiveDeveloperBalancesOrPacks()
    {
        assertFalse(DeveloperTestingRules.isAllowed(
            true,
            IntegrityMode.INTEGRITY));
        assertTrue(DeveloperTestingRules.isAllowed(
            true,
            IntegrityMode.CASUAL));
        assertFalse(DeveloperTestingRules.isAllowed(
            false,
            IntegrityMode.CASUAL));
        assertFalse(DeveloperTestingRules.isAllowed(
            false,
            null));
    }

    @Test
    public void verifiedDevelopmentRuntimeCanUseDisposableUnmarkedProfile()
    {
        assertTrue(DeveloperTestingRules.isAllowed(true, null));
    }
}
