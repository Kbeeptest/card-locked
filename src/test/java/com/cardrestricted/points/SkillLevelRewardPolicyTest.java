package com.cardrestricted.points;

import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SkillLevelRewardPolicyTest
{
    @Test
    public void endpointsMatchAgreedCurve()
    {
        assertEquals(
            SkillLevelRewardPolicy.LEVEL_1_REWARD,
            SkillLevelRewardPolicy.rewardForLevel(1));
        assertEquals(
            SkillLevelRewardPolicy.LEVEL_99_REWARD,
            SkillLevelRewardPolicy.rewardForLevel(99));
    }

    @Test
    public void rewardsIncreaseWithRunescapeXpCurve()
    {
        long previous = SkillLevelRewardPolicy.rewardForLevel(1);
        for (int level = 2; level <= 99; level++)
        {
            long current = SkillLevelRewardPolicy.rewardForLevel(level);
            assertTrue(current >= previous);
            previous = current;
        }
        assertTrue(
            SkillLevelRewardPolicy.rewardForLevel(90)
                > SkillLevelRewardPolicy.rewardForLevel(50));
    }

    @Test
    public void multiLevelAwardSumsEachReachedLevel()
    {
        SkillLevelRewardPolicy policy = new SkillLevelRewardPolicy();
        PointAward award = policy.createAward(
            "FISHING",
            50,
            53,
            Instant.parse("2026-08-04T18:00:00Z"));
        assertEquals(
            SkillLevelRewardPolicy.rewardForLevel(51)
                + SkillLevelRewardPolicy.rewardForLevel(52)
                + SkillLevelRewardPolicy.rewardForLevel(53),
            award.getAmount());
    }
}
