package com.cardrestricted.prototype;

import com.cardrestricted.runelite.SpellbookWidgetRules;

/** Focused packed-widget tests for spellbook versus item interactions. */
public final class SpellbookWidgetRulesVerification
{
    private SpellbookWidgetRulesVerification()
    {
    }

    public static void main(String[] args)
    {
        int varrockTeleport = packed(218, 24);
        int inventoryItem = packed(149, 0);
        int bankItem = packed(12, 13);

        require(
            SpellbookWidgetRules.isSpellbookPackedId(varrockTeleport),
            "Spellbook group 218 must be recognised.");
        require(
            !SpellbookWidgetRules.isSpellbookPackedId(inventoryItem),
            "Inventory widgets must not be classified as spellbook widgets.");
        require(
            !SpellbookWidgetRules.isSpellbookPackedId(-1),
            "Missing widgets must not be classified as spellbook widgets.");
        require(
            SpellbookWidgetRules.isDirectSpellbookClick(
                -1,
                -1,
                varrockTeleport),
            "Packed menu parameter must identify a direct spellbook click.");
        require(
            SpellbookWidgetRules.isDirectSpellbookClick(
                varrockTeleport,
                -1,
                -1),
            "Explicit event widget must identify a direct spellbook click.");
        require(
            SpellbookWidgetRules.isDirectSpellbookClick(
                -1,
                varrockTeleport,
                -1),
            "Menu-entry widget must identify a direct spellbook click.");
        require(
            !SpellbookWidgetRules.isDirectSpellbookClick(
                inventoryItem,
                bankItem,
                bankItem),
            "Ordinary item widgets must remain item interactions.");

        System.out.println("Spellbook packed-widget separation verification passed.");
    }

    private static int packed(int groupId, int childId)
    {
        return groupId << 16 | childId & 0xffff;
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
