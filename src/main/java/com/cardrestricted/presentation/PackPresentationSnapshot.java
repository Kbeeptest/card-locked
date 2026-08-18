package com.cardrestricted.presentation;

import com.cardrestricted.collection.activity.PackNames;
import com.cardrestricted.pack.PackCardResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PackPresentationSnapshot
{
    private final UUID openingId;
    private final String packId;
    private final Instant purchasedAt;
    private final PackPresentationState state;
    private final List<PackCardResult> cardResults;
    private final Set<Integer> revealedPositions;
    private final Set<Integer> requestedPositions;
    private final Map<Integer, Double> flipProgressByPosition;
    private final boolean openingComplete;
    private final double transitionProgress;

    PackPresentationSnapshot(
        UUID openingId,
        String packId,
        Instant purchasedAt,
        PackPresentationState state,
        List<PackCardResult> cardResults,
        Set<Integer> revealedPositions,
        Set<Integer> requestedPositions,
        Map<Integer, Double> flipProgressByPosition,
        boolean openingComplete,
        double transitionProgress)
    {
        this.openingId = openingId;
        this.packId = packId;
        this.purchasedAt = purchasedAt;
        this.state = Objects.requireNonNull(state, "state");
        this.cardResults = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(
                cardResults,
                "cardResults")));
        this.revealedPositions = Collections.unmodifiableSet(
            new LinkedHashSet<>(Objects.requireNonNull(
                revealedPositions,
                "revealedPositions")));
        this.requestedPositions = Collections.unmodifiableSet(
            new LinkedHashSet<>(Objects.requireNonNull(
                requestedPositions,
                "requestedPositions")));
        this.flipProgressByPosition = Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(
                flipProgressByPosition,
                "flipProgressByPosition")));
        this.openingComplete = openingComplete;
        if (transitionProgress < 0.0 || transitionProgress > 1.0)
        {
            throw new IllegalArgumentException(
                "Transition progress must be between zero and one.");
        }
        this.transitionProgress = transitionProgress;
    }

    public Optional<UUID> getOpeningId()
    {
        return Optional.ofNullable(openingId);
    }


    public Optional<String> getPackId()
    {
        return Optional.ofNullable(packId);
    }

    public String getPackDisplayName()
    {
        if ("nexus.single-card".equals(packId))
        {
            return "Nexus Unlock";
        }
        return PackNames.displayName(packId);
    }

    public Optional<Instant> getPurchasedAt()
    {
        return Optional.ofNullable(purchasedAt);
    }

    public PackPresentationState getState()
    {
        return state;
    }

    public List<PackCardResult> getCardResults()
    {
        return cardResults;
    }

    public Set<Integer> getRevealedPositions()
    {
        return revealedPositions;
    }

    public Set<Integer> getRequestedPositions()
    {
        return requestedPositions;
    }

    public boolean isRevealed(int position)
    {
        return revealedPositions.contains(position);
    }

    public boolean isRequested(int position)
    {
        return requestedPositions.contains(position);
    }

    public boolean isFlipping(int position)
    {
        return flipProgressByPosition.containsKey(position);
    }

    public double getFlipProgress(int position)
    {
        return flipProgressByPosition.getOrDefault(position, 1.0);
    }

    public int getRevealedCount()
    {
        return revealedPositions.size();
    }

    public int getTotalCards()
    {
        return cardResults.size();
    }


    public int getNewCardCount()
    {
        int count = 0;
        for (PackCardResult result : cardResults)
        {
            if (!result.isDuplicate())
            {
                count++;
            }
        }
        return count;
    }

    public int getDuplicateCount()
    {
        return cardResults.size() - getNewCardCount();
    }

    public long getDuplicateShards()
    {
        long shards = 0L;
        for (PackCardResult result : cardResults)
        {
            shards = Math.addExact(shards, result.getShardsAwarded());
        }
        return shards;
    }

    public String getProgressLabel()
    {
        return getRevealedCount() + "/" + getTotalCards() + " REVEALED";
    }

    public boolean isOpeningComplete()
    {
        return openingComplete;
    }

    public boolean hasActiveFlips()
    {
        return !flipProgressByPosition.isEmpty();
    }

    public double getTransitionProgress()
    {
        return transitionProgress;
    }

    public boolean canRevealPosition(int position)
    {
        return state == PackPresentationState.READY_TO_REVEAL
            && position >= 0
            && position < cardResults.size()
            && !revealedPositions.contains(position)
            && !requestedPositions.contains(position);
    }

    public boolean canRevealAny()
    {
        if (state != PackPresentationState.READY_TO_REVEAL)
        {
            return false;
        }
        for (int position = 0; position < cardResults.size(); position++)
        {
            if (canRevealPosition(position))
            {
                return true;
            }
        }
        return false;
    }

    public boolean canSkipTransition()
    {
        return state == PackPresentationState.PACK_ENTER
            || state == PackPresentationState.PACK_OPEN
            || state == PackPresentationState.CARD_DEAL
            || hasActiveFlips();
    }
}
