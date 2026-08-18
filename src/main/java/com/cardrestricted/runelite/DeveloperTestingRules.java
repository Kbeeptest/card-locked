package com.cardrestricted.runelite;

import com.cardrestricted.domain.IntegrityMode;

/** Prevents developer economy shortcuts from entering release or integrity profiles. */
public final class DeveloperTestingRules
{
    private DeveloperTestingRules()
    {
    }

    public static boolean isAllowed(
        boolean verifiedDeveloperRuntime,
        IntegrityMode integrityMode)
    {
        return verifiedDeveloperRuntime
            && integrityMode != IntegrityMode.INTEGRITY;
    }
}
