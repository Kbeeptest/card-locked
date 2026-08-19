package com.cardrestricted.runelite;

import java.util.Locale;
import net.runelite.api.gameval.InterfaceID;

/**
 * Identifies transaction confirmations that can move or consume items even
 * when the confirmation button itself contains no item metadata.
 */
public final class TransactionInterfaceIntegrityRules
{
    private TransactionInterfaceIntegrityRules()
    {
    }

    public static boolean isPlayerTradeConfirmation(
        String option,
        int... packedWidgetIds)
    {
        String value = normalise(option);
        return containsGroup(
                InterfaceID.TRADEMAIN,
                packedWidgetIds)
            && (startsWith(value, "accept")
                || startsWith(value, "confirm"));
    }

    public static boolean isGrandExchangeSubmission(
        String option,
        int... packedWidgetIds)
    {
        String value = normalise(option);
        if (!containsGroup(InterfaceID.GE_OFFERS, packedWidgetIds)
            && !containsGroup(
                InterfaceID.GE_OFFERS_SIDE,
                packedWidgetIds))
        {
            return false;
        }
        return startsWith(value, "confirm")
            || startsWith(value, "offer")
            || startsWith(value, "buy")
            || startsWith(value, "sell");
    }

    static boolean containsGroup(int groupId, int... packedWidgetIds)
    {
        if (packedWidgetIds == null)
        {
            return false;
        }
        for (int packedWidgetId : packedWidgetIds)
        {
            if (packedWidgetId >= 0 && packedWidgetId >>> 16 == groupId)
            {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(String value, String prefix)
    {
        return value.equals(prefix)
            || value.startsWith(prefix + " ")
            || value.startsWith(prefix + "-");
    }

    private static String normalise(String value)
    {
        return value == null
            ? ""
            : value.replaceAll("<[^>]*>", "")
                .replace('\u00a0', ' ')
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
