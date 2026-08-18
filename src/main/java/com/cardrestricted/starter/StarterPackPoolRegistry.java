package com.cardrestricted.starter;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardCategory;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.domain.ActionType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class StarterPackPoolRegistry
{
    public static final String RESOURCE =
        "com/cardrestricted/starter/starter-pack-pools.tsv";

    private final Map<StarterPackPool, List<StarterPackCandidate>> candidates;

    private StarterPackPoolRegistry(
        Map<StarterPackPool, List<StarterPackCandidate>> candidates)
    {
        EnumMap<StarterPackPool, List<StarterPackCandidate>> copy =
            new EnumMap<>(StarterPackPool.class);
        for (StarterPackPool pool : StarterPackPool.values())
        {
            List<StarterPackCandidate> values = candidates.get(pool);
            if (values == null || values.size() < pool.getDrawCount())
            {
                throw new IllegalArgumentException(
                    "Starter pool " + pool + " does not contain enough candidates.");
            }
            copy.put(
                pool,
                Collections.unmodifiableList(new ArrayList<>(values)));
        }
        this.candidates = Collections.unmodifiableMap(copy);
    }

    public static StarterPackPoolRegistry load(
        ClassLoader classLoader,
        CardCatalogue catalogue)
    {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(catalogue, "catalogue");
        InputStream stream = classLoader.getResourceAsStream(RESOURCE);
        if (stream == null)
        {
            throw new IllegalStateException(
                "Missing starter pack pool resource " + RESOURCE + ".");
        }

        EnumMap<StarterPackPool, List<StarterPackCandidate>> loaded =
            new EnumMap<>(StarterPackPool.class);
        for (StarterPackPool pool : StarterPackPool.values())
        {
            loaded.put(pool, new ArrayList<>());
        }
        Set<String> cardIds = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            String header = reader.readLine();
            if (!"pool\tcard_id\tmaximum_healing\taccess_note".equals(header))
            {
                throw new IllegalArgumentException(
                    "Starter pack pool header is invalid.");
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null)
            {
                lineNumber++;
                if (line.trim().isEmpty() || line.startsWith("#"))
                {
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length != 4)
                {
                    throw new IllegalArgumentException(
                        "Starter pack pool line " + lineNumber
                            + " must contain four columns.");
                }
                StarterPackCandidate candidate = new StarterPackCandidate(
                    StarterPackPool.valueOf(fields[0]),
                    fields[1],
                    Integer.parseInt(fields[2]),
                    fields[3]);
                if (!cardIds.add(candidate.getCardId()))
                {
                    throw new IllegalArgumentException(
                        "Starter card appears in more than one pool: "
                            + candidate.getCardId());
                }
                validateCandidate(candidate, catalogue);
                loaded.get(candidate.getPool()).add(candidate);
            }
        }
        catch (IOException exception)
        {
            throw new IllegalStateException(
                "Unable to read starter pack pools.", exception);
        }
        return new StarterPackPoolRegistry(loaded);
    }

    public List<StarterPackCandidate> getCandidates(StarterPackPool pool)
    {
        return candidates.get(Objects.requireNonNull(pool, "pool"));
    }

    private static void validateCandidate(
        StarterPackCandidate candidate,
        CardCatalogue catalogue)
    {
        CardDefinition card = catalogue.requireCard(candidate.getCardId());
        if (!card.isFreeToPlay())
        {
            throw new IllegalArgumentException(
                "Starter candidates must be F2P cards: "
                    + card.getCardId());
        }
        boolean lowTier = card.getRarity() == Rarity.COMMON
            || card.getRarity() == Rarity.UNCOMMON;

        switch (candidate.getPool())
        {
            case WEAPON:
                String lowerName = card.getDisplayName().toLowerCase(
                    java.util.Locale.ROOT);
                if (card.getRarity() != Rarity.COMMON
                    || card.getCardType() != CardType.ITEM
                    || !card.getCategories().contains(CardCategory.COMBAT_METHOD)
                    || !card.getPermissions().contains(ActionType.ITEM_EQUIP)
                    || (!lowerName.contains("bronze")
                        && !lowerName.contains("iron"))
                    || lowerName.contains("axe")
                    || lowerName.contains("pickaxe")
                    || candidate.getMaximumHealing() != 0)
                {
                    throw invalid(candidate, "weapon");
                }
                break;
            case NPC:
                if (!lowTier
                    || card.getCardType() != CardType.NPC
                    || !card.getCategories().contains(CardCategory.NPC_TARGET)
                    || !card.getPermissions().contains(ActionType.NPC_ATTACK)
                    || candidate.getMaximumHealing() != 0)
                {
                    throw invalid(candidate, "NPC");
                }
                break;
            case HEALING:
                if (!lowTier
                    || card.getCardType() != CardType.ITEM
                    || !card.getCategories().contains(CardCategory.SUPPORT)
                    || !card.getPermissions().contains(ActionType.ITEM_CONSUME)
                    || candidate.getMaximumHealing() < 1
                    || candidate.getMaximumHealing() > 3)
                {
                    throw invalid(candidate, "healing item");
                }
                break;
            default:
                throw new IllegalStateException(
                    "Unhandled starter pool " + candidate.getPool() + ".");
        }
    }

    private static IllegalArgumentException invalid(
        StarterPackCandidate candidate,
        String expected)
    {
        return new IllegalArgumentException(
            "Starter candidate " + candidate.getCardId()
                + " is not a valid " + expected + ".");
    }
}
