package com.cardrestricted.starter;

public enum StarterPackPool
{
    WEAPON(1),
    NPC(2),
    HEALING(2);

    private final int drawCount;

    StarterPackPool(int drawCount)
    {
        this.drawCount = drawCount;
    }

    public int getDrawCount()
    {
        return drawCount;
    }
}
