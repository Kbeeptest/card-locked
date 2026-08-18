package com.cardrestricted.runelite;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.InventoryID;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;

/**
 * Identifies metadata-free collection actions on authenticated reward
 * interfaces and maps the clicked interface to its corresponding item
 * container. Unknown/stripped reward context can then fail closed without
 * scanning unrelated stale reward containers.
 */
public final class RewardContainerIntegrityRules
{
    private RewardContainerIntegrityRules()
    {
    }

    public static boolean isRewardCollectionAction(
        MenuAction action,
        String option,
        int... packedWidgetIds)
    {
        return isPotentialRewardCollectionAction(action, option)
            && !rewardInventoriesForContext(packedWidgetIds).isEmpty();
    }

    public static boolean isPotentialRewardCollectionAction(
        MenuAction action,
        String option)
    {
        if (!isWidgetOrRewrittenAction(action))
        {
            return false;
        }
        String value = normalise(option);
        return startsWithAny(value,
            "take", "take-all", "take all", "collect", "claim",
            "loot", "retrieve", "bank-all", "bank all",
            "deposit-all", "deposit all", "send-to-bank",
            "send to bank");
    }

    public static Set<InventoryID> rewardInventoriesForContext(
        int... packedWidgetIds)
    {
        if (packedWidgetIds == null)
        {
            return Collections.emptySet();
        }
        EnumSet<InventoryID> result = EnumSet.noneOf(InventoryID.class);
        for (int packedWidgetId : packedWidgetIds)
        {
            if (packedWidgetId < 0)
            {
                continue;
            }
            int groupId = packedWidgetId >>> 16;
            switch (groupId)
            {
                case InterfaceID.TRAWLER_REWARD:
                    result.add(InventoryID.FISHING_TRAWLER_REWARD);
                    break;
                case InterfaceID.BARROWS_REWARD:
                    result.add(InventoryID.BARROWS_REWARD);
                    break;
                case InterfaceID.FOSSIL_DRIFTNET:
                    result.add(InventoryID.DRIFT_NET_FISHING_REWARD);
                    break;
                case InterfaceID.MISC_COLLECTION:
                    result.add(InventoryID.KINGDOM_OF_MISCELLANIA);
                    break;
                case InterfaceID.RAIDS_REWARDS:
                    result.add(InventoryID.CHAMBERS_OF_XERIC_CHEST);
                    break;
                case InterfaceID.TOB_CHESTS:
                    result.add(InventoryID.THEATRE_OF_BLOOD_CHEST);
                    break;
                case InterfaceID.WILDY_LOOT_CHEST:
                    result.add(InventoryID.WILDERNESS_LOOT_CHEST);
                    break;
                case InterfaceID.TOA_CHESTS:
                    result.add(InventoryID.TOA_REWARD_CHEST);
                    break;
                case InterfaceID.PMOON_REWARD:
                    result.add(InventoryID.LUNAR_CHEST);
                    break;
                case InterfaceID.COLOSSEUM_REWARD_CHEST:
                case InterfaceID.COLOSSEUM_REWARD_CHEST_2:
                case InterfaceID.COLOSSEUM_REWARD:
                    result.add(InventoryID.FORTIS_COLOSSEUM_REWARD_CHEST);
                    break;
                default:
                    break;
            }
        }
        return result.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(result);
    }

    private static boolean isWidgetOrRewrittenAction(MenuAction action)
    {
        if (action == null)
        {
            return true;
        }
        switch (action)
        {
            case CC_OP:
            case CC_OP_LOW_PRIORITY:
            case WIDGET_FIRST_OPTION:
            case WIDGET_SECOND_OPTION:
            case WIDGET_THIRD_OPTION:
            case WIDGET_FOURTH_OPTION:
            case WIDGET_FIFTH_OPTION:
            case WIDGET_TYPE_1:
            case WIDGET_TYPE_4:
            case WIDGET_TYPE_5:
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

    private static boolean startsWithAny(String value, String... prefixes)
    {
        for (String prefix : prefixes)
        {
            if (value.equals(prefix)
                || value.startsWith(prefix + " ")
                || value.startsWith(prefix + "-"))
            {
                return true;
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
