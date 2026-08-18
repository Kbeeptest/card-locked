package com.cardrestricted.runelite;

import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StorageInteractionAuthorizationTest
{
    private static final int BANK_WIDGET = WidgetID.BANK_GROUP_ID << 16;

    @Test
    public void verifiedBankerDirectOpenAuthorizesTransfers()
    {
        StorageInteractionAuthorization authorization =
            new StorageInteractionAuthorization();
        authorization.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Bank",
            "Banker",
            100,
            true,
            true);
        authorization.onWidgetLoaded(WidgetID.BANK_GROUP_ID, 102);

        assertTrue(authorization.isTransferAuthorized(
            MenuAction.CC_OP, "Withdraw-1", BANK_WIDGET));
    }

    @Test
    public void lockedBankerOrAlreadyOpenBankDoesNotAuthorizeTransfers()
    {
        StorageInteractionAuthorization locked =
            new StorageInteractionAuthorization();
        locked.observeAllowedWorldInteraction(
            MenuAction.NPC_SECOND_OPTION,
            "Bank",
            "Banker",
            100,
            true,
            false);
        locked.onWidgetLoaded(WidgetID.BANK_GROUP_ID, 101);
        assertFalse(locked.isTransferAuthorized(
            MenuAction.CC_OP, "Withdraw-1", BANK_WIDGET));

        StorageInteractionAuthorization inherited =
            new StorageInteractionAuthorization();
        inherited.onWidgetLoaded(WidgetID.BANK_GROUP_ID, 200);
        assertFalse(inherited.isTransferAuthorized(
            MenuAction.CC_OP, "Deposit-all", BANK_WIDGET));
    }

    @Test
    public void bankBoothAndVerifiedDialoguePathAuthorize()
    {
        StorageInteractionAuthorization booth =
            new StorageInteractionAuthorization();
        booth.observeAllowedWorldInteraction(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            "Bank",
            "Bank booth",
            10,
            false,
            false);
        booth.onWidgetLoaded(WidgetID.BANK_GROUP_ID, 12);
        assertTrue(booth.isTransferAuthorized(
            MenuAction.CC_OP, "Deposit-1", BANK_WIDGET));

        StorageInteractionAuthorization dialogue =
            new StorageInteractionAuthorization();
        dialogue.observeAllowedWorldInteraction(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            "Banker",
            50,
            true,
            true);
        assertTrue(dialogue.observeAllowedDialogueChoice(
            "Access my bank",
            "",
            "",
            55,
            WidgetID.DIALOG_OPTION_GROUP_ID << 16));
        dialogue.onWidgetLoaded(WidgetID.BANK_GROUP_ID, 57);
        assertTrue(dialogue.isTransferAuthorized(
            MenuAction.CC_OP, "Withdraw-all", BANK_WIDGET));
    }

    @Test
    public void unrelatedObjectAndExpiredProofDoNotAuthorize()
    {
        StorageInteractionAuthorization authorization =
            new StorageInteractionAuthorization();
        authorization.observeAllowedWorldInteraction(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            "Open",
            "Door",
            1,
            false,
            false);
        authorization.onWidgetLoaded(WidgetID.BANK_GROUP_ID, 2);
        assertFalse(authorization.isTransferAuthorized(
            MenuAction.CC_OP, "Withdraw-1", BANK_WIDGET));

        authorization.reset();
        authorization.observeAllowedWorldInteraction(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            "Bank",
            "Bank booth",
            10,
            false,
            false);
        authorization.onWidgetLoaded(WidgetID.BANK_GROUP_ID, 30);
        assertFalse(authorization.isTransferAuthorized(
            MenuAction.CC_OP, "Withdraw-1", BANK_WIDGET));
    }

    @Test
    public void navigationAndClosingRemainAvailable()
    {
        StorageInteractionAuthorization authorization =
            new StorageInteractionAuthorization();
        authorization.onWidgetLoaded(WidgetID.BANK_GROUP_ID, 1);
        assertTrue(authorization.isTransferAuthorized(
            MenuAction.CC_OP, "View tab", BANK_WIDGET));
        authorization.onWidgetClosed(WidgetID.BANK_GROUP_ID);
        assertFalse(authorization.isStorageOpen());
    }

    @Test
    public void immediateStorageOpenedByUnlockedTalkIsAuthorizedBriefly()
    {
        StorageInteractionAuthorization authorization =
            new StorageInteractionAuthorization();
        authorization.observeAllowedWorldInteraction(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            "Banker",
            100,
            true,
            true);
        authorization.onWidgetLoaded(WidgetID.BANK_GROUP_ID, 102);
        assertTrue(authorization.isTransferAuthorized(
            MenuAction.CC_OP,
            "Withdraw-1",
            BANK_WIDGET));
    }

    @Test
    public void sailingCargoAndDriftNetStoresAreProtectedStorage()
    {
        assertTrue(StorageInteractionRules.isStorageGroup(
            InterfaceID.SAILING_BOAT_CARGOHOLD));
        assertTrue(StorageInteractionRules.isStorageGroup(
            InterfaceID.SAILING_BOAT_CARGOHOLD_SIDE));
        assertTrue(StorageInteractionRules.isStorageGroup(
            InterfaceID.FOSSIL_DRIFTNET_STORE));
        assertTrue(StorageInteractionRules.isStorageGroup(
            InterfaceID.FOSSIL_DRIFTNET_SIDESTORE));
        assertTrue(StorageInteractionRules.isStorageGroup(
            InterfaceID.BANK_DEPOSIT_IMP));
        assertTrue(StorageInteractionRules.isStorageGroup(
            InterfaceID.SEED_VAULT_DEPOSIT));
        assertTrue(StorageInteractionAuthorization.canOpenStorage(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            "Open",
            "Boat cargo hold"));

        StorageInteractionAuthorization inherited =
            new StorageInteractionAuthorization();
        inherited.onWidgetLoaded(InterfaceID.SAILING_BOAT_CARGOHOLD, 10);
        assertFalse(inherited.isTransferAuthorized(
            MenuAction.CC_OP,
            "Withdraw-1",
            InterfaceID.SAILING_BOAT_CARGOHOLD << 16));
    }

}
