package com.cardrestricted.runelite;

import java.util.Locale;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.WidgetID;

/**
 * Session-local proof that a bank or storage interface was opened through an
 * interaction already evaluated and allowed by Card Locked.
 *
 * <p>This prevents an interface left open while the plugin was disabled, or
 * opened through a locked NPC before enforcement became active, from becoming
 * a post-activation transfer bypass.</p>
 */
public final class StorageInteractionAuthorization
{
    private static final int DIRECT_OPEN_WINDOW_TICKS = 6;
    private static final int TALK_AUTO_OPEN_WINDOW_TICKS = 3;
    private static final int DIALOGUE_SOURCE_WINDOW_TICKS = 200;
    private static final int DIALOGUE_OPEN_WINDOW_TICKS = 10;

    private int pendingDirectOpenTick = Integer.MIN_VALUE;
    private int pendingDialogueOpenTick = Integer.MIN_VALUE;
    private int pendingTalkAutoOpenTick = Integer.MIN_VALUE;
    private int verifiedDialogueSourceTick = Integer.MIN_VALUE;
    private boolean storageOpen;
    private boolean storageAuthorized;

    public void reset()
    {
        pendingDirectOpenTick = Integer.MIN_VALUE;
        pendingDialogueOpenTick = Integer.MIN_VALUE;
        pendingTalkAutoOpenTick = Integer.MIN_VALUE;
        verifiedDialogueSourceTick = Integer.MIN_VALUE;
        storageOpen = false;
        storageAuthorized = false;
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
            storageAuthorized = false;
            return;
        }

        boolean recognisedOpen = canOpenStorage(action, value, target);
        boolean verifiedNpcSource = npcInteraction
            && npcDialogueSourceAuthorized
            && recognisedOpen;
        boolean verifiedObjectSource = isObjectWorldInteraction(action)
            && recognisedOpen;
        if (!verifiedNpcSource && !verifiedObjectSource)
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
        storageAuthorized = false;
    }

    public boolean observeAllowedDialogueChoice(
        String option,
        String target,
        String widgetText,
        int clientTick,
        int... packedWidgetIds)
    {
        if (!containsDialogueWidget(packedWidgetIds)
            || !withinWindow(
                verifiedDialogueSourceTick,
                clientTick,
                DIALOGUE_SOURCE_WINDOW_TICKS)
            || !isStorageDialogueChoice(option, target, widgetText))
        {
            return false;
        }
        pendingDialogueOpenTick = clientTick;
        pendingDirectOpenTick = Integer.MIN_VALUE;
        pendingTalkAutoOpenTick = Integer.MIN_VALUE;
        storageAuthorized = false;
        return true;
    }

    public void onWidgetLoaded(int groupId, int clientTick)
    {
        if (!StorageInteractionRules.isStorageGroup(groupId))
        {
            return;
        }
        if (!storageOpen)
        {
            storageOpen = true;
            storageAuthorized = withinWindow(
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
        if (StorageInteractionRules.isPrimaryStorageGroup(groupId))
        {
            reset();
        }
    }

    public boolean isTransferAuthorized(
        MenuAction action,
        String option,
        int... packedWidgetIds)
    {
        if (!StorageInteractionRules.isStorageTransferAction(
            action,
            option,
            packedWidgetIds))
        {
            return true;
        }
        return storageOpen && storageAuthorized;
    }

    public boolean isStorageOpen()
    {
        return storageOpen;
    }

    public boolean isStorageAuthorized()
    {
        return storageOpen && storageAuthorized;
    }

    static boolean canOpenStorage(
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
            "bank", "deposit", "access-bank", "access bank",
            "use-bank", "use bank", "open-bank", "open bank"))
        {
            return true;
        }
        if (!startsWithAny(value, "open", "use", "access", "store"))
        {
            return false;
        }
        return containsPhrase(subject,
            "bank booth", "bank chest", "bank deposit box",
            "deposit box", "seed vault", "group storage",
            "storage unit", "treasure chest", "storage chest",
            "fossil storage", "elnock storage", "clan storage",
            "shared bank", "death coffer", "bank coffer", "bank counter",
            "cargo hold", "boat cargo hold", "ship cargo hold",
            "drift net storage", "driftnet storage");
    }

    static boolean isStorageDialogueChoice(
        String option,
        String target,
        String widgetText)
    {
        String combined = normalise(option) + " "
            + normalise(target) + " " + normalise(widgetText);
        return containsPhrase(combined,
            "bank", "access my bank", "access the bank",
            "open my bank", "open the bank", "deposit box",
            "seed vault", "group storage", "storage unit",
            "fossil storage", "elnock storage", "clan storage",
            "shared bank", "death coffer", "cargo hold",
            "boat cargo hold", "ship cargo hold", "drift net storage",
            "driftnet storage");
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

    private static boolean containsDialogueWidget(int... packedWidgetIds)
    {
        if (packedWidgetIds == null)
        {
            return false;
        }
        for (int packedWidgetId : packedWidgetIds)
        {
            if (packedWidgetId >= 0
                && packedWidgetId >>> 16 == WidgetID.DIALOG_OPTION_GROUP_ID)
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
