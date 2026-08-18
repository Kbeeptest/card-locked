package com.cardrestricted.runelite;

/**
 * Coarse source of a RuneLite menu action. The mapping is intentionally
 * explicit so a RuneLite API update cannot silently create an unreviewed
 * interaction surface.
 */
public enum InteractionSurface
{
    NPC,
    ITEM,
    GROUND_ITEM,
    GAME_OBJECT,
    PLAYER,
    WORLD_ENTITY,
    WIDGET,
    MOVEMENT,
    CLIENT,
    UNKNOWN
}
