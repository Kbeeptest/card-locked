package com.cardrestricted.runelite;

import net.runelite.api.GameState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SessionLifecycleRulesTest
{
    @Test
    public void loggedInOpensAndTransientWorldStatesSuspend()
    {
        assertEquals(
            SessionLifecycleRules.Transition.OPEN_OR_RESUME,
            SessionLifecycleRules.transitionFor(GameState.LOGGED_IN));
        assertEquals(
            SessionLifecycleRules.Transition.SUSPEND,
            SessionLifecycleRules.transitionFor(GameState.HOPPING));
        assertEquals(
            SessionLifecycleRules.Transition.SUSPEND,
            SessionLifecycleRules.transitionFor(GameState.LOADING));
        assertEquals(
            SessionLifecycleRules.Transition.SUSPEND,
            SessionLifecycleRules.transitionFor(GameState.CONNECTION_LOST));
    }

    @Test
    public void terminalLoginStatesCloseTheCharacterSession()
    {
        assertEquals(
            SessionLifecycleRules.Transition.CLOSE,
            SessionLifecycleRules.transitionFor(GameState.LOGIN_SCREEN));
        assertEquals(
            SessionLifecycleRules.Transition.CLOSE,
            SessionLifecycleRules.transitionFor(
                GameState.LOGIN_SCREEN_AUTHENTICATOR));
        assertEquals(
            SessionLifecycleRules.Transition.CLOSE,
            SessionLifecycleRules.transitionFor(GameState.STARTING));
        assertEquals(
            SessionLifecycleRules.Transition.CLOSE,
            SessionLifecycleRules.transitionFor(GameState.UNKNOWN));
    }
}
