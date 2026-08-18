package com.cardrestricted.runelite;

import net.runelite.api.InventoryID;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RewardContainerIntegrityRulesTest
{
    @Test
    public void rewardButtonsRequireMatchingInterfaceContext()
    {
        int barrows = InterfaceID.BARROWS_REWARD << 16;
        assertTrue(RewardContainerIntegrityRules.isRewardCollectionAction(
            MenuAction.CC_OP, "Take all", barrows));
        assertEquals(
            java.util.Collections.singleton(InventoryID.BARROWS_REWARD),
            RewardContainerIntegrityRules.rewardInventoriesForContext(barrows));

        assertFalse(RewardContainerIntegrityRules.isRewardCollectionAction(
            MenuAction.CC_OP, "Take all"));
        assertTrue(RewardContainerIntegrityRules
            .isPotentialRewardCollectionAction(MenuAction.CC_OP, "Take all"));
    }

    @Test
    public void eachSupportedRewardInterfaceMapsOnlyItsContainer()
    {
        assertEquals(
            java.util.Collections.singleton(InventoryID.KINGDOM_OF_MISCELLANIA),
            RewardContainerIntegrityRules.rewardInventoriesForContext(
                InterfaceID.MISC_COLLECTION << 16));
        assertEquals(
            java.util.Collections.singleton(InventoryID.LUNAR_CHEST),
            RewardContainerIntegrityRules.rewardInventoriesForContext(
                InterfaceID.PMOON_REWARD << 16));
        assertEquals(
            java.util.Collections.singleton(
                InventoryID.FORTIS_COLOSSEUM_REWARD_CHEST),
            RewardContainerIntegrityRules.rewardInventoriesForContext(
                InterfaceID.COLOSSEUM_REWARD << 16));
    }

    @Test
    public void worldAndObservationActionsAreNotMisclassified()
    {
        int barrows = InterfaceID.BARROWS_REWARD << 16;
        assertFalse(RewardContainerIntegrityRules.isRewardCollectionAction(
            MenuAction.GROUND_ITEM_FIRST_OPTION, "Take", barrows));
        assertFalse(RewardContainerIntegrityRules.isRewardCollectionAction(
            MenuAction.NPC_FIRST_OPTION, "Claim", barrows));
        assertFalse(RewardContainerIntegrityRules.isRewardCollectionAction(
            MenuAction.CC_OP, "Examine", barrows));
    }
}
