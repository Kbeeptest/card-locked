package com.cardrestricted.runelite;

import com.cardrestricted.domain.RestrictionMode;
import net.runelite.api.GameState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RestrictionRuntimeGateTest
{
    @Test
    public void collectionRuntimeRequiresReadyCollectionAndLoggedInClient()
    {
        assertTrue(RestrictionRuntimeGate.isCollectionRuntimeActive(
            true, GameState.LOGGED_IN));
        assertFalse(RestrictionRuntimeGate.isCollectionRuntimeActive(
            false, GameState.LOGGED_IN));
        assertFalse(RestrictionRuntimeGate.isCollectionRuntimeActive(
            true, GameState.HOPPING));
        assertFalse(RestrictionRuntimeGate.isCollectionRuntimeActive(
            true, GameState.LOADING));
        assertFalse(RestrictionRuntimeGate.isCollectionRuntimeActive(
            true, GameState.CONNECTION_LOST));
        assertFalse(RestrictionRuntimeGate.isCollectionRuntimeActive(
            true, GameState.LOGIN_SCREEN));
    }

    @Test
    public void disabledRestrictionModeSuppressesDecisionsAndVisuals()
    {
        assertFalse(RestrictionRuntimeGate.isRestrictionRuntimeActive(
            true, GameState.LOGGED_IN, RestrictionMode.DISABLED));
        assertTrue(RestrictionRuntimeGate.isRestrictionRuntimeActive(
            true, GameState.LOGGED_IN, RestrictionMode.AUDIT_ONLY));
        assertTrue(RestrictionRuntimeGate.isRestrictionRuntimeActive(
            true, GameState.LOGGED_IN, RestrictionMode.ENFORCE));
        assertFalse(RestrictionRuntimeGate.isRestrictionRuntimeActive(
            true, GameState.HOPPING, RestrictionMode.ENFORCE));
    }
}
