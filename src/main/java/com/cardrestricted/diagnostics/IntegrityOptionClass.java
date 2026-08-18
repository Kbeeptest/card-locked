package com.cardrestricted.diagnostics;

import java.util.Locale;

/** Privacy-preserving interaction verb classification. */
public enum IntegrityOptionClass
{
    EMPTY,
    TALK,
    ATTACK,
    PICKPOCKET,
    TRADE,
    BUY_SELL,
    CAST,
    ITEM_USE,
    EQUIP,
    UNEQUIP,
    STORAGE,
    PRODUCTION,
    TRAVEL,
    CLAIM,
    EXAMINE,
    MOVEMENT,
    CONTINUE,
    CLOSE_CANCEL,
    OTHER;

    public static IntegrityOptionClass classify(String option)
    {
        String value = normalise(option);
        if (value.isEmpty())
        {
            return EMPTY;
        }
        if (starts(value, "talk-to", "talk to", "talk"))
        {
            return TALK;
        }
        if (starts(value, "attack"))
        {
            return ATTACK;
        }
        if (starts(value, "pickpocket", "steal-from", "steal from"))
        {
            return PICKPOCKET;
        }
        if (starts(value, "trade", "shop"))
        {
            return TRADE;
        }
        if (starts(value, "buy", "sell"))
        {
            return BUY_SELL;
        }
        if (starts(value, "cast", "autocast", "defensive autocast"))
        {
            return CAST;
        }
        if (starts(value, "use", "rub", "drink", "eat", "bury", "scatter", "activate"))
        {
            return ITEM_USE;
        }
        if (starts(value, "wear", "wield", "equip"))
        {
            return EQUIP;
        }
        if (starts(value, "remove", "unequip", "unwear"))
        {
            return UNEQUIP;
        }
        if (starts(value, "withdraw", "deposit", "store", "retrieve", "bank"))
        {
            return STORAGE;
        }
        if (starts(value, "make", "cook", "smelt", "smith", "craft", "fletch", "spin", "tan", "decant"))
        {
            return PRODUCTION;
        }
        if (starts(value, "travel", "sail", "charter", "teleport", "fly", "pay-fare", "pay fare"))
        {
            return TRAVEL;
        }
        if (starts(value, "claim", "collect", "redeem", "receive"))
        {
            return CLAIM;
        }
        if (starts(value, "examine", "inspect"))
        {
            return EXAMINE;
        }
        if (starts(value, "walk here", "walk"))
        {
            return MOVEMENT;
        }
        if (starts(value, "continue"))
        {
            return CONTINUE;
        }
        if (starts(value, "close", "cancel", "back"))
        {
            return CLOSE_CANCEL;
        }
        return OTHER;
    }

    private static boolean starts(String value, String... prefixes)
    {
        for (String prefix : prefixes)
        {
            if (value.equals(prefix)
                || value.startsWith(prefix + " ")
                || value.startsWith(prefix + "-"))
            {
                return true;
            }
        }
        return false;
    }

    private static String normalise(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replaceAll("<[^>]*>", " ")
            .replace('\u00a0', ' ')
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }
}
