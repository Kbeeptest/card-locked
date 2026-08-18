package com.cardrestricted.pack;

import com.cardrestricted.persistence.CollectionState;
import java.util.Objects;

public final class PackRevealResult
{
    private final CollectionState state;
    private final PackCardResult revealedCard;
    private final int revealNumber;
    private final int totalCards;
    private final int cardPosition;

    public PackRevealResult(
        CollectionState state,
        PackCardResult revealedCard,
        int revealNumber,
        int totalCards)
    {
        this(
            state,
            revealedCard,
            revealNumber,
            totalCards,
            revealNumber);
    }

    public PackRevealResult(
        CollectionState state,
        PackCardResult revealedCard,
        int revealNumber,
        int totalCards,
        int cardPosition)
    {
        this.state = Objects.requireNonNull(state, "state");
        this.revealedCard = Objects.requireNonNull(
            revealedCard,
            "revealedCard");
        if (revealNumber < 1 || revealNumber > totalCards)
        {
            throw new IllegalArgumentException(
                "Pack reveal number is invalid.");
        }
        if (cardPosition < 1 || cardPosition > totalCards)
        {
            throw new IllegalArgumentException(
                "Pack card position is invalid.");
        }
        this.revealNumber = revealNumber;
        this.totalCards = totalCards;
        this.cardPosition = cardPosition;
    }

    public CollectionState getState()
    {
        return state;
    }

    public PackCardResult getRevealedCard()
    {
        return revealedCard;
    }

    public int getRevealNumber()
    {
        return revealNumber;
    }

    public int getTotalCards()
    {
        return totalCards;
    }

    public int getCardPosition()
    {
        return cardPosition;
    }

    public int getCardIndex()
    {
        return cardPosition - 1;
    }

    public boolean isComplete()
    {
        return revealNumber == totalCards;
    }
}
