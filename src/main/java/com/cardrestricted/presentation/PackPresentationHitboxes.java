package com.cardrestricted.presentation;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PackPresentationHitboxes
{
    private static final PackPresentationHitboxes EMPTY =
        new PackPresentationHitboxes(
            new Rectangle(),
            new Rectangle(),
            new Rectangle(),
            Collections.emptyList(),
            new Rectangle(),
            Collections.emptyList());

    private final Rectangle skipBounds;
    private final Rectangle closeBounds;
    private final Rectangle packBounds;
    private final List<CardHitbox> cardHitboxes;
    private final Rectangle backgroundCloseBounds;
    private final List<Rectangle> backgroundCloseExclusions;

    public PackPresentationHitboxes(
        Rectangle skipBounds,
        Rectangle closeBounds,
        Rectangle packBounds,
        List<CardHitbox> cardHitboxes)
    {
        this(
            skipBounds,
            closeBounds,
            packBounds,
            cardHitboxes,
            new Rectangle(),
            Collections.emptyList());
    }

    public PackPresentationHitboxes(
        Rectangle skipBounds,
        Rectangle closeBounds,
        Rectangle packBounds,
        List<CardHitbox> cardHitboxes,
        Rectangle backgroundCloseBounds,
        List<Rectangle> backgroundCloseExclusions)
    {
        this.skipBounds = new Rectangle(Objects.requireNonNull(
            skipBounds,
            "skipBounds"));
        this.closeBounds = new Rectangle(Objects.requireNonNull(
            closeBounds,
            "closeBounds"));
        this.packBounds = new Rectangle(Objects.requireNonNull(
            packBounds,
            "packBounds"));
        this.cardHitboxes = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(
                cardHitboxes,
                "cardHitboxes")));
        this.backgroundCloseBounds = new Rectangle(Objects.requireNonNull(
            backgroundCloseBounds,
            "backgroundCloseBounds"));
        Objects.requireNonNull(
            backgroundCloseExclusions,
            "backgroundCloseExclusions");
        List<Rectangle> exclusions = new ArrayList<>();
        for (Rectangle exclusion : backgroundCloseExclusions)
        {
            exclusions.add(new Rectangle(Objects.requireNonNull(
                exclusion,
                "backgroundCloseExclusion")));
        }
        this.backgroundCloseExclusions = Collections.unmodifiableList(
            exclusions);
    }

    public static PackPresentationHitboxes empty()
    {
        return EMPTY;
    }

    public PackPresentationSelection resolve(Point point)
    {
        Objects.requireNonNull(point, "point");
        if (skipBounds.contains(point))
        {
            return PackPresentationSelection.skip();
        }
        if (closeBounds.contains(point))
        {
            return PackPresentationSelection.closeSummary();
        }
        if (packBounds.contains(point))
        {
            return PackPresentationSelection.openPack();
        }
        for (CardHitbox hitbox : cardHitboxes)
        {
            if (hitbox.bounds.contains(point))
            {
                return PackPresentationSelection.revealCard(
                    hitbox.cardPosition);
            }
        }
        if (backgroundCloseBounds.contains(point)
            && backgroundCloseExclusions.stream()
                .noneMatch(exclusion -> exclusion.contains(point)))
        {
            return PackPresentationSelection.closeSummary();
        }
        return PackPresentationSelection.none();
    }


    public Rectangle getSkipBounds()
    {
        return new Rectangle(skipBounds);
    }

    public Rectangle getCloseBounds()
    {
        return new Rectangle(closeBounds);
    }

    public Rectangle getPackBounds()
    {
        return new Rectangle(packBounds);
    }

    public List<Rectangle> getCardBounds()
    {
        List<Rectangle> bounds = new ArrayList<>();
        for (CardHitbox hitbox : cardHitboxes)
        {
            bounds.add(new Rectangle(hitbox.bounds));
        }
        return Collections.unmodifiableList(bounds);
    }

    public static final class CardHitbox
    {
        private final int cardPosition;
        private final Rectangle bounds;

        public CardHitbox(int cardPosition, Rectangle bounds)
        {
            if (cardPosition < 0)
            {
                throw new IllegalArgumentException(
                    "Card position cannot be negative.");
            }
            this.cardPosition = cardPosition;
            this.bounds = new Rectangle(Objects.requireNonNull(
                bounds,
                "bounds"));
        }
    }
}
