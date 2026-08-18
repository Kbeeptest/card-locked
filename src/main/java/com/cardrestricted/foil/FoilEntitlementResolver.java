package com.cardrestricted.foil;

import com.cardrestricted.catalog.CardCatalogue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resolves direct foil rewards without converting them into ownership. */
public final class FoilEntitlementResolver
{
    private final CardCatalogue catalogue;
    private final FoilRewardRegistry registry;

    public FoilEntitlementResolver(
        CardCatalogue catalogue,
        FoilRewardRegistry registry)
    {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public FoilEntitlementSnapshot resolve(
        Set<String> ownedCardIds,
        Set<String> foilCardIds)
    {
        Set<String> owned = catalogue.canonicalizeCardIds(
            ownedCardIds == null ? Collections.emptySet() : ownedCardIds);
        Set<String> foils = catalogue.canonicalizeCardIds(
            foilCardIds == null ? Collections.emptySet() : foilCardIds);
        if (!owned.containsAll(foils))
        {
            throw new IllegalArgumentException(
                "Every foil source must also be an owned card.");
        }

        List<String> orderedFoils = new ArrayList<>(foils);
        orderedFoils.sort(Comparator.naturalOrder());
        LinkedHashSet<String> derived = new LinkedHashSet<>();
        Map<String, List<FoilUnlockProvenance>> provenance =
            new LinkedHashMap<>();
        for (String sourceCardId : orderedFoils)
        {
            // Deliberately iterate only the persisted foil set. A target gained
            // from another foil can never act as a source unless it is itself
            // genuinely owned as a foil.
            for (FoilRewardGrant grant
                : registry.getRewardsForSource(sourceCardId))
            {
                addEntitlement(owned, derived, provenance, grant);
            }
        }

        // Combination rewards are evaluated only against the genuinely owned
        // foil set. Direct or combination-derived access cannot satisfy a
        // missing source, so the system remains non-recursive.
        for (FoilCombinationReward combination
            : registry.getCombinationRewards())
        {
            if (!foils.containsAll(
                combination.getRequiredSourceCardIds()))
            {
                continue;
            }
            for (String target : combination.getTargetCardIds())
            {
                if (owned.contains(target))
                {
                    continue;
                }
                derived.add(target);
                for (String source
                    : combination.getRequiredSourceCardIds())
                {
                    provenance.computeIfAbsent(
                        target,
                        ignored -> new ArrayList<>()).add(
                            new FoilUnlockProvenance(
                                combination.asGrant(source, target)));
                }
            }
        }
        return new FoilEntitlementSnapshot(
            owned,
            foils,
            derived,
            provenance);
    }

    private static void addEntitlement(
        Set<String> owned,
        Set<String> derived,
        Map<String, List<FoilUnlockProvenance>> provenance,
        FoilRewardGrant grant)
    {
        String target = grant.getTargetCardId();
        if (owned.contains(target))
        {
            return;
        }
        derived.add(target);
        provenance.computeIfAbsent(
            target,
            ignored -> new ArrayList<>()).add(
                new FoilUnlockProvenance(grant));
    }

    public FoilRewardRegistry getRegistry()
    {
        return registry;
    }
}
