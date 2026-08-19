package com.cardrestricted.runelite;

import java.util.Locale;
import net.runelite.api.gameval.InterfaceID;

/**
 * Blocks clearly functional dialogue choices reached through the otherwise
 * permitted Talk-to option on a locked NPC. Ordinary conversation and quest
 * responses remain available; service/travel/shop/payment choices fail closed
 * when their displayed text reveals the functional action.
 */
public final class LockedNpcDialogueGuard
{
    private static final int CONTEXT_WINDOW_TICKS = 200;

    private int sourceTalkTick = Integer.MIN_VALUE;
    private boolean sourceLocked;

    public void reset()
    {
        sourceTalkTick = Integer.MIN_VALUE;
        sourceLocked = false;
    }

    public void observeNpcTalk(boolean locked, int clientTick)
    {
        sourceLocked = locked;
        sourceTalkTick = clientTick;
    }

    public void observeNonTalkNpcInteraction()
    {
        reset();
    }

    public boolean shouldBlock(
        int packedWidgetId,
        int eventWidgetId,
        String option,
        String target,
        String widgetText,
        int clientTick)
    {
        if (!sourceLocked || !withinWindow(clientTick))
        {
            return false;
        }
        if (!isDialogueOptionWidget(packedWidgetId)
            && !isDialogueOptionWidget(eventWidgetId))
        {
            return false;
        }
        String combined = normalise(option) + " "
            + normalise(target) + " "
            + normalise(widgetText);
        return containsFunctionalDialogueText(combined);
    }

    static boolean containsFunctionalDialogueText(String text)
    {
        String value = normalise(text);
        return containsAny(value,
            "buy", "sell", "trade", "shop", "store", "wares",
            "goods", "browse", "stock", "bank", "deposit", "withdraw",
            "collect", "claim", "reward", "travel", "teleport",
            "transport", "sail", "charter", "fare", "take me",
            "take you", "bring me", "bring you", "send me", "fly me",
            "journey", "destination", "passage", "boat ride",
            "ship passage", "minecart", "magic carpet", "pay", "hire",
            "reclaim", "repair", "exchange", "convert", "insure",
            "upgrade", "recharge", "charge", "heal me", "restore me",
            "makeover", "change my appearance", "slayer task",
            "slayer assignment", "give me an assignment",
            "give me a task", "cancel my task", "block this task",
            "tan hides", "tan my hides", "decant", "clean herbs",
            "crush", "grind", "smelt", "smith", "craft", "cook",
            "process", "enchant", "imbue", "note my items",
            "un-note", "unnote", "deliver items", "deliver my items",
            "access my bank", "access the bank", "open an account",
            "show me what you have", "what have you got",
            "where would you like to go", "where do you want to go",
            "choose your destination", "which destination",
            "where shall i take you", "where can i take you",
            "would you like to travel", "where would you like to sail",
            "select a destination");
    }

    static boolean isDialogueOptionWidget(int packedWidgetId)
    {
        return packedWidgetId >= 0
            && packedWidgetId >>> 16 == InterfaceID.CHATMENU;
    }

    private boolean withinWindow(int clientTick)
    {
        if (sourceTalkTick == Integer.MIN_VALUE)
        {
            return false;
        }
        int elapsed = clientTick - sourceTalkTick;
        return elapsed >= 0 && elapsed <= CONTEXT_WINDOW_TICKS;
    }

    private static boolean containsAny(String value, String... fragments)
    {
        String padded = " " + value.replaceAll("[^a-z0-9]+", " ") + " ";
        for (String fragment : fragments)
        {
            String keyword = normalise(fragment)
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
