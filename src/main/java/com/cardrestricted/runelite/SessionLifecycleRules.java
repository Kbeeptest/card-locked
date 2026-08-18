package com.cardrestricted.runelite;

import net.runelite.api.GameState;

/** Classifies RuneLite client states for persistent collection sessions. */
public final class SessionLifecycleRules
{
    public enum Transition
    {
        OPEN_OR_RESUME,
        SUSPEND,
        CLOSE
    }

    private SessionLifecycleRules()
    {
    }

    public static Transition transitionFor(GameState gameState)
    {
        if (gameState == GameState.LOGGED_IN)
        {
            return Transition.OPEN_OR_RESUME;
        }
        if (gameState == GameState.LOGIN_SCREEN
            || gameState == GameState.LOGIN_SCREEN_AUTHENTICATOR
            || gameState == GameState.STARTING
            || gameState == GameState.UNKNOWN)
        {
            return Transition.CLOSE;
        }
        return Transition.SUSPEND;
    }
}
