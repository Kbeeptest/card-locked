package com.cardrestricted.runelite;

import java.util.Locale;
import net.runelite.api.MenuAction;

/** Determines whether worn locked equipment could affect the requested action. */
public final class EquippedItemActionRules
{
    private EquippedItemActionRules()
    {
    }

    public static boolean shouldGate(
        MenuAction action,
        String option,
        boolean directSpellbookAction,
        boolean safeStorageRecovery)
    {
        if (safeStorageRecovery)
        {
            return false;
        }
        String value = normalise(option);
        if (value.equals("remove")
            || value.equals("unequip")
            || value.equals("unwear"))
        {
            return false;
        }
        if (action == MenuAction.WALK && value.equals("walk here"))
        {
            return false;
        }
        if (InteractionIntegrityRules.isGenericClientOrWidgetAction(action))
        {
            return InteractionIntegrityRules.shouldGateAmbiguousWidgetAction(
                value,
                directSpellbookAction);
        }
        return InteractionIntegrityRules.isFunctionalAction(
            action,
            value,
            directSpellbookAction);
    }

    private static String normalise(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replaceAll("<[^>]*>", "")
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}
