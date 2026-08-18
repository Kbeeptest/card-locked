package com.cardrestricted.presentation;

import java.util.Objects;

public final class PackPresentationSelection
{
    private static final PackPresentationSelection NONE =
        new PackPresentationSelection(PackPresentationAction.NONE, -1);

    private final PackPresentationAction action;
    private final int cardPosition;

    private PackPresentationSelection(
        PackPresentationAction action,
        int cardPosition)
    {
        this.action = Objects.requireNonNull(action, "action");
        this.cardPosition = cardPosition;
    }

    public static PackPresentationSelection none()
    {
        return NONE;
    }

    public static PackPresentationSelection skip()
    {
        return new PackPresentationSelection(
            PackPresentationAction.SKIP,
            -1);
    }

    public static PackPresentationSelection openPack()
    {
        return new PackPresentationSelection(
            PackPresentationAction.OPEN_PACK,
            -1);
    }

    public static PackPresentationSelection revealCard(int cardPosition)
    {
        if (cardPosition < 0)
        {
            throw new IllegalArgumentException(
                "Card position cannot be negative.");
        }
        return new PackPresentationSelection(
            PackPresentationAction.REVEAL_CARD,
            cardPosition);
    }

    public static PackPresentationSelection closeSummary()
    {
        return new PackPresentationSelection(
            PackPresentationAction.CLOSE_SUMMARY,
            -1);
    }

    public PackPresentationAction getAction()
    {
        return action;
    }

    public int getCardPosition()
    {
        if (action != PackPresentationAction.REVEAL_CARD)
        {
            throw new IllegalStateException(
                "This selection does not contain a card position.");
        }
        return cardPosition;
    }
}
