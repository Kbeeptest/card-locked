package com.cardrestricted.runelite;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import java.util.Collections;
import java.util.Set;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class IntegrityRestrictionTest
{
    private static SimpleRestrictionService service;
    private static InteractionFamilyIndex familyIndex;

    @BeforeClass
    public static void setUpCatalogue()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        familyIndex = new InteractionFamilyIndex(catalogue);
        service = new SimpleRestrictionService(familyIndex);
    }

    @Test
    public void lockedNpcAllowsOnlyNonFunctionalOptions()
    {
        assertFalse(service.evaluateNpcInteraction(
            3010,
            "Guard",
            "Talk-to",
            Collections.emptySet()).isBlocked());
        assertTrue(service.evaluateNpcInteraction(
            3010,
            "Guard",
            "Examine",
            Collections.emptySet()).isBlocked());
        assertTrue(service.evaluateNpcInteraction(
            3010,
            "Guard",
            "Attack",
            Collections.emptySet()).isBlocked());
        assertTrue(service.evaluateNpcInteraction(
            3010,
            "Guard",
            "Pickpocket",
            Collections.emptySet()).isBlocked());
        assertTrue(service.evaluateNpcInteraction(
            3010,
            "Guard",
            "Trade",
            Collections.emptySet()).isBlocked());
        assertTrue(service.evaluateNpcInteraction(
            3010,
            "Guard",
            "Buy-plank",
            Collections.emptySet()).isBlocked());
    }


    @Test
    public void reportedPickpocketAndServiceNpcsUseTheSameGate()
    {
        assertTrue(service.evaluateNpcInteraction(
            3295,
            "Hero",
            "Pickpocket",
            Collections.emptySet()).isBlocked());
        assertTrue(service.evaluateNpcInteraction(
            12929,
            "Citizen",
            "Pickpocket",
            Collections.emptySet()).isBlocked());
        assertTrue(service.evaluateNpcInteraction(
            15407,
            "Brikka",
            "Trade",
            Collections.emptySet()).isBlocked());
        assertTrue(service.evaluateNpcInteraction(
            15376,
            "Shipwright Sornik",
            "Buy-boat",
            Collections.emptySet()).isBlocked());
        assertTrue(service.evaluateNpcInteraction(
            15459,
            "Port master",
            "Claim-rewards",
            Collections.emptySet()).isBlocked());
    }

    @Test
    public void unknownGuardIdFallsBackToSingleGuardCardFamily()
    {
        String guardFamily = familyIndex.familyIdForNpc(1546, "Guard");
        assertTrue("npc-family.guard".equals(guardFamily));
        Set<String> guardCards = familyIndex.cardIdsForFamily(guardFamily);
        assertTrue(guardCards.contains("npc.guard"));
        assertTrue(service.evaluateNpcInteraction(
            1546,
            "Guard",
            "Pickpocket",
            Collections.emptySet()).isBlocked());
        assertFalse(service.evaluateNpcInteraction(
            1546,
            "Guard",
            "Pickpocket",
            guardCards).isBlocked());
    }

    @Test
    public void distinctNamedGuardsDoNotCollapseIntoGenericGuard()
    {
        String generic = familyIndex.familyIdForNpc(-1, "Guard");
        String ham = familyIndex.familyIdForNpc(-1, "H.A.M. Guard");
        String khazard = familyIndex.familyIdForNpc(-1, "Khazard Guard");
        assertTrue(generic != null);
        assertTrue(ham != null);
        assertTrue(khazard != null);
        assertFalse(generic.equals(ham));
        assertFalse(generic.equals(khazard));
    }

    @Test
    public void faladorTeleportRequiresAirWaterAndLawRuneEntitlements()
    {
        Set<Integer> requirements = SpellRuneRequirementResolver
            .namedRequirements("Falador Teleport");
        assertTrue(requirements.contains(ItemID.AIR_RUNE));
        assertTrue(requirements.contains(ItemID.WATER_RUNE));
        assertTrue(requirements.contains(ItemID.LAW_RUNE));

        Set<String> onlyAirAndWater = union(
            service.requiredCardsForItem(ItemID.AIR_RUNE),
            service.requiredCardsForItem(ItemID.WATER_RUNE));
        assertTrue(RuneEntitlementPolicy.evaluate(
            requirements,
            onlyAirAndWater,
            service).isBlocked());

        Set<String> all = union(
            onlyAirAndWater,
            service.requiredCardsForItem(ItemID.LAW_RUNE));
        assertFalse(RuneEntitlementPolicy.evaluate(
            requirements,
            all,
            service).isBlocked());
    }

    @Test
    public void combinationRuneCardCanSatisfyBothElementalRequirements()
    {
        Set<Integer> requirements = Set.of(
            ItemID.AIR_RUNE,
            ItemID.WATER_RUNE,
            ItemID.LAW_RUNE);
        Set<String> owned = union(
            service.requiredCardsForItem(ItemID.MIST_RUNE),
            service.requiredCardsForItem(ItemID.LAW_RUNE));
        assertFalse(RuneEntitlementPolicy.evaluate(
            requirements,
            owned,
            service).isBlocked());
    }

    @Test
    public void equippedLockedItemsGateFunctionalActionsButNotRecovery()
    {
        assertTrue(EquippedItemActionRules.shouldGate(
            MenuAction.NPC_SECOND_OPTION,
            "Attack",
            false,
            false));
        assertTrue(EquippedItemActionRules.shouldGate(
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            "Chop down",
            false,
            false));
        assertTrue(EquippedItemActionRules.shouldGate(
            MenuAction.WIDGET_TARGET,
            "Cast",
            true,
            false));
        assertFalse(EquippedItemActionRules.shouldGate(
            MenuAction.NPC_FIRST_OPTION,
            "Talk-to",
            false,
            false));
        assertFalse(EquippedItemActionRules.shouldGate(
            MenuAction.ITEM_FIRST_OPTION,
            "Remove",
            false,
            false));
        assertFalse(EquippedItemActionRules.shouldGate(
            MenuAction.WALK,
            "Walk here",
            false,
            false));
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets)
    {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (Set<String> set : sets)
        {
            result.addAll(set);
        }
        return result;
    }
}
