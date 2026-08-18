package com.cardrestricted.runelite;

import java.util.Locale;
import net.runelite.api.MenuAction;

/**
 * Inventory items which can provide an automatic environmental benefit without
 * appearing as the selected item in a menu click.
 */
public final class PassiveInventoryUsageRules
{
    private PassiveInventoryUsageRules()
    {
    }

    public static boolean shouldCheckInventory(MenuAction action, String option)
    {
        String value = normalise(option);
        return action == MenuAction.WALK || value.equals("walk here")
            || value.startsWith("enter") || value.startsWith("cross")
            || value.startsWith("climb") || value.startsWith("descend")
            || value.startsWith("go-through") || value.startsWith("go through");
    }

    public static boolean isPotentialPassiveItem(String itemName)
    {
        return isPotentialPassiveItem(itemName, null);
    }

    public static boolean isPotentialPassiveItem(
        String itemName,
        String[] inventoryActions)
    {
        String value = normalise(itemName);
        if (value.isEmpty())
        {
            return false;
        }
        if (value.contains("waterskin")
            || value.equals("bruma torch")
            || value.equals("bruma torch (off-hand)")
            || value.equals("lit torch")
            || value.equals("lit candle")
            || value.equals("lit black candle")
            || value.equals("lit bug lantern"))
        {
            return true;
        }
        if (inventoryActions != null)
        {
            for (String action : inventoryActions)
            {
                if ("extinguish".equals(normalise(action)))
                {
                    return true;
                }
            }
        }
        return false;
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
