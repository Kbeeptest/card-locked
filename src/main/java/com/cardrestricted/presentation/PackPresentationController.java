package com.cardrestricted.presentation;

import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.pack.PackRevealResult;
import com.cardrestricted.pack.PendingPackReveal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PackPresentationController
{
    public static final long PACK_ENTER_MILLIS = 280L;
    public static final long PACK_OPEN_MILLIS = 0L;
    public static final long CARD_DEAL_MILLIS = 720L;
    /** Prime one display frame so the first post-click repaint is already moving. */
    public static final long CARD_DEAL_PRIME_MILLIS = 16L;
    public static final long CARD_FLIP_MILLIS = 430L;
    public static final long FOIL_CARD_FLIP_MILLIS = 1350L;
    /** Preserve visual continuity if Swing/EDT scheduling briefly stalls. */
    public static final long MAX_ANIMATION_STEP_MILLIS = 75L;

    private UUID openingId;
    private String packId;
    private Instant purchasedAt;
    private List<PackCardResult> cardResults = Collections.emptyList();
    private final Set<Integer> revealedPositions = new LinkedHashSet<>();
    private final Set<Integer> requestedPositions = new LinkedHashSet<>();
    private final Map<Integer, Long> flipElapsedByPosition =
        new LinkedHashMap<>();
    private PackPresentationState state = PackPresentationState.IDLE;
    private boolean openingComplete;
    private boolean reducedMotion;
    private long stateElapsedMillis;
    private long lastTickMillis = -1L;

    public synchronized void setReducedMotion(boolean reducedMotion)
    {
        this.reducedMotion = reducedMotion;
    }

    public synchronized void synchronise(
        Optional<PendingPackReveal> pendingReveal)
    {
        Objects.requireNonNull(pendingReveal, "pendingReveal");
        if (pendingReveal.isEmpty())
        {
            if (!openingComplete && state != PackPresentationState.SUMMARY)
            {
                reset();
            }
            return;
        }

        PendingPackReveal reveal = pendingReveal.orElseThrow();
        boolean newOpening = !reveal.getOpeningId().equals(openingId);
        openingId = reveal.getOpeningId();
        packId = reveal.getPackId();
        purchasedAt = reveal.getPurchasedAt();
        cardResults = Collections.unmodifiableList(
            new ArrayList<>(reveal.getCardResults()));

        Set<Integer> persisted = reveal.getRevealedPositions();
        revealedPositions.addAll(persisted);
        requestedPositions.removeAll(persisted);

        if (!newOpening)
        {
            return;
        }

        revealedPositions.clear();
        revealedPositions.addAll(persisted);
        requestedPositions.clear();
        flipElapsedByPosition.clear();
        openingComplete = false;
        lastTickMillis = -1L;

        transitionTo(revealedPositions.isEmpty()
            ? PackPresentationState.PACK_ENTER
            : PackPresentationState.READY_TO_REVEAL);
    }

    public synchronized boolean markRevealRequested(int cardPosition)
    {
        if (!canRequest(cardPosition))
        {
            return false;
        }
        boolean animationWasIdle = !needsAnimationTick();
        requestedPositions.add(cardPosition);
        // Start presentation immediately. The durable reveal commit runs on
        // the profile mutation executor and must not be allowed to add disk
        // latency to the click-to-flip response. If persistence is still in
        // flight when the animation finishes, advanceFlips deliberately holds
        // the card at progress 1.0 until onRevealCommitted() confirms it.
        flipElapsedByPosition.put(cardPosition, 0L);
        if (animationWasIdle)
        {
            resetAnimationClock();
        }
        return true;
    }

    public synchronized List<Integer> markAllRevealRequested()
    {
        if (state != PackPresentationState.READY_TO_REVEAL)
        {
            return Collections.emptyList();
        }
        boolean animationWasIdle = !needsAnimationTick();
        List<Integer> positions = new ArrayList<>();
        for (int position = 0; position < cardResults.size(); position++)
        {
            if (canRequest(position))
            {
                requestedPositions.add(position);
                flipElapsedByPosition.put(position, 0L);
                positions.add(position);
            }
        }
        if (!positions.isEmpty() && animationWasIdle)
        {
            resetAnimationClock();
        }
        return Collections.unmodifiableList(positions);
    }

    public synchronized void clearRevealRequest(int cardPosition)
    {
        requestedPositions.remove(cardPosition);
        if (!revealedPositions.contains(cardPosition))
        {
            flipElapsedByPosition.remove(cardPosition);
        }
    }

    public synchronized void onRevealCommitted(PackRevealResult result)
    {
        Objects.requireNonNull(result, "result");
        int position = result.getCardIndex();
        if (position < 0 || position >= cardResults.size())
        {
            throw new IllegalArgumentException(
                "Committed reveal position is outside the pack.");
        }
        requestedPositions.remove(position);
        revealedPositions.add(position);
        // markRevealRequested() normally started this flip optimistically.
        // Preserve that elapsed visual time when persistence catches up rather
        // than restarting the animation after the disk commit. A committed
        // reveal restored from another path still receives a normal flip.
        Long elapsed = flipElapsedByPosition.get(position);
        if (elapsed == null)
        {
            boolean animationWasIdle = !needsAnimationTick();
            flipElapsedByPosition.put(position, 0L);
            if (animationWasIdle)
            {
                resetAnimationClock();
            }
        }
        else if (elapsed >= flipDuration(position))
        {
            flipElapsedByPosition.remove(position);
        }
        openingComplete = result.isComplete();
        if (state != PackPresentationState.READY_TO_REVEAL)
        {
            transitionTo(PackPresentationState.READY_TO_REVEAL);
        }
        completeSummaryWhenReady();
    }

    /**
     * True only while time-based presentation state can visibly change.
     * READY_TO_REVEAL with no active flip and SUMMARY are intentionally idle
     * so the overlay does not repaint the entire RuneLite canvas at 30 FPS.
     */
    public synchronized boolean needsAnimationTick()
    {
        if (isIntroTransition(state))
        {
            return true;
        }
        for (Map.Entry<Integer, Long> entry
            : flipElapsedByPosition.entrySet())
        {
            if (entry.getValue() < flipDuration(entry.getKey()))
            {
                return true;
            }
        }
        return false;
    }


    public synchronized void startSingleCardReveal(PackCardResult result)
    {
        Objects.requireNonNull(result, "result");
        openingId = UUID.randomUUID();
        packId = "nexus.single-card";
        purchasedAt = Instant.now();
        cardResults = Collections.singletonList(result);
        revealedPositions.clear();
        revealedPositions.add(0);
        requestedPositions.clear();
        flipElapsedByPosition.clear();
        flipElapsedByPosition.put(0, 0L);
        openingComplete = true;
        lastTickMillis = -1L;
        transitionTo(PackPresentationState.READY_TO_REVEAL);
    }

    public synchronized void tick(long nowMillis)
    {
        if (lastTickMillis < 0L)
        {
            lastTickMillis = nowMillis;
            return;
        }
        long elapsed = Math.max(0L, nowMillis - lastTickMillis);
        lastTickMillis = nowMillis;
        advance(Math.min(elapsed, MAX_ANIMATION_STEP_MILLIS));
    }

    public synchronized void advance(long elapsedMillis)
    {
        if (elapsedMillis < 0L)
        {
            throw new IllegalArgumentException(
                "Elapsed animation time cannot be negative.");
        }

        advanceIntro(elapsedMillis);
        advanceFlips(elapsedMillis);
        completeSummaryWhenReady();
    }

    public synchronized void skipTransition()
    {
        switch (state)
        {
            case PACK_ENTER:
            case PACK_OPEN:
            case CARD_DEAL:
                transitionTo(PackPresentationState.READY_TO_REVEAL);
                break;
            case READY_TO_REVEAL:
                flipElapsedByPosition.clear();
                completeSummaryWhenReady();
                break;
            default:
                break;
        }
    }

    public synchronized boolean openPack()
    {
        if (state != PackPresentationState.PACK_OPEN)
        {
            return false;
        }
        transitionTo(PackPresentationState.CARD_DEAL);
        // The animation timer may have been stopped while the sealed pack sat
        // waiting for input. Do not let that idle wall-clock gap become the
        // first CARD_DEAL delta and skip the deal animation. Prime one normal
        // display frame before the immediate repaint so the first painted
        // CARD_DEAL frame is already in motion instead of sitting at 0%.
        advance(CARD_DEAL_PRIME_MILLIS);
        resetAnimationClock();
        return true;
    }

    public synchronized void closeSummary()
    {
        if (state == PackPresentationState.SUMMARY)
        {
            reset();
        }
    }

    public synchronized PackPresentationState getState()
    {
        return state;
    }

    public synchronized PackPresentationSnapshot snapshot()
    {
        Map<Integer, Double> flipProgress = new LinkedHashMap<>();
        for (Map.Entry<Integer, Long> entry
            : flipElapsedByPosition.entrySet())
        {
            long duration = flipDuration(entry.getKey());
            flipProgress.put(
                entry.getKey(),
                Math.min(1.0, (double) entry.getValue() / duration));
        }
        return new PackPresentationSnapshot(
            openingId,
            packId,
            purchasedAt,
            state,
            cardResults,
            revealedPositions,
            requestedPositions,
            flipProgress,
            openingComplete,
            transitionProgress());
    }

    public synchronized void reset()
    {
        openingId = null;
        packId = null;
        purchasedAt = null;
        cardResults = Collections.emptyList();
        revealedPositions.clear();
        requestedPositions.clear();
        flipElapsedByPosition.clear();
        state = PackPresentationState.IDLE;
        openingComplete = false;
        stateElapsedMillis = 0L;
        lastTickMillis = -1L;
    }

    private boolean canRequest(int position)
    {
        return state == PackPresentationState.READY_TO_REVEAL
            && position >= 0
            && position < cardResults.size()
            && !revealedPositions.contains(position)
            && !requestedPositions.contains(position);
    }

    private void advanceIntro(long elapsedMillis)
    {
        long remaining = elapsedMillis;
        while (remaining > 0L && isIntroTransition(state))
        {
            long duration = introDurationFor(state);
            long available = duration - stateElapsedMillis;
            long consumed = Math.min(remaining, available);
            stateElapsedMillis += consumed;
            remaining -= consumed;
            if (stateElapsedMillis >= duration)
            {
                transitionTo(nextIntroState(state));
            }
        }
    }

    private void advanceFlips(long elapsedMillis)
    {
        if (elapsedMillis == 0L || flipElapsedByPosition.isEmpty())
        {
            return;
        }
        Iterator<Map.Entry<Integer, Long>> iterator =
            flipElapsedByPosition.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<Integer, Long> entry = iterator.next();
            long duration = flipDuration(entry.getKey());
            long next = Math.min(duration, entry.getValue() + elapsedMillis);
            if (next >= duration
                && revealedPositions.contains(entry.getKey()))
            {
                iterator.remove();
            }
            else
            {
                entry.setValue(next);
            }
        }
    }

    private void completeSummaryWhenReady()
    {
        if (state == PackPresentationState.READY_TO_REVEAL
            && openingComplete
            && requestedPositions.isEmpty()
            && flipElapsedByPosition.isEmpty())
        {
            transitionTo(PackPresentationState.SUMMARY);
        }
    }

    private void transitionTo(PackPresentationState next)
    {
        state = Objects.requireNonNull(next, "next");
        stateElapsedMillis = 0L;
    }

    private void resetAnimationClock()
    {
        lastTickMillis = -1L;
    }

    private boolean isIntroTransition(PackPresentationState value)
    {
        return value == PackPresentationState.PACK_ENTER
            || value == PackPresentationState.CARD_DEAL;
    }

    private long introDurationFor(PackPresentationState value)
    {
        long normal;
        switch (value)
        {
            case PACK_ENTER:
                normal = PACK_ENTER_MILLIS;
                break;
            case CARD_DEAL:
                normal = CARD_DEAL_MILLIS;
                break;
            default:
                throw new IllegalArgumentException(
                    "The state has no intro duration: " + value);
        }
        return reducedMotion ? Math.max(120L, normal / 3L) : normal;
    }

    private long flipDuration(int position)
    {
        boolean foil = position >= 0
            && position < cardResults.size()
            && cardResults.get(position).isFoil();
        long duration = foil ? FOIL_CARD_FLIP_MILLIS : CARD_FLIP_MILLIS;
        return reducedMotion
            ? Math.max(120L, duration / 3L)
            : duration;
    }

    private PackPresentationState nextIntroState(
        PackPresentationState value)
    {
        switch (value)
        {
            case PACK_ENTER:
                return PackPresentationState.PACK_OPEN;
            case CARD_DEAL:
                return PackPresentationState.READY_TO_REVEAL;
            default:
                throw new IllegalArgumentException(
                    "The state has no intro successor: " + value);
        }
    }

    private double transitionProgress()
    {
        if (!isIntroTransition(state))
        {
            return 1.0;
        }
        return Math.min(
            1.0,
            (double) stateElapsedMillis / introDurationFor(state));
    }
}
