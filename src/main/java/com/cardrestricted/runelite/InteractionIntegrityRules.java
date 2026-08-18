package com.cardrestricted.runelite;

import java.util.Locale;
import net.runelite.api.MenuAction;

/**
 * Central fail-closed classification for interactions that can change game
 * state or consume restricted resources.
 */
public final class InteractionIntegrityRules
{
    private InteractionIntegrityRules()
    {
    }

    /**
     * Uses the click event as the primary source because it represents the
     * final action after menu-entry rewriting. The menu entry remains a
     * fallback for clients/plugins that omit event text.
     */
    public static String effectiveText(String entryValue, String eventValue)
    {
        String event = trim(eventValue);
        return event.isEmpty() ? (entryValue == null ? "" : entryValue) : eventValue;
    }

    /**
     * Selects the more restrictive option when event and entry metadata
     * disagree. This prevents a stale or rewritten Talk-to/Examine label from
     * masking an Attack, Trade, Pickpocket or other functional click.
     */
    public static String effectiveOption(String entryValue, String eventValue)
    {
        String entry = trim(entryValue);
        String event = trim(eventValue);
        if (entry.isEmpty())
        {
            return eventValue == null ? "" : eventValue;
        }
        if (event.isEmpty())
        {
            return entryValue == null ? "" : entryValue;
        }
        int entryRisk = optionRisk(entry);
        int eventRisk = optionRisk(event);
        return entryRisk > eventRisk ? entryValue : eventValue;
    }

    /** Final click target, with the event value preferred when available. */
    public static String effectiveTarget(String entryValue, String eventValue)
    {
        return effectiveText(entryValue, eventValue);
    }

    /**
     * Selects the more gameplay-capable action when entry and event metadata
     * conflict. Equal-risk conflicts prefer the final event action.
     */
    public static MenuAction effectiveAction(MenuAction entryAction, MenuAction eventAction)
    {
        if (entryAction == null || entryAction == MenuAction.UNKNOWN)
        {
            return eventAction == null ? entryAction : eventAction;
        }
        if (eventAction == null || eventAction == MenuAction.UNKNOWN)
        {
            return entryAction;
        }
        return actionRisk(entryAction) > actionRisk(eventAction)
            ? entryAction
            : eventAction;
    }

    public static boolean isNpcInteraction(MenuAction action, boolean npcAttached)
    {
        return isNpcInteraction(action, npcAttached, false);
    }

    /**
     * Includes a catalogue-backed target fallback for menu entries rewritten by
     * another plugin so aggressively that the original NPC action and actor are
     * no longer attached to the click event. Explicit player/item/object action
     * types are never reclassified solely because their target shares an NPC
     * display name.
     */
    public static boolean isNpcInteraction(
        MenuAction action,
        boolean npcAttached,
        boolean knownNpcTarget)
    {
        if (npcAttached || InteractionContextRules.isNpcAction(action))
        {
            return true;
        }
        if (!knownNpcTarget)
        {
            return false;
        }
        if (isExplicitNonNpcEntityAction(action))
        {
            return false;
        }
        // Generic widget/client actions are included because menu-entry
        // swapping plugins commonly rewrite NPC options into CC_OP or
        // RUNELITE variants while preserving only the displayed target.
        return true;
    }

    /**
     * Allows a live NPC-index lookup when direct actor metadata was stripped
     * from an otherwise catalogue-known NPC interaction. This is primarily
     * needed for active-combat re-clicks and menu rewriting. Explicit item,
     * object, player and ground-item actions remain excluded.
     */
    public static boolean mayResolveNpcActorFromLiveIndex(
        MenuAction action,
        boolean npcAttached,
        boolean knownNpcTarget)
    {
        return isNpcInteraction(action, npcAttached, knownNpcTarget);
    }

    private static boolean isExplicitNonNpcEntityAction(MenuAction action)
    {
        if (action == null)
        {
            return false;
        }
        switch (action)
        {
            case ITEM_USE_ON_GAME_OBJECT:
            case WIDGET_TARGET_ON_GAME_OBJECT:
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
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
            case ITEM_USE_ON_GROUND_ITEM:
            case WIDGET_TARGET_ON_GROUND_ITEM:
            case GROUND_ITEM_FIRST_OPTION:
            case GROUND_ITEM_SECOND_OPTION:
            case GROUND_ITEM_THIRD_OPTION:
            case GROUND_ITEM_FOURTH_OPTION:
            case GROUND_ITEM_FIFTH_OPTION:
            case ITEM_USE_ON_ITEM:
            case WIDGET_USE_ON_ITEM:
            case ITEM_FIRST_OPTION:
            case ITEM_SECOND_OPTION:
            case ITEM_THIRD_OPTION:
            case ITEM_FOURTH_OPTION:
            case ITEM_FIFTH_OPTION:
            case ITEM_USE:
            case EXAMINE_OBJECT:
            case EXAMINE_ITEM:
            case EXAMINE_ITEM_GROUND:
            case WORLD_ENTITY_FIRST_OPTION:
            case WORLD_ENTITY_SECOND_OPTION:
            case WORLD_ENTITY_THIRD_OPTION:
            case WORLD_ENTITY_FOURTH_OPTION:
            case WORLD_ENTITY_FIFTH_OPTION:
            case EXAMINE_WORLD_ENTITY:
                return true;
            default:
                return false;
        }
    }

    /** Safe actions while profile permissions are loading or being replaced. */
    public static boolean shouldBlockWhileStatePending(
        MenuAction action,
        String option,
        boolean npcAttached,
        boolean bankNavigation)
    {
        return shouldBlockWhileStatePending(
            action,
            option,
            npcAttached,
            false,
            bankNavigation);
    }

    /**
     * Pending-state gate with a catalogue target fallback for actorless or
     * rewritten NPC menu entries.
     */
    public static boolean shouldBlockWhileStatePending(
        MenuAction action,
        String option,
        boolean npcAttached,
        boolean knownNpcTarget,
        boolean bankNavigation)
    {
        if (bankNavigation)
        {
            return false;
        }
        String value = normalise(option);
        if (isNpcInteraction(action, npcAttached, knownNpcTarget))
        {
            return !SimpleRestrictionService.isTalkOption(value);
        }
        if (isPendingStateSafeOption(value))
        {
            return false;
        }
        // Walking is always a recovery-safe route. A player who logs in with
        // equipment that is no longer entitled must still be able to reach a
        // bank, open the equipment interface and correct the profile state.
        if (action == MenuAction.WALK && value.equals("walk here"))
        {
            return false;
        }
        // Generic client/widget actions also carry tab navigation, logout and
        // reporting. Gate them only when the option itself identifies a
        // gameplay operation; treating every CC_OP as gameplay traps the user
        // outside the very interfaces needed for recovery.
        if (isGenericClientOrWidgetAction(action)
            && !shouldGateAmbiguousWidgetAction(value, false))
        {
            return false;
        }
        return isFunctionalAction(action, value, false);
    }

    /**
     * True when option text independently identifies an operation that can
     * consume resources, use equipment or change gameplay state. This is the
     * narrow fail-closed signal used for otherwise ambiguous widget actions;
     * ordinary tab navigation, logout and reporting intentionally return
     * false.
     */
    public static boolean isExplicitGameplayOption(
        String option,
        boolean directSpellbookAction)
    {
        String value = normalise(option);
        if (isRecoveryOrObservationOption(value))
        {
            return false;
        }
        return directSpellbookAction
            || SpellbookWidgetRules.isSpellCastingOption(value)
            || isProductionOption(value)
            || isKnownFunctionalOption(value);
    }

    /**
     * Fail-closed classifier for action types shared by game widgets and
     * client chrome. Recovery and observation controls are explicitly
     * allowlisted; a new non-empty option cannot silently become an equipped
     * item or pending-state bypass merely because RuneLite reports CC_OP.
     */
    public static boolean shouldGateAmbiguousWidgetAction(
        String option,
        boolean directSpellbookAction)
    {
        String value = normalise(option);
        if (value.isEmpty()
            || isRecoveryOrObservationOption(value)
            || isClientRecoveryNavigationOption(value))
        {
            return false;
        }
        return true;
    }

    /** Generic actions shared by gameplay widgets and client-only controls. */
    public static boolean isGenericClientOrWidgetAction(MenuAction action)
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
            case WIDGET_TARGET:
            case RUNELITE:
            case RUNELITE_HIGH_PRIORITY:
            case RUNELITE_LOW_PRIORITY:
            case RUNELITE_WIDGET:
            case RUNELITE_PLAYER:
            case UNKNOWN:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns true for actions where worn equipment could materially affect
     * the outcome. It intentionally excludes only explicit recovery,
     * observation and client-configuration operations. Unknown/future actions
     * fail closed rather than silently becoming a bypass after a RuneLite
     * update or menu-entry rewrite.
     */
    public static boolean isFunctionalAction(
        MenuAction action,
        String option,
        boolean directSpellbookAction)
    {
        String value = normalise(option);
        if (isRecoveryOrObservationOption(value))
        {
            return false;
        }
        if (directSpellbookAction || SpellbookWidgetRules.isSpellCastingOption(value))
        {
            return true;
        }
        if (isProductionOption(value))
        {
            return true;
        }
        if (action == null)
        {
            return !value.isEmpty();
        }
        switch (action)
        {
            case ITEM_USE_ON_GAME_OBJECT:
            case WIDGET_TARGET_ON_GAME_OBJECT:
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
            case ITEM_USE_ON_NPC:
            case WIDGET_TARGET_ON_NPC:
            case NPC_FIRST_OPTION:
            case NPC_SECOND_OPTION:
            case NPC_THIRD_OPTION:
            case NPC_FOURTH_OPTION:
            case NPC_FIFTH_OPTION:
            case ITEM_USE_ON_PLAYER:
            case WIDGET_TARGET_ON_PLAYER:
            case ITEM_USE_ON_GROUND_ITEM:
            case WIDGET_TARGET_ON_GROUND_ITEM:
            case GROUND_ITEM_FIRST_OPTION:
            case GROUND_ITEM_SECOND_OPTION:
            case GROUND_ITEM_THIRD_OPTION:
            case GROUND_ITEM_FOURTH_OPTION:
            case GROUND_ITEM_FIFTH_OPTION:
            case ITEM_USE_ON_ITEM:
            case WIDGET_USE_ON_ITEM:
            case ITEM_FIRST_OPTION:
            case ITEM_SECOND_OPTION:
            case ITEM_THIRD_OPTION:
            case ITEM_FOURTH_OPTION:
            case ITEM_FIFTH_OPTION:
            case ITEM_USE:
            case PLAYER_FIRST_OPTION:
            case PLAYER_SECOND_OPTION:
            case PLAYER_THIRD_OPTION:
            case PLAYER_FOURTH_OPTION:
            case PLAYER_FIFTH_OPTION:
            case PLAYER_SIXTH_OPTION:
            case PLAYER_SEVENTH_OPTION:
            case PLAYER_EIGHTH_OPTION:
            case WIDGET_TARGET_ON_WIDGET:
            case WORLD_ENTITY_FIRST_OPTION:
            case WORLD_ENTITY_SECOND_OPTION:
            case WORLD_ENTITY_THIRD_OPTION:
            case WORLD_ENTITY_FOURTH_OPTION:
            case WORLD_ENTITY_FIFTH_OPTION:
                return true;
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
            case WIDGET_TARGET:
            case RUNELITE:
            case RUNELITE_HIGH_PRIORITY:
            case RUNELITE_LOW_PRIORITY:
            case RUNELITE_WIDGET:
            case RUNELITE_PLAYER:
            case UNKNOWN:
                return true;
            case WALK:
            case CANCEL:
            case WIDGET_CONTINUE:
            case WIDGET_CLOSE:
            case SET_HEADING:
            case EXAMINE_OBJECT:
            case EXAMINE_NPC:
            case EXAMINE_ITEM:
            case EXAMINE_ITEM_GROUND:
            case EXAMINE_WORLD_ENTITY:
            case RUNELITE_OVERLAY:
            case RUNELITE_OVERLAY_CONFIG:
            case RUNELITE_INFOBOX:
                return false;
            default:
                return true;
        }
    }

    /**
     * Fails closed when an unmistakably NPC-functional option has been
     * rewritten into a generic action and both actor and target identity have
     * disappeared. Genuine widget actions are excluded and handled by their
     * interface-specific gates.
     */
    public static boolean shouldBlockUnresolvedNpcFunctionalAction(
        MenuAction action,
        String option,
        boolean npcAttached,
        boolean knownNpcTarget,
        boolean genuineWidgetContext)
    {
        if (npcAttached || knownNpcTarget || genuineWidgetContext
            || InteractionContextRules.isNpcAction(action))
        {
            return false;
        }
        if (!isGenericRewrittenAction(action))
        {
            return false;
        }
        String value = normalise(option);
        if (SimpleRestrictionService.isTalkOption(value))
        {
            return false;
        }
        return startsWithAny(value,
            "attack", "pickpocket", "trade", "buy", "sell",
            "claim", "collect", "travel", "hire", "charter",
            "bank", "steal-from", "steal from", "slayer task",
            "assignment", "reclaim", "repair", "exchange");
    }

    private static boolean isGenericRewrittenAction(MenuAction action)
    {
        return action == null
            || action == MenuAction.UNKNOWN
            || action == MenuAction.RUNELITE
            || action == MenuAction.RUNELITE_HIGH_PRIORITY
            || action == MenuAction.RUNELITE_LOW_PRIORITY
            || action == MenuAction.RUNELITE_WIDGET
            || action == MenuAction.CC_OP
            || action == MenuAction.CC_OP_LOW_PRIORITY;
    }

    public static boolean isProductionOption(String option)
    {
        String value = normalise(option);
        return startsWithAny(value,
            "make", "prepare", "process", "cook", "bake", "roast",
            "smelt", "smith", "forge", "spin", "weave", "craft",
            "fletch", "whittle", "carve", "sew", "stitch", "mix",
            "brew", "grind", "pulverise", "cut", "string", "tan",
            "blow", "fire", "enchant", "combine", "create", "assemble",
            "build", "infuse", "charge", "imbue", "mould", "mold",
            "pot", "ferment", "distill", "attach", "add-to");
    }

    public static boolean isAutocastSelection(String option)
    {
        String value = normalise(option);
        return value.equals("autocast")
            || value.equals("defensive autocast")
            || value.startsWith("autocast ")
            || value.startsWith("defensive autocast ");
    }

    public static boolean isAttackOption(String option)
    {
        String value = normalise(option);
        return value.equals("attack") || value.startsWith("attack ");
    }

    private static int optionRisk(String value)
    {
        String normalised = normalise(value);
        if (normalised.isEmpty())
        {
            return -1;
        }
        if (isRecoveryOrObservationOption(normalised))
        {
            return 0;
        }
        if (isKnownFunctionalOption(normalised)
            || isProductionOption(normalised)
            || SpellbookWidgetRules.isSpellCastingOption(normalised))
        {
            return 3;
        }
        return 1;
    }

    private static int actionRisk(MenuAction action)
    {
        if (action == null)
        {
            return -1;
        }
        if (InteractionContextRules.isNpcAction(action))
        {
            // NPC identity restrictions are the most consequential and must
            // survive conflicts with rewritten player/widget action types.
            return 6;
        }
        if (InteractionContextRules.isSelectedItemSourceAction(action)
            || InteractionContextRules.isGroundItemAction(action)
            || action == MenuAction.WIDGET_USE_ON_ITEM
            || action == MenuAction.ITEM_FIRST_OPTION
            || action == MenuAction.ITEM_SECOND_OPTION
            || action == MenuAction.ITEM_THIRD_OPTION
            || action == MenuAction.ITEM_FOURTH_OPTION
            || action == MenuAction.ITEM_FIFTH_OPTION
            || action == MenuAction.ITEM_USE)
        {
            return 5;
        }
        switch (action)
        {
            case ITEM_USE_ON_GAME_OBJECT:
            case WIDGET_TARGET_ON_GAME_OBJECT:
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
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
            case WIDGET_TARGET_ON_WIDGET:
            case WORLD_ENTITY_FIRST_OPTION:
            case WORLD_ENTITY_SECOND_OPTION:
            case WORLD_ENTITY_THIRD_OPTION:
            case WORLD_ENTITY_FOURTH_OPTION:
            case WORLD_ENTITY_FIFTH_OPTION:
                return 4;
            case RUNELITE:
            case RUNELITE_HIGH_PRIORITY:
            case RUNELITE_LOW_PRIORITY:
            case RUNELITE_WIDGET:
            case RUNELITE_PLAYER:
            case UNKNOWN:
                return 3;
            case WIDGET_TYPE_1:
            case WIDGET_TYPE_4:
            case WIDGET_TYPE_5:
            case WIDGET_TARGET:
            case WIDGET_FIRST_OPTION:
            case WIDGET_SECOND_OPTION:
            case WIDGET_THIRD_OPTION:
            case WIDGET_FOURTH_OPTION:
            case WIDGET_FIFTH_OPTION:
            case CC_OP:
            case CC_OP_LOW_PRIORITY:
                return 2;
            case WALK:
                // Movement is functional for equipped-item integrity even
                // though it is not an NPC/item interaction by itself.
                return 1;
            case CANCEL:
            case WIDGET_CONTINUE:
            case WIDGET_CLOSE:
            case SET_HEADING:
            case EXAMINE_OBJECT:
            case EXAMINE_NPC:
            case EXAMINE_ITEM:
            case EXAMINE_ITEM_GROUND:
            case EXAMINE_WORLD_ENTITY:
            case RUNELITE_OVERLAY:
            case RUNELITE_OVERLAY_CONFIG:
            case RUNELITE_INFOBOX:
                return 0;
            default:
                return 3;
        }
    }

    private static boolean isPendingStateSafeOption(String value)
    {
        return value.isEmpty()
            || value.equals("cancel")
            || value.equals("examine")
            || value.equals("continue")
            || value.equals("close")
            || value.equals("back")
            || value.equals("remove")
            || value.equals("unequip")
            || value.equals("unwear")
            || value.equals("drop")
            || value.equals("destroy")
            || value.startsWith("configure")
            || value.startsWith("settings");
    }

    private static boolean isRecoveryOrObservationOption(String value)
    {
        return value.isEmpty()
            || value.equals("walk here")
            || value.equals("cancel")
            || value.equals("examine")
            || value.equals("talk-to")
            || value.equals("talk to")
            || value.equals("continue")
            || value.equals("close")
            || value.equals("back")
            || value.equals("remove")
            || value.equals("unequip")
            || value.equals("unwear")
            || value.equals("drop")
            || value.equals("destroy")
            || value.startsWith("configure")
            || value.startsWith("settings");
    }

    private static boolean isClientRecoveryNavigationOption(String value)
    {
        return value.equals("combat options")
            || value.equals("skills")
            || value.equals("quest list")
            || value.equals("quests")
            || value.equals("inventory")
            || value.equals("worn equipment")
            || value.equals("equipment")
            || value.equals("prayer")
            || value.equals("magic")
            || value.equals("grouping")
            || value.equals("clan chat")
            || value.equals("friends list")
            || value.equals("ignore list")
            || value.equals("account management")
            || value.equals("character summary")
            || value.equals("emotes")
            || value.equals("music player")
            || value.equals("options")
            || value.equals("logout")
            || value.equals("log out")
            || value.equals("report")
            || value.equals("report abuse")
            || value.equals("toggle run")
            || value.equals("face north")
            || value.equals("price checker")
            || value.equals("items kept on death")
            || value.equals("view equipment stats")
            || value.equals("activity adviser")
            || value.equals("activity advisor")
            || value.equals("wiki")
            || value.equals("lookup")
            || value.startsWith("open wiki")
            || value.startsWith("wiki ");
    }

    private static boolean isKnownFunctionalOption(String value)
    {
        return startsWithAny(value,
            "attack", "pickpocket", "trade", "buy", "sell", "purchase",
            "withdraw", "deposit",
            "claim", "redeem", "exchange", "travel", "pay", "hire",
            "charter", "board", "sail", "teleport", "cast", "autocast",
            "defensive autocast", "chop", "mine", "fish", "net", "bait",
            "lure", "harpoon", "catch", "hunt", "search", "open", "enter",
            "climb", "cross", "jump", "squeeze", "crawl", "use", "take",
            "steal", "loot", "thieve", "operate", "activate", "rub",
            "drink", "eat", "wear", "wield", "equip", "release", "check", "plant",
            "rake", "harvest", "pick", "fill", "empty", "light", "burn",
            "clean", "crush", "decant", "repair", "pray-at", "offer",
            "sacrifice", "commune", "study", "read", "play", "inspect",
            "collect", "confirm", "select", "choose", "start", "join",
            "leave", "accept", "proceed", "set", "switch", "toggle");
    }

    private static boolean startsWithAny(String value, String... prefixes)
    {
        for (String prefix : prefixes)
        {
            if (value.equals(prefix) || value.startsWith(prefix + "-")
                || value.startsWith(prefix + " "))
            {
                return true;
            }
        }
        return false;
    }

    private static String trim(String value)
    {
        return value == null ? "" : value.trim();
    }

    private static String normalise(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replaceAll("<[^>]*>", "")
            .replace('\u00a0', ' ')
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ");
    }
}
