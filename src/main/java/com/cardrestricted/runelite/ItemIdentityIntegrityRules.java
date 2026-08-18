package com.cardrestricted.runelite;

import java.util.Set;

/** Fail-closed handling for item actions whose participating item vanished. */
public final class ItemIdentityIntegrityRules
{
    private ItemIdentityIntegrityRules()
    {
    }

    public static boolean shouldBlockUnresolvedItemAction(
        boolean itemContext,
        Set<Integer> resolvedItemIds,
        Set<String> resolvedItemNames,
        String option,
        boolean allowBanking,
        boolean equipmentRemoval)
    {
        if (!itemContext
            || SimpleRestrictionService.isSafeItemOption(
                option,
                allowBanking,
                equipmentRemoval))
        {
            return false;
        }
        return (resolvedItemIds == null || resolvedItemIds.isEmpty())
            && (resolvedItemNames == null || resolvedItemNames.isEmpty());
    }

    /** Compatibility bypasses are deliberately unavailable to integrity profiles. */
    public static boolean mayBypassUnresolvedItemBlock(
        boolean compatibilityEnabled,
        boolean integrityProfile)
    {
        return UnverifiedActionCompatibilityRules.mayBypass(
            compatibilityEnabled,
            integrityProfile);
    }
}
