package com.cardrestricted.selftest;

import com.cardrestricted.runelite.GrandExchangeInteractionAuthorization;
import com.cardrestricted.runelite.NpcServiceInterfaceAuthorization;
import com.cardrestricted.runelite.ShopInteractionAuthorization;
import com.cardrestricted.runelite.StorageInteractionAuthorization;
import net.runelite.api.MenuAction;

/**
 * Deterministic event-order harness for the four stateful interface provenance
 * gates. It deliberately mirrors the plugin's world -> dialogue -> widget ->
 * transaction lifecycle without requiring a live RuneLite client.
 */
final class IntegrityReplayHarness
{
    private final ShopInteractionAuthorization shop =
        new ShopInteractionAuthorization();
    private final StorageInteractionAuthorization storage =
        new StorageInteractionAuthorization();
    private final GrandExchangeInteractionAuthorization exchange =
        new GrandExchangeInteractionAuthorization();
    private final NpcServiceInterfaceAuthorization service =
        new NpcServiceInterfaceAuthorization();
    private int tick;

    IntegrityReplayHarness at(int clientTick)
    {
        this.tick = clientTick;
        return this;
    }

    IntegrityReplayHarness advance(int ticks)
    {
        tick += Math.max(0, ticks);
        return this;
    }

    IntegrityReplayHarness world(
        MenuAction action,
        String option,
        String target,
        boolean npcInteraction,
        boolean npcSourceAuthorized)
    {
        shop.observeAllowedWorldInteraction(
            action, option, target, tick, npcInteraction, npcSourceAuthorized);
        storage.observeAllowedWorldInteraction(
            action, option, target, tick, npcInteraction, npcSourceAuthorized);
        exchange.observeAllowedWorldInteraction(
            action, option, target, tick, npcInteraction, npcSourceAuthorized);
        service.observeAllowedWorldInteraction(
            action, option, target, tick, npcInteraction, npcSourceAuthorized);
        return this;
    }

    IntegrityReplayHarness dialogue(
        MenuAction action,
        String option,
        String target,
        String widgetText,
        int... packedWidgetIds)
    {
        shop.observeAllowedDialogueChoice(
            action, option, target, widgetText, tick, packedWidgetIds);
        storage.observeAllowedDialogueChoice(
            option, target, widgetText, tick, packedWidgetIds);
        exchange.observeAllowedDialogueChoice(
            option, target, widgetText, tick, packedWidgetIds);
        service.observeAllowedDialogueChoice(
            option, target, widgetText, tick, packedWidgetIds);
        return this;
    }

    IntegrityReplayHarness load(int groupId)
    {
        shop.onWidgetLoaded(groupId, tick);
        storage.onWidgetLoaded(groupId, tick);
        exchange.onWidgetLoaded(groupId, tick);
        service.onWidgetLoaded(groupId, tick);
        return this;
    }

    IntegrityReplayHarness close(int groupId)
    {
        shop.onWidgetClosed(groupId);
        storage.onWidgetClosed(groupId);
        exchange.onWidgetClosed(groupId);
        service.onWidgetClosed(groupId);
        return this;
    }

    IntegrityReplayHarness reset()
    {
        shop.reset();
        storage.reset();
        exchange.reset();
        service.reset();
        return this;
    }

    boolean shopAllowed(MenuAction action, String option, int... widgets)
    {
        return shop.isTransactionAuthorized(action, option, tick, widgets);
    }

    boolean storageAllowed(MenuAction action, String option, int... widgets)
    {
        return storage.isTransferAuthorized(action, option, widgets);
    }

    boolean exchangeAllowed(MenuAction action, String option, int... widgets)
    {
        return exchange.isInterfaceActionAuthorized(action, option, widgets);
    }

    boolean serviceAllowed(MenuAction action, String option, int... widgets)
    {
        return service.isInterfaceActionAuthorized(action, option, widgets);
    }

    ShopInteractionAuthorization shop()
    {
        return shop;
    }

    StorageInteractionAuthorization storage()
    {
        return storage;
    }

    GrandExchangeInteractionAuthorization exchange()
    {
        return exchange;
    }

    NpcServiceInterfaceAuthorization service()
    {
        return service;
    }
}
