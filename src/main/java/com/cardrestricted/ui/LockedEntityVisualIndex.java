package com.cardrestricted.ui;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.runelite.InteractionFamilyIndex;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/** Shared visual lookup using the same family resolution as action enforcement. */
public final class LockedEntityVisualIndex
{
    private final InteractionFamilyIndex familyIndex;

    public LockedEntityVisualIndex(CardCatalogue catalogue)
    {
        this.familyIndex = new InteractionFamilyIndex(
            Objects.requireNonNull(catalogue, "catalogue"));
    }

    boolean isItemLocked(int itemId, Set<String> ownedCardIds)
    {
        return isItemLocked(itemId, "", ownedCardIds);
    }

    boolean isItemLocked(
        int itemId,
        String itemName,
        Set<String> ownedCardIds)
    {
        String familyId = familyIndex.familyIdForItem(itemId);
        Set<String> cards = familyIndex.cardIdsForFamily(familyId);
        if (cards.isEmpty() && itemName != null && !itemName.isEmpty())
        {
            String normalised = com.cardrestricted.runelite
                .InteractionNameNormalizer.normaliseItemName(itemName);
            familyId = familyIndex.familyIdForUniqueItemName(normalised);
            cards = familyIndex.cardIdsForFamily(familyId);
            if (cards.isEmpty()
                && familyIndex.isAmbiguousItemName(normalised))
            {
                return true;
            }
        }
        return isLocked(cards, ownedCardIds);
    }

    boolean isNpcLocked(int npcId, String npcName, Set<String> ownedCardIds)
    {
        String familyId = familyIndex.familyIdForNpc(npcId, npcName);
        Set<String> cards = familyIndex.cardIdsForFamily(familyId);
        if (cards.isEmpty() && familyIndex.isAmbiguousNpcName(npcName))
        {
            // Enforcement fails closed for an ambiguous tracked identity. Keep
            // the visual state aligned instead of displaying an apparently
            // unrestricted NPC that the click gate will reject.
            return true;
        }
        return isLocked(cards, ownedCardIds);
    }

    private static boolean isLocked(
        Set<String> cardIds,
        Set<String> ownedCardIds)
    {
        if (cardIds == null || cardIds.isEmpty())
        {
            return false;
        }
        Set<String> owned = ownedCardIds == null
            ? Collections.emptySet()
            : ownedCardIds;
        for (String cardId : cardIds)
        {
            if (owned.contains(cardId))
            {
                return false;
            }
        }
        return true;
    }
}
