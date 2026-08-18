package com.cardrestricted.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class CardSearchQuery
{
    private final List<String> tokens;

    private CardSearchQuery(List<String> tokens)
    {
        this.tokens = Collections.unmodifiableList(new ArrayList<>(tokens));
    }

    public static CardSearchQuery parse(String value)
    {
        String normalized = normalize(value);
        if (normalized.isEmpty())
        {
            return new CardSearchQuery(Collections.emptyList());
        }
        return new CardSearchQuery(List.of(normalized.split(" ")));
    }

    public boolean isEmpty()
    {
        return tokens.isEmpty();
    }

    boolean matches(String searchable)
    {
        Objects.requireNonNull(searchable, "searchable");
        for (String token : tokens)
        {
            if (!searchable.contains(token))
            {
                return false;
            }
        }
        return true;
    }

    static String normalize(String value)
    {
        Objects.requireNonNull(value, "value");
        return value.toLowerCase(Locale.ROOT)
            .replace('_', ' ')
            .replace('-', ' ')
            .replace('.', ' ')
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
