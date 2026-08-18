package com.cardrestricted.runelite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UnverifiedActionCompatibilityRulesTest
{
    @Test
    public void betaCompatibilityIsAvailableOnlyToNonIntegrityProfiles()
    {
        assertTrue(UnverifiedActionCompatibilityRules.mayBypass(true, false));
        assertFalse(UnverifiedActionCompatibilityRules.mayBypass(false, false));
        assertFalse(UnverifiedActionCompatibilityRules.mayBypass(true, true));
    }
}
