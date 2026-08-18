package com.cardrestricted.nexus;

import com.cardrestricted.catalog.Rarity;

public final class NexusExchangeCosts
{
    private NexusExchangeCosts()
    {
    }

    public static long forRarity(Rarity rarity)
    {
        switch (rarity)
        {
            case COMMON:
                return 300L;
            case UNCOMMON:
                return 800L;
            case RARE:
                return 2_250L;
            case EPIC:
                return 6_000L;
            case LEGENDARY:
                return 15_000L;
            case MYTHIC:
                return 36_000L;
            case GODLY:
                return 90_000L;
            default:
                throw new IllegalArgumentException("Unsupported rarity: " + rarity);
        }
    }
}
