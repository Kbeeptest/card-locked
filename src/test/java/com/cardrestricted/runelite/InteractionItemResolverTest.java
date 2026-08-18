package com.cardrestricted.runelite;

import java.util.Set;
import net.runelite.api.MenuAction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class InteractionItemResolverTest
{
    private static int packed(int groupId, int childId)
    {
        return groupId << 16 | childId & 0xffff;
    }

    @Test
    public void itemOnItemIncludesBothClickedAndSelectedItems()
    {
        Set<Integer> resolved = InteractionItemResolver.resolve(
            MenuAction.ITEM_USE_ON_ITEM,
            true,
            100,
            101,
            -1,
            102,
            packed(149, 0),
            103,
            packed(149, 1),
            true,
            104,
            packed(149, 2));

        assertEquals(Set.of(100, 101, 102, 103, 104), resolved);
    }

    @Test
    public void itemOnNpcUsesSelectedItemButNotNpcMetadata()
    {
        assertFalse(InteractionContextRules.shouldReadClickedItemMetadata(
            MenuAction.ITEM_USE_ON_NPC,
            false,
            false));
        Set<Integer> resolved = InteractionItemResolver.resolve(
            MenuAction.ITEM_USE_ON_NPC,
            false,
            200,
            201,
            202,
            203,
            packed(149, 0),
            204,
            packed(149, 1),
            true,
            205,
            packed(149, 2));

        assertEquals(Set.of(205), resolved);
    }

    @Test
    public void itemOnObjectUsesSelectedItemButNotObjectMetadata()
    {
        Set<Integer> resolved = InteractionItemResolver.resolve(
            MenuAction.ITEM_USE_ON_GAME_OBJECT,
            false,
            300,
            301,
            302,
            303,
            packed(149, 0),
            304,
            packed(149, 1),
            true,
            305,
            packed(149, 2));

        assertEquals(Set.of(305), resolved);
    }

    @Test
    public void spellOnItemIncludesTargetButExcludesSpellWidget()
    {
        int spellbook = packed(SpellbookWidgetRules.SPELLBOOK_GROUP_ID, 24);
        assertTrue(InteractionContextRules.shouldReadClickedItemMetadata(
            MenuAction.WIDGET_USE_ON_ITEM,
            false,
            true));
        Set<Integer> resolved = InteractionItemResolver.resolve(
            MenuAction.WIDGET_USE_ON_ITEM,
            true,
            400,
            401,
            -1,
            402,
            packed(149, 0),
            -1,
            spellbook,
            true,
            -1,
            spellbook);

        assertEquals(Set.of(400, 401, 402), resolved);
    }

    @Test
    public void spellOnGroundItemIncludesGroundIdentifierOnlyAsItemTarget()
    {
        int spellbook = packed(SpellbookWidgetRules.SPELLBOOK_GROUP_ID, 30);
        assertTrue(InteractionContextRules.shouldReadClickedItemMetadata(
            MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,
            false,
            true));
        Set<Integer> resolved = InteractionItemResolver.resolve(
            MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,
            true,
            -1,
            -1,
            500,
            -1,
            spellbook,
            -1,
            spellbook,
            true,
            -1,
            spellbook);

        assertEquals(Set.of(500), resolved);
    }

    @Test
    public void spellOnNpcDoesNotInventAnItemInteraction()
    {
        int spellbook = packed(SpellbookWidgetRules.SPELLBOOK_GROUP_ID, 31);
        assertFalse(InteractionContextRules.shouldReadClickedItemMetadata(
            MenuAction.WIDGET_TARGET_ON_NPC,
            false,
            true));
        Set<Integer> resolved = InteractionItemResolver.resolve(
            MenuAction.WIDGET_TARGET_ON_NPC,
            false,
            600,
            601,
            602,
            603,
            spellbook,
            604,
            spellbook,
            true,
            -1,
            spellbook);

        assertTrue(resolved.isEmpty());
    }

    @Test
    public void groundItemActionsUseTheEntryIdentifier()
    {
        Set<Integer> resolved = InteractionItemResolver.resolve(
            MenuAction.GROUND_ITEM_FIRST_OPTION,
            true,
            -1,
            -1,
            700,
            -1,
            -1,
            -1,
            -1,
            false,
            -1,
            -1);

        assertEquals(Set.of(700), resolved);
    }

    @Test
    public void staleMetadataIsIgnoredForUnrelatedWidgetClicks()
    {
        Set<Integer> resolved = InteractionItemResolver.resolve(
            MenuAction.WIDGET_FIRST_OPTION,
            false,
            800,
            801,
            802,
            803,
            packed(12, 0),
            804,
            packed(12, 1),
            true,
            805,
            packed(149, 0));

        assertTrue(resolved.isEmpty());
    }
}
