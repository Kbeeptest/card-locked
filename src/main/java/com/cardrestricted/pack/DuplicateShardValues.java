package com.cardrestricted.pack;

import com.cardrestricted.catalog.Rarity;

public final class DuplicateShardValues
{
    private DuplicateShardValues()
    {
    }

    public static long forRarity(Rarity rarity)
    {
        switch (rarity)
        {
            case COMMON:
                return 5;
            case UNCOMMON:
                return 12;
            case RARE:
                return 30;
            case EPIC:
                return 80;
            case LEGENDARY:
                return 200;
            case MYTHIC:
                return 500;
            case GODLY:
                return 1_200;
            default:
                throw new IllegalArgumentException(
                    "Unsupported rarity: " + rarity);
        }
    }
}
