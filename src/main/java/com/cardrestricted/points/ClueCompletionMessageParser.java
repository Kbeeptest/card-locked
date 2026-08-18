package com.cardrestricted.points;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the completion-count game message emitted after a clue reward. */
public final class ClueCompletionMessageParser
{
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern COMPLETION = Pattern.compile(
        "^you have completed\\s+(a|[0-9,]+)\\s+"
            + "(beginner|easy|medium|hard|elite|master)\\s+"
            + "treasure trails?[.!]?$",
        Pattern.CASE_INSENSITIVE);

    public Optional<ClueCompletionObservation> parse(
        String message,
        Instant occurredAt)
    {
        if (message == null)
        {
            return Optional.empty();
        }
        String clean = TAGS.matcher(message)
            .replaceAll("")
            .trim()
            .toLowerCase(Locale.ROOT);
        Matcher matcher = COMPLETION.matcher(clean);
        if (!matcher.matches())
        {
            return Optional.empty();
        }
        String countText = matcher.group(1);
        long count = "a".equals(countText)
            ? 1L
            : Long.parseLong(countText.replace(",", ""));
        return Optional.of(new ClueCompletionObservation(
            ClueTier.fromKey(matcher.group(2)),
            count,
            occurredAt));
    }
}
