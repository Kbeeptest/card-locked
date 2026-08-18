package com.cardrestricted.runelite;

import java.util.Collections;
import java.util.Set;

/** Prevents progression rewards from being earned through a locked-NPC bypass. */
public final class NpcRewardIntegrityRules
{
    private NpcRewardIntegrityRules()
    {
    }

    public static boolean mayAward(
        boolean enforcedNpcRestrictions,
        Set<String> requiredCardIds,
        Set<String> usableCardIds)
    {
        return mayAward(
            enforcedNpcRestrictions,
            false,
            requiredCardIds,
            usableCardIds);
    }

    /**
     * Ambiguous tracked identities fail closed when enforcement is active;
     * otherwise at least one card in the resolved NPC family must be usable.
     */
    public static boolean mayAward(
        boolean enforcedNpcRestrictions,
        boolean ambiguousTrackedIdentity,
        Set<String> requiredCardIds,
        Set<String> usableCardIds)
    {
        if (!enforcedNpcRestrictions)
        {
            return true;
        }
        if (requiredCardIds == null || requiredCardIds.isEmpty())
        {
            return !ambiguousTrackedIdentity;
        }
        Set<String> usable = usableCardIds == null
            ? Collections.emptySet()
            : usableCardIds;
        for (String cardId : requiredCardIds)
        {
            if (usable.contains(cardId))
            {
                return true;
            }
        }
        return false;
    }
}
