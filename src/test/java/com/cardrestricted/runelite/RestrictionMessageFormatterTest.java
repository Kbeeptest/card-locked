package com.cardrestricted.runelite;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RestrictionMessageFormatterTest
{
    @Test
    public void includesEverySmallMissingCardSet()
    {
        SimpleRestrictionService.RestrictionDecision decision =
            SimpleRestrictionService.RestrictionDecision.block(
                new LinkedHashSet<>(Arrays.asList("a", "b", "c")),
                "This action is locked");

        assertEquals(
            "[Cards] This action is locked. Unlock with ownership or direct foil access: A, B and C.",
            RestrictionMessageFormatter.format(
                decision,
                String::toUpperCase));
    }

    @Test
    public void boundsLongRequirementsAndReportsHiddenCount()
    {
        SimpleRestrictionService.RestrictionDecision decision =
            SimpleRestrictionService.RestrictionDecision.block(
                new LinkedHashSet<>(Arrays.asList(
                    "a", "b", "c", "d", "e", "f")),
                "Locked.");

        String message = RestrictionMessageFormatter.format(
            decision,
            String::toUpperCase);
        assertEquals(
            "[Cards] Locked. Unlock with ownership or direct foil access: A, B, C and D (+2 more).",
            message);
        assertFalse(message.contains("E"));
    }

    @Test
    public void stripsMarkupAndNormalisesWhitespace()
    {
        SimpleRestrictionService.RestrictionDecision decision =
            SimpleRestrictionService.RestrictionDecision.block(
                Collections.singleton("card"),
                "  <col=ff0000>Not   allowed</col>  ");

        assertEquals(
            "[Cards] Not allowed. Unlock with ownership or direct foil access: Bronze sword.",
            RestrictionMessageFormatter.format(
                decision,
                ignored -> "Bronze   sword"));
    }

    @Test
    public void preservesUsefulFeedbackWithoutRequirementMetadata()
    {
        String message = RestrictionMessageFormatter.format(
            SimpleRestrictionService.RestrictionDecision.block(
                Collections.emptySet(),
                null),
            ignored -> "unused");

        assertEquals("[Cards] Locked.", message);
        assertTrue(message.length() < 80);
    }
}
