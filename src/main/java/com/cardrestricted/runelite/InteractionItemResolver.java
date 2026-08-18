package com.cardrestricted.runelite;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.MenuAction;

/**
 * Resolves the item identities that genuinely participate in a RuneLite menu
 * interaction without retaining Widget or MenuEntry objects.
 *
 * <p>The resolver is deliberately pure so the complete menu-context matrix can
 * be exercised headlessly. Spellbook widgets are excluded as item sources,
 * while the item targeted by a spell remains eligible for restriction checks.</p>
 */
public final class InteractionItemResolver
{
    private InteractionItemResolver()
    {
    }

    /** Compatibility overload retained for existing callers/tests. */
    public static Set<Integer> resolve(
        MenuAction menuAction,
        boolean clickedItemMetadata,
        int eventItemId,
        int entryItemId,
        int entryIdentifier,
        int entryWidgetItemId,
        int entryWidgetPackedId,
        int explicitWidgetItemId,
        int explicitWidgetPackedId,
        boolean widgetSelected,
        int selectedWidgetItemId,
        int selectedWidgetPackedId)
    {
        return resolve(
            menuAction,
            clickedItemMetadata,
            eventItemId,
            -1,
            entryItemId,
            entryIdentifier,
            entryWidgetItemId,
            entryWidgetPackedId,
            explicitWidgetItemId,
            explicitWidgetPackedId,
            widgetSelected,
            selectedWidgetItemId,
            selectedWidgetPackedId);
    }

    /**
     * Includes both MenuOptionClicked#getId and MenuEntry#getIdentifier for
     * entryless or rewritten ground-item actions. For other action types these
     * identifiers are slot/NPC/object indices and are therefore not treated as
     * item ids.
     */
    public static Set<Integer> resolve(
        MenuAction menuAction,
        boolean clickedItemMetadata,
        int eventItemId,
        int eventIdentifier,
        int entryItemId,
        int entryIdentifier,
        int entryWidgetItemId,
        int entryWidgetPackedId,
        int explicitWidgetItemId,
        int explicitWidgetPackedId,
        boolean widgetSelected,
        int selectedWidgetItemId,
        int selectedWidgetPackedId)
    {
        Set<Integer> itemIds = new LinkedHashSet<>();
        if (clickedItemMetadata)
        {
            addItemId(itemIds, eventItemId);
            addItemId(itemIds, entryItemId);
            if (!SpellbookWidgetRules.isSpellbookPackedId(
                entryWidgetPackedId))
            {
                addItemId(itemIds, entryWidgetItemId);
            }
            if (InteractionContextRules.isGroundItemAction(menuAction))
            {
                addItemId(itemIds, eventIdentifier);
                addItemId(itemIds, entryIdentifier);
            }
            if (!SpellbookWidgetRules.isSpellbookPackedId(
                explicitWidgetPackedId))
            {
                addItemId(itemIds, explicitWidgetItemId);
            }
        }
        if (InteractionContextRules.shouldIncludeSelectedItem(
            widgetSelected,
            menuAction,
            selectedWidgetPackedId))
        {
            addItemId(itemIds, selectedWidgetItemId);
        }
        return Collections.unmodifiableSet(itemIds);
    }

    private static void addItemId(Set<Integer> destination, int itemId)
    {
        if (itemId >= 0)
        {
            destination.add(itemId);
        }
    }
}
