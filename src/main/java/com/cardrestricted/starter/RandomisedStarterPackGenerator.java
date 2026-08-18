package com.cardrestricted.starter;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.pack.PackCardResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public final class RandomisedStarterPackGenerator
{
    private final CardCatalogue catalogue;
    private final StarterPackPoolRegistry registry;

    public RandomisedStarterPackGenerator(
        CardCatalogue catalogue,
        StarterPackPoolRegistry registry)
    {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public List<PackCardResult> generate(
        Set<String> ownedCardIds,
        Random random)
    {
        Objects.requireNonNull(ownedCardIds, "ownedCardIds");
        Objects.requireNonNull(random, "random");
        Set<String> excluded = new HashSet<>(
            catalogue.canonicalizeCardIds(ownedCardIds));
        List<PackCardResult> results = new ArrayList<>();
        for (StarterPackPool pool : StarterPackPool.values())
        {
            List<StarterPackCandidate> available = new ArrayList<>();
            for (StarterPackCandidate candidate : registry.getCandidates(pool))
            {
                String canonical = catalogue.requireCard(
                    candidate.getCardId()).getCardId();
                if (!excluded.contains(canonical))
                {
                    available.add(candidate);
                }
            }
            if (available.size() < pool.getDrawCount())
            {
                throw new IllegalStateException(
                    "The one-time starter pack must be redeemed before its "
                        + pool.name().toLowerCase(java.util.Locale.ROOT)
                        + " pool is exhausted by other unlocks.");
            }
            Collections.shuffle(available, random);
            for (int index = 0; index < pool.getDrawCount(); index++)
            {
                String cardId = catalogue.requireCard(
                    available.get(index).getCardId()).getCardId();
                excluded.add(cardId);
                results.add(new PackCardResult(cardId, false, 0));
            }
        }
        if (results.size() != 5)
        {
            throw new IllegalStateException(
                "The randomised starter pack must contain exactly five cards.");
        }
        return Collections.unmodifiableList(results);
    }
}
