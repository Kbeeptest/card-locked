package com.cardrestricted.runelite;

import java.util.Locale;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;

/**
 * Verifies that banking/storage recovery text originates from a genuine
 * storage interface rather than trusting menu wording alone.
 */
public final class StorageInteractionRules
{
    private StorageInteractionRules()
    {
    }

    public static boolean isStorageWidget(int... packedWidgetIds)
    {
        if (packedWidgetIds == null)
        {
            return false;
        }
        for (int packedWidgetId : packedWidgetIds)
        {
            if (packedWidgetId < 0)
            {
                continue;
            }
            int groupId = packedWidgetId >>> 16;
            if (isStorageGroup(groupId))
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isAuthenticStorageContext(
        MenuAction action,
        int... packedWidgetIds)
    {
        return isStorageWidget(packedWidgetIds)
            && !isWorldEntityAction(action);
    }

    public static boolean isBankTabNavigation(
        MenuAction action,
        String option,
        String target,
        int... packedWidgetIds)
    {
        return isAuthenticStorageContext(action, packedWidgetIds)
            && BankTabInteractionRules.isBankTabNavigation(option, target);
    }

    /**
     * Storage actions that may be exempted when the active preset explicitly
     * permits banking locked items.
     */
    public static boolean isSafeStorageItemAction(
        MenuAction action,
        String option,
        boolean allowLockedItemBanking,
        int... packedWidgetIds)
    {
        if (!allowLockedItemBanking
            || !isAuthenticStorageContext(action, packedWidgetIds))
        {
            return false;
        }
        String value = normalise(option);
        return value.startsWith("deposit")
            || value.startsWith("withdraw")
            || value.startsWith("store")
            || value.startsWith("remove from")
            || value.equals("remove")
            || value.startsWith("empty")
            || value.startsWith("fill");
    }

    /**
     * During an unknown profile state only reversible removal/recovery actions
     * are allowed. Withdrawing an unknown item remains blocked until the
     * profile permissions are known.
     */
    public static boolean isPendingRecoveryAction(
        MenuAction action,
        String option,
        int... packedWidgetIds)
    {
        if (!isAuthenticStorageContext(action, packedWidgetIds))
        {
            return false;
        }
        String value = normalise(option);
        return value.startsWith("deposit")
            || value.startsWith("store")
            || value.startsWith("empty")
            || value.equals("remove")
            || value.startsWith("remove from inventory");
    }

    public static boolean isStorageGroup(int groupId)
    {
        return groupId == InterfaceID.BANKMAIN
            || groupId == InterfaceID.BANKSIDE
            || groupId == InterfaceID.BANK_DEPOSITBOX
            || groupId == InterfaceID.SEED_VAULT
            || groupId == InterfaceID.SEED_VAULT_DEPOSIT
            || groupId == InterfaceID.SHARED_BANK
            || groupId == InterfaceID.SHARED_BANK_SIDE
            || groupId == InterfaceID.RAIDS_STORAGE_PRIVATE
            || groupId == InterfaceID.RAIDS_STORAGE_SHARED
            || groupId == InterfaceID.RAIDS_STORAGE_SIDE
            || groupId == InterfaceID.POH_COSTUMES_SIDE
            || groupId == InterfaceID.FOSSIL_STORAGE
            || groupId == InterfaceID.FOSSIL_STORAGE_INV
            || groupId == InterfaceID.II_ELNOCK_STORAGE
            || groupId == InterfaceID.II_ELNOCK_STORAGE_SIDE
            || groupId == InterfaceID.CLANS_STORAGE_MAIN
            || groupId == InterfaceID.CLANS_STORAGE_SIDE
            || groupId == InterfaceID.SHARED_BANK
            || groupId == InterfaceID.SHARED_BANK_SIDE
            || groupId == InterfaceID.DEATH_COFFER
            || groupId == InterfaceID.DEATH_COFFER_SIDE
            || groupId == InterfaceID.FOSSIL_DRIFTNET_STORE
            || groupId == InterfaceID.FOSSIL_DRIFTNET_SIDESTORE
            || groupId == InterfaceID.BANK_DEPOSIT_IMP
            || groupId == InterfaceID.SEED_VAULT_DEPOSIT
            || groupId == InterfaceID.SAILING_BOAT_CARGOHOLD
            || groupId == InterfaceID.SAILING_BOAT_CARGOHOLD_SIDE;
    }

    public static boolean isPrimaryStorageGroup(int groupId)
    {
        return groupId == InterfaceID.BANKMAIN
            || groupId == InterfaceID.BANK_DEPOSITBOX
            || groupId == InterfaceID.SEED_VAULT
            || groupId == InterfaceID.SHARED_BANK
            || groupId == InterfaceID.RAIDS_STORAGE_PRIVATE
            || groupId == InterfaceID.RAIDS_STORAGE_SHARED
            || groupId == InterfaceID.POH_COSTUMES_SIDE
            || groupId == InterfaceID.FOSSIL_STORAGE
            || groupId == InterfaceID.II_ELNOCK_STORAGE
            || groupId == InterfaceID.CLANS_STORAGE_MAIN
            || groupId == InterfaceID.SHARED_BANK
            || groupId == InterfaceID.DEATH_COFFER
            || groupId == InterfaceID.FOSSIL_DRIFTNET_STORE
            || groupId == InterfaceID.BANK_DEPOSIT_IMP
            || groupId == InterfaceID.SEED_VAULT_DEPOSIT
            || groupId == InterfaceID.SAILING_BOAT_CARGOHOLD;
    }

    public static boolean isStorageTransferAction(
        MenuAction action,
        String option,
        int... packedWidgetIds)
    {
        if (!isAuthenticStorageContext(action, packedWidgetIds))
        {
            return false;
        }
        String value = normalise(option);
        return value.startsWith("deposit")
            || value.startsWith("withdraw")
            || value.startsWith("store")
            || value.startsWith("remove from")
            || value.equals("remove")
            || value.startsWith("empty")
            || value.startsWith("fill")
            || value.startsWith("bank")
            || value.startsWith("take")
            || value.startsWith("retrieve");
    }

    private static boolean isWorldEntityAction(MenuAction action)
    {
        if (action == null)
        {
            return false;
        }
        return InteractionContextRules.isNpcAction(action)
            || action == MenuAction.ITEM_USE_ON_GAME_OBJECT
            || action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT
            || action == MenuAction.GAME_OBJECT_FIRST_OPTION
            || action == MenuAction.GAME_OBJECT_SECOND_OPTION
            || action == MenuAction.GAME_OBJECT_THIRD_OPTION
            || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
            || action == MenuAction.GAME_OBJECT_FIFTH_OPTION
            || action == MenuAction.ITEM_USE_ON_PLAYER
            || action == MenuAction.WIDGET_TARGET_ON_PLAYER
            || action == MenuAction.PLAYER_FIRST_OPTION
            || action == MenuAction.PLAYER_SECOND_OPTION
            || action == MenuAction.PLAYER_THIRD_OPTION
            || action == MenuAction.PLAYER_FOURTH_OPTION
            || action == MenuAction.PLAYER_FIFTH_OPTION
            || action == MenuAction.PLAYER_SIXTH_OPTION
            || action == MenuAction.PLAYER_SEVENTH_OPTION
            || action == MenuAction.PLAYER_EIGHTH_OPTION
            || InteractionContextRules.isGroundItemAction(action)
            || action == MenuAction.WORLD_ENTITY_FIRST_OPTION
            || action == MenuAction.WORLD_ENTITY_SECOND_OPTION
            || action == MenuAction.WORLD_ENTITY_THIRD_OPTION
            || action == MenuAction.WORLD_ENTITY_FOURTH_OPTION
            || action == MenuAction.WORLD_ENTITY_FIFTH_OPTION;
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
