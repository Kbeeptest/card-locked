package com.cardrestricted.runelite;

import net.runelite.api.MenuAction;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class InteractionContextRulesTest
{
    private static int packed(int groupId, int childId)
    {
        return groupId << 16 | childId & 0xffff;
    }

    @Test
    public void staleSelectedWidgetIsNotAppliedToUnrelatedClicks()
    {
        int inventoryWidget = packed(149, 0);
        assertFalse(InteractionContextRules.shouldIncludeSelectedItem(
            false,
            MenuAction.ITEM_USE_ON_NPC,
            inventoryWidget));
        assertFalse(InteractionContextRules.shouldIncludeSelectedItem(
            true,
            MenuAction.NPC_FIRST_OPTION,
            inventoryWidget));
        assertTrue(InteractionContextRules.shouldIncludeSelectedItem(
            true,
            MenuAction.ITEM_USE_ON_NPC,
            inventoryWidget));
        assertFalse(InteractionContextRules.shouldIncludeSelectedItem(
            true,
            MenuAction.ITEM_USE_ON_NPC,
            packed(SpellbookWidgetRules.SPELLBOOK_GROUP_ID, 24)));
    }

    @Test
    public void itemNamesAreOnlyParsedInItemContexts()
    {
        assertTrue(InteractionContextRules.shouldUseItemNameFallback(
            MenuAction.ITEM_FIRST_OPTION,
            true,
            false));
        assertTrue(InteractionContextRules.shouldUseItemNameFallback(
            MenuAction.GROUND_ITEM_FIRST_OPTION,
            false,
            false));
        assertTrue(InteractionContextRules.shouldUseItemNameFallback(
            MenuAction.ITEM_USE_ON_ITEM,
            false,
            false));
        assertFalse(InteractionContextRules.shouldUseItemNameFallback(
            MenuAction.NPC_FIRST_OPTION,
            false,
            false));
        assertFalse(InteractionContextRules.shouldUseItemNameFallback(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            false,
            false));
        assertFalse(InteractionContextRules.shouldUseItemNameFallback(
            MenuAction.ITEM_FIRST_OPTION,
            true,
            true));
    }

    @Test
    public void staleClickedItemMetadataIsIgnoredOutsideItemContexts()
    {
        assertTrue(InteractionContextRules.shouldReadClickedItemMetadata(
            MenuAction.ITEM_SECOND_OPTION,
            true,
            false));
        assertTrue(InteractionContextRules.shouldReadClickedItemMetadata(
            MenuAction.GROUND_ITEM_FIRST_OPTION,
            false,
            false));
        assertFalse(InteractionContextRules.shouldReadClickedItemMetadata(
            MenuAction.NPC_FIRST_OPTION,
            false,
            false));
        assertFalse(InteractionContextRules.shouldReadClickedItemMetadata(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            false,
            false));
        assertFalse(InteractionContextRules.shouldReadClickedItemMetadata(
            MenuAction.ITEM_FIRST_OPTION,
            true,
            true));
    }

    @Test
    public void equipmentWidgetsAreDistinguishedFromStorageWidgets()
    {
        assertTrue(InteractionContextRules.isEquipmentWidget(
            packed(387, 0)));
        assertTrue(InteractionContextRules.isEquipmentWidget(
            packed(85, 0)));
        assertFalse(InteractionContextRules.isEquipmentWidget(
            packed(149, 0)));
        assertFalse(InteractionContextRules.isEquipmentWidget(
            packed(12, 0)));
        assertFalse(InteractionContextRules.isEquipmentWidget(-1));
    }

    @Test
    public void groundItemIdentifiersAreRecognisedPrecisely()
    {
        assertTrue(InteractionContextRules.isGroundItemAction(
            MenuAction.GROUND_ITEM_THIRD_OPTION));
        assertTrue(InteractionContextRules.isGroundItemAction(
            MenuAction.ITEM_USE_ON_GROUND_ITEM));
        assertFalse(InteractionContextRules.isGroundItemAction(
            MenuAction.ITEM_USE_ON_NPC));
    }
}
