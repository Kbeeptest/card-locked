package com.cardrestricted.progression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class ProgressionMilestonePolicyTest
{
    @Test
    public void fullTrackIsOrderedAndEndsAtGodsPack()
    {
        List<ProgressionMilestoneDefinition> track =
            ProgressionMilestonePolicy.track();
        assertEquals(14, track.size());
        int previous = -1;
        for (ProgressionMilestoneDefinition milestone : track)
        {
            assertTrue(milestone.getRequiredCards() >= previous);
            previous = milestone.getRequiredCards();
        }
        assertEquals("Standard Pack", track.get(0).getTitle());
        assertEquals(0, track.get(0).getRequiredCards());
        assertEquals(
            ProgressionMilestonePolicy.GODS_PACK,
            track.get(track.size() - 1).getRequiredCards());
        assertEquals(
            ProgressionMilestonePolicy.GODS_PACK,
            ProgressionMilestonePolicy.finalTrackThreshold());
    }

    @Test
    public void oneTimeRewardsExposeUniquePersistedMarkers()
    {
        Set<String> markers = new HashSet<>();
        int oneTimeRewards = 0;
        for (ProgressionMilestoneDefinition milestone :
            ProgressionMilestonePolicy.track())
        {
            if (milestone.getKind()
                == ProgressionMilestoneDefinition.Kind.ONE_TIME_REWARD)
            {
                oneTimeRewards++;
                assertFalse(milestone.getClaimedMarker().isEmpty());
                assertTrue(markers.add(milestone.getClaimedMarker()));
            }
            else
            {
                assertTrue(milestone.getClaimedMarker().isEmpty());
            }
        }
        assertEquals(6, oneTimeRewards);
    }

    @Test
    public void thresholdTrackIncludesEveryGameplayMilestone()
    {
        Set<Integer> thresholds = new HashSet<>();
        for (ProgressionMilestoneDefinition milestone :
            ProgressionMilestonePolicy.track())
        {
            thresholds.add(milestone.getRequiredCards());
        }
        assertTrue(thresholds.contains(ProgressionMilestonePolicy.UNCOMMON_PLUS_PACK));
        assertTrue(thresholds.contains(ProgressionMilestonePolicy.EXPLORER_PACK));
        assertTrue(thresholds.contains(ProgressionMilestonePolicy.RARE_PLUS_PACK));
        assertTrue(thresholds.contains(ProgressionMilestonePolicy.COINS));
        assertTrue(thresholds.contains(ProgressionMilestonePolicy.ADVENTURE_PACK));
        assertTrue(thresholds.contains(ProgressionMilestonePolicy.HERO_PACK));
        assertTrue(thresholds.contains(ProgressionMilestonePolicy.NEXUS_CACHE));
        assertTrue(thresholds.contains(ProgressionMilestonePolicy.NOBLE_PACK));
        assertTrue(thresholds.contains(ProgressionMilestonePolicy.COLLECTOR_PACK));
        assertTrue(thresholds.contains(ProgressionMilestonePolicy.LEGEND_PACK));
        assertTrue(thresholds.contains(ProgressionMilestonePolicy.MYTHICAL_PACK));
        assertTrue(thresholds.contains(ProgressionMilestonePolicy.GODS_PACK));
    }

    @Test
    public void coinsTrackEntryNamesTheExclusiveCardReward()
    {
        ProgressionMilestoneDefinition coins =
            ProgressionMilestonePolicy.track().stream()
                .filter(milestone -> milestone.getRequiredCards()
                    == ProgressionMilestonePolicy.COINS)
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("Coins card", coins.getTitle());
        assertTrue(coins.getDetail().contains("only from the progression track"));
    }
}
