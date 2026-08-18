package com.cardrestricted.runelite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AutocastIntegrityRulesTest
{
    @Test
    public void preconfiguredAutocastBlocksAttackUntilVerified()
    {
        assertTrue(AutocastIntegrityRules.shouldBlockAttack(
            true, false, "Attack"));
        assertFalse(AutocastIntegrityRules.shouldBlockAttack(
            true, true, "Attack"));
        assertFalse(AutocastIntegrityRules.shouldBlockAttack(
            false, false, "Attack"));
        assertFalse(AutocastIntegrityRules.shouldBlockAttack(
            true, false, "Talk-to"));
    }
}
