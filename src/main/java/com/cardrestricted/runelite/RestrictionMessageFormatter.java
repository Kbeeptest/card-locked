package com.cardrestricted.runelite;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Produces bounded, deterministic chat feedback for blocked actions. */
public final class RestrictionMessageFormatter
{
    private static final int MAX_CARD_NAMES = 4;
    private static final int MAX_EXPLANATION_LENGTH = 220;
    private static final int MAX_CARD_NAME_LENGTH = 72;
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private RestrictionMessageFormatter()
    {
    }

    public static String format(
        SimpleRestrictionService.RestrictionDecision decision,
        Function<String, String> displayNameResolver)
    {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(displayNameResolver, "displayNameResolver");

        String explanation = clean(
            decision.getExplanation(),
            MAX_EXPLANATION_LENGTH);
        if (explanation.isEmpty())
        {
            explanation = "Action blocked.";
        }
        else if (!endsWithSentencePunctuation(explanation))
        {
            explanation += ".";
        }

        List<String> names = resolvedNames(
            decision.getRequiredCardIds(),
            displayNameResolver);
        StringBuilder output = new StringBuilder("[Cards] ")
            .append(explanation);
        if (!names.isEmpty())
        {
            output.append(
                " Unlock with ownership or direct foil access: ");
            appendNames(output, names);
            int hidden = decision.getRequiredCardIds().size() - names.size();
            if (hidden > 0)
            {
                output.append(" (+").append(hidden).append(" more)");
            }
            output.append('.');
        }
        return output.toString();
    }

    private static List<String> resolvedNames(
        Set<String> cardIds,
        Function<String, String> displayNameResolver)
    {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String cardId : cardIds)
        {
            String resolved = clean(
                displayNameResolver.apply(cardId),
                MAX_CARD_NAME_LENGTH);
            if (resolved.isEmpty())
            {
                resolved = clean(cardId, MAX_CARD_NAME_LENGTH);
            }
            if (!resolved.isEmpty())
            {
                unique.add(resolved);
            }
            if (unique.size() == MAX_CARD_NAMES)
            {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    private static void appendNames(StringBuilder output, List<String> names)
    {
        for (int index = 0; index < names.size(); index++)
        {
            if (index > 0)
            {
                output.append(index == names.size() - 1 ? " and " : ", ");
            }
            output.append(names.get(index));
        }
    }

    private static String clean(String value, int maximumLength)
    {
        if (value == null)
        {
            return "";
        }
        String cleaned = WHITESPACE.matcher(
            TAGS.matcher(value).replaceAll(" ")).replaceAll(" ").trim();
        if (cleaned.length() <= maximumLength)
        {
            return cleaned;
        }
        return cleaned.substring(0, maximumLength - 1).trim() + "…";
    }

    private static boolean endsWithSentencePunctuation(String value)
    {
        char last = value.charAt(value.length() - 1);
        return last == '.' || last == '!' || last == '?';
    }
}
