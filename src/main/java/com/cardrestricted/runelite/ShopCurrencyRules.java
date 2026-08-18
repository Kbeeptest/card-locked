package com.cardrestricted.runelite;

import java.util.Locale;

/** Item currencies consumed implicitly by shop and exchange interfaces. */
public final class ShopCurrencyRules
{
    private ShopCurrencyRules()
    {
    }

    public static boolean isPotentialCurrency(String itemName)
    {
        String value = normalise(itemName);
        return value.equals("tokkul")
            || value.equals("trading sticks")
            || value.equals("ecto-token")
            || value.equals("ecto-tokens")
            || value.equals("pieces of eight")
            || value.equals("numulite")
            || value.equals("golden nugget")
            || value.equals("golden nuggets")
            || value.equals("stardust")
            || value.equals("molch pearl")
            || value.equals("molch pearls")
            || value.equals("mark of grace")
            || value.equals("marks of grace")
            || value.equals("castle wars ticket")
            || value.equals("castle wars tickets")
            || value.equals("warrior guild token")
            || value.equals("warrior guild tokens")
            || value.equals("agility arena ticket")
            || value.equals("agility arena tickets")
            || value.equals("unidentified minerals")
            || value.equals("barronite shards")
            || value.equals("anima-infused bark")
            || value.equals("abyssal pearls")
            || value.equals("termite")
            || value.equals("termites")
            || value.equals("frog token")
            || value.equals("frog tokens")
            || value.equals("brimhaven voucher")
            || value.equals("brimhaven vouchers")
            || value.equals("archery ticket")
            || value.equals("archery tickets")
            || value.equals("sawmill voucher")
            || value.equals("sawmill vouchers")
            || value.equals("hallowed token")
            || value.equals("hallowed tokens")
            || value.equals("ship ticket")
            || value.equals("ship tickets");
    }

    public static boolean isImplicitCoinSpendOption(String option, String target)
    {
        String action = normalise(option);
        String subject = normalise(target);
        if (action.contains("coin") || subject.contains("coin"))
        {
            return true;
        }
        return startsWithAny(action,
            "buy", "purchase", "pay", "hire", "charter", "repair",
            "reclaim", "insure", "upgrade", "convert", "exchange");
    }

    private static boolean startsWithAny(String value, String... prefixes)
    {
        for (String prefix : prefixes)
        {
            if (value.equals(prefix) || value.startsWith(prefix + " ")
                || value.startsWith(prefix + "-"))
            {
                return true;
            }
        }
        return false;
    }

    private static String normalise(String value)
    {
        return value == null
            ? ""
            : value.replaceAll("<[^>]*>", "")
                .replace('\u00a0', ' ')
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
