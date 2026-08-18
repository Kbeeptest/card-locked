package com.cardrestricted.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Distinguishes reviewed game examine text from generated catalogue prose.
 * Generated descriptions remain searchable and useful as metadata, but must
 * not be presented to players as though they were the official in-game text.
 */
public final class CatalogueTextQuality
{
    private static final String VERIFIED_EXAMINE_RESOURCE =
        "com/cardrestricted/catalog/members/examine-overrides.tsv";
    private static final Pattern COMBAT_LEVEL = Pattern.compile(
        "an attackable npc with combat level \\d+\\.",
        Pattern.CASE_INSENSITIVE);
    private static final Set<String> GENERATED = new HashSet<>(Arrays.asList(
        "a piece of usable equipment.",
        "consumable item.",
        "a useful processing resource.",
        "an npc who provides an account-progressing service.",
        "an interactable item.",
        "a useful tool.",
        "a useful everyday tool.",
        "an npc who can be targeted by thieving interactions.",
        "an npc who provides access to transportation.",
        "an npc who provides access to a minigame or challenge.",
        "a useful piece of combat equipment.",
        "protective or decorative equipment.",
        "a valuable crafting or smithing material.",
        "a farming resource.",
        "a hunter resource.",
        "a runecrafting resource.",
        "no examine text is available for this card."
    ));
    private static final Set<String> VERIFIED_CARD_IDS =
        loadVerifiedCardIds();

    private CatalogueTextQuality()
    {
    }

    public static boolean isGeneratedDescription(String text)
    {
        if (text == null)
        {
            return true;
        }
        String normalised = text.trim().toLowerCase(Locale.ROOT);
        return normalised.isEmpty()
            || GENERATED.contains(normalised)
            || COMBAT_LEVEL.matcher(normalised).matches();
    }

    public static boolean isVerifiedExamine(CardDefinition card)
    {
        return card != null
            && VERIFIED_CARD_IDS.contains(card.getCardId())
            && !isGeneratedDescription(card.getExamineText());
    }

    static boolean hasVerifiedExamineProvenance(String cardId)
    {
        return cardId != null && VERIFIED_CARD_IDS.contains(cardId);
    }

    private static Set<String> loadVerifiedCardIds()
    {
        Set<String> result = new HashSet<>();
        ClassLoader loader = CatalogueTextQuality.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(
            VERIFIED_EXAMINE_RESOURCE))
        {
            if (stream == null)
            {
                return result;
            }
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)))
            {
                String line = reader.readLine();
                while ((line = reader.readLine()) != null)
                {
                    if (line.trim().isEmpty())
                    {
                        continue;
                    }
                    String[] columns = line.split("\t", -1);
                    if (columns.length > 0 && !columns[0].trim().isEmpty())
                    {
                        result.add(columns[0].trim());
                    }
                }
            }
        }
        catch (IOException ignored)
        {
            result.clear();
        }
        return result;
    }

    public static String cardDisplayText(CardDefinition card)
    {
        if (isVerifiedExamine(card))
        {
            return card.getExamineText();
        }
        return conciseCatalogueDescription(card.getExamineText());
    }

    private static String conciseCatalogueDescription(String text)
    {
        String normalised = text == null ? "" : text.trim();
        String lower = normalised.toLowerCase(Locale.ROOT);
        if (lower.matches("an attackable npc with combat level \\d+\\."))
        {
            String level = lower.replaceAll("\\D+", "");
            return "Attackable NPC · Level " + level;
        }
        if (lower.equals("a piece of usable equipment."))
        {
            return "Usable equipment.";
        }
        if (lower.equals("a useful piece of combat equipment."))
        {
            return "Combat equipment.";
        }
        if (lower.equals("a useful processing resource."))
        {
            return "Processing resource.";
        }
        if (lower.equals("a useful tool."))
        {
            return "Utility tool.";
        }
        if (lower.equals("an npc who provides an account-progressing service."))
        {
            return "Account service NPC.";
        }
        if (lower.equals("an npc who can be targeted by thieving interactions."))
        {
            return "Thieving target.";
        }
        if (lower.equals("an npc who provides access to transportation."))
        {
            return "Transport NPC.";
        }
        if (lower.startsWith("an npc who "))
        {
            String role = normalised.substring("An NPC who ".length());
            return "NPC: " + Character.toLowerCase(role.charAt(0))
                + role.substring(1);
        }
        if (lower.startsWith("a useful "))
        {
            return Character.toUpperCase(normalised.charAt(9))
                + normalised.substring(10);
        }
        return normalised.isEmpty()
            ? "Description pending."
            : normalised;
    }

    public static String detailHeading(CardDefinition card)
    {
        return isVerifiedExamine(card)
            ? "Official examine"
            : "Catalogue description — official examine pending";
    }
}
