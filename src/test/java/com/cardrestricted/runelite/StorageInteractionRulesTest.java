package com.cardrestricted.runelite;

import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StorageInteractionRulesTest
{
    @Test
    public void bankNavigationRequiresAnAuthenticStorageWidget()
    {
        assertTrue(StorageInteractionRules.isBankTabNavigation(
            MenuAction.CC_OP,
            "View tab",
            "Tab 2",
            packed(WidgetID.BANK_GROUP_ID, 10)));
        assertFalse(StorageInteractionRules.isBankTabNavigation(
            MenuAction.CC_OP,
            "View tab",
            "Tab 2",
            packed(WidgetID.INVENTORY_GROUP_ID, 10)));
        assertFalse(StorageInteractionRules.isBankTabNavigation(
            MenuAction.NPC_SECOND_OPTION,
            "View tab",
            "Guard",
            packed(WidgetID.BANK_GROUP_ID, 10)));
    }

    @Test
    public void bankingExemptionRequiresBothPresetAndStorageProvenance()
    {
        int bankItem = packed(WidgetID.BANK_GROUP_ID, 12);
        assertTrue(StorageInteractionRules.isSafeStorageItemAction(
            MenuAction.CC_OP,
            "Withdraw-1",
            true,
            bankItem));
        assertFalse(StorageInteractionRules.isSafeStorageItemAction(
            MenuAction.CC_OP,
            "Withdraw-1",
            false,
            bankItem));
        assertFalse(StorageInteractionRules.isSafeStorageItemAction(
            MenuAction.NPC_SECOND_OPTION,
            "Withdraw-1",
            true,
            bankItem));
        assertFalse(StorageInteractionRules.isSafeStorageItemAction(
            MenuAction.CC_OP,
            "Withdraw-1",
            true,
            packed(WidgetID.INVENTORY_GROUP_ID, 12)));
    }

    @Test
    public void pendingStateAllowsOnlyReversibleStorageRecovery()
    {
        int bankInventory = packed(WidgetID.BANK_INVENTORY_GROUP_ID, 5);
        assertTrue(StorageInteractionRules.isPendingRecoveryAction(
            MenuAction.CC_OP,
            "Deposit-All",
            bankInventory));
        assertTrue(StorageInteractionRules.isPendingRecoveryAction(
            MenuAction.CC_OP,
            "Remove",
            bankInventory));
        assertFalse(StorageInteractionRules.isPendingRecoveryAction(
            MenuAction.CC_OP,
            "Withdraw-1",
            bankInventory));
    }

    @Test
    public void extendedStorageInterfacesAreProvenanceBound()
    {
        assertTrue(StorageInteractionRules.isStorageGroup(
            InterfaceID.FOSSIL_STORAGE));
        assertTrue(StorageInteractionRules.isStorageGroup(
            InterfaceID.II_ELNOCK_STORAGE));
        assertTrue(StorageInteractionRules.isStorageGroup(
            InterfaceID.CLANS_STORAGE_MAIN));
        assertTrue(StorageInteractionRules.isStorageGroup(
            InterfaceID.SHARED_BANK));
        assertTrue(StorageInteractionRules.isStorageGroup(
            InterfaceID.DEATH_COFFER));
        assertTrue(StorageInteractionRules.isStorageTransferAction(
            MenuAction.CC_OP,
            "Deposit-all",
            packed(InterfaceID.DEATH_COFFER, 5)));
    }

    private static int packed(int groupId, int childId)
    {
        return groupId << 16 | childId;
    }
}
