package com.cardrestricted.points;

import java.time.Instant;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ClueCompletionRewardPolicyTest
{
    private static final Instant NOW =
        Instant.parse("2026-08-04T18:15:00Z");

    @Test
    public void parsesTaggedPluralCompletionMessage()
    {
        Optional<ClueCompletionObservation> parsed =
            new ClueCompletionMessageParser().parse(
                "<col=800000>You have completed 1,234 hard Treasure Trails.</col>",
                NOW);
        assertTrue(parsed.isPresent());
        assertEquals(ClueTier.HARD, parsed.get().getTier());
        assertEquals(1_234L, parsed.get().getCompletionCount());
    }

    @Test
    public void ignoresUnrelatedGameMessages()
    {
        assertFalse(new ClueCompletionMessageParser().parse(
            "You have completed a quest.", NOW).isPresent());
    }

    @Test
    public void tierValuesMatchAgreedRewards()
    {
        assertEquals(500L, ClueTier.BEGINNER.getPoints());
        assertEquals(1_000L, ClueTier.EASY.getPoints());
        assertEquals(2_000L, ClueTier.MEDIUM.getPoints());
        assertEquals(3_000L, ClueTier.HARD.getPoints());
        assertEquals(4_000L, ClueTier.ELITE.getPoints());
        assertEquals(5_000L, ClueTier.MASTER.getPoints());
    }

    @Test
    public void sourceIdentityUsesTierAndCompletionCount()
    {
        PointAward award = new ClueCompletionRewardPolicy().createAward(
            new ClueCompletionObservation(ClueTier.MASTER, 42L, NOW));
        assertEquals("clue-completion:v1:master:42", award.getSourceId());
        assertEquals(5_000L, award.getAmount());
    }
}
