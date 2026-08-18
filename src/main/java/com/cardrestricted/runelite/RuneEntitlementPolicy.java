package com.cardrestricted.runelite;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.ItemID;

/** Card-family entitlement checks for runes, including combination-rune substitutes. */
public final class RuneEntitlementPolicy
{
    private RuneEntitlementPolicy()
    {
    }

    public static SimpleRestrictionService.RestrictionDecision evaluate(
        Set<Integer> requiredRuneIds,
        Set<String> ownedCardIds,
        SimpleRestrictionService service)
    {
        Set<String> missingPrimaryCards = new LinkedHashSet<>();
        if (requiredRuneIds == null || requiredRuneIds.isEmpty())
        {
            return SimpleRestrictionService.RestrictionDecision.allow();
        }
        Set<String> owned = ownedCardIds == null
            ? Collections.emptySet()
            : ownedCardIds;
        for (Integer required : requiredRuneIds)
        {
            if (required == null)
            {
                continue;
            }
            Set<Integer> alternatives = alternativesFor(required.intValue());
            if (ownsAnyRuneCard(alternatives, owned, service))
            {
                continue;
            }
            missingPrimaryCards.addAll(service.requiredCardsForItem(
                required.intValue()));
        }
        if (missingPrimaryCards.isEmpty())
        {
            return SimpleRestrictionService.RestrictionDecision.allow();
        }
        return SimpleRestrictionService.RestrictionDecision.block(
            missingPrimaryCards,
            "A rune card required by this spell has not been acquired.");
    }

    static Set<Integer> alternativesFor(int runeId)
    {
        if (runeId == ItemID.AIR_RUNE)
        {
            return set(ItemID.AIR_RUNE, ItemID.MIST_RUNE, ItemID.DUST_RUNE,
                ItemID.SMOKE_RUNE);
        }
        if (runeId == ItemID.WATER_RUNE)
        {
            return set(ItemID.WATER_RUNE, ItemID.MIST_RUNE, ItemID.MUD_RUNE,
                ItemID.STEAM_RUNE);
        }
        if (runeId == ItemID.EARTH_RUNE)
        {
            return set(ItemID.EARTH_RUNE, ItemID.DUST_RUNE, ItemID.MUD_RUNE,
                ItemID.LAVA_RUNE);
        }
        if (runeId == ItemID.FIRE_RUNE)
        {
            return set(ItemID.FIRE_RUNE, ItemID.SMOKE_RUNE, ItemID.STEAM_RUNE,
                ItemID.LAVA_RUNE, ItemID.SUNFIRE_RUNE);
        }
        return Collections.singleton(runeId);
    }

    private static boolean ownsAnyRuneCard(
        Set<Integer> runeIds,
        Set<String> ownedCardIds,
        SimpleRestrictionService service)
    {
        for (Integer runeId : runeIds)
        {
            for (String cardId : service.requiredCardsForItem(runeId))
            {
                if (ownedCardIds.contains(cardId))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<Integer> set(Integer... ids)
    {
        return Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(ids)));
    }
}
