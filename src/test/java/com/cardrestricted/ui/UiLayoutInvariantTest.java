package com.cardrestricted.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Build gate for sidebar clipping, boundary and deterministic paint invariants. */
public final class UiLayoutInvariantTest
{
    @Test
    public void sidebarHasNoDetectedBoundaryOrTypographyViolations()
        throws Exception
    {
        UiLayoutAuditHarness.Result result = UiLayoutAuditHarness.run();
        assertTrue(result.captures >= 20);
        assertTrue(result.paintCycles >= 20);
        assertTrue(result.componentsInspected > 1_000);
        assertEquals(String.join("\n", result.violations),
            0, result.violations.size());
        assertEquals(64, result.structuralSha256.length());
        assertEquals(64, result.pixelSha256.length());
    }
}
