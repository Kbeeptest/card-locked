package com.cardrestricted.runelite;

import com.cardrestricted.domain.RestrictionMode;
import net.runelite.api.GameState;

/**
 * Central readiness gate for collection events, restriction decisions and
 * locked-entity visuals.
 *
 * <p>A loaded collection is not sufficient on its own: RuneLite can retain
 * widgets and overlays while the client is hopping, loading or reconnecting.
 * Runtime work must therefore require both a ready collection and a genuinely
 * logged-in client state.</p>
 */
public final class RestrictionRuntimeGate
{
    private RestrictionRuntimeGate()
    {
    }

    public static boolean isClientReady(GameState gameState)
    {
        return gameState == GameState.LOGGED_IN;
    }

    public static boolean isCollectionRuntimeActive(
        boolean collectionReady,
        GameState gameState)
    {
        return collectionReady && isClientReady(gameState);
    }

    public static boolean isRestrictionRuntimeActive(
        boolean collectionReady,
        GameState gameState,
        RestrictionMode restrictionMode)
    {
        return isCollectionRuntimeActive(collectionReady, gameState)
            && restrictionMode != null
            && restrictionMode != RestrictionMode.DISABLED;
    }
}
