package com.cardrestricted.prototype;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.EntityFamily;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.runelite.InteractionFamilyIndex;
import com.cardrestricted.runelite.InteractionNameNormalizer;
import com.cardrestricted.runelite.SimpleRestrictionService;
import java.util.Collections;
import java.util.Set;

/** Focused checks for the deliberately small Phase 0.65 restriction runtime. */
public final class CoreResetVerification
{
    private CoreResetVerification()
    {
    }

    public static void main(String[] args)
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        InteractionFamilyIndex familyIndex = new InteractionFamilyIndex(
            catalogue);
        SimpleRestrictionService service = new SimpleRestrictionService(
            familyIndex);

        verifyEveryTrackedFamily(catalogue, service);
        verifyUniqueNameFallbacks(catalogue, familyIndex, service);
        require("item.wooden_shield".equals(catalogue.resolveCardId(
            "item.wooden_shield.20166")),
            "Retired exact-name duplicates must migrate to their canonical card.");

        CardDefinition bones = card(catalogue, "Bones", CardType.ITEM);
        EntityFamily bonesFamily = family(catalogue, bones.getEntityFamilyId());
        int bonesId = bonesFamily.getCanonicalEntityId();
        Set<Integer> bonesOnly = Set.of(bonesId);

        require(service.evaluateItems(
            bonesOnly, "Bury", Collections.emptySet(), true).isBlocked(),
            "Locked bones must block Bury.");
        require(service.evaluateItems(
            Collections.emptySet(),
            InteractionNameNormalizer.itemNameCandidates(
                "<col=ff9040>Bones</col>"),
            "Bury",
            Collections.emptySet(),
            true).isBlocked(),
            "A real menu target must block locked bones even when RuneLite supplies no item ID.");
        require(!service.evaluateItems(
            Collections.emptySet(),
            InteractionNameNormalizer.itemNameCandidates(
                "<col=ff9040>Bones</col>"),
            "Bury",
            Set.of(bones.getCardId()),
            true).isBlocked(),
            "The name fallback must honour owned family cards.");
        require(service.evaluateItems(
            bonesOnly, "Some future action", Collections.emptySet(), true)
            .isBlocked(),
            "Unknown functional verbs must not bypass a known locked item.");
        require(!service.evaluateItems(
            bonesOnly, "Examine", Collections.emptySet(), true).isBlocked(),
            "Examine must remain available.");
        require(!service.evaluateItems(
            bonesOnly, "Drop", Collections.emptySet(), true).isBlocked(),
            "Drop must remain available.");
        require(!service.evaluateItems(
            bonesOnly, "Deposit-all", Collections.emptySet(), true).isBlocked(),
            "Bank deposits must be available in balanced mode.");
        require(service.evaluateItems(
            bonesOnly, "Deposit-all", Collections.emptySet(), false).isBlocked(),
            "Strict mode must be able to disable locked-item banking.");
        require(!service.evaluateItems(
            bonesOnly, "Bury", Set.of(bones.getCardId()), true).isBlocked(),
            "Owning the family card must unlock every functional verb.");
        require(!service.evaluateItems(
            Set.of(Integer.MAX_VALUE), "Activate", Collections.emptySet(), true)
            .isBlocked(),
            "Untracked items must fail open.");

        CardDefinition knife = card(catalogue, "Knife", CardType.ITEM);
        EntityFamily knifeFamily = family(
            catalogue,
            knife.getEntityFamilyId());
        require(service.evaluateItems(
            Set.of(knifeFamily.getCanonicalEntityId()),
            InteractionNameNormalizer.itemNameCandidates(
                "<col=ff9040>Knife</col> -> <col=ff9040>Bones</col>"),
            "Use",
            Set.of(knife.getCardId()),
            true).isBlocked(),
            "A known owned target ID must not suppress a locked source/target "
                + "resolved from the menu name.");

        CardDefinition goblin = card(catalogue, "Goblin", CardType.NPC);
        EntityFamily goblinFamily = family(
            catalogue,
            goblin.getEntityFamilyId());
        int goblinId = goblinFamily.getCanonicalEntityId();
        require(service.evaluateNpcAttack(
            goblinId, "Attack", Collections.emptySet()).isBlocked(),
            "Tracked locked NPCs must block Attack.");
        require(!service.evaluateNpcAttack(
            goblinId, "Talk-to", Collections.emptySet()).isBlocked(),
            "NPC dialogue must not be restricted by the stable runtime.");
        require(!service.evaluateNpcAttack(
            goblinId, "Attack", Set.of(goblin.getCardId())).isBlocked(),
            "Owning the NPC card must permit Attack.");
        require(!service.evaluateNpcAttack(
            Integer.MAX_VALUE, "Attack", Collections.emptySet()).isBlocked(),
            "Untracked NPCs must fail open.");

        require(InteractionNameNormalizer.normaliseItemName(
            "<col=ff9040>Prayer potion(3)</col>").equals("prayer potion"),
            "Potion dose suffixes must resolve to their shared card name.");
        require(InteractionNameNormalizer.itemNameCandidates(
            "<col=ff9040>Knife</col> -> <col=ff9040>Logs</col>")
            .contains("logs"),
            "Item-on-item targets must expose the target item name.");

        System.out.println("Phase 0.66 focused restriction verification passed.");
    }


    private static void verifyEveryTrackedFamily(
        CardCatalogue catalogue,
        SimpleRestrictionService service)
    {
        java.util.Map<String, CardDefinition> cardByFamily =
            new java.util.HashMap<>();
        for (CardDefinition card : catalogue.getCards())
        {
            cardByFamily.putIfAbsent(card.getEntityFamilyId(), card);
        }

        int itemFamilies = 0;
        int npcFamilies = 0;
        for (EntityFamily family : catalogue.getFamilies())
        {
            CardDefinition card = cardByFamily.get(family.getFamilyId());
            if (card == null)
            {
                continue;
            }
            if (card.getCardType() == CardType.ITEM)
            {
                itemFamilies++;
                for (int entityId : family.allEntityIds())
                {
                    require(service.evaluateItems(
                        Set.of(entityId),
                        "Any functional action",
                        Collections.emptySet(),
                        true).isBlocked(),
                        "Every tracked item variant must be locked by family: "
                            + family.getFamilyId() + ":" + entityId);
                    require(!service.evaluateItems(
                        Set.of(entityId),
                        "Any functional action",
                        Set.of(card.getCardId()),
                        true).isBlocked(),
                        "Owning a family card must unlock every mapped variant: "
                            + family.getFamilyId() + ":" + entityId);
                }
            }
            else if (card.getCardType() == CardType.NPC)
            {
                npcFamilies++;
                for (int entityId : family.allEntityIds())
                {
                    require(service.evaluateNpcAttack(
                        entityId,
                        "Attack",
                        Collections.emptySet()).isBlocked(),
                        "Every tracked NPC variant must block Attack: "
                            + family.getFamilyId() + ":" + entityId);
                    require(!service.evaluateNpcAttack(
                        entityId,
                        "Attack",
                        Set.of(card.getCardId())).isBlocked(),
                        "Owning an NPC family card must unlock every variant: "
                            + family.getFamilyId() + ":" + entityId);
                }
            }
        }
        require(itemFamilies > 0, "Expected tracked item families.");
        require(npcFamilies > 0, "Expected tracked NPC families.");
    }

    private static void verifyUniqueNameFallbacks(
        CardCatalogue catalogue,
        InteractionFamilyIndex familyIndex,
        SimpleRestrictionService service)
    {
        int verified = 0;
        for (CardDefinition card : catalogue.getCards())
        {
            if (card.getCardType() != CardType.ITEM)
            {
                continue;
            }
            String normalised = InteractionNameNormalizer.normaliseItemName(
                card.getDisplayName());
            if (!card.getEntityFamilyId().equals(
                familyIndex.familyIdForUniqueItemName(normalised)))
            {
                continue;
            }
            verified++;
            require(service.evaluateItems(
                Collections.emptySet(),
                Set.of(normalised),
                "Any functional action",
                Collections.emptySet(),
                true).isBlocked(),
                "Unique menu-name fallback must block " + card.getCardId());
            require(!service.evaluateItems(
                Collections.emptySet(),
                Set.of(normalised),
                "Any functional action",
                Set.of(card.getCardId()),
                true).isBlocked(),
                "Unique menu-name fallback must honour ownership for "
                    + card.getCardId());
        }
        require(verified > 4_000,
            "Expected broad item-name fallback coverage, found " + verified);
        require(familyIndex.ambiguousItemNameCount() < 50,
            "Too many ambiguous item names remain for safe fallback: "
                + familyIndex.ambiguousItemNameCount());
    }

    private static CardDefinition card(
        CardCatalogue catalogue,
        String displayName,
        CardType type)
    {
        return catalogue.getCards().stream()
            .filter(candidate -> candidate.getCardType() == type)
            .filter(candidate -> displayName.equalsIgnoreCase(
                candidate.getDisplayName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "Missing test card " + displayName + "."));
    }

    private static EntityFamily family(
        CardCatalogue catalogue,
        String familyId)
    {
        return catalogue.getFamilies().stream()
            .filter(candidate -> familyId.equals(candidate.getFamilyId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "Missing family " + familyId + "."));
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
