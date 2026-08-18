package com.cardrestricted.runelite;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Conservative quarantine for service/dialogue actions that can consume an
 * inventory item without exposing an item id in MenuOptionClicked.
 */
public final class InventoryConsumptionIntegrityRules
{
    private InventoryConsumptionIntegrityRules()
    {
    }

    public static boolean canConsumeImplicitInventory(
        String option,
        String target,
        String dialogueContext)
    {
        String value = normalise(option) + " "
            + normalise(target) + " "
            + normalise(dialogueContext);
        return containsPhrase(value,
            "pay", "payment", "exchange", "trade in", "trade-in",
            "redeem", "hand in", "hand-in", "give", "offer",
            "sacrifice", "donate", "repair", "recharge", "reclaim",
            "insure", "upgrade", "convert", "feed", "load", "fuel",
            "refill", "compost", "fertilise", "fertilize", "cure",
            "deposit materials", "turn in", "turn-in");
    }

    public static Set<Integer> restrictedCandidates(
        String option,
        String target,
        String dialogueContext,
        int uniqueCardCount,
        Set<Integer> inventoryItemIds)
    {
        if (!canConsumeImplicitInventory(option, target, dialogueContext)
            || inventoryItemIds == null
            || inventoryItemIds.isEmpty())
        {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(
            CoinMilestoneRules.cardRestrictedItemIds(
                uniqueCardCount,
                inventoryItemIds)));
    }

    private static boolean containsPhrase(String value, String... phrases)
    {
        String padded = " " + value.replaceAll("[^a-z0-9]+", " ") + " ";
        for (String phrase : phrases)
        {
            String normalised = normalise(phrase)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
            if (!normalised.isEmpty()
                && padded.contains(" " + normalised + " "))
            {
                return true;
            }
        }
        return false;
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
