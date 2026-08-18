package com.cardrestricted.runelite;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Minimal ownership gate used by the stabilised runtime.
 *
 * <p>The service owns the final card-family decision for tracked items, NPCs
 * and explicit prerequisite items such as spell runes. Higher-level menu
 * context classification remains in the RuneLite adapter.</p>
 */
public final class SimpleRestrictionService
{
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final InteractionFamilyIndex familyIndex;

    public SimpleRestrictionService(InteractionFamilyIndex familyIndex)
    {
        this.familyIndex = Objects.requireNonNull(familyIndex, "familyIndex");
    }

    public RestrictionDecision evaluateItems(
        Set<Integer> itemIds,
        String option,
        Set<String> ownedCardIds,
        boolean allowBanking)
    {
        return evaluateItems(
            itemIds,
            Collections.emptySet(),
            option,
            ownedCardIds,
            allowBanking,
            false);
    }

    public RestrictionDecision evaluateItems(
        Set<Integer> itemIds,
        Set<String> normalisedItemNames,
        String option,
        Set<String> ownedCardIds,
        boolean allowBanking)
    {
        return evaluateItems(
            itemIds,
            normalisedItemNames,
            option,
            ownedCardIds,
            allowBanking,
            false);
    }

    public RestrictionDecision evaluateItems(
        Set<Integer> itemIds,
        Set<String> normalisedItemNames,
        String option,
        Set<String> ownedCardIds,
        boolean allowBanking,
        boolean equipmentRemoval)
    {
        ItemRequirement requirement = itemRequirement(
            itemIds,
            normalisedItemNames,
            ownedCardIds);
        if (isSafeItemOption(option, allowBanking, equipmentRemoval))
        {
            return RestrictionDecision.allow();
        }
        if (requirement.hasAmbiguousNameWithoutResolvedId())
        {
            return RestrictionDecision.block(
                Collections.emptySet(),
                "This item name maps to multiple card families and its exact identity could not be verified, so the action was blocked.");
        }
        if (!requirement.isTracked() || requirement.getLockedCards().isEmpty())
        {
            return RestrictionDecision.allow();
        }
        return RestrictionDecision.block(
            requirement.getLockedCards(),
            "This item's card has not been acquired.");
    }

    public RestrictionDecision evaluateNpcAttack(
        int npcId,
        String option,
        Set<String> ownedCardIds)
    {
        return evaluateNpcInteraction(
            npcId,
            "",
            option,
            ownedCardIds);
    }

    public RestrictionDecision evaluateNpcInteraction(
        int npcId,
        String npcName,
        String option,
        Set<String> ownedCardIds)
    {
        String value = normalise(option);
        if (isSafeNpcOption(value))
        {
            return RestrictionDecision.allow();
        }
        String familyId = familyIndex.familyIdForNpc(npcId, npcName);
        Set<String> familyCards = familyIndex.cardIdsForFamily(familyId);
        if (familyCards.isEmpty())
        {
            if (familyIndex.isAmbiguousNpcName(npcName))
            {
                return RestrictionDecision.block(
                    Collections.emptySet(),
                    "This NPC maps to multiple card families and could not be verified, so the action was blocked.");
            }
            return RestrictionDecision.allow();
        }
        if (ownsAny(familyCards, ownedCardIds))
        {
            return RestrictionDecision.allow();
        }
        return RestrictionDecision.block(
            familyCards,
            "This NPC's card has not been acquired. Only Talk-to is allowed while it is locked.");
    }

    public RestrictionDecision evaluateRequiredItems(
        Set<Integer> itemIds,
        Set<String> ownedCardIds,
        String explanation)
    {
        ItemRequirement requirement = itemRequirement(
            itemIds,
            Collections.emptySet(),
            ownedCardIds);
        if (!requirement.isTracked() || requirement.getLockedCards().isEmpty())
        {
            return RestrictionDecision.allow();
        }
        return RestrictionDecision.block(
            requirement.getLockedCards(),
            explanation);
    }

    public Set<String> requiredCardsForItem(int itemId)
    {
        String familyId = familyIndex.familyIdForItem(itemId);
        return familyIndex.cardIdsForFamily(familyId);
    }

    public Set<String> requiredCardsForNpc(int npcId, String npcName)
    {
        String familyId = familyIndex.familyIdForNpc(npcId, npcName);
        return familyIndex.cardIdsForFamily(familyId);
    }

    public boolean hasKnownOrAmbiguousNpcIdentity(String npcName)
    {
        String familyId = familyIndex.familyIdForNpc(-1, npcName);
        return !familyIndex.cardIdsForFamily(familyId).isEmpty()
            || familyIndex.isAmbiguousNpcName(npcName);
    }

    public boolean isAmbiguousNpcIdentity(String npcName)
    {
        return familyIndex.isAmbiguousNpcName(npcName);
    }

    public boolean hasConflictingNpcIdentity(int npcId, String npcName)
    {
        return familyIndex.hasConflictingNpcIdentity(npcId, npcName);
    }

    /**
     * Authoritative lock-state query shared by gameplay gates, shop/dialogue
     * provenance and reward suppression. Ambiguous tracked names fail closed.
     * Completely untracked NPCs remain outside the card ruleset.
     */
    public boolean isNpcLocked(
        int npcId,
        String npcName,
        Set<String> ownedCardIds)
    {
        String familyId = familyIndex.familyIdForNpc(npcId, npcName);
        Set<String> familyCards = familyIndex.cardIdsForFamily(familyId);
        if (familyCards.isEmpty())
        {
            return familyIndex.isAmbiguousNpcName(npcName);
        }
        return !ownsAny(familyCards, ownedCardIds);
    }

    public boolean isItemLocked(int itemId, Set<String> ownedCardIds)
    {
        if (itemId < 0)
        {
            return false;
        }
        String familyId = familyIndex.familyIdForItem(itemId);
        Set<String> cards = familyIndex.cardIdsForFamily(familyId);
        return !cards.isEmpty() && !ownsAny(cards, ownedCardIds);
    }


    public static boolean isTalkOption(String option)
    {
        String value = normalise(option);
        return value.equals("talk-to") || value.equals("talk to")
            || value.equals("talk") || value.equals("speak-to")
            || value.equals("speak to");
    }

    public static boolean isSafeNpcOption(String option)
    {
        return isTalkOption(option);
    }

    public static boolean isSafeItemOption(
        String option,
        boolean allowBanking)
    {
        return isSafeItemOption(option, allowBanking, false);
    }

    public static boolean isSafeItemOption(
        String option,
        boolean allowBanking,
        boolean equipmentRemoval)
    {
        String value = normalise(option);
        if (value.equals("examine")
            || value.equals("drop")
            || value.equals("destroy")
            || value.equals("unequip")
            || value.equals("unwear"))
        {
            return true;
        }
        if (value.equals("remove"))
        {
            return equipmentRemoval || allowBanking;
        }
        return allowBanking
            && (value.startsWith("deposit")
                || value.startsWith("withdraw")
                || value.startsWith("store")
                || value.startsWith("remove from"));
    }

    private ItemRequirement itemRequirement(
        Set<Integer> itemIds,
        Set<String> normalisedItemNames,
        Set<String> ownedCardIds)
    {
        Set<String> owned = ownedCardIds == null
            ? Collections.emptySet()
            : ownedCardIds;
        Set<String> required = new LinkedHashSet<>();
        boolean trackedById = false;
        boolean ambiguousByName = false;
        if (itemIds != null)
        {
            for (Integer itemId : itemIds)
            {
                if (itemId == null || itemId.intValue() < 0)
                {
                    continue;
                }
                String familyId = familyIndex.familyIdForItem(
                    itemId.intValue());
                Set<String> familyCards = familyIndex.cardIdsForFamily(
                    familyId);
                if (familyCards.isEmpty())
                {
                    continue;
                }
                trackedById = true;
                if (!ownsAny(familyCards, owned))
                {
                    required.addAll(familyCards);
                }
            }
        }
        boolean trackedByName = false;
        if (normalisedItemNames != null)
        {
            for (String itemName : normalisedItemNames)
            {
                String familyId = familyIndex.familyIdForUniqueItemName(
                    itemName);
                Set<String> familyCards = familyIndex.cardIdsForFamily(
                    familyId);
                if (familyCards.isEmpty())
                {
                    if (familyIndex.isAmbiguousItemName(itemName))
                    {
                        ambiguousByName = true;
                    }
                    continue;
                }
                trackedByName = true;
                if (!ownsAny(familyCards, owned))
                {
                    required.addAll(familyCards);
                }
            }
        }
        return new ItemRequirement(
            trackedById || trackedByName,
            ambiguousByName && !trackedById,
            required);
    }

    private static boolean ownsAny(
        Set<String> required,
        Set<String> ownedCardIds)
    {
        if (ownedCardIds == null || ownedCardIds.isEmpty())
        {
            return false;
        }
        for (String cardId : required)
        {
            if (ownedCardIds.contains(cardId))
            {
                return true;
            }
        }
        return false;
    }

    private static String normalise(String value)
    {
        if (value == null)
        {
            return "";
        }
        String withoutTags = TAGS.matcher(value).replaceAll("");
        return WHITESPACE.matcher(withoutTags)
            .replaceAll(" ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    private static final class ItemRequirement
    {
        private final boolean tracked;
        private final boolean ambiguousNameWithoutResolvedId;
        private final Set<String> lockedCards;

        private ItemRequirement(
            boolean tracked,
            boolean ambiguousNameWithoutResolvedId,
            Set<String> lockedCards)
        {
            this.tracked = tracked;
            this.ambiguousNameWithoutResolvedId = ambiguousNameWithoutResolvedId;
            this.lockedCards = Collections.unmodifiableSet(
                new LinkedHashSet<>(lockedCards));
        }

        private boolean isTracked()
        {
            return tracked;
        }

        private boolean hasAmbiguousNameWithoutResolvedId()
        {
            return ambiguousNameWithoutResolvedId;
        }

        private Set<String> getLockedCards()
        {
            return lockedCards;
        }
    }

    public static final class RestrictionDecision
    {
        private static final RestrictionDecision ALLOW =
            new RestrictionDecision(false, Collections.emptySet(), "");

        private final boolean blocked;
        private final Set<String> requiredCardIds;
        private final String explanation;

        private RestrictionDecision(
            boolean blocked,
            Set<String> requiredCardIds,
            String explanation)
        {
            this.blocked = blocked;
            this.requiredCardIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(requiredCardIds));
            this.explanation = explanation;
        }

        public static RestrictionDecision allow()
        {
            return ALLOW;
        }

        public static RestrictionDecision block(
            Set<String> requiredCardIds,
            String explanation)
        {
            return new RestrictionDecision(
                true,
                requiredCardIds,
                explanation == null ? "Locked." : explanation);
        }

        public boolean isBlocked()
        {
            return blocked;
        }

        public Set<String> getRequiredCardIds()
        {
            return requiredCardIds;
        }

        public String getExplanation()
        {
            return explanation;
        }
    }
}
