package com.cardrestricted.collection.progress;

import java.util.Locale;

public final class CollectionProgress
{
    private final int total;
    private final int owned;
    private final int foil;

    public CollectionProgress(int total, int owned, int foil)
    {
        if (total < 0 || owned < 0 || foil < 0)
        {
            throw new IllegalArgumentException(
                "Collection progress counts cannot be negative.");
        }
        if (owned > total)
        {
            throw new IllegalArgumentException(
                "Owned cards cannot exceed the total card count.");
        }
        if (foil > owned)
        {
            throw new IllegalArgumentException(
                "Foil cards cannot exceed owned cards.");
        }
        this.total = total;
        this.owned = owned;
        this.foil = foil;
    }

    public int getTotal()
    {
        return total;
    }

    public int getOwned()
    {
        return owned;
    }

    public int getMissing()
    {
        return total - owned;
    }

    public int getFoil()
    {
        return foil;
    }

    public double getCompletionPercent()
    {
        return total == 0 ? 0.0 : owned * 100.0 / total;
    }

    public boolean isComplete()
    {
        return total > 0 && owned == total;
    }

    public String formatRatio()
    {
        return owned + " / " + total;
    }

    public String formatPercent()
    {
        return String.format(
            Locale.ROOT,
            "%.1f%%",
            getCompletionPercent());
    }
}
