package com.cardrestricted.runelite;

import java.util.Locale;

/**
 * Narrow guard for direct furnace interactions that do not expose recipe item
 * IDs through the clicked menu entry.
 */
public final class FurnaceInteractionRules
{
    private FurnaceInteractionRules()
    {
    }

    public static boolean isFurnaceInteraction(String option, String target)
    {
        String action = normalise(option);
        String subject = normalise(target);
        if (!subject.contains("furnace"))
        {
            return false;
        }
        return action.equals("smelt")
            || action.equals("use")
            || action.equals("make")
            || action.equals("operate")
            || action.equals("click")
            || action.isEmpty();
    }

    public static boolean isPotentialFurnaceIngredient(String itemName)
    {
        String value = normalise(itemName);
        if (value.isEmpty())
        {
            return false;
        }
        if (value.equals("coal")
            || value.equals("soda ash")
            || value.equals("bucket of sand")
            || value.equals("sand")
            || value.equals("seaweed")
            || value.equals("giant seaweed"))
        {
            return true;
        }
        if (value.endsWith(" ore") || value.endsWith(" bar"))
        {
            return true;
        }
        if (value.endsWith(" mould") || value.endsWith(" mold"))
        {
            return true;
        }
        return value.equals("opal")
            || value.equals("jade")
            || value.equals("red topaz")
            || value.equals("sapphire")
            || value.equals("emerald")
            || value.equals("ruby")
            || value.equals("diamond")
            || value.equals("dragonstone")
            || value.equals("onyx")
            || value.equals("zenyte");
    }

    private static String normalise(String value)
    {
        return value == null
            ? ""
            : value.replaceAll("<[^>]*>", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
