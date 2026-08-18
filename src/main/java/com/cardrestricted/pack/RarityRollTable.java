package com.cardrestricted.pack;

import com.cardrestricted.catalog.Rarity;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class RarityRollTable
{
    public static final int TOTAL_WEIGHT = 100_000;

    private final String tableId;
    private final int version;
    private final Rarity minimumRarity;
    private final Map<Rarity, Integer> weights;

    public RarityRollTable(
        String tableId,
        int version,
        Rarity minimumRarity,
        Map<Rarity, Integer> weights)
    {
        Objects.requireNonNull(tableId, "tableId");
        if (tableId.trim().isEmpty() || version < 1)
        {
            throw new IllegalArgumentException(
                "Roll table identity is invalid.");
        }
        this.tableId = tableId;
        this.version = version;
        this.minimumRarity =
            Objects.requireNonNull(minimumRarity, "minimumRarity");
        Objects.requireNonNull(weights, "weights");
        EnumMap<Rarity, Integer> copy =
            new EnumMap<>(Rarity.class);
        int total = 0;
        for (Rarity rarity : Rarity.values())
        {
            int weight = weights.getOrDefault(rarity, 0);
            if (weight < 0)
            {
                throw new IllegalArgumentException(
                    "Rarity weights cannot be negative.");
            }
            if (rarity.ordinal() < minimumRarity.ordinal()
                && weight != 0)
            {
                throw new IllegalArgumentException(
                    "A roll table cannot weight a rarity below its floor.");
            }
            copy.put(rarity, weight);
            total = Math.addExact(total, weight);
        }
        if (total != TOTAL_WEIGHT)
        {
            throw new IllegalArgumentException(
                "Rarity weights must total " + TOTAL_WEIGHT + ".");
        }
        this.weights = Collections.unmodifiableMap(copy);
    }

    public Rarity roll(Random random)
    {
        Objects.requireNonNull(random, "random");
        int roll = random.nextInt(TOTAL_WEIGHT);
        int cumulative = 0;
        for (Rarity rarity : Rarity.values())
        {
            cumulative += weights.get(rarity);
            if (roll < cumulative)
            {
                return rarity;
            }
        }
        throw new IllegalStateException(
            "The rarity roll table did not resolve a result.");
    }

    public String getTableId()
    {
        return tableId;
    }

    public int getVersion()
    {
        return version;
    }

    public Rarity getMinimumRarity()
    {
        return minimumRarity;
    }

    public int getWeight(Rarity rarity)
    {
        return weights.get(Objects.requireNonNull(rarity, "rarity"));
    }
}
