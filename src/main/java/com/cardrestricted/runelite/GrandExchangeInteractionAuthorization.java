package com.cardrestricted.runelite;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;

/** Provenance proof for Grand Exchange interfaces opened through NPCs/booths. */
public final class GrandExchangeInteractionAuthorization
{
    private static final int DIRECT_OPEN_WINDOW_TICKS = 6;
    private static final int TALK_AUTO_OPEN_WINDOW_TICKS = 3;
    private static final int DIALOGUE_SOURCE_WINDOW_TICKS = 200;
    private static final int DIALOGUE_OPEN_WINDOW_TICKS = 10;

    private int pendingDirectOpenTick = Integer.MIN_VALUE;
    private int pendingDialogueOpenTick = Integer.MIN_VALUE;
    private int pendingTalkAutoOpenTick = Integer.MIN_VALUE;
    private int verifiedDialogueSourceTick = Integer.MIN_VALUE;
    private boolean exchangeOpen;
    private boolean exchangeAuthorized;
    private final Set<Integer> loadedExchangeGroups = new HashSet<>();

    public void reset()
    {
        pendingDirectOpenTick = Integer.MIN_VALUE;
        pendingDialogueOpenTick = Integer.MIN_VALUE;
        pendingTalkAutoOpenTick = Integer.MIN_VALUE;
        verifiedDialogueSourceTick = Integer.MIN_VALUE;
        exchangeOpen = false;
        exchangeAuthorized = false;
        loadedExchangeGroups.clear();
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
            exchangeAuthorized = false;
            return;
        }
        boolean recognised = canOpenExchange(action, value, target);
        boolean verifiedNpc = npcInteraction
            && npcDialogueSourceAuthorized
            && recognised;
        boolean verifiedObject = isObjectWorldInteraction(action) && recognised;
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
        exchangeAuthorized = false;
    }

    public boolean observeAllowedDialogueChoice(
        String option,
        String target,
        String widgetText,
        int clientTick,
        int... packedWidgetIds)
    {
        if (!containsGroup(InterfaceID.CHATMENU, packedWidgetIds)
            || !withinWindow(
                verifiedDialogueSourceTick,
                clientTick,
                DIALOGUE_SOURCE_WINDOW_TICKS)
            || !isExchangeDialogueChoice(option, target, widgetText))
        {
            return false;
        }
        pendingDialogueOpenTick = clientTick;
        pendingDirectOpenTick = Integer.MIN_VALUE;
        pendingTalkAutoOpenTick = Integer.MIN_VALUE;
        exchangeAuthorized = false;
        return true;
    }

    public void onWidgetLoaded(int groupId, int clientTick)
    {
        if (!isExchangeGroup(groupId))
        {
            return;
        }
        loadedExchangeGroups.add(groupId);
        if (!exchangeOpen)
        {
            exchangeOpen = true;
            exchangeAuthorized = withinWindow(
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
        if (!isExchangeGroup(groupId))
        {
            return;
        }
        loadedExchangeGroups.remove(groupId);
        if (loadedExchangeGroups.isEmpty())
        {
            reset();
        }
    }

    public boolean isExchangeOpen()
    {
        return exchangeOpen;
    }

    public boolean isExchangeAuthorized()
    {
        return exchangeOpen && exchangeAuthorized;
    }

    public boolean isInterfaceActionAuthorized(
        MenuAction action,
        String option,
        int... packedWidgetIds)
    {
        if (!isFunctionalExchangeInterfaceAction(
            action,
            option,
            packedWidgetIds))
        {
            return true;
        }
        return exchangeOpen && exchangeAuthorized;
    }

    static boolean isFunctionalExchangeInterfaceAction(
        MenuAction action,
        String option,
        int... packedWidgetIds)
    {
        if (!containsExchangeGroup(packedWidgetIds))
        {
            return false;
        }
        String value = normalise(option);
        if (value.isEmpty()
            || value.equals("close")
            || value.equals("back")
            || value.equals("examine")
            || value.equals("cancel"))
        {
            return false;
        }
        return InteractionIntegrityRules.isFunctionalAction(
            action,
            value,
            false);
    }


    public static boolean isExchangeGroup(int groupId)
    {
        return groupId == InterfaceID.GE_OFFERS
            || groupId == InterfaceID.GE_OFFERS_SIDE
            || groupId == InterfaceID.GE_COLLECT;
    }

    public static boolean isCollectionAction(
        String option,
        int... packedWidgetIds)
    {
        String value = normalise(option);
        return containsGroup(InterfaceID.GE_COLLECT, packedWidgetIds)
            && startsWithAny(value, "collect", "claim", "take");
    }

    private static boolean containsExchangeGroup(int... packedWidgetIds)
    {
        if (packedWidgetIds == null)
        {
            return false;
        }
        for (int packedWidgetId : packedWidgetIds)
        {
            if (packedWidgetId >= 0
                && isExchangeGroup(packedWidgetId >>> 16))
            {
                return true;
            }
        }
        return false;
    }

    static boolean canOpenExchange(
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
        return startsWithAny(value, "exchange", "grand exchange")
            || (startsWithAny(value, "open", "use")
                && containsPhrase(subject,
                    "grand exchange", "exchange booth", "exchange desk"));
    }

    static boolean isExchangeDialogueChoice(
        String option,
        String target,
        String widgetText)
    {
        String combined = normalise(option) + " "
            + normalise(target) + " " + normalise(widgetText);
        return containsPhrase(combined,
            "grand exchange", "exchange", "buy offer", "sell offer",
            "manage my offers", "open the exchange");
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
