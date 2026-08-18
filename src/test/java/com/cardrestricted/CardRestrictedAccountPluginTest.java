package com.cardrestricted;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class CardRestrictedAccountPluginTest
{
    private CardRestrictedAccountPluginTest()
    {
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(CardRestrictedAccountPlugin.class);
        RuneLite.main(args);
    }
}
