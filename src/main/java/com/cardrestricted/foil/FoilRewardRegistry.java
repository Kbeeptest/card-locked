package com.cardrestricted.foil;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Loads and validates the reviewed, data-driven foil reward relationships. */
public final class FoilRewardRegistry
{
    public static final String RESOURCE_ROOT = "com/cardrestricted/foil";

    private final CardCatalogue catalogue;
    private final Map<String, List<FoilRewardGrant>> grantsBySource;
    private final List<FoilCombinationReward> combinationRewards;
    private final Map<String, List<FoilCombinationReward>> combinationsBySource;
    private final int directGrantCount;
    private final int conditionalGrantCount;
    private final int mappedSourceCount;

    private FoilRewardRegistry(
        CardCatalogue catalogue,
        Map<String, List<FoilRewardGrant>> grantsBySource,
        List<FoilCombinationReward> combinationRewards)
    {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        Map<String, List<FoilRewardGrant>> frozen = new LinkedHashMap<>();
        int directTotal = 0;
        for (Map.Entry<String, List<FoilRewardGrant>> entry
            : grantsBySource.entrySet())
        {
            List<FoilRewardGrant> grants = sortedGrants(entry.getValue());
            frozen.put(entry.getKey(), Collections.unmodifiableList(grants));
            directTotal += grants.size();
        }
        this.grantsBySource = Collections.unmodifiableMap(frozen);
        this.directGrantCount = directTotal;

        List<FoilCombinationReward> combinations =
            new ArrayList<>(combinationRewards);
        combinations.sort(Comparator.comparing(
            FoilCombinationReward::getRuleId));
        this.combinationRewards = Collections.unmodifiableList(combinations);
        Map<String, List<FoilCombinationReward>> bySource =
            new LinkedHashMap<>();
        int conditionalTotal = 0;
        LinkedHashSet<String> allSources = new LinkedHashSet<>(frozen.keySet());
        for (FoilCombinationReward combination : combinations)
        {
            conditionalTotal += combination.getTargetCardIds().size();
            for (String source : combination.getRequiredSourceCardIds())
            {
                allSources.add(source);
                bySource.computeIfAbsent(
                    source,
                    ignored -> new ArrayList<>()).add(combination);
            }
        }
        Map<String, List<FoilCombinationReward>> frozenBySource =
            new LinkedHashMap<>();
        for (Map.Entry<String, List<FoilCombinationReward>> entry
            : bySource.entrySet())
        {
            frozenBySource.put(
                entry.getKey(),
                Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        this.combinationsBySource = Collections.unmodifiableMap(frozenBySource);
        this.conditionalGrantCount = conditionalTotal;
        this.mappedSourceCount = allSources.size();
    }

    public static FoilRewardRegistry load(
        ClassLoader classLoader,
        CardCatalogue catalogue)
    {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(catalogue, "catalogue");
        Builder builder = new Builder(catalogue);
        builder.readTierGroups(
            classLoader,
            RESOURCE_ROOT + "/tier-groups.tsv");
        builder.readSetGroups(
            classLoader,
            RESOURCE_ROOT + "/set-groups.tsv");
        builder.readSourceRewards(
            classLoader,
            RESOURCE_ROOT + "/source-rewards.tsv");
        builder.readSupplementalRewards(
            classLoader,
            RESOURCE_ROOT + "/supplemental-rewards.tsv");
        builder.readCombinationRewards(
            classLoader,
            RESOURCE_ROOT + "/combination-rewards.tsv");
        return new FoilRewardRegistry(
            catalogue,
            builder.grantsBySource,
            builder.combinationRewards);
    }

    /** Returns direct rewards that activate from this foil by itself. */
    public List<FoilRewardGrant> getRewardsForSource(String sourceCardId)
    {
        String canonical = catalogue.resolveCardId(sourceCardId);
        return grantsBySource.getOrDefault(
            canonical,
            Collections.emptyList());
    }

    /** Returns direct and conditional rewards shown while inspecting a foil source. */
    public List<FoilRewardGrant> getPotentialRewardsForSource(
        String sourceCardId)
    {
        String canonical = catalogue.resolveCardId(sourceCardId);
        List<FoilRewardGrant> result = new ArrayList<>(
            getRewardsForSource(canonical));
        for (FoilCombinationReward combination
            : combinationsBySource.getOrDefault(
                canonical,
                Collections.emptyList()))
        {
            for (String target : combination.getTargetCardIds())
            {
                result.add(combination.asGrant(canonical, target));
            }
        }
        return Collections.unmodifiableList(sortedGrants(result));
    }

    public List<FoilCombinationReward> getCombinationRewards()
    {
        return combinationRewards;
    }

    public List<FoilCombinationReward> getCombinationRewardsForSource(
        String sourceCardId)
    {
        String canonical = catalogue.resolveCardId(sourceCardId);
        return combinationsBySource.getOrDefault(
            canonical,
            Collections.emptyList());
    }

    /** Returns only targets immediately supplied by this foil source. */
    public Set<String> getTargetCardIdsForSource(String sourceCardId)
    {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        for (FoilRewardGrant grant : getRewardsForSource(sourceCardId))
        {
            targets.add(grant.getTargetCardId());
        }
        return Collections.unmodifiableSet(targets);
    }

    /** Returns direct targets plus targets that require additional foil sources. */
    public Set<String> getPotentialTargetCardIdsForSource(String sourceCardId)
    {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        for (FoilRewardGrant grant : getPotentialRewardsForSource(sourceCardId))
        {
            targets.add(grant.getTargetCardId());
        }
        return Collections.unmodifiableSet(targets);
    }

    public int getMappedSourceCount()
    {
        return mappedSourceCount;
    }

    public int getDirectGrantCount()
    {
        return directGrantCount;
    }

    public int getConditionalGrantCount()
    {
        return conditionalGrantCount;
    }

    public List<String> getSourceCardIdsForKind(FoilRewardKind kind)
    {
        return getSourceCardIdsForKinds(Collections.singleton(
            Objects.requireNonNull(kind, "kind")));
    }

    public List<String> getSourceCardIdsForKinds(
        Set<FoilRewardKind> kinds)
    {
        Objects.requireNonNull(kinds, "kinds");
        if (kinds.isEmpty())
        {
            return Collections.emptyList();
        }
        LinkedHashSet<FoilRewardKind> requested =
            new LinkedHashSet<>();
        for (FoilRewardKind kind : kinds)
        {
            requested.add(Objects.requireNonNull(kind, "kind"));
        }
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        for (Map.Entry<String, List<FoilRewardGrant>> entry
            : grantsBySource.entrySet())
        {
            if (entry.getValue().stream()
                .anyMatch(grant -> requested.contains(grant.getKind())))
            {
                sources.add(entry.getKey());
            }
        }
        if (requested.contains(FoilRewardKind.MULTI_SOURCE_COMPLETION))
        {
            sources.addAll(combinationsBySource.keySet());
        }
        return sortedSourceIds(sources);
    }

    public List<String> getSourceCardIdsForRules(Set<String> ruleIds)
    {
        Objects.requireNonNull(ruleIds, "ruleIds");
        if (ruleIds.isEmpty())
        {
            return Collections.emptyList();
        }
        LinkedHashSet<String> normalizedRules = new LinkedHashSet<>();
        for (String ruleId : ruleIds)
        {
            if (ruleId != null && !ruleId.trim().isEmpty())
            {
                normalizedRules.add(ruleId.trim());
            }
        }
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        for (Map.Entry<String, List<FoilRewardGrant>> entry
            : grantsBySource.entrySet())
        {
            if (entry.getValue().stream()
                .anyMatch(grant -> normalizedRules.contains(grant.getRuleId())))
            {
                sources.add(entry.getKey());
            }
        }
        for (FoilCombinationReward combination : combinationRewards)
        {
            if (normalizedRules.contains(combination.getRuleId()))
            {
                sources.addAll(combination.getRequiredSourceCardIds());
            }
        }
        return sortedSourceIds(sources);
    }

    public boolean hasReward(String sourceCardId)
    {
        return !getPotentialRewardsForSource(sourceCardId).isEmpty();
    }

    private List<String> sortedSourceIds(Set<String> sources)
    {
        List<String> result = new ArrayList<>(sources);
        result.sort(Comparator
            .comparing((String cardId) -> catalogue.requireCard(cardId)
                .getDisplayName())
            .thenComparing(cardId -> cardId));
        return Collections.unmodifiableList(result);
    }

    private List<FoilRewardGrant> sortedGrants(List<FoilRewardGrant> values)
    {
        List<FoilRewardGrant> grants = new ArrayList<>(values);
        grants.sort(Comparator
            .comparing((FoilRewardGrant grant) -> grant.getKind().ordinal())
            .thenComparing(grant -> catalogue.requireCard(
                grant.getTargetCardId()).getDisplayName())
            .thenComparing(FoilRewardGrant::getTargetCardId));
        return grants;
    }

    private static final class Builder
    {
        private static final Set<FoilRewardKind> NPC_DIRECT_KINDS =
            Collections.unmodifiableSet(EnumSet.of(
                FoilRewardKind.SOURCE_UNIQUES,
                FoilRewardKind.NPC_REQUIRED_TOOL,
                FoilRewardKind.ACHIEVEMENT_REWARD,
                FoilRewardKind.SOURCE_EQUIPMENT_SET));

        private final CardCatalogue catalogue;
        private final Map<String, List<FoilRewardGrant>> grantsBySource =
            new LinkedHashMap<>();
        private final List<FoilCombinationReward> combinationRewards =
            new ArrayList<>();
        private final Set<String> deduplicationKeys = new LinkedHashSet<>();
        private final Set<String> ruleIds = new LinkedHashSet<>();
        private final Map<String, FoilRewardKind> primaryKindBySource =
            new LinkedHashMap<>();

        private Builder(CardCatalogue catalogue)
        {
            this.catalogue = catalogue;
        }

        private void readTierGroups(ClassLoader loader, String path)
        {
            List<String[]> rows = readTsv(
                loader,
                path,
                new String[] {"group_id", "description", "ordered_card_ids"});
            for (int index = 0; index < rows.size(); index++)
            {
                String[] row = rows.get(index);
                int line = index + 2;
                String ruleId = requireText(row[0], path, line);
                String description = requireText(row[1], path, line);
                List<String> cards = parseCardIds(row[2], path, line, ruleId);
                requireUniqueRule(ruleId, path, line);
                if (cards.size() < 2)
                {
                    throw dataError(path, line,
                        "Tier groups require at least two cards.");
                }
                for (String cardId : cards)
                {
                    if (requireCard(cardId, path, line).getCardType()
                        != CardType.ITEM)
                    {
                        throw dataError(path, line,
                            "Tier groups require item cards.");
                    }
                }
                for (int sourceIndex = 1;
                     sourceIndex < cards.size();
                     sourceIndex++)
                {
                    String source = cards.get(sourceIndex);
                    registerPrimaryKind(
                        source,
                        FoilRewardKind.TIER_CASCADE,
                        path,
                        line);
                    for (int targetIndex = 0;
                         targetIndex < sourceIndex;
                         targetIndex++)
                    {
                        addGrant(new FoilRewardGrant(
                            source,
                            cards.get(targetIndex),
                            FoilRewardKind.TIER_CASCADE,
                            ruleId,
                            description), path, line);
                    }
                }
            }
        }

        private void readSetGroups(ClassLoader loader, String path)
        {
            List<String[]> rows = readTsv(
                loader,
                path,
                new String[] {"group_id", "description", "card_ids"});
            for (int index = 0; index < rows.size(); index++)
            {
                String[] row = rows.get(index);
                int line = index + 2;
                String ruleId = requireText(row[0], path, line);
                String description = requireText(row[1], path, line);
                List<String> cards = parseCardIds(row[2], path, line, ruleId);
                requireUniqueRule(ruleId, path, line);
                if (cards.size() < 2)
                {
                    throw dataError(path, line,
                        "Signature sets require at least two cards.");
                }
                for (String cardId : cards)
                {
                    if (requireCard(cardId, path, line).getCardType()
                        != CardType.ITEM)
                    {
                        throw dataError(path, line,
                            "Signature sets require item cards.");
                    }
                }
                for (String source : cards)
                {
                    registerPrimaryKind(
                        source,
                        FoilRewardKind.SIGNATURE_SET,
                        path,
                        line);
                    for (String target : cards)
                    {
                        if (!source.equals(target))
                        {
                            addGrant(new FoilRewardGrant(
                                source,
                                target,
                                FoilRewardKind.SIGNATURE_SET,
                                ruleId,
                                description), path, line);
                        }
                    }
                }
            }
        }

        private void readSourceRewards(ClassLoader loader, String path)
        {
            List<String[]> rows = readTsv(
                loader,
                path,
                new String[] {
                    "source_card_id",
                    "reward_kind",
                    "rule_id",
                    "description",
                    "target_card_ids"
                });
            for (int index = 0; index < rows.size(); index++)
            {
                String[] row = rows.get(index);
                int line = index + 2;
                String source = requireText(row[0], path, line);
                FoilRewardKind kind = parseDirectKind(row[1], path, line);
                String ruleId = requireText(row[2], path, line);
                String description = requireText(row[3], path, line);
                List<String> targets = parseCardIds(
                    row[4], path, line, ruleId);
                requireUniqueRule(ruleId, path, line);
                CardDefinition sourceCard = requireCard(source, path, line);
                if (kind == FoilRewardKind.RECIPE_COMPONENTS)
                {
                    if (sourceCard.getCardType() != CardType.ITEM)
                    {
                        throw dataError(path, line,
                            "Recipe component rewards require an item source.");
                    }
                }
                else if (NPC_DIRECT_KINDS.contains(kind))
                {
                    if (sourceCard.getCardType() != CardType.NPC)
                    {
                        throw dataError(path, line,
                            kind + " rewards require an NPC source.");
                    }
                }
                else
                {
                    throw dataError(path, line,
                        kind + " belongs in the supplemental reward resource.");
                }
                registerPrimaryKind(source, kind, path, line);
                for (String target : targets)
                {
                    if (requireCard(target, path, line).getCardType()
                        != CardType.ITEM)
                    {
                        throw dataError(path, line,
                            "Direct foil rewards must target item cards.");
                    }
                    addGrant(new FoilRewardGrant(
                        source,
                        target,
                        kind,
                        ruleId,
                        description), path, line);
                }
            }
        }

        private void readSupplementalRewards(ClassLoader loader, String path)
        {
            List<String[]> rows = readTsv(
                loader,
                path,
                new String[] {
                    "source_card_id",
                    "reward_kind",
                    "rule_id",
                    "description",
                    "target_card_ids"
                });
            Set<FoilRewardKind> itemKinds = EnumSet.of(
                FoilRewardKind.RECIPE_COMPONENTS,
                FoilRewardKind.FARMING_SEED,
                FoilRewardKind.PACKAGE_CONTENTS,
                FoilRewardKind.MATERIAL_CONVERSION);
            for (int index = 0; index < rows.size(); index++)
            {
                String[] row = rows.get(index);
                int line = index + 2;
                String source = requireText(row[0], path, line);
                FoilRewardKind kind = parseDirectKind(row[1], path, line);
                String ruleId = requireText(row[2], path, line);
                String description = requireText(row[3], path, line);
                List<String> targets = parseCardIds(
                    row[4], path, line, ruleId);
                requireUniqueRule(ruleId, path, line);
                CardDefinition sourceCard = requireCard(source, path, line);

                CardType requiredType;
                if (itemKinds.contains(kind))
                {
                    requiredType = CardType.ITEM;
                }
                else if (kind == FoilRewardKind.ENCOUNTER_TIER_CASCADE)
                {
                    requiredType = CardType.NPC;
                }
                else
                {
                    throw dataError(path, line,
                        "Unsupported supplemental reward kind: " + kind);
                }
                if (sourceCard.getCardType() != requiredType)
                {
                    throw dataError(path, line,
                        kind + " rewards require a "
                            + requiredType.name().toLowerCase()
                            + " source.");
                }
                for (String target : targets)
                {
                    if (requireCard(target, path, line).getCardType()
                        != requiredType)
                    {
                        throw dataError(path, line,
                            kind + " rewards require "
                                + requiredType.name().toLowerCase()
                                + " targets.");
                    }
                    addGrant(new FoilRewardGrant(
                        source,
                        target,
                        kind,
                        ruleId,
                        description), path, line);
                }
            }
        }

        private void readCombinationRewards(ClassLoader loader, String path)
        {
            List<String[]> rows = readTsv(
                loader,
                path,
                new String[] {
                    "rule_id",
                    "description",
                    "required_source_card_ids",
                    "target_card_ids"
                });
            for (int index = 0; index < rows.size(); index++)
            {
                String[] row = rows.get(index);
                int line = index + 2;
                String ruleId = requireText(row[0], path, line);
                String description = requireText(row[1], path, line);
                List<String> sources = parseCardIds(
                    row[2], path, line, ruleId);
                List<String> targets = parseCardIds(
                    row[3], path, line, ruleId);
                requireUniqueRule(ruleId, path, line);
                if (sources.size() < 2)
                {
                    throw dataError(path, line,
                        "Combination rewards require at least two sources.");
                }
                for (String source : sources)
                {
                    requireCard(source, path, line);
                }
                for (String target : targets)
                {
                    if (requireCard(target, path, line).getCardType()
                        != CardType.ITEM)
                    {
                        throw dataError(path, line,
                            "Combination rewards must target item cards.");
                    }
                }
                combinationRewards.add(new FoilCombinationReward(
                    ruleId,
                    description,
                    new LinkedHashSet<>(sources),
                    new LinkedHashSet<>(targets)));
            }
        }

        private FoilRewardKind parseDirectKind(
            String value,
            String path,
            int line)
        {
            String name = requireText(value, path, line);
            try
            {
                FoilRewardKind kind = FoilRewardKind.valueOf(name);
                if (kind == FoilRewardKind.TIER_CASCADE
                    || kind == FoilRewardKind.SIGNATURE_SET
                    || kind == FoilRewardKind.MULTI_SOURCE_COMPLETION)
                {
                    throw dataError(path, line,
                        "Reward kind " + kind
                            + " belongs in its dedicated group resource.");
                }
                return kind;
            }
            catch (IllegalArgumentException exception)
            {
                throw dataError(path, line,
                    "Unknown foil reward kind " + name + '.');
            }
        }

        private void registerPrimaryKind(
            String source,
            FoilRewardKind kind,
            String path,
            int line)
        {
            FoilRewardKind existing = primaryKindBySource.putIfAbsent(
                source,
                kind);
            if (existing != null && existing != kind)
            {
                throw dataError(path, line,
                    "Foil source " + source + " mixes primary reward kinds "
                        + existing + " and " + kind + '.');
            }
        }

        private void requireUniqueRule(String ruleId, String path, int line)
        {
            if (!ruleIds.add(ruleId))
            {
                throw dataError(path, line,
                    "Duplicate rule ID " + ruleId + '.');
            }
        }

        private CardDefinition requireCard(String cardId, String path, int line)
        {
            try
            {
                CardDefinition card = catalogue.requireCard(cardId);
                if (!card.getCardId().equals(cardId))
                {
                    throw dataError(path, line,
                        "Foil reward IDs must be canonical: " + cardId
                            + " resolves to " + card.getCardId() + '.');
                }
                return card;
            }
            catch (IllegalArgumentException exception)
            {
                throw dataError(path, line,
                    "Unknown foil reward card " + cardId + '.');
            }
        }

        private void addGrant(FoilRewardGrant grant, String path, int line)
        {
            String key = grant.getSourceCardId() + "\u0000"
                + grant.getTargetCardId() + "\u0000"
                + grant.getKind() + "\u0000" + grant.getRuleId();
            if (!deduplicationKeys.add(key))
            {
                throw dataError(path, line,
                    "Duplicate foil reward grant "
                        + grant.getSourceCardId() + " -> "
                        + grant.getTargetCardId() + '.');
            }
            grantsBySource.computeIfAbsent(
                grant.getSourceCardId(),
                ignored -> new ArrayList<>()).add(grant);
        }
    }

    private static List<String[]> readTsv(
        ClassLoader loader,
        String path,
        String[] expectedHeader)
    {
        try (InputStream input = loader.getResourceAsStream(path))
        {
            if (input == null)
            {
                throw new IllegalStateException(
                    "Missing foil reward resource " + path + '.');
            }
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)))
            {
                String header = reader.readLine();
                String expected = String.join("\t", expectedHeader);
                if (!expected.equals(header))
                {
                    throw new IllegalStateException(
                        path + " has an unexpected header.");
                }
                List<String[]> rows = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.trim().isEmpty() || line.startsWith("#"))
                    {
                        continue;
                    }
                    String[] columns = line.split("\t", -1);
                    if (columns.length != expectedHeader.length)
                    {
                        throw new IllegalStateException(
                            path + " contains a malformed row: " + line);
                    }
                    rows.add(columns);
                }
                return rows;
            }
        }
        catch (IOException exception)
        {
            throw new IllegalStateException(
                "Unable to load foil reward resource " + path + '.',
                exception);
        }
    }

    private static List<String> parseCardIds(
        String value,
        String path,
        int line,
        String ruleId)
    {
        LinkedHashSet<String> cards = new LinkedHashSet<>();
        for (String token : value.split("\\|", -1))
        {
            String cardId = token.trim();
            if (cardId.isEmpty())
            {
                throw dataError(path, line,
                    "Blank card ID in rule " + ruleId + '.');
            }
            if (!cards.add(cardId))
            {
                throw dataError(path, line,
                    "Duplicate card ID " + cardId + " in rule " + ruleId + '.');
            }
        }
        return new ArrayList<>(cards);
    }

    private static String requireText(String value, String path, int line)
    {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty())
        {
            throw dataError(path, line, "Required field is blank.");
        }
        return trimmed;
    }

    private static IllegalStateException dataError(
        String path,
        int line,
        String message)
    {
        return new IllegalStateException(path + ':' + line + ": " + message);
    }
}
