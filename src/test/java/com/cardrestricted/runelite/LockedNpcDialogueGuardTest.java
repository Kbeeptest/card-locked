package com.cardrestricted.runelite;

import net.runelite.api.widgets.WidgetID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LockedNpcDialogueGuardTest
{
    @Test
    public void functionalDialogueFromLockedNpcIsBlocked()
    {
        LockedNpcDialogueGuard guard = new LockedNpcDialogueGuard();
        guard.observeNpcTalk(true, 100);
        int dialogue = packed(WidgetID.DIALOG_OPTION_GROUP_ID, 5);
        assertTrue(guard.shouldBlock(
            dialogue,
            -1,
            "Yes, please",
            "",
            "Would you like me to take you to Karamja for 30 coins?",
            102));
        assertTrue(guard.shouldBlock(
            dialogue,
            -1,
            "Show me what you have",
            "",
            "Browse my wares",
            103));
    }

    @Test
    public void ordinaryConversationRemainsAvailable()
    {
        LockedNpcDialogueGuard guard = new LockedNpcDialogueGuard();
        guard.observeNpcTalk(true, 100);
        int dialogue = packed(WidgetID.DIALOG_OPTION_GROUP_ID, 5);
        assertFalse(guard.shouldBlock(
            dialogue,
            -1,
            "What happened here?",
            "",
            "Tell me about the village.",
            102));
        assertFalse(guard.shouldBlock(
            packed(WidgetID.INVENTORY_GROUP_ID, 5),
            -1,
            "Buy",
            "",
            "",
            102));
    }

    @Test
    public void unlockedOrExpiredTalkContextDoesNotBlock()
    {
        LockedNpcDialogueGuard guard = new LockedNpcDialogueGuard();
        int dialogue = packed(WidgetID.DIALOG_OPTION_GROUP_ID, 5);
        guard.observeNpcTalk(false, 100);
        assertFalse(guard.shouldBlock(
            dialogue, -1, "Buy", "", "", 101));
        guard.observeNpcTalk(true, 100);
        assertFalse(guard.shouldBlock(
            dialogue, -1, "Buy", "", "", 301));
    }

    @Test
    public void specialistServicesReachedThroughTalkAreBlocked()
    {
        LockedNpcDialogueGuard guard = new LockedNpcDialogueGuard();
        guard.observeNpcTalk(true, 40);
        int dialogue = packed(WidgetID.DIALOG_OPTION_GROUP_ID, 7);
        assertTrue(guard.shouldBlock(
            dialogue, -1, "Yes please", "", "I can tan hides for you.", 41));
        assertTrue(guard.shouldBlock(
            dialogue, -1, "Decant my potions", "", "", 42));
        assertTrue(guard.shouldBlock(
            dialogue, -1, "Give me a task", "", "Slayer assignment", 43));
        assertTrue(guard.shouldBlock(
            dialogue, -1, "Note my items", "", "", 44));
    }

    private static int packed(int groupId, int childId)
    {
        return groupId << 16 | childId;
    }

    @Test
    public void destinationOnlyChoiceUsesTravelContextAndIsBlocked()
    {
        LockedNpcDialogueGuard guard = new LockedNpcDialogueGuard();
        guard.observeNpcTalk(true, 20);
        int dialogue = packed(WidgetID.DIALOG_OPTION_GROUP_ID, 4);
        assertTrue(guard.shouldBlock(
            dialogue,
            -1,
            "Port Sarim",
            "",
            "Where would you like to go?",
            21));
    }

}
