package com.cardrestricted.runelite;

import java.util.Collections;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NpcRewardIntegrityRulesTest
{
    @Test
    public void enforcedRewardsRequireAUsableFamilyCard()
    {
        assertFalse(NpcRewardIntegrityRules.mayAward(
            true,
            Set.of("npc.guard"),
            Collections.emptySet()));
        assertTrue(NpcRewardIntegrityRules.mayAward(
            true,
            Set.of("npc.guard", "npc.guard_alt"),
            Set.of("npc.guard_alt")));
    }

    @Test
    public void ambiguousTrackedIdentityFailsClosed()
    {
        assertFalse(NpcRewardIntegrityRules.mayAward(
            true,
            true,
            Collections.emptySet(),
            Collections.emptySet()));
        assertTrue(NpcRewardIntegrityRules.mayAward(
            false,
            true,
            Collections.emptySet(),
            Collections.emptySet()));
    }
}
