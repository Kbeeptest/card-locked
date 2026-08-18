package com.cardrestricted.pack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class PendingPackReveal
{
    private final UUID openingId;
    private final String packId;
    private final Instant purchasedAt;
    private final List<PackCardResult> cardResults;
    private final Set<Integer> revealedPositions;

    public PendingPackReveal(
        UUID openingId,
        String packId,
        Instant purchasedAt,
        List<PackCardResult> cardResults,
        int revealedCount)
    {
        this(
            openingId,
            packId,
            purchasedAt,
            cardResults,
            sequentialPositions(revealedCount));
    }

    public PendingPackReveal(
        UUID openingId,
        String packId,
        Instant purchasedAt,
        List<PackCardResult> cardResults,
        Set<Integer> revealedPositions)
    {
        this.openingId = Objects.requireNonNull(openingId, "openingId");
        Objects.requireNonNull(packId, "packId");
        if (packId.trim().isEmpty())
        {
            throw new IllegalArgumentException("packId cannot be blank.");
        }
        this.packId = packId;
        this.purchasedAt = Objects.requireNonNull(
            purchasedAt,
            "purchasedAt");
        Objects.requireNonNull(cardResults, "cardResults");
        if (cardResults.isEmpty())
        {
            throw new IllegalArgumentException(
                "A pack reveal must contain cards.");
        }
        List<PackCardResult> resultCopy = new ArrayList<>(cardResults);
        if (resultCopy.stream().anyMatch(Objects::isNull))
        {
            throw new IllegalArgumentException(
                "Pack reveal cards cannot be null.");
        }

        Objects.requireNonNull(revealedPositions, "revealedPositions");
        LinkedHashSet<Integer> positionCopy =
            new LinkedHashSet<>(revealedPositions);
        if (positionCopy.size() >= resultCopy.size())
        {
            throw new IllegalArgumentException(
                "A pending reveal must contain an unrevealed card.");
        }
        for (int position : positionCopy)
        {
            if (position < 0 || position >= resultCopy.size())
            {
                throw new IllegalArgumentException(
                    "A revealed card position is outside the pack.");
            }
        }

        this.cardResults = Collections.unmodifiableList(resultCopy);
        this.revealedPositions = Collections.unmodifiableSet(positionCopy);
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

    public List<PackCardResult> getCardResults()
    {
        return cardResults;
    }

    public Set<Integer> getRevealedPositions()
    {
        return revealedPositions;
    }

    public int getRevealedCount()
    {
        return revealedPositions.size();
    }

    public boolean isRevealed(int position)
    {
        validatePosition(position);
        return revealedPositions.contains(position);
    }

    public PackCardResult getCardAt(int position)
    {
        validatePosition(position);
        return cardResults.get(position);
    }

    public int getNextUnrevealedPosition()
    {
        for (int position = 0; position < cardResults.size(); position++)
        {
            if (!revealedPositions.contains(position))
            {
                return position;
            }
        }
        throw new IllegalStateException(
            "The pending pack has no unrevealed cards.");
    }

    public PackCardResult getNextCard()
    {
        return getCardAt(getNextUnrevealedPosition());
    }

    public boolean isFinalCard()
    {
        return revealedPositions.size() + 1 == cardResults.size();
    }

    public PendingPackReveal reveal(int position)
    {
        validatePosition(position);
        if (revealedPositions.contains(position))
        {
            throw new IllegalStateException(
                "That pack position has already been revealed.");
        }
        if (isFinalCard())
        {
            throw new IllegalStateException(
                "The final reveal has no pending successor.");
        }
        LinkedHashSet<Integer> next =
            new LinkedHashSet<>(revealedPositions);
        next.add(position);
        return new PendingPackReveal(
            openingId,
            packId,
            purchasedAt,
            cardResults,
            next);
    }

    public PendingPackReveal advance()
    {
        return reveal(getNextUnrevealedPosition());
    }

    private void validatePosition(int position)
    {
        if (position < 0 || position >= cardResults.size())
        {
            throw new IllegalArgumentException(
                "Pack card position is invalid.");
        }
    }

    private static Set<Integer> sequentialPositions(int revealedCount)
    {
        if (revealedCount < 0)
        {
            throw new IllegalArgumentException(
                "Pending reveal count is invalid.");
        }
        return IntStream.range(0, revealedCount)
            .boxed()
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
