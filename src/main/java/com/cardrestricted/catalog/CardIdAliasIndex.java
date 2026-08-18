package com.cardrestricted.catalog;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves retired card IDs to active canonical card IDs.
 *
 * Aliases are deliberately one-way. Active cards never resolve back to a
 * retired state variant, and unknown IDs are preserved so older or future
 * saves are not destructively rewritten.
 */
public final class CardIdAliasIndex
{
    private final Map<String, String> aliases;

    public CardIdAliasIndex(
        Map<String, String> aliases,
        Set<String> activeCardIds)
    {
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(activeCardIds, "activeCardIds");

        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : aliases.entrySet())
        {
            String legacy = requireText(entry.getKey(), "legacyCardId");
            String canonical = requireText(
                entry.getValue(), "canonicalCardId");
            if (legacy.equals(canonical))
            {
                throw new IllegalArgumentException(
                    "A card alias cannot target itself: " + legacy);
            }
            if (activeCardIds.contains(legacy))
            {
                throw new IllegalArgumentException(
                    "A legacy card alias is still active: " + legacy);
            }
            if (copy.put(legacy, canonical) != null)
            {
                throw new IllegalArgumentException(
                    "Duplicate card alias " + legacy + ".");
            }
        }

        for (String legacy : copy.keySet())
        {
            String resolved = resolve(copy, legacy);
            if (!activeCardIds.contains(resolved))
            {
                throw new IllegalArgumentException(
                    "Card alias " + legacy
                        + " does not resolve to an active card: "
                        + resolved);
            }
        }
        this.aliases = Collections.unmodifiableMap(copy);
    }

    public static CardIdAliasIndex empty(Set<String> activeCardIds)
    {
        return new CardIdAliasIndex(Collections.emptyMap(), activeCardIds);
    }

    public String canonicalize(String cardId)
    {
        String id = requireText(cardId, "cardId");
        return resolve(aliases, id);
    }

    public Set<String> canonicalizeAll(Set<String> cardIds)
    {
        Objects.requireNonNull(cardIds, "cardIds");
        Set<String> canonical = new HashSet<>();
        for (String cardId : cardIds)
        {
            canonical.add(canonicalize(cardId));
        }
        return canonical;
    }

    public boolean isAlias(String cardId)
    {
        return aliases.containsKey(cardId);
    }

    public int size()
    {
        return aliases.size();
    }

    public Map<String, String> asMap()
    {
        return aliases;
    }

    private static String resolve(
        Map<String, String> values,
        String startingId)
    {
        String current = startingId;
        Set<String> visited = new HashSet<>();
        while (values.containsKey(current))
        {
            if (!visited.add(current))
            {
                throw new IllegalArgumentException(
                    "Card alias cycle detected at " + current + ".");
            }
            current = values.get(current);
        }
        return current;
    }

    private static String requireText(String value, String field)
    {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value;
    }
}
