package com.cardrestricted.progression;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.persistence.CollectionState;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Central source of truth for collection-size progression milestones. */
public final class ProgressionMilestonePolicy
{
    public static final int UNCOMMON_PLUS_PACK = 250;
    public static final int EXPLORER_PACK = 500;
    public static final int RARE_PLUS_PACK = 750;
    public static final int COINS = 1_000;
    public static final int ADVENTURE_PACK = 1_250;
    public static final int NEXUS_CACHE = 1_750;
    public static final int COLLECTOR_PACK = 2_500;

    public static final int INITIATE_FOIL_PACK = 500;
    public static final int HERO_PACK = 1_500;
    public static final int NOBLE_PACK = 2_250;
    public static final int LEGEND_PACK = 3_000;
    public static final int MYTHICAL_PACK = 3_750;
    public static final int GODS_PACK = 4_500;

    public static final String INITIATE_FOIL_MARKER =
        "milestone-pack:initiate-foil:v1";
    public static final String HERO_PACK_MARKER =
        "milestone-pack:hero:v1";
    public static final String NOBLE_PACK_MARKER =
        "milestone-pack:noble:v1";
    public static final String LEGEND_PACK_MARKER =
        "milestone-pack:legend:v1";
    public static final String MYTHICAL_PACK_MARKER =
        "milestone-pack:mythical:v1";
    public static final String GODS_PACK_MARKER =
        "milestone-pack:gods:v1";


    private static final List<ProgressionMilestoneDefinition> TRACK =
        Collections.unmodifiableList(Arrays.asList(
            permanent(
                0,
                "Standard Pack",
                "Available immediately · 3,000 points",
                "Contains 3 Common cards, 1 Uncommon card and 1 unrestricted slot."),
            permanent(
                UNCOMMON_PLUS_PACK,
                "Uncommon+ Pack",
                "Permanent store unlock · 4,000 points",
                "Contains 2 Uncommon cards, 2 Uncommon-or-better cards and 1 unrestricted slot."),
            permanent(
                EXPLORER_PACK,
                "Explorer Pack",
                "Permanent store unlock · 4,000 points",
                "Targets non-combat NPCs only, with strong unowned-card weighting and a Common/Uncommon-focused discovery structure."),
            oneTime(
                INITIATE_FOIL_PACK,
                "Initiate's Foil Pack",
                "One-time reward · 5 foils",
                "Contains 5 foil cards drawn from the Common and Uncommon tiers.",
                INITIATE_FOIL_MARKER),
            permanent(
                RARE_PLUS_PACK,
                "Rare+ Pack",
                "Permanent store unlock · 6,000 points",
                "Contains 3 Rare cards plus 2 improved Rare-or-better rolls weighted toward Epic and above."),
            permanent(
                COINS,
                "Coins card",
                "Permanent card unlock",
                "Adds the Coins card to the album. This card is available only from the progression track."),
            permanent(
                ADVENTURE_PACK,
                "Adventure Pack",
                "Permanent store unlock · 7,500 points",
                "Targets combat NPCs with 1 Uncommon+, 3 Rare+ and 1 guaranteed Epic+ slot, plus mild unowned-card weighting."),
            oneTime(
                HERO_PACK,
                "Hero's Pack",
                "One-time reward · 5 Rare cards",
                "Contains 3 Rare foils and 2 normal Rare cards.",
                HERO_PACK_MARKER),
            permanent(
                NEXUS_CACHE,
                "Nexus Cache",
                "Permanent store unlock · 6,000 points",
                "Awards 225–375 Nexus Shards and contains no cards."),
            oneTime(
                NOBLE_PACK,
                "Noble's Pack",
                "One-time reward · 5 Epic cards",
                "Contains 3 Epic foils and 2 normal Epic cards.",
                NOBLE_PACK_MARKER),
            permanent(
                COLLECTOR_PACK,
                "Collector Pack",
                "Permanent store unlock · 25,000 points",
                "Contains one Rare, Epic, Legendary, Mythic and Godly card. At least one eligible tier produces a new card while an unowned card remains."),
            oneTime(
                LEGEND_PACK,
                "Legend's Pack",
                "One-time reward · 5 Legendary cards",
                "Contains 3 Legendary foils and 2 normal Legendary cards.",
                LEGEND_PACK_MARKER),
            oneTime(
                MYTHICAL_PACK,
                "Mythical Pack",
                "One-time reward · 5 Mythic cards",
                "Contains 3 Mythic foils and 2 normal Mythic cards.",
                MYTHICAL_PACK_MARKER),
            oneTime(
                GODS_PACK,
                "Pack of the Gods",
                "One-time reward · 5 Godly cards",
                "Contains 3 Godly foils and 2 normal Godly cards.",
                GODS_PACK_MARKER)));

    private ProgressionMilestonePolicy()
    {
    }


    public static List<ProgressionMilestoneDefinition> track()
    {
        return TRACK;
    }

    public static int finalTrackThreshold()
    {
        return GODS_PACK;
    }

    private static ProgressionMilestoneDefinition permanent(
        int requiredCards,
        String title,
        String rewardSummary,
        String detail)
    {
        return new ProgressionMilestoneDefinition(
            requiredCards,
            title,
            rewardSummary,
            detail,
            ProgressionMilestoneDefinition.Kind.PERMANENT_UNLOCK,
            "");
    }

    private static ProgressionMilestoneDefinition oneTime(
        int requiredCards,
        String title,
        String rewardSummary,
        String detail,
        String claimedMarker)
    {
        return new ProgressionMilestoneDefinition(
            requiredCards,
            title,
            rewardSummary,
            detail,
            ProgressionMilestoneDefinition.Kind.ONE_TIME_REWARD,
            claimedMarker);
    }

    public static int uniqueOwnedCardCount(
        CardCatalogue catalogue,
        CollectionState state)
    {
        Objects.requireNonNull(catalogue, "catalogue");
        Objects.requireNonNull(state, "state");
        return (int) catalogue.canonicalizeCardIds(state.getOwnedCardIds())
            .stream()
            .filter(cardId -> catalogue.findCard(cardId).isPresent())
            .filter(cardId -> !ProgressionRewardCardPolicy
                .isTrackOnlyReward(cardId))
            .count();
    }

    public static boolean hasReached(
        CardCatalogue catalogue,
        CollectionState state,
        int requiredCards)
    {
        return uniqueOwnedCardCount(catalogue, state) >= requiredCards;
    }

    public static boolean hasClaimed(
        CollectionState state,
        String marker)
    {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(marker, "marker");
        return state.getClaimedPointSourceIds().contains(marker);
    }
}
