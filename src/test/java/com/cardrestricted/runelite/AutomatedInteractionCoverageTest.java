package com.cardrestricted.runelite;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.EntityFamily;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.domain.RestrictionMode;
import java.util.Collections;
import java.util.Set;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Headless decision matrix for player-facing RuneLite interaction contexts. */
public final class AutomatedInteractionCoverageTest
{
    private static SimpleRestrictionService service;
    private static int itemId;
    private static String itemCardId;
    private static int npcId;
    private static String npcCardId;

    @BeforeClass
    public static void buildCatalogueFixture()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        service = new SimpleRestrictionService(
            new InteractionFamilyIndex(catalogue));

        CardDefinition itemCard = catalogue.getCards().stream()
            .filter(card -> card.getCardType() == CardType.ITEM)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No item card available."));
        EntityFamily itemFamily = catalogue.getFamilies().stream()
            .filter(family -> family.getFamilyId().equals(
                itemCard.getEntityFamilyId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing item family."));
        itemId = itemFamily.getCanonicalEntityId();
        itemCardId = catalogue.resolveCardId(itemCard.getCardId());

        CardDefinition npcCard = catalogue.getCards().stream()
            .filter(card -> card.getCardType() == CardType.NPC)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No NPC card available."));
        EntityFamily npcFamily = catalogue.getFamilies().stream()
            .filter(family -> family.getFamilyId().equals(
                npcCard.getEntityFamilyId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing NPC family."));
        npcId = npcFamily.getCanonicalEntityId();
        npcCardId = catalogue.resolveCardId(npcCard.getCardId());
    }

    @Test
    public void groundPickupBlocksLockedItemButExamineFailsOpen()
    {
        assertTrue(service.evaluateItems(
            Set.of(itemId),
            "Take",
            Collections.emptySet(),
            false).isBlocked());
        assertFalse(service.evaluateItems(
            Set.of(itemId),
            "Examine",
            Collections.emptySet(),
            false).isBlocked());
    }

    @Test
    public void itemOnItemChecksLockedParticipantsAndOwnership()
    {
        assertTrue(service.evaluateItems(
            Set.of(itemId),
            "Use",
            Collections.emptySet(),
            false).isBlocked());
        assertFalse(service.evaluateItems(
            Set.of(itemId),
            "Use",
            Set.of(itemCardId),
            false).isBlocked());
    }

    @Test
    public void itemOnNpcAndObjectDoNotTreatTargetNamesAsItems()
    {
        assertFalse(InteractionContextRules.shouldUseItemNameFallback(
            MenuAction.ITEM_USE_ON_NPC,
            false,
            false));
        assertFalse(InteractionContextRules.shouldUseItemNameFallback(
            MenuAction.ITEM_USE_ON_GAME_OBJECT,
            false,
            false));
        assertTrue(InteractionContextRules.shouldIncludeSelectedItem(
            true,
            MenuAction.ITEM_USE_ON_NPC,
            149 << 16));
        assertTrue(InteractionContextRules.shouldIncludeSelectedItem(
            true,
            MenuAction.ITEM_USE_ON_GAME_OBJECT,
            149 << 16));
    }

    @Test
    public void spellOnItemChecksTargetWithoutTreatingSpellAsItem()
    {
        assertTrue(InteractionContextRules.shouldReadClickedItemMetadata(
            MenuAction.WIDGET_USE_ON_ITEM,
            false,
            true));
        Set<String> names = InteractionNameNormalizer
            .targetItemNameCandidates("High Level Alchemy -> Coins");
        assertFalse(names.contains("high level alchemy"));
        assertTrue(service.evaluateItems(
            Set.of(itemId),
            names,
            "Cast",
            Collections.emptySet(),
            false).isBlocked());
    }

    @Test
    public void spellOnNpcOrObjectDoesNotCreateItemFalsePositive()
    {
        assertFalse(InteractionContextRules.shouldReadClickedItemMetadata(
            MenuAction.WIDGET_TARGET_ON_NPC,
            false,
            true));
        assertFalse(InteractionContextRules.shouldReadClickedItemMetadata(
            MenuAction.WIDGET_TARGET_ON_GAME_OBJECT,
            false,
            true));
        assertFalse(service.evaluateItems(
            Collections.emptySet(),
            Collections.emptySet(),
            "Cast",
            Collections.emptySet(),
            false).isBlocked());
    }

    @Test
    public void equipmentRemovalIsSafeButGenericRemoveRequiresBanking()
    {
        assertFalse(service.evaluateItems(
            Set.of(itemId),
            Collections.emptySet(),
            "Remove",
            Collections.emptySet(),
            false,
            true).isBlocked());
        assertTrue(service.evaluateItems(
            Set.of(itemId),
            Collections.emptySet(),
            "Remove",
            Collections.emptySet(),
            false,
            false).isBlocked());
        assertFalse(service.evaluateItems(
            Set.of(itemId),
            Collections.emptySet(),
            "Remove",
            Collections.emptySet(),
            true,
            false).isBlocked());
    }

    @Test
    public void bankPlaceholdersFailOpenAndStrictBankingStillBlocksItems()
    {
        assertTrue(BankTabInteractionRules.isBankTabNavigation(
            "Use", "<col=ff9040>View tab 8</col>"));
        assertTrue(service.evaluateItems(
            Set.of(itemId),
            "Withdraw-1",
            Collections.emptySet(),
            false).isBlocked());
        assertFalse(service.evaluateItems(
            Set.of(itemId),
            "Withdraw-1",
            Collections.emptySet(),
            true).isBlocked());
    }

    @Test
    public void npcGateBlocksEveryFunctionalOptionAndAcceptsFormattedOption()
    {
        assertTrue(service.evaluateNpcInteraction(
            npcId,
            "",
            "<col=ff0000>Attack</col>",
            Collections.emptySet()).isBlocked());
        assertTrue(service.evaluateNpcInteraction(
            npcId,
            "",
            "Pickpocket",
            Collections.emptySet()).isBlocked());
        assertTrue(service.evaluateNpcInteraction(
            npcId,
            "",
            "Trade",
            Collections.emptySet()).isBlocked());
        assertFalse(service.evaluateNpcInteraction(
            npcId,
            "",
            "Talk-to",
            Collections.emptySet()).isBlocked());
        assertTrue(service.evaluateNpcInteraction(
            npcId,
            "",
            "Examine",
            Collections.emptySet()).isBlocked());
        assertFalse(service.evaluateNpcInteraction(
            npcId,
            "",
            "Attack",
            Set.of(npcCardId)).isBlocked());
    }

    @Test
    public void conflictingNpcIdAndNameFailIdentityReconciliation()
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        java.util.List<CardDefinition> npcCards = catalogue.getCards().stream()
            .filter(card -> card.getCardType() == CardType.NPC)
            .filter(card -> !card.getDisplayName().trim().isEmpty())
            .collect(java.util.stream.Collectors.toList());
        CardDefinition first = npcCards.get(0);
        CardDefinition second = npcCards.stream()
            .filter(card -> !card.getEntityFamilyId().equals(
                first.getEntityFamilyId()))
            .filter(card -> catalogue.getCards().stream()
                .filter(candidate -> candidate.getCardType() == CardType.NPC)
                .filter(candidate -> InteractionNameNormalizer
                    .normaliseEntityName(candidate.getDisplayName()).equals(
                        InteractionNameNormalizer.normaliseEntityName(
                            card.getDisplayName())))
                .map(CardDefinition::getEntityFamilyId)
                .distinct().count() == 1)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "No second unique NPC identity available."));
        EntityFamily firstFamily = catalogue.getFamilies().stream()
            .filter(family -> family.getFamilyId().equals(
                first.getEntityFamilyId()))
            .findFirst().orElseThrow();
        assertTrue(service.hasConflictingNpcIdentity(
            firstFamily.getCanonicalEntityId(),
            second.getDisplayName()));
        assertFalse(service.hasConflictingNpcIdentity(
            firstFamily.getCanonicalEntityId(),
            first.getDisplayName()));
    }

    @Test
    public void restrictionModesAndLoginStatesFormAnExplicitMatrix()
    {
        for (GameState state : GameState.values())
        {
            boolean loggedIn = state == GameState.LOGGED_IN;
            assertTrue(
                RestrictionRuntimeGate.isRestrictionRuntimeActive(
                    true,
                    state,
                    RestrictionMode.ENFORCE) == loggedIn);
            assertTrue(
                RestrictionRuntimeGate.isRestrictionRuntimeActive(
                    true,
                    state,
                    RestrictionMode.AUDIT_ONLY) == loggedIn);
            assertFalse(
                RestrictionRuntimeGate.isRestrictionRuntimeActive(
                    true,
                    state,
                    RestrictionMode.DISABLED));
        }
    }
}
