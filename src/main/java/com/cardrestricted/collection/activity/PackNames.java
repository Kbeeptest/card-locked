package com.cardrestricted.collection.activity;

import java.util.Locale;

public final class PackNames
{
    private PackNames()
    {
    }

    public static String displayName(String packId)
    {
        if (packId == null || packId.trim().isEmpty())
        {
            return "Unknown pack";
        }
        if (packId.startsWith("pack.starter-randomised."))
        {
            return "One-time Starter Pack";
        }
        if (packId.startsWith("pack.standard."))
        {
            return "Standard Pack";
        }
        if (packId.startsWith("pack.uncommon-plus."))
        {
            return "Uncommon+ Pack";
        }
        if (packId.startsWith("pack.explorer."))
        {
            return "Explorer Pack";
        }
        if (packId.startsWith("pack.rare-plus.")
            || packId.startsWith("pack.rare-plus-items."))
        {
            return "Rare+ Pack";
        }
        if (packId.startsWith("pack.adventure."))
        {
            return "Adventure Pack";
        }
        if (packId.startsWith("pack.collector."))
        {
            return "Collector Pack";
        }
        if (packId.startsWith("pack.milestone.initiate-foil."))
        {
            return "Initiate's Foil Pack";
        }
        if (packId.startsWith("pack.milestone.hero."))
        {
            return "Hero's Pack";
        }
        if (packId.startsWith("pack.milestone.noble."))
        {
            return "Noble's Pack";
        }
        if (packId.startsWith("pack.milestone.legend."))
        {
            return "Legend's Pack";
        }
        if (packId.startsWith("pack.milestone.mythical."))
        {
            return "Mythical Pack";
        }
        if (packId.startsWith("pack.milestone.gods."))
        {
            return "Pack of the Gods";
        }
        if (packId.startsWith("pack.noncombat-npc."))
        {
            return "Noncombat NPC Pack";
        }
        if (packId.startsWith("pack.attackable-npc."))
        {
            return "Attackable NPC Pack";
        }
        if (packId.startsWith("pack.foil-test."))
        {
            return "Temporary Foil Pack";
        }
        if (packId.startsWith("pack.premium-foil-test."))
        {
            return "Temporary Legendary+ Foil Pack";
        }
        if (packId.startsWith("pack.test.foil-tier."))
        {
            return "Tier-chain Foil Test Pack";
        }
        if (packId.startsWith("pack.test.foil-armour-slot."))
        {
            return "Armour-slot Foil Test Pack";
        }
        if (packId.startsWith("pack.test.foil-boss."))
        {
            return "Boss-reward Foil Test Pack";
        }
        String value = packId.toLowerCase(Locale.ROOT)
            .replace('.', ' ')
            .replace('-', ' ')
            .trim();
        return value.isEmpty()
            ? "Unknown pack"
            : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
