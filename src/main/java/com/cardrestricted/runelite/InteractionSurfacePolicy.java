package com.cardrestricted.runelite;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.MenuAction;

/**
 * Explicit inventory of every RuneLite {@link MenuAction} understood by this
 * build. Tests compare this inventory with {@code MenuAction.values()} so a
 * newly introduced API action becomes a build failure until it receives an
 * intentional policy classification.
 */
public final class InteractionSurfacePolicy
{
    private static final Set<MenuAction> REVIEWED_ACTIONS;

    static
    {
        EnumSet<MenuAction> reviewed = EnumSet.noneOf(MenuAction.class);
        Collections.addAll(reviewed,
            MenuAction.ITEM_USE_ON_GAME_OBJECT,
            MenuAction.WIDGET_TARGET_ON_GAME_OBJECT,
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            MenuAction.GAME_OBJECT_SECOND_OPTION,
            MenuAction.GAME_OBJECT_THIRD_OPTION,
            MenuAction.GAME_OBJECT_FOURTH_OPTION,
            MenuAction.GAME_OBJECT_FIFTH_OPTION,
            MenuAction.ITEM_USE_ON_NPC,
            MenuAction.WIDGET_TARGET_ON_NPC,
            MenuAction.NPC_FIRST_OPTION,
            MenuAction.NPC_SECOND_OPTION,
            MenuAction.NPC_THIRD_OPTION,
            MenuAction.NPC_FOURTH_OPTION,
            MenuAction.NPC_FIFTH_OPTION,
            MenuAction.ITEM_USE_ON_PLAYER,
            MenuAction.WIDGET_TARGET_ON_PLAYER,
            MenuAction.ITEM_USE_ON_GROUND_ITEM,
            MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,
            MenuAction.GROUND_ITEM_FIRST_OPTION,
            MenuAction.GROUND_ITEM_SECOND_OPTION,
            MenuAction.GROUND_ITEM_THIRD_OPTION,
            MenuAction.GROUND_ITEM_FOURTH_OPTION,
            MenuAction.GROUND_ITEM_FIFTH_OPTION,
            MenuAction.WALK,
            MenuAction.WIDGET_TYPE_1,
            MenuAction.WIDGET_TARGET,
            MenuAction.WIDGET_CLOSE,
            MenuAction.WIDGET_TYPE_4,
            MenuAction.WIDGET_TYPE_5,
            MenuAction.WIDGET_CONTINUE,
            MenuAction.ITEM_USE_ON_ITEM,
            MenuAction.WIDGET_USE_ON_ITEM,
            MenuAction.ITEM_FIRST_OPTION,
            MenuAction.ITEM_SECOND_OPTION,
            MenuAction.ITEM_THIRD_OPTION,
            MenuAction.ITEM_FOURTH_OPTION,
            MenuAction.ITEM_FIFTH_OPTION,
            MenuAction.ITEM_USE,
            MenuAction.WIDGET_FIRST_OPTION,
            MenuAction.WIDGET_SECOND_OPTION,
            MenuAction.WIDGET_THIRD_OPTION,
            MenuAction.WIDGET_FOURTH_OPTION,
            MenuAction.WIDGET_FIFTH_OPTION,
            MenuAction.PLAYER_FIRST_OPTION,
            MenuAction.PLAYER_SECOND_OPTION,
            MenuAction.PLAYER_THIRD_OPTION,
            MenuAction.PLAYER_FOURTH_OPTION,
            MenuAction.PLAYER_FIFTH_OPTION,
            MenuAction.PLAYER_SIXTH_OPTION,
            MenuAction.PLAYER_SEVENTH_OPTION,
            MenuAction.PLAYER_EIGHTH_OPTION,
            MenuAction.CC_OP,
            MenuAction.WIDGET_TARGET_ON_WIDGET,
            MenuAction.SET_HEADING,
            MenuAction.WORLD_ENTITY_FIRST_OPTION,
            MenuAction.WORLD_ENTITY_SECOND_OPTION,
            MenuAction.WORLD_ENTITY_THIRD_OPTION,
            MenuAction.WORLD_ENTITY_FOURTH_OPTION,
            MenuAction.WORLD_ENTITY_FIFTH_OPTION,
            MenuAction.RUNELITE_WIDGET,
            MenuAction.RUNELITE_HIGH_PRIORITY,
            MenuAction.EXAMINE_OBJECT,
            MenuAction.EXAMINE_NPC,
            MenuAction.EXAMINE_ITEM_GROUND,
            MenuAction.EXAMINE_ITEM,
            MenuAction.CANCEL,
            MenuAction.CC_OP_LOW_PRIORITY,
            MenuAction.EXAMINE_WORLD_ENTITY,
            MenuAction.RUNELITE,
            MenuAction.RUNELITE_OVERLAY,
            MenuAction.RUNELITE_OVERLAY_CONFIG,
            MenuAction.RUNELITE_PLAYER,
            MenuAction.RUNELITE_INFOBOX,
            MenuAction.RUNELITE_LOW_PRIORITY,
            MenuAction.UNKNOWN);
        REVIEWED_ACTIONS = Collections.unmodifiableSet(reviewed);
    }

    private InteractionSurfacePolicy()
    {
    }

    public static Set<MenuAction> reviewedActions()
    {
        return REVIEWED_ACTIONS;
    }

    public static InteractionSurface surfaceFor(MenuAction action)
    {
        if (action == null)
        {
            return InteractionSurface.UNKNOWN;
        }
        switch (action)
        {
            case ITEM_USE_ON_NPC:
            case WIDGET_TARGET_ON_NPC:
            case NPC_FIRST_OPTION:
            case NPC_SECOND_OPTION:
            case NPC_THIRD_OPTION:
            case NPC_FOURTH_OPTION:
            case NPC_FIFTH_OPTION:
            case EXAMINE_NPC:
                return InteractionSurface.NPC;

            case ITEM_USE_ON_ITEM:
            case WIDGET_USE_ON_ITEM:
            case ITEM_FIRST_OPTION:
            case ITEM_SECOND_OPTION:
            case ITEM_THIRD_OPTION:
            case ITEM_FOURTH_OPTION:
            case ITEM_FIFTH_OPTION:
            case ITEM_USE:
            case EXAMINE_ITEM:
                return InteractionSurface.ITEM;

            case ITEM_USE_ON_GROUND_ITEM:
            case WIDGET_TARGET_ON_GROUND_ITEM:
            case GROUND_ITEM_FIRST_OPTION:
            case GROUND_ITEM_SECOND_OPTION:
            case GROUND_ITEM_THIRD_OPTION:
            case GROUND_ITEM_FOURTH_OPTION:
            case GROUND_ITEM_FIFTH_OPTION:
            case EXAMINE_ITEM_GROUND:
                return InteractionSurface.GROUND_ITEM;

            case ITEM_USE_ON_GAME_OBJECT:
            case WIDGET_TARGET_ON_GAME_OBJECT:
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
            case EXAMINE_OBJECT:
                return InteractionSurface.GAME_OBJECT;

            case ITEM_USE_ON_PLAYER:
            case WIDGET_TARGET_ON_PLAYER:
            case PLAYER_FIRST_OPTION:
            case PLAYER_SECOND_OPTION:
            case PLAYER_THIRD_OPTION:
            case PLAYER_FOURTH_OPTION:
            case PLAYER_FIFTH_OPTION:
            case PLAYER_SIXTH_OPTION:
            case PLAYER_SEVENTH_OPTION:
            case PLAYER_EIGHTH_OPTION:
            case RUNELITE_PLAYER:
                return InteractionSurface.PLAYER;

            case WORLD_ENTITY_FIRST_OPTION:
            case WORLD_ENTITY_SECOND_OPTION:
            case WORLD_ENTITY_THIRD_OPTION:
            case WORLD_ENTITY_FOURTH_OPTION:
            case WORLD_ENTITY_FIFTH_OPTION:
            case EXAMINE_WORLD_ENTITY:
                return InteractionSurface.WORLD_ENTITY;

            case WIDGET_TYPE_1:
            case WIDGET_TARGET:
            case WIDGET_CLOSE:
            case WIDGET_TYPE_4:
            case WIDGET_TYPE_5:
            case WIDGET_CONTINUE:
            case WIDGET_FIRST_OPTION:
            case WIDGET_SECOND_OPTION:
            case WIDGET_THIRD_OPTION:
            case WIDGET_FOURTH_OPTION:
            case WIDGET_FIFTH_OPTION:
            case CC_OP:
            case CC_OP_LOW_PRIORITY:
            case WIDGET_TARGET_ON_WIDGET:
            case RUNELITE_WIDGET:
                return InteractionSurface.WIDGET;

            case WALK:
                return InteractionSurface.MOVEMENT;

            case SET_HEADING:
            case CANCEL:
            case RUNELITE_HIGH_PRIORITY:
            case RUNELITE:
            case RUNELITE_OVERLAY:
            case RUNELITE_OVERLAY_CONFIG:
            case RUNELITE_INFOBOX:
            case RUNELITE_LOW_PRIORITY:
                return InteractionSurface.CLIENT;

            case UNKNOWN:
            default:
                return InteractionSurface.UNKNOWN;
        }
    }

    public static InteractionRiskClass riskClassFor(MenuAction action)
    {
        if (action == null || action == MenuAction.UNKNOWN)
        {
            return InteractionRiskClass.UNKNOWN_FAIL_CLOSED;
        }
        switch (action)
        {
            case EXAMINE_OBJECT:
            case EXAMINE_NPC:
            case EXAMINE_ITEM_GROUND:
            case EXAMINE_ITEM:
            case EXAMINE_WORLD_ENTITY:
                return InteractionRiskClass.OBSERVATION;

            case SET_HEADING:
            case CANCEL:
            case WIDGET_CLOSE:
            case WIDGET_CONTINUE:
            case RUNELITE_HIGH_PRIORITY:
            case RUNELITE_OVERLAY:
            case RUNELITE_OVERLAY_CONFIG:
            case RUNELITE_INFOBOX:
            case RUNELITE_LOW_PRIORITY:
                return InteractionRiskClass.CLIENT_ONLY;

            default:
                return InteractionRiskClass.DYNAMIC_RESTRICTION;
        }
    }
}
