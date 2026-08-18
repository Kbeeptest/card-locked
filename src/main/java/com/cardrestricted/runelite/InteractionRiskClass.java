package com.cardrestricted.runelite;

/** Static policy class used by the automated action-surface audit. */
public enum InteractionRiskClass
{
    /** Requires runtime option, target, item and provenance evaluation. */
    DYNAMIC_RESTRICTION,
    /** Pure inspection action which does not alter game state. */
    OBSERVATION,
    /** Client-only control with no direct game-state effect. */
    CLIENT_ONLY,
    /** Unknown action; integrity mode must treat it as unreviewed. */
    UNKNOWN_FAIL_CLOSED
}
