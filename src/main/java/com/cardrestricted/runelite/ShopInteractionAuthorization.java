package com.cardrestricted.runelite;

import java.util.Locale;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;

/**
 * Session-local proof that a shop interface was opened through an interaction
 * Card Locked evaluated and allowed. This closes startup/re-enable, rewritten
 * widget and dialogue-opened shop bypasses.
 */
public final class ShopInteractionAuthorization
{
    private static final int DIRECT_OPEN_WINDOW_TICKS = 4;
    private static final int TALK_AUTO_OPEN_WINDOW_TICKS = 3;
    private static final int DIALOGUE_SOURCE_WINDOW_TICKS = 200;
    private static final int DIALOGUE_OPEN_WINDOW_TICKS = 8;
    private static final int CUSTOM_INTERFACE_SOURCE_WINDOW_TICKS = 200;

    private int pendingDirectOpenTick = Integer.MIN_VALUE;
    private int pendingDialogueOpenTick = Integer.MIN_VALUE;
    private int pendingTalkAutoOpenTick = Integer.MIN_VALUE;
    private int verifiedDialogueSourceTick = Integer.MIN_VALUE;
    private boolean shopOpen;
    private boolean shopAuthorized;
    private int authorizedCustomGroupId = -1;

    public void reset()
    {
        pendingDirectOpenTick = Integer.MIN_VALUE;
        pendingDialogueOpenTick = Integer.MIN_VALUE;
        pendingTalkAutoOpenTick = Integer.MIN_VALUE;
        verifiedDialogueSourceTick = Integer.MIN_VALUE;
        shopOpen = false;
        shopAuthorized = false;
        authorizedCustomGroupId = -1;
    }

    /** Compatibility overload retained for the original direct-shop path. */
    public void observeAllowedWorldInteraction(
        MenuAction action,
        String option,
        int clientTick)
    {
        observeAllowedWorldInteraction(
            action,
            option,
            "",
            clientTick,
            InteractionContextRules.isNpcAction(action),
            false);
    }

    /**
     * Records either a direct Trade/Shop/Open-shop action or an unlocked
     * Talk-to source. A verified unlocked Talk-to may authorise an interface
     * that opens immediately, while delayed dialogue flows still require a
     * recognised shop choice.
     */
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
            shopAuthorized = false;
            return;
        }
        boolean recognisedDirectShopOpen = canOpenShop(
            action,
            value,
            target);
        boolean verifiedNpcDirectSource = npcInteraction
            && npcDialogueSourceAuthorized
            && recognisedDirectShopOpen;
        boolean verifiedObjectSource = isObjectWorldInteraction(action)
            && recognisedDirectShopOpen;
        if (!verifiedNpcDirectSource && !verifiedObjectSource)
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
        shopAuthorized = false;
    }

    /**
     * Converts a recent unlocked Talk-to proof into a short-lived shop-open
     * proof only when a genuine dialogue option visibly requests a shop.
     */
    public boolean observeAllowedDialogueChoice(
        MenuAction action,
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
                DIALOGUE_SOURCE_WINDOW_TICKS))
        {
            return false;
        }
        if (!isShopDialogueChoice(option, target, widgetText))
        {
            return false;
        }
        pendingDialogueOpenTick = clientTick;
        pendingDirectOpenTick = Integer.MIN_VALUE;
        pendingTalkAutoOpenTick = Integer.MIN_VALUE;
        shopAuthorized = false;
        return true;
    }

    public void onWidgetLoaded(int groupId, int clientTick)
    {
        if (!isStandardShopGroup(groupId))
        {
            return;
        }
        // The inventory-side group can load before the main shop group. Only
        // establish provenance once so the second load cannot erase it.
        if (!shopOpen)
        {
            shopOpen = true;
            authorizedCustomGroupId = -1;
            shopAuthorized = withinWindow(
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
        if (groupId == InterfaceID.SHOPMAIN
            || groupId == authorizedCustomGroupId)
        {
            reset();
        }
    }

    public boolean isTransactionAuthorized(
        String option,
        int... packedWidgetIds)
    {
        return isTransactionAuthorized(
            null,
            option,
            Integer.MIN_VALUE,
            packedWidgetIds);
    }

    /**
     * Distinguishes a world-menu shop opener from a Buy/Sell transaction. A
     * stripped widget transaction fails closed. A Buy/Sell dialogue option is
     * allowed only after a recently verified unlocked Talk-to source, and must
     * then be observed as the explicit shop-opening dialogue choice.
     */
    public boolean isTransactionAuthorized(
        MenuAction action,
        String option,
        int clientTick,
        int... packedWidgetIds)
    {
        String value = normalise(option);
        if (!isBuyOrSell(value))
        {
            return true;
        }
        if (containsKnownNonShopMarketWidget(packedWidgetIds))
        {
            return true;
        }
        boolean shopWidget = containsShopWidget(packedWidgetIds);
        if (shopWidget)
        {
            return shopOpen && shopAuthorized;
        }
        if (containsDialogueWidget(packedWidgetIds))
        {
            return withinWindow(
                verifiedDialogueSourceTick,
                clientTick,
                DIALOGUE_SOURCE_WINDOW_TICKS);
        }
        int customGroupId = customTransactionGroup(packedWidgetIds);
        if (customGroupId == Integer.MIN_VALUE)
        {
            return false;
        }
        if (customGroupId >= 0)
        {
            if (shopOpen && authorizedCustomGroupId == customGroupId)
            {
                return shopAuthorized;
            }
            boolean recentVerifiedSource = withinWindow(
                    pendingDirectOpenTick,
                    clientTick,
                    CUSTOM_INTERFACE_SOURCE_WINDOW_TICKS)
                || withinWindow(
                    pendingDialogueOpenTick,
                    clientTick,
                    CUSTOM_INTERFACE_SOURCE_WINDOW_TICKS)
                || withinWindow(
                    pendingTalkAutoOpenTick,
                    clientTick,
                    TALK_AUTO_OPEN_WINDOW_TICKS);
            if (!recentVerifiedSource)
            {
                return false;
            }
            shopOpen = true;
            shopAuthorized = true;
            authorizedCustomGroupId = customGroupId;
            pendingDirectOpenTick = Integer.MIN_VALUE;
            pendingDialogueOpenTick = Integer.MIN_VALUE;
            pendingTalkAutoOpenTick = Integer.MIN_VALUE;
            return true;
        }
        return isWorldInteraction(action);
    }

    public boolean isShopOpen()
    {
        return shopOpen;
    }

    public boolean isShopAuthorized()
    {
        return shopOpen && shopAuthorized;
    }

    public static boolean isShopTransaction(
        String option,
        int... packedWidgetIds)
    {
        return isShopTransaction(option, false, packedWidgetIds);
    }

    static boolean isShopTransaction(
        String option,
        boolean shopAlreadyOpen,
        int... packedWidgetIds)
    {
        String value = normalise(option);
        if (!isBuyOrSell(value))
        {
            return false;
        }
        return shopAlreadyOpen || containsShopWidget(packedWidgetIds);
    }

    static boolean canOpenShop(
        MenuAction action,
        String option,
        String target)
    {
        String value = normalise(option);
        String subject = normalise(target);
        boolean directShopVerb = value.equals("trade")
            || value.startsWith("trade ")
            || value.startsWith("trade-")
            || value.equals("shop")
            || value.startsWith("shop ")
            || value.startsWith("open shop")
            || value.startsWith("open-shop")
            || isBuyOrSell(value);
        boolean identifiedShopObject = value.equals("open")
            && (subject.contains("shop")
                || subject.contains("store")
                || subject.contains("stall")
                || subject.contains("counter"));
        if (!directShopVerb && !identifiedShopObject)
        {
            return false;
        }
        return isWorldInteraction(action);
    }

    static boolean isShopDialogueChoice(
        String option,
        String target,
        String widgetText)
    {
        String combined = normalise(option) + " "
            + normalise(target) + " "
            + normalise(widgetText);
        return containsPhrase(combined,
            "buy", "sell", "trade", "shop", "store", "wares",
            "goods", "browse", "stock", "show me what you have",
            "what have you got", "open the shop", "open shop");
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
        return action == MenuAction.NPC_FIRST_OPTION
            || action == MenuAction.NPC_SECOND_OPTION
            || action == MenuAction.NPC_THIRD_OPTION
            || action == MenuAction.NPC_FOURTH_OPTION
            || action == MenuAction.NPC_FIFTH_OPTION
            || action == MenuAction.GAME_OBJECT_FIRST_OPTION
            || action == MenuAction.GAME_OBJECT_SECOND_OPTION
            || action == MenuAction.GAME_OBJECT_THIRD_OPTION
            || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
            || action == MenuAction.GAME_OBJECT_FIFTH_OPTION
            || action == MenuAction.WALK
            || action == MenuAction.RUNELITE
            || action == MenuAction.RUNELITE_HIGH_PRIORITY
            || action == MenuAction.RUNELITE_LOW_PRIORITY
            || action == MenuAction.RUNELITE_WIDGET
            || action == MenuAction.UNKNOWN;
    }

    private static int customTransactionGroup(int... packedWidgetIds)
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
            int groupId = packedWidgetId >>> 16;
            if (groupId <= 0
                || isStandardShopGroup(groupId)
                || GrandExchangeInteractionAuthorization.isExchangeGroup(groupId)
                || groupId == InterfaceID.CHATMENU
                || groupId == InterfaceID.TRADEMAIN)
            {
                continue;
            }
            if (selected >= 0 && selected != groupId)
            {
                return Integer.MIN_VALUE;
            }
            selected = groupId;
        }
        return selected;
    }

    private static boolean containsKnownNonShopMarketWidget(
        int... packedWidgetIds)
    {
        return containsWidgetGroup(
                InterfaceID.GE_OFFERS,
                packedWidgetIds)
            || containsWidgetGroup(
                InterfaceID.GE_OFFERS_SIDE,
                packedWidgetIds)
            || containsWidgetGroup(InterfaceID.GE_COLLECT, packedWidgetIds);
    }

    private static boolean containsShopWidget(int... packedWidgetIds)
    {
        return containsWidgetGroup(InterfaceID.SHOPMAIN, packedWidgetIds)
            || containsWidgetGroup(
                InterfaceID.SHOPSIDE,
                packedWidgetIds);
    }

    private static boolean isStandardShopGroup(int groupId)
    {
        return groupId == InterfaceID.SHOPMAIN
            || groupId == InterfaceID.SHOPSIDE;
    }

    private static boolean containsDialogueWidget(int... packedWidgetIds)
    {
        return containsWidgetGroup(
            InterfaceID.CHATMENU,
            packedWidgetIds);
    }

    private static boolean containsWidgetGroup(
        int groupId,
        int... packedWidgetIds)
    {
        if (packedWidgetIds == null)
        {
            return false;
        }
        for (int packedWidgetId : packedWidgetIds)
        {
            if (packedWidgetId >= 0
                && packedWidgetId >>> 16 == groupId)
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isBuyOption(String option)
    {
        String value = normalise(option);
        return value.equals("buy")
            || value.startsWith("buy ")
            || value.startsWith("buy-")
            || value.equals("purchase")
            || value.startsWith("purchase ")
            || value.startsWith("purchase-");
    }

    public static boolean isSellOption(String option)
    {
        String value = normalise(option);
        return value.equals("sell")
            || value.startsWith("sell ")
            || value.startsWith("sell-");
    }

    public static boolean isBuyOrSellOption(String option)
    {
        return isBuyOption(option) || isSellOption(option);
    }

    private static boolean isBuyOrSell(String value)
    {
        return isBuyOrSellOption(value);
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
