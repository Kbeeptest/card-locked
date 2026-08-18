package com.cardrestricted.runelite;

/**
 * Beta compatibility escape hatch for interaction metadata that RuneLite or
 * another menu plugin could not reconcile to an exact tracked identity.
 *
 * <p>Known resolved locked content is still evaluated normally. Integrity
 * profiles deliberately remain fail-closed.</p>
 */
public final class UnverifiedActionCompatibilityRules
{
    private UnverifiedActionCompatibilityRules()
    {
    }

    public static boolean mayBypass(
        boolean compatibilityEnabled,
        boolean integrityProfile)
    {
        return compatibilityEnabled && !integrityProfile;
    }
}
