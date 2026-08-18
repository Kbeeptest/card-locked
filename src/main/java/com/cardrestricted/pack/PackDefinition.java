package com.cardrestricted.pack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PackDefinition
{
    private final String packId;
    private final int version;
    private final long price;
    public static final int NORMAL_SELECTION_WEIGHT = 100;

    private final PackContentPool contentPool;
    private final List<PackSlotDefinition> slots;
    private final int unownedCardWeightPercent;
    private final int npcCardWeightPercent;
    private final int equipmentCardWeightPercent;

    public PackDefinition(
        String packId,
        int version,
        long price,
        List<PackSlotDefinition> slots)
    {
        this(
            packId,
            version,
            price,
            PackContentPool.ITEMS,
            NORMAL_SELECTION_WEIGHT,
            NORMAL_SELECTION_WEIGHT,
            NORMAL_SELECTION_WEIGHT,
            slots);
    }

    public PackDefinition(
        String packId,
        int version,
        long price,
        PackContentPool contentPool,
        List<PackSlotDefinition> slots)
    {
        this(
            packId,
            version,
            price,
            contentPool,
            NORMAL_SELECTION_WEIGHT,
            NORMAL_SELECTION_WEIGHT,
            NORMAL_SELECTION_WEIGHT,
            slots);
    }

    public PackDefinition(
        String packId,
        int version,
        long price,
        PackContentPool contentPool,
        int unownedCardWeightPercent,
        List<PackSlotDefinition> slots)
    {
        this(
            packId,
            version,
            price,
            contentPool,
            unownedCardWeightPercent,
            NORMAL_SELECTION_WEIGHT,
            NORMAL_SELECTION_WEIGHT,
            slots);
    }

    /**
     * Full selection-weight constructor. Card-type weighting is applied only
     * after a slot's rarity has been rolled, so it cannot alter pack rarity
     * odds. A value of 100 is neutral.
     */
    public PackDefinition(
        String packId,
        int version,
        long price,
        PackContentPool contentPool,
        int unownedCardWeightPercent,
        int npcCardWeightPercent,
        int equipmentCardWeightPercent,
        List<PackSlotDefinition> slots)
    {
        Objects.requireNonNull(packId, "packId");
        if (packId.trim().isEmpty() || version < 1 || price < 0)
        {
            throw new IllegalArgumentException(
                "Pack definition identity or price is invalid.");
        }
        this.contentPool = Objects.requireNonNull(
            contentPool,
            "contentPool");
        if (unownedCardWeightPercent < NORMAL_SELECTION_WEIGHT)
        {
            throw new IllegalArgumentException(
                "Unowned-card weighting cannot be below normal selection weight.");
        }
        this.unownedCardWeightPercent = unownedCardWeightPercent;
        if (npcCardWeightPercent < 1 || equipmentCardWeightPercent < 1)
        {
            throw new IllegalArgumentException(
                "Card-type selection weights must be positive.");
        }
        this.npcCardWeightPercent = npcCardWeightPercent;
        this.equipmentCardWeightPercent = equipmentCardWeightPercent;
        Objects.requireNonNull(slots, "slots");
        if (slots.isEmpty() || slots.stream().anyMatch(Objects::isNull))
        {
            throw new IllegalArgumentException(
                "A pack definition must contain slots.");
        }
        this.packId = packId;
        this.version = version;
        this.price = price;
        this.slots = Collections.unmodifiableList(
            new ArrayList<>(slots));
    }

    public String getPackId()
    {
        return packId;
    }

    public int getVersion()
    {
        return version;
    }

    public long getPrice()
    {
        return price;
    }

    public PackContentPool getContentPool()
    {
        return contentPool;
    }

    public int getUnownedCardWeightPercent()
    {
        return unownedCardWeightPercent;
    }

    public int getNpcCardWeightPercent()
    {
        return npcCardWeightPercent;
    }

    public int getEquipmentCardWeightPercent()
    {
        return equipmentCardWeightPercent;
    }

    public List<PackSlotDefinition> getSlots()
    {
        return slots;
    }
}
