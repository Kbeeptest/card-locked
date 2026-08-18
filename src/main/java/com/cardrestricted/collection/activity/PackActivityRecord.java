package com.cardrestricted.collection.activity;

import com.cardrestricted.pack.PackCardResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PackActivityRecord
{
    private final UUID openingId;
    private final String packId;
    private final Instant purchasedAt;
    private final long price;
    private final List<PackCardResult> cardResults;
    private final int revealedCount;
    private final long duplicateShards;

    public PackActivityRecord(
        UUID openingId,
        String packId,
        Instant purchasedAt,
        long price,
        List<PackCardResult> cardResults,
        int revealedCount,
        long duplicateShards)
    {
        this.openingId = Objects.requireNonNull(openingId, "openingId");
        this.packId = requireText(packId, "packId");
        this.purchasedAt = Objects.requireNonNull(purchasedAt, "purchasedAt");
        if (price < 0 || revealedCount < 0 || duplicateShards < 0)
        {
            throw new IllegalArgumentException(
                "Pack activity values cannot be negative.");
        }
        this.price = price;
        this.cardResults = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(cardResults, "cardResults")));
        if (this.cardResults.stream().anyMatch(Objects::isNull))
        {
            throw new IllegalArgumentException(
                "Pack activity cannot contain null card results.");
        }
        if (revealedCount > this.cardResults.size())
        {
            throw new IllegalArgumentException(
                "Revealed count cannot exceed the pack size.");
        }
        this.revealedCount = revealedCount;
        this.duplicateShards = duplicateShards;
    }

    public UUID getOpeningId()
    {
        return openingId;
    }

    public String getPackId()
    {
        return packId;
    }

    public Instant getPurchasedAt()
    {
        return purchasedAt;
    }

    public long getPrice()
    {
        return price;
    }

    public List<PackCardResult> getCardResults()
    {
        return cardResults;
    }

    public int getRevealedCount()
    {
        return revealedCount;
    }

    public boolean isFullyRevealed()
    {
        return revealedCount == cardResults.size();
    }

    public int getNewCardCount()
    {
        return (int) cardResults.stream()
            .filter(result -> !result.isDuplicate())
            .count();
    }

    public int getDuplicateCount()
    {
        return cardResults.size() - getNewCardCount();
    }

    public long getDuplicateShards()
    {
        return duplicateShards;
    }

    private static String requireText(String value, String field)
    {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value;
    }
}
