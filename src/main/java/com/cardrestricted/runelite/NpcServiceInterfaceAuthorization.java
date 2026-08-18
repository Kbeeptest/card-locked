package com.cardrestricted.runelite;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetID;

/**
 * Session-local provenance for specialist NPC interfaces which are neither the
 * standard Shop, Bank nor Grand Exchange widgets.
 *
 * <p>Examples include tanning, decanting, makeover, Slayer rewards, ticket
 * exchanges and bespoke minigame reward shops. An interface inherited from a
 * disabled plugin session remains unusable until it is closed and reopened
 * through an interaction already allowed by Card Locked.</p>
 */
public final class NpcServiceInterfaceAuthorization
{
    private static final int DIRECT_OPEN_WINDOW_TICKS = 8;
    private static final int TALK_AUTO_OPEN_WINDOW_TICKS = 3;
    private static final int DIALOGUE_SOURCE_WINDOW_TICKS = 200;
    private static final int DIALOGUE_OPEN_WINDOW_TICKS = 12;

    private int pendingDirectOpenTick = Integer.MIN_VALUE;
    private int pendingDialogueOpenTick = Integer.MIN_VALUE;
    private int pendingTalkAutoOpenTick = Integer.MIN_VALUE;
    private int verifiedDialogueSourceTick = Integer.MIN_VALUE;
    private int openServiceFamily = -1;
    private boolean serviceAuthorized;
    private final Set<Integer> loadedServiceGroups = new HashSet<>();

    public void reset()
    {
        pendingDirectOpenTick = Integer.MIN_VALUE;
        pendingDialogueOpenTick = Integer.MIN_VALUE;
        pendingTalkAutoOpenTick = Integer.MIN_VALUE;
        verifiedDialogueSourceTick = Integer.MIN_VALUE;
        openServiceFamily = -1;
        serviceAuthorized = false;
        loadedServiceGroups.clear();
    }

    public void observeAllowedWorldInteraction(
        MenuAction action,
        String option,
        String target,
        int clientTick,
        boolean npcInteraction,
        boolean npcDialogueSourceAuthorized)
    {
        String value = normalise(option);
        if (npcInteraction && SimpleRestrictionService.isTalkOption(value))
        {
            verifiedDialogueSourceTick = npcDialogueSourceAuthorized
                ? clientTick
                : Integer.MIN_VALUE;
            pendingTalkAutoOpenTick = npcDialogueSourceAuthorized
                ? clientTick
                : Integer.MIN_VALUE;
            pendingDirectOpenTick = Integer.MIN_VALUE;
            pendingDialogueOpenTick = Integer.MIN_VALUE;
            serviceAuthorized = false;
            return;
        }

        boolean recognised = canOpenService(action, value, target);
        boolean verifiedNpc = npcInteraction
            && npcDialogueSourceAuthorized
            && recognised;
        boolean verifiedObject = isObjectWorldInteraction(action)
            && recognised;
        if (!verifiedNpc && !verifiedObject)
        {
            if (isWorldInteraction(action))
            {
                verifiedDialogueSourceTick = Integer.MIN_VALUE;
                pendingDirectOpenTick = Integer.MIN_VALUE;
                pendingDialogueOpenTick = Integer.MIN_VALUE;
                pendingTalkAutoOpenTick = Integer.MIN_VALUE;
            }
            return;
        }
        pendingDirectOpenTick = clientTick;
        pendingDialogueOpenTick = Integer.MIN_VALUE;
        pendingTalkAutoOpenTick = Integer.MIN_VALUE;
        serviceAuthorized = false;
    }

    public boolean observeAllowedDialogueChoice(
        String option,
        String target,
        String widgetText,
        int clientTick,
        int... packedWidgetIds)
    {
        if (!containsGroup(WidgetID.DIALOG_OPTION_GROUP_ID, packedWidgetIds)
            || !withinWindow(
                verifiedDialogueSourceTick,
                clientTick,
                DIALOGUE_SOURCE_WINDOW_TICKS)
            || !isServiceDialogueChoice(option, target, widgetText))
        {
            return false;
        }
        pendingDialogueOpenTick = clientTick;
        pendingDirectOpenTick = Integer.MIN_VALUE;
        pendingTalkAutoOpenTick = Integer.MIN_VALUE;
        serviceAuthorized = false;
        return true;
    }

    public void onWidgetLoaded(int groupId, int clientTick)
    {
        int family = serviceFamily(groupId);
        if (family < 0)
        {
            return;
        }
        loadedServiceGroups.add(groupId);
        if (openServiceFamily != family)
        {
            openServiceFamily = family;
            serviceAuthorized = withinWindow(
                    pendingDirectOpenTick,
                    clientTick,
                    DIRECT_OPEN_WINDOW_TICKS)
                || withinWindow(
                    pendingDialogueOpenTick,
                    clientTick,
                    DIALOGUE_OPEN_WINDOW_TICKS)
                || withinWindow(
                    pendingTalkAutoOpenTick,
                    clientTick,
                    TALK_AUTO_OPEN_WINDOW_TICKS);
        }
        pendingDirectOpenTick = Integer.MIN_VALUE;
        pendingDialogueOpenTick = Integer.MIN_VALUE;
        pendingTalkAutoOpenTick = Integer.MIN_VALUE;
    }

    public void onWidgetClosed(int groupId)
    {
        int family = serviceFamily(groupId);
        if (family < 0)
        {
            return;
        }
        loadedServiceGroups.remove(groupId);
        if (family == openServiceFamily && !hasLoadedFamily(family))
        {
            openServiceFamily = -1;
            serviceAuthorized = false;
        }
    }

    private boolean hasLoadedFamily(int family)
    {
        for (int groupId : loadedServiceGroups)
        {
            if (serviceFamily(groupId) == family)
            {
                return true;
            }
        }
        return false;
    }

    public boolean isServiceOpen()
    {
        return openServiceFamily >= 0;
    }

    public boolean isServiceAuthorized()
    {
        return openServiceFamily >= 0 && serviceAuthorized;
    }

    public boolean isInterfaceActionAuthorized(
        MenuAction action,
        String option,
        int... packedWidgetIds)
    {
        int family = serviceFamilyFromPackedIds(packedWidgetIds);
        if (family == -1)
        {
            return true;
        }
        String value = normalise(option);
        if (value.isEmpty()
            || value.equals("close")
            || value.equals("back")
            || value.equals("cancel")
            || value.equals("continue")
            || value.equals("examine"))
        {
            return true;
        }
        if (!InteractionIntegrityRules.isFunctionalAction(
            action,
            value,
            false))
        {
            return true;
        }
        if (family == Integer.MIN_VALUE)
        {
            return false;
        }
        return serviceAuthorized && openServiceFamily == family;
    }

    static boolean canOpenService(
        MenuAction action,
        String option,
        String target)
    {
        if (!isWorldInteraction(action))
        {
            return false;
        }
        String value = normalise(option);
        String subject = normalise(target);
        if (startsWithAny(value,
            "trade", "shop", "buy", "sell", "claim", "collect",
            "tan", "decant",
            "make-over", "makeover", "rewards", "reward shop",
            "exchange", "ticket exchange", "repair",
            "insure", "slayer rewards", "assignment", "charter",
            "travel", "sail", "manage kingdom", "collect resources",
            "customise", "customize", "customise-boat", "customize-boat",
            "recover", "recover-boat", "manage crew", "select boat"))
        {
            return true;
        }
        if (!startsWithAny(value, "open", "use", "access", "manage"))
        {
            return false;
        }
        return containsPhrase(subject,
            "tanner", "tanning", "decant", "makeover", "make-over",
            "reward shop", "rewards", "ticket exchange", "coin exchanger",
            "slayer rewards", "pet insurance", "charter", "travel map",
            "repair locker", "kingdom management", "nightmare zone rewards",
            "pest control rewards", "soul wars rewards", "boat customisation",
            "boat customization", "ship customisation", "ship customization",
            "boat selection", "manage crew", "sailing crew",
            "castle wars shop", "pvp store", "league rewards",
            "event rewards", "supply chest", "raid supply chest",
            "theatre of blood supplies", "midway supplies");
    }

    static boolean isServiceDialogueChoice(
        String option,
        String target,
        String widgetText)
    {
        String combined = normalise(option) + " "
            + normalise(target) + " " + normalise(widgetText);
        return containsPhrase(combined,
            "buy", "sell", "trade", "shop", "browse the shop",
            "show me what you have", "tan hides", "tan my hides",
            "decant", "makeover", "make-over", "change my appearance",
            "slayer rewards",
            "slayer assignment", "reward shop", "show me the rewards",
            "ticket exchange", "exchange my tickets", "coin exchanger",
            "pet insurance", "insure my pet", "repair my items",
            "charter a ship", "travel map", "manage my kingdom",
            "collect my kingdom resources", "nightmare zone rewards",
            "pest control rewards", "soul wars rewards", "customise my boat",
            "customize my boat", "recover my boat", "manage my crew",
            "select a boat", "where would you like to go",
            "where do you want to go", "choose your destination",
            "which destination", "where shall i take you",
            "where can i take you", "would you like to travel",
            "where would you like to sail", "select a destination",
            "castle wars rewards", "pvp rewards", "league rewards",
            "event rewards", "buy supplies", "raid supplies");
    }

    static int serviceFamily(int groupId)
    {
        if (groupId == InterfaceID.MAKEOVER
            || groupId == InterfaceID.MAKEOVER_MAGE
            || groupId == InterfaceID.LOTG_MAKEOVER)
        {
            return InterfaceID.MAKEOVER;
        }
        if (groupId == InterfaceID.SLAYER_REWARDS
            || groupId == InterfaceID.SLAYER_REWARDS_TASK_LIST)
        {
            return InterfaceID.SLAYER_REWARDS;
        }
        if (groupId == InterfaceID.OMNISHOP_MAIN
            || groupId == InterfaceID.OMNISHOP_SIDE)
        {
            return InterfaceID.OMNISHOP_MAIN;
        }
        if (groupId == InterfaceID.SAILING_CUSTOMISATION
            || groupId == InterfaceID.SAILING_BOAT_SELECTION
            || groupId == InterfaceID.SAILING_CREW)
        {
            return InterfaceID.SAILING_CUSTOMISATION;
        }
        if (groupId == InterfaceID.CASTLEWARS_TRADE
            || groupId == InterfaceID.CASTLEWARS_SHOPSIDE)
        {
            return InterfaceID.CASTLEWARS_TRADE;
        }
        if (groupId == InterfaceID.PVP_STORE
            || groupId == InterfaceID.PVP_STORE_SIDE)
        {
            return InterfaceID.PVP_STORE;
        }
        if (groupId == InterfaceID.LEAGUE_REWARDS
            || groupId == InterfaceID.LEAGUE_SKILLCAPES_SHOP)
        {
            return InterfaceID.LEAGUE_REWARDS;
        }
        switch (groupId)
        {
            case InterfaceID.TANNER:
            case InterfaceID.DECANT:
            case InterfaceID.PET_INSURANCE:
            case InterfaceID.RANGINGGUILD_TICKETEXCHANGE:
            case InterfaceID.II_ELNOCKS_EXCHANGE:
            case InterfaceID.POG_COIN_EXCHANGER:
            case InterfaceID.CHARTERING_MENU_SIDE:
            case InterfaceID.SEASLUG_BOAT_TRAVEL:
            case InterfaceID.QUEST_FEVER_REPAIR_LOCKER:
            case InterfaceID.ZMI_BANK_PAYMENT:
            case InterfaceID.NZONE_REWARDS:
            case InterfaceID.AGILITYARENA_REWARDS:
            case InterfaceID.PEST_REWARDSHOP:
            case InterfaceID.SOUL_WARS_REWARDS:
            case InterfaceID.BARBASSAULT_REWARD_SHOP:
            case InterfaceID.BR_REWARD_SHOP:
            case InterfaceID.MAGICTRAINING_SHOP:
            case InterfaceID.CONSTRUCTION_CONTRACT_SHOP:
            case InterfaceID.CAMDOZAAL_RAMARNO_SHOP:
            case InterfaceID.FOSSIL_VOLCANIC_SHOP:
            case InterfaceID.GIANTS_FOUNDRY_REWARD_SHOP:
            case InterfaceID.PVP_ARENA_REWARDS:
            case InterfaceID.SPEEDRUNNING_REWARDS:
            case InterfaceID.CA_REWARDS:
            case InterfaceID.EVENT_REWARDS:
            case InterfaceID.TOB_MIDWAY_STORES:
                return groupId;
            default:
                return -1;
        }
    }

    private static int serviceFamilyFromPackedIds(int... packedWidgetIds)
    {
        if (packedWidgetIds == null)
        {
            return -1;
        }
        int selected = -1;
        for (int packedWidgetId : packedWidgetIds)
        {
            if (packedWidgetId < 0)
            {
                continue;
            }
            int family = serviceFamily(packedWidgetId >>> 16);
            if (family < 0)
            {
                continue;
            }
            if (selected >= 0 && selected != family)
            {
                return Integer.MIN_VALUE;
            }
            selected = family;
        }
        return selected;
    }

    private static boolean isObjectWorldInteraction(MenuAction action)
    {
        return action == MenuAction.GAME_OBJECT_FIRST_OPTION
            || action == MenuAction.GAME_OBJECT_SECOND_OPTION
            || action == MenuAction.GAME_OBJECT_THIRD_OPTION
            || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
            || action == MenuAction.GAME_OBJECT_FIFTH_OPTION;
    }

    private static boolean isWorldInteraction(MenuAction action)
    {
        return InteractionContextRules.isNpcAction(action)
            || isObjectWorldInteraction(action)
            || action == MenuAction.WALK
            || action == MenuAction.RUNELITE
            || action == MenuAction.RUNELITE_HIGH_PRIORITY
            || action == MenuAction.RUNELITE_LOW_PRIORITY
            || action == MenuAction.UNKNOWN;
    }

    private static boolean containsGroup(int groupId, int... packedWidgetIds)
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

    private static boolean withinWindow(
        int observedTick,
        int currentTick,
        int maximumTicks)
    {
        if (observedTick == Integer.MIN_VALUE)
        {
            return false;
        }
        int elapsed = currentTick - observedTick;
        return elapsed >= 0 && elapsed <= maximumTicks;
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

    private static boolean containsPhrase(String value, String... phrases)
    {
        String padded = " " + normalise(value)
            .replaceAll("[^a-z0-9]+", " ") + " ";
        for (String phrase : phrases)
        {
            String keyword = normalise(phrase)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
            if (!keyword.isEmpty()
                && padded.contains(" " + keyword + " "))
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
