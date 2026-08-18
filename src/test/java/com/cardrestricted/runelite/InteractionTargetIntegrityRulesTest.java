package com.cardrestricted.runelite;

import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class InteractionTargetIntegrityRulesTest
{
    @Test
    public void trackedNpcIdentityCannotBeErasedByRewrittenTarget()
    {
        InteractionTargetIntegrityRules.NpcTargetResolution resolution =
            InteractionTargetIntegrityRules.resolveNpcTarget(
                "<col=ffff00>Guard (level-21)</col>",
                "Walk here",
                name -> name.equals("guard"));
        assertEquals("guard", resolution.getSelectedName());
        assertTrue(resolution.isKnownTrackedTarget());
        assertFalse(resolution.hasConflictingTrackedTargets());
    }

    @Test
    public void conflictingTrackedNpcTargetsFailClosed()
    {
        InteractionTargetIntegrityRules.NpcTargetResolution resolution =
            InteractionTargetIntegrityRules.resolveNpcTarget(
                "Guard",
                "Hero",
                name -> Set.of("guard", "hero").contains(name));
        assertTrue(resolution.hasConflictingTrackedTargets());
        assertEquals(Set.of("guard", "hero"), resolution.getTrackedNames());
    }

    @Test
    public void itemCandidatesRetainBothMetadataSources()
    {
        Set<String> all = InteractionTargetIntegrityRules.allItemNameCandidates(
            "Knife -> Logs",
            "Tinderbox -> Oak logs");
        assertTrue(all.contains("knife"));
        assertTrue(all.contains("logs"));
        assertTrue(all.contains("tinderbox"));
        assertTrue(all.contains("oak logs"));
    }
}
