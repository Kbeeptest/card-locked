package com.cardrestricted.foil;

import com.cardrestricted.catalog.CardCatalogue;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** UI-safe summaries for reviewed foil reward relationships. */
public final class FoilRewardText
{
    private FoilRewardText()
    {
    }

    public static String potentialSummary(
        FoilRewardRegistry registry,
        CardCatalogue catalogue,
        String sourceCardId)
    {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(catalogue, "catalogue");
        LinkedHashSet<String> rewardNames = new LinkedHashSet<>();
        for (FoilRewardGrant grant
            : registry.getRewardsForSource(sourceCardId))
        {
            rewardNames.add(catalogue.requireCard(
                grant.getTargetCardId()).getDisplayName());
        }
        for (FoilCombinationReward combination
            : registry.getCombinationRewardsForSource(sourceCardId))
        {
            for (String target : combination.getTargetCardIds())
            {
                rewardNames.add(
                    catalogue.requireCard(target).getDisplayName());
            }
        }
        if (rewardNames.isEmpty())
        {
            return "None";
        }
        List<String> names = new ArrayList<>(rewardNames);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", names);
    }

    /** Retained for source compatibility; foil reward lists are never truncated. */
    public static String potentialSummary(
        FoilRewardRegistry registry,
        CardCatalogue catalogue,
        String sourceCardId,
        int ignoredMaximumNames)
    {
        return potentialSummary(registry, catalogue, sourceCardId);
    }

    public static String accessSourceSummary(
        FoilEntitlementSnapshot snapshot,
        CardCatalogue catalogue,
        String targetCardId,
        int ignoredMaximumSources)
    {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(catalogue, "catalogue");
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        for (FoilUnlockProvenance provenance : snapshot.getProvenance(targetCardId))
        {
            sources.add(catalogue.requireCard(
                provenance.getSourceCardId()).getDisplayName());
        }
        if (sources.isEmpty())
        {
            return "";
        }
        List<String> names = new ArrayList<>(sources);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return "Usable through foil " + joinNames(names);
    }

    private static String joinNames(List<String> names)
    {
        if (names.isEmpty())
        {
            return "";
        }
        if (names.size() == 1)
        {
            return names.get(0);
        }
        if (names.size() == 2)
        {
            return names.get(0) + " and " + names.get(1);
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < names.size(); index++)
        {
            if (index > 0)
            {
                result.append(index == names.size() - 1 ? ", and " : ", ");
            }
            result.append(names.get(index));
        }
        return result.toString();
    }
}
