package com.cardrestricted.runelite;

import java.util.Collections;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ItemIdentityIntegrityRulesTest
{
    @Test
    public void unresolvedFunctionalItemActionsFailClosed()
    {
        assertTrue(ItemIdentityIntegrityRules.shouldBlockUnresolvedItemAction(
            true,
            Collections.emptySet(),
            Collections.emptySet(),
            "Use",
            false,
            false));
        assertFalse(ItemIdentityIntegrityRules.shouldBlockUnresolvedItemAction(
            true,
            Set.of(1),
            Collections.emptySet(),
            "Use",
            false,
            false));
    }

    @Test
    public void onlyExplicitRecoveryExemptionsRemainOpen()
    {
        assertFalse(ItemIdentityIntegrityRules.shouldBlockUnresolvedItemAction(
            true,
            Collections.emptySet(),
            Collections.emptySet(),
            "Remove",
            false,
            true));
        assertFalse(ItemIdentityIntegrityRules.shouldBlockUnresolvedItemAction(
            true,
            Collections.emptySet(),
            Collections.emptySet(),
            "Withdraw-1",
            true,
            false));
        assertTrue(ItemIdentityIntegrityRules.shouldBlockUnresolvedItemAction(
            true,
            Collections.emptySet(),
            Collections.emptySet(),
            "Withdraw-1",
            false,
            false));
    }

    @Test
    public void compatibilityBypassIsLimitedToNonIntegrityProfiles()
    {
        assertFalse(ItemIdentityIntegrityRules.mayBypassUnresolvedItemBlock(
            false,
            false));
        assertTrue(ItemIdentityIntegrityRules.mayBypassUnresolvedItemBlock(
            true,
            false));
        assertFalse(ItemIdentityIntegrityRules.mayBypassUnresolvedItemBlock(
            true,
            true));
    }
}
