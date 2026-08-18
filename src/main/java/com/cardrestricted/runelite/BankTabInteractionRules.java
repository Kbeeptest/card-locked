package com.cardrestricted.runelite;

import java.util.Locale;

/**
 * Identifies bank-tab controls whose placeholder item metadata must never be
 * interpreted as an item interaction.
 */
public final class BankTabInteractionRules
{
    private BankTabInteractionRules()
    {
    }

    public static boolean isBankTabNavigation(String option)
    {
        return isBankTabNavigation(option, "");
    }

    public static boolean isBankTabNavigation(String option, String target)
    {
        String action = normalise(option);
        String subject = normalise(target);
        return isNavigationText(action) || isNavigationText(subject);
    }

    private static boolean isNavigationText(String value)
    {
        return value.equals("view all items")
            || value.equals("view tab")
            || value.startsWith("view tab ")
            || value.equals("collapse tab")
            || value.equals("new tab");
    }

    private static String normalise(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value
            .replaceAll("<[^>]*>", "")
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ");
    }
}
