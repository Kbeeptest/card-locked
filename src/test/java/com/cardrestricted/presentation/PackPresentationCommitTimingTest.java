package com.cardrestricted.presentation;

import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.pack.PackRevealResult;
import com.cardrestricted.pack.PendingPackReveal;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.starter.StarterRewardState;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PackPresentationCommitTimingTest
{
    @Test
    public void slowPersistenceCannotDelayOrRestartFlip()
    {
        PackPresentationController controller = new PackPresentationController();
        PackCardResult card = new PackCardResult("item.bronze-sword", false, 0);
        PendingPackReveal pending = new PendingPackReveal(
            UUID.randomUUID(),
            "pack.standard",
            Instant.now(),
            List.of(card),
            Set.of());
        controller.synchronise(Optional.of(pending));
        controller.skipTransition();

        assertTrue(controller.markRevealRequested(0));
        assertTrue(controller.snapshot().isFlipping(0));
        assertEquals(
            0.0,
            controller.snapshot().getFlipProgress(0),
            0.0001);

        controller.advance(PackPresentationController.CARD_FLIP_MILLIS * 3);
        assertTrue(controller.snapshot().isFlipping(0));
        assertFalse(controller.snapshot().isRevealed(0));
        assertTrue(controller.snapshot().isRequested(0));
        assertEquals(
            1.0,
            controller.snapshot().getFlipProgress(0),
            0.0001);

        CollectionState state = new CollectionState(
            UUID.randomUUID(),
            "timing-test-character",
            "Timing test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.now(),
            1,
            18,
            1,
            0L,
            0L,
            0L,
            Collections.emptySet(),
            Collections.emptySet(),
            Set.of(StarterRewardState.POINTS_CHOICE_MARKER));
        PackRevealResult committed = new PackRevealResult(
            state,
            card,
            1,
            1,
            1);
        controller.onRevealCommitted(committed);
        assertTrue(controller.snapshot().isRevealed(0));
        assertFalse(controller.snapshot().isRequested(0));
        assertFalse(controller.snapshot().isFlipping(0));
        assertEquals(
            1.0,
            controller.snapshot().getFlipProgress(0),
            0.0001);
    }
    @Test
    public void rapidRequestsStartAllFlipsWithoutWaitingForCommits()
    {
        PackPresentationController controller = new PackPresentationController();
        List<PackCardResult> cards = List.of(
            new PackCardResult("item.bronze_sword", false, 0),
            new PackCardResult("item.bronze_scimitar", false, 0),
            new PackCardResult("item.bronze-longsword", false, 0));
        PendingPackReveal pending = new PendingPackReveal(
            UUID.randomUUID(),
            "pack.standard",
            Instant.now(),
            cards,
            Set.of());
        controller.synchronise(Optional.of(pending));
        controller.skipTransition();

        assertTrue(controller.markRevealRequested(0));
        assertTrue(controller.markRevealRequested(1));
        assertTrue(controller.markRevealRequested(2));
        controller.advance(33L);

        PackPresentationSnapshot snapshot = controller.snapshot();
        for (int position = 0; position < cards.size(); position++)
        {
            assertTrue(snapshot.isRequested(position));
            assertTrue(snapshot.isFlipping(position));
            assertTrue(snapshot.getFlipProgress(position) > 0.0);
            assertFalse(snapshot.isRevealed(position));
        }
    }

    @Test
    public void animationTickIsIdleWhenNoVisualMotionRemains()
    {
        PackPresentationController controller = new PackPresentationController();
        PackCardResult card = new PackCardResult("item.bronze-sword", false, 0);
        PendingPackReveal pending = new PendingPackReveal(
            UUID.randomUUID(),
            "pack.standard",
            Instant.now(),
            List.of(card),
            Set.of());
        controller.synchronise(Optional.of(pending));
        assertTrue(controller.needsAnimationTick());

        controller.skipTransition();
        assertFalse(controller.needsAnimationTick());

        assertTrue(controller.markRevealRequested(0));
        assertTrue(controller.needsAnimationTick());
        controller.advance(PackPresentationController.CARD_FLIP_MILLIS * 2);
        // Presentation is held at 100% while persistence is outstanding, but
        // no visual time is changing and therefore no 30 FPS repaint is needed.
        assertFalse(controller.needsAnimationTick());
    }

    @Test
    public void resumingAfterIdleDoesNotConsumeIdleWallClockAsFlipTime()
    {
        PackPresentationController controller = new PackPresentationController();
        List<PackCardResult> cards = List.of(
            new PackCardResult("item.bronze-sword", false, 0),
            new PackCardResult("item.bronze-scimitar", false, 0));
        controller.synchronise(Optional.of(new PendingPackReveal(
            UUID.randomUUID(),
            "pack.standard",
            Instant.now(),
            cards,
            Set.of())));
        controller.skipTransition();

        assertTrue(controller.markRevealRequested(0));
        controller.tick(1_000L);
        controller.advance(PackPresentationController.CARD_FLIP_MILLIS * 2);
        assertEquals(1.0, controller.snapshot().getFlipProgress(0), 0.0001);

        CollectionState state = new CollectionState(
            UUID.randomUUID(),
            "idle-resume-character",
            "Idle resume",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.now(),
            1,
            18,
            1,
            0L,
            0L,
            0L,
            Collections.emptySet(),
            Collections.emptySet(),
            Set.of(StarterRewardState.POINTS_CHOICE_MARKER));
        controller.onRevealCommitted(new PackRevealResult(
            state, cards.get(0), 1, 2, 1));
        assertFalse(controller.needsAnimationTick());

        assertTrue(controller.markRevealRequested(1));
        // The UI timer was idle between 1.5s and 5s. The first resumed tick
        // establishes a new baseline instead of jumping the fresh flip ahead.
        controller.tick(5_000L);
        assertEquals(0.0, controller.snapshot().getFlipProgress(1), 0.0001);
        controller.tick(5_033L);
        assertTrue(controller.snapshot().getFlipProgress(1) > 0.0);
        assertTrue(controller.snapshot().getFlipProgress(1) < 0.20);
    }

    @Test
    public void longUiTimerStallCannotSkipMostOfFreshFlip()
    {
        PackPresentationController controller = new PackPresentationController();
        PackCardResult card = new PackCardResult("item.bronze-sword", false, 0);
        controller.synchronise(Optional.of(new PendingPackReveal(
            UUID.randomUUID(),
            "pack.standard",
            Instant.now(),
            List.of(card),
            Set.of())));
        controller.skipTransition();
        assertTrue(controller.markRevealRequested(0));
        controller.tick(1_000L);

        // Simulate a one-second Swing/EDT scheduling stall. Visual time is
        // intentionally capped rather than consuming the whole wall-clock gap.
        controller.tick(2_000L);
        double progress = controller.snapshot().getFlipProgress(0);
        assertTrue(progress > 0.0);
        assertTrue(progress <= (PackPresentationController.MAX_ANIMATION_STEP_MILLIS
            / (double) PackPresentationController.CARD_FLIP_MILLIS) + 0.001);
        assertTrue(progress < 0.20);
    }

    @Test
    public void openingSealedPackAfterIdleDoesNotSkipCardDeal()
    {
        PackPresentationController controller = new PackPresentationController();
        PackCardResult card = new PackCardResult("item.bronze-sword", false, 0);
        controller.synchronise(Optional.of(new PendingPackReveal(
            UUID.randomUUID(),
            "pack.standard",
            Instant.now(),
            List.of(card),
            Set.of())));

        controller.tick(1_000L);
        controller.advance(PackPresentationController.PACK_ENTER_MILLIS);
        assertEquals(PackPresentationState.PACK_OPEN, controller.getState());
        assertTrue(controller.openPack());

        // The click should paint an already-moving deal frame. Previously the
        // animation clock consumed the first timer callback only to establish
        // its timestamp, leaving a perceptible stationary frame before motion.
        double immediateProgress = controller.snapshot().getTransitionProgress();
        assertTrue(immediateProgress > 0.0);
        assertEquals(
            PackPresentationController.CARD_DEAL_PRIME_MILLIS
                / (double) PackPresentationController.CARD_DEAL_MILLIS,
            immediateProgress,
            0.0001);

        controller.tick(8_000L);
        assertEquals(PackPresentationState.CARD_DEAL, controller.getState());
        assertEquals(immediateProgress,
            controller.snapshot().getTransitionProgress(), 0.0001);
        controller.tick(8_033L);
        assertTrue(controller.snapshot().getTransitionProgress()
            > immediateProgress);
        assertTrue(controller.snapshot().getTransitionProgress() < 0.20);
    }

}
