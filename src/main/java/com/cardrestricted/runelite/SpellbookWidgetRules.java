package com.cardrestricted.runelite;

/** Packed-widget rules used to keep spellbook actions separate from items. */
public final class SpellbookWidgetRules
{
    public static final int SPELLBOOK_GROUP_ID = 218;

    private SpellbookWidgetRules()
    {
    }

    public static boolean isSpellbookPackedId(int packedWidgetId)
    {
        return packedWidgetId >= 0
            && packedWidgetId >>> 16 == SPELLBOOK_GROUP_ID;
    }

    public static boolean isDirectSpellbookClick(int... packedWidgetIds)
    {
        if (packedWidgetIds == null)
        {
            return false;
        }
        for (int packedWidgetId : packedWidgetIds)
        {
            if (isSpellbookPackedId(packedWidgetId))
            {
                return true;
            }
        }
        return false;
    }
    public static boolean isPotentialRewrittenSpellAction(
        net.runelite.api.MenuAction action)
    {
        if (action == null)
        {
            return true;
        }
        switch (action)
        {
            case CC_OP:
            case CC_OP_LOW_PRIORITY:
            case WIDGET_TYPE_1:
            case WIDGET_TYPE_4:
            case WIDGET_TYPE_5:
            case WIDGET_TARGET:
            case WIDGET_FIRST_OPTION:
            case WIDGET_SECOND_OPTION:
            case WIDGET_THIRD_OPTION:
            case WIDGET_FOURTH_OPTION:
            case WIDGET_FIFTH_OPTION:
            case RUNELITE:
            case RUNELITE_HIGH_PRIORITY:
            case RUNELITE_LOW_PRIORITY:
            case RUNELITE_WIDGET:
            case UNKNOWN:
                return true;
            default:
                return false;
        }
    }

    public static boolean isSpellCastingOption(String option)
    {
        if (option == null)
        {
            return false;
        }
        String value = option.replaceAll("<[^>]*>", "")
            .trim()
            .toLowerCase(java.util.Locale.ROOT);
        return value.equals("cast")
            || value.equals("autocast")
            || value.equals("defensive autocast")
            || value.startsWith("cast ")
            || value.startsWith("autocast ")
            || value.startsWith("defensive autocast ");
    }

}
