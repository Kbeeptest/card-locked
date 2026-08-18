package com.cardrestricted.runelite;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Normalises RuneLite menu targets and catalogue display names for fallback lookup. */
public final class InteractionNameNormalizer
{
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern QUANTITY_SUFFIX = Pattern.compile(
        "\\s+[xX]\\s*[0-9][0-9,]*$");
    private static final Pattern DOSE_SUFFIX = Pattern.compile(
        "\\s*\\(([1-4])\\)$");
    private static final Pattern CHARGE_SUFFIX = Pattern.compile(
        "\\s*\\(([0-9][0-9,]*)\\)$");
    private static final Pattern COMBAT_LEVEL_SUFFIX = Pattern.compile(
        "\\s*\\(level[- ]?[0-9][0-9,]*\\)$", Pattern.CASE_INSENSITIVE);

    private InteractionNameNormalizer()
    {
    }

    public static String normaliseItemName(String value)
    {
        String cleaned = clean(value);
        cleaned = QUANTITY_SUFFIX.matcher(cleaned).replaceFirst("");
        cleaned = DOSE_SUFFIX.matcher(cleaned).replaceFirst("");
        return WHITESPACE.matcher(cleaned).replaceAll(" ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }


    public static String normaliseEntityName(String value)
    {
        String cleaned = COMBAT_LEVEL_SUFFIX.matcher(clean(value))
            .replaceFirst("");
        return WHITESPACE.matcher(cleaned).replaceAll(" ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    /** Returns the NPC/object side of source-to-target menu text. */
    public static String targetEntityName(String menuTarget)
    {
        String cleaned = clean(menuTarget);
        if (cleaned.isEmpty())
        {
            return "";
        }
        String target = cleaned;
        for (String separator : new String[]{" -> ", " → ", "->", "→"})
        {
            int index = cleaned.lastIndexOf(separator);
            if (index >= 0)
            {
                target = cleaned.substring(index + separator.length());
                break;
            }
        }
        return normaliseEntityName(target);
    }

    public static String spellName(String menuTarget)
    {
        String cleaned = clean(menuTarget);
        if (cleaned.isEmpty())
        {
            return "";
        }
        for (String separator : new String[]{" -> ", " → ", "->", "→"})
        {
            int index = cleaned.indexOf(separator);
            if (index >= 0)
            {
                cleaned = cleaned.substring(0, index);
                break;
            }
        }
        return cleaned.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns conservative item-name candidates from a menu target.
     *
     * <p>RuneLite commonly renders item-on-item targets as
     * {@code Source -> Target}. Both sides are retained. Ambiguous names are
     * rejected later by the catalogue index rather than guessed here.</p>
     */
    public static Set<String> itemNameCandidates(String menuTarget)
    {
        String cleaned = clean(menuTarget);
        if (cleaned.isEmpty())
        {
            return Collections.emptySet();
        }
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, cleaned);
        for (String separator : new String[]{" -> ", " → ", "->", "→"})
        {
            if (!cleaned.contains(separator))
            {
                continue;
            }
            for (String part : cleaned.split(Pattern.quote(separator)))
            {
                addCandidate(candidates, part);
            }
        }
        return Collections.unmodifiableSet(candidates);
    }


    /**
     * Returns only the selected source-item side of a source-to-target menu
     * entry. If the source name was stripped and no separator remains, no
     * candidate is guessed from the NPC/object target.
     */
    public static Set<String> sourceItemNameCandidates(String menuTarget)
    {
        String cleaned = clean(menuTarget);
        if (cleaned.isEmpty())
        {
            return Collections.emptySet();
        }
        String source = "";
        for (String separator : new String[]{" -> ", " → ", "->", "→"})
        {
            int index = cleaned.indexOf(separator);
            if (index >= 0)
            {
                source = cleaned.substring(0, index);
                break;
            }
        }
        if (source.isEmpty())
        {
            return Collections.emptySet();
        }
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, source);
        return Collections.unmodifiableSet(candidates);
    }

    /**
     * Returns only the item-target side of a spell-on-item menu target. The
     * spell name must never be looked up as an item family.
     */
    public static Set<String> targetItemNameCandidates(String menuTarget)
    {
        String cleaned = clean(menuTarget);
        if (cleaned.isEmpty())
        {
            return Collections.emptySet();
        }
        String target = cleaned;
        for (String separator : new String[]{" -> ", " → ", "->", "→"})
        {
            int index = cleaned.lastIndexOf(separator);
            if (index >= 0)
            {
                target = cleaned.substring(index + separator.length());
                break;
            }
        }
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, target);
        return Collections.unmodifiableSet(candidates);
    }

    private static void addCandidate(Set<String> destination, String value)
    {
        String normalised = normaliseItemName(value);
        if (!normalised.isEmpty())
        {
            destination.add(normalised);
        }
        String withoutCharge = CHARGE_SUFFIX.matcher(clean(value))
            .replaceFirst("");
        String chargeNormalised = normaliseItemName(withoutCharge);
        if (!chargeNormalised.isEmpty())
        {
            destination.add(chargeNormalised);
        }
    }

    private static String clean(String value)
    {
        if (value == null)
        {
            return "";
        }
        String cleaned = TAGS.matcher(value).replaceAll("")
            .replace('\u00a0', ' ')
            .replace("&nbsp;", " ")
            .replace("&#160;", " ");
        return WHITESPACE.matcher(cleaned).replaceAll(" ").trim();
    }
}
