package com.cardrestricted.runelite;

import java.util.EnumSet;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.WidgetID;

/**
 * Conservative menu-context rules used to avoid treating stale widget state or
 * unrelated NPC/object targets as item interactions.
 */
public final class InteractionContextRules
{
    private static final EnumSet<MenuAction> SELECTED_ITEM_SOURCE_ACTIONS =
        EnumSet.of(
            MenuAction.ITEM_USE_ON_GAME_OBJECT,
            MenuAction.ITEM_USE_ON_NPC,
            MenuAction.ITEM_USE_ON_PLAYER,
            MenuAction.ITEM_USE_ON_GROUND_ITEM,
            MenuAction.ITEM_USE_ON_ITEM);

    private static final EnumSet<MenuAction> ITEM_NAME_ACTIONS =
        EnumSet.of(
            MenuAction.ITEM_USE_ON_ITEM,
            MenuAction.WIDGET_USE_ON_ITEM,
            MenuAction.ITEM_FIRST_OPTION,
            MenuAction.ITEM_SECOND_OPTION,
            MenuAction.ITEM_THIRD_OPTION,
            MenuAction.ITEM_FOURTH_OPTION,
            MenuAction.ITEM_FIFTH_OPTION,
            MenuAction.ITEM_USE,
            MenuAction.GROUND_ITEM_FIRST_OPTION,
            MenuAction.GROUND_ITEM_SECOND_OPTION,
            MenuAction.GROUND_ITEM_THIRD_OPTION,
            MenuAction.GROUND_ITEM_FOURTH_OPTION,
            MenuAction.GROUND_ITEM_FIFTH_OPTION,
            MenuAction.ITEM_USE_ON_GROUND_ITEM,
            MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,
            MenuAction.EXAMINE_ITEM_GROUND,
            MenuAction.EXAMINE_ITEM);

    private InteractionContextRules()
    {
    }

    public static boolean isSelectedItemSourceAction(MenuAction menuAction)
    {
        return menuAction != null
            && SELECTED_ITEM_SOURCE_ACTIONS.contains(menuAction);
    }

    public static boolean shouldIncludeSelectedItem(
        boolean widgetSelected,
        MenuAction menuAction,
        int selectedWidgetPackedId)
    {
        return widgetSelected
            && menuAction != null
            && SELECTED_ITEM_SOURCE_ACTIONS.contains(menuAction)
            && !SpellbookWidgetRules.isSpellbookPackedId(
                selectedWidgetPackedId);
    }

    public static boolean shouldUseItemNameFallback(
        MenuAction menuAction,
        boolean itemOperation,
        boolean directSpellbookClick)
    {
        if (directSpellbookClick)
        {
            return isSpellTargetItemAction(menuAction);
        }
        return itemOperation
            || (menuAction != null
                && ITEM_NAME_ACTIONS.contains(menuAction));
    }

    /**
     * A spellbook widget is not an item source, but a spell cast on an item is
     * still a functional interaction with the target item.
     */
    public static boolean isSpellTargetItemAction(MenuAction menuAction)
    {
        return menuAction == MenuAction.WIDGET_USE_ON_ITEM
            || menuAction == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM;
    }

    public static boolean shouldReadClickedItemMetadata(
        MenuAction menuAction,
        boolean itemOperation,
        boolean directSpellbookClick)
    {
        return shouldUseItemNameFallback(
            menuAction,
            itemOperation,
            directSpellbookClick);
    }

    public static boolean isEquipmentWidget(int packedWidgetId)
    {
        if (packedWidgetId < 0)
        {
            return false;
        }
        int groupId = packedWidgetId >>> 16;
        return groupId == WidgetID.EQUIPMENT_GROUP_ID
            || groupId == WidgetID.EQUIPMENT_INVENTORY_GROUP_ID;
    }

    public static boolean isGroundItemAction(MenuAction menuAction)
    {
        if (menuAction == null)
        {
            return false;
        }
        return menuAction == MenuAction.ITEM_USE_ON_GROUND_ITEM
            || menuAction == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM
            || menuAction == MenuAction.GROUND_ITEM_FIRST_OPTION
            || menuAction == MenuAction.GROUND_ITEM_SECOND_OPTION
            || menuAction == MenuAction.GROUND_ITEM_THIRD_OPTION
            || menuAction == MenuAction.GROUND_ITEM_FOURTH_OPTION
            || menuAction == MenuAction.GROUND_ITEM_FIFTH_OPTION
            || menuAction == MenuAction.EXAMINE_ITEM_GROUND;
    }
    public static boolean isNpcAction(MenuAction menuAction)
    {
        if (menuAction == null)
        {
            return false;
        }
        return menuAction == MenuAction.ITEM_USE_ON_NPC
            || menuAction == MenuAction.WIDGET_TARGET_ON_NPC
            || menuAction == MenuAction.NPC_FIRST_OPTION
            || menuAction == MenuAction.NPC_SECOND_OPTION
            || menuAction == MenuAction.NPC_THIRD_OPTION
            || menuAction == MenuAction.NPC_FOURTH_OPTION
            || menuAction == MenuAction.NPC_FIFTH_OPTION
            || menuAction == MenuAction.EXAMINE_NPC;
    }

    public static boolean isSpellTargetAction(MenuAction menuAction)
    {
        return menuAction == MenuAction.WIDGET_USE_ON_ITEM
            || menuAction == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM
            || menuAction == MenuAction.WIDGET_TARGET_ON_NPC
            || menuAction == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT
            || menuAction == MenuAction.WIDGET_TARGET_ON_PLAYER
            || menuAction == MenuAction.WIDGET_TARGET_ON_WIDGET;
    }

}
