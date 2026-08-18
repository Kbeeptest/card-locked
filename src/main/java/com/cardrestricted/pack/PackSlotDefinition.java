package com.cardrestricted.pack;

import java.util.Objects;

public final class PackSlotDefinition
{
    private final String slotId;
    private final RarityRollTable rollTable;
    private final PackFoilRule foilRule;

    public PackSlotDefinition(
        String slotId,
        RarityRollTable rollTable)
    {
        this(slotId, rollTable, PackFoilRule.RANDOM);
    }

    public PackSlotDefinition(
        String slotId,
        RarityRollTable rollTable,
        PackFoilRule foilRule)
    {
        Objects.requireNonNull(slotId, "slotId");
        if (slotId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                "slotId cannot be blank.");
        }
        this.slotId = slotId;
        this.rollTable =
            Objects.requireNonNull(rollTable, "rollTable");
        this.foilRule = Objects.requireNonNull(foilRule, "foilRule");
    }

    public String getSlotId()
    {
        return slotId;
    }

    public RarityRollTable getRollTable()
    {
        return rollTable;
    }

    public PackFoilRule getFoilRule()
    {
        return foilRule;
    }
}
