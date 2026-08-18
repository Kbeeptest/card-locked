package com.cardrestricted.collection.activity;

import com.cardrestricted.collection.achievement.AchievementCompletionRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CollectionActivitySnapshot
{
    private final boolean available;
    private final String warning;
    private final List<PackActivityRecord> packs;
    private final List<CardUnlockRecord> unlocks;
    private final List<AchievementCompletionRecord> achievementCompletions;
    private final Map<String, CardUnlockRecord> unlockByCardId;
    private final Map<String, Integer> packCounts;
    private final Map<String, Integer> duplicateCounts;
    private final long recordedPointsEarned;
    private final long recordedEligibleXp;
    private final int nexusUnlockCount;
    private final int totalCardsDrawn;
    private final int newPackCardCount;
    private final int duplicateCardCount;
    private final long duplicateShards;
    private final int currentNewCardStreak;
    private final int longestNewCardStreak;
    private final int ignoredEventCount;

    public CollectionActivitySnapshot(
        boolean available,
        String warning,
        List<PackActivityRecord> packs,
        List<CardUnlockRecord> unlocks,
        List<AchievementCompletionRecord> achievementCompletions,
        Map<String, Integer> packCounts,
        Map<String, Integer> duplicateCounts,
        long recordedPointsEarned,
        long recordedEligibleXp,
        int nexusUnlockCount,
        int totalCardsDrawn,
        int newPackCardCount,
        int duplicateCardCount,
        long duplicateShards,
        int currentNewCardStreak,
        int longestNewCardStreak,
        int ignoredEventCount)
    {
        if (recordedPointsEarned < 0
            || recordedEligibleXp < 0
            || nexusUnlockCount < 0
            || totalCardsDrawn < 0
            || newPackCardCount < 0
            || duplicateCardCount < 0
            || duplicateShards < 0
            || currentNewCardStreak < 0
            || longestNewCardStreak < 0
            || ignoredEventCount < 0)
        {
            throw new IllegalArgumentException(
                "Activity counters cannot be negative.");
        }
        this.available = available;
        this.warning = warning == null ? "" : warning;
        this.packs = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(packs, "packs")));
        this.unlocks = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(unlocks, "unlocks")));
        this.achievementCompletions = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(
                achievementCompletions,
                "achievementCompletions")));
        Map<String, CardUnlockRecord> indexedUnlocks = new LinkedHashMap<>();
        for (CardUnlockRecord unlock : this.unlocks)
        {
            indexedUnlocks.putIfAbsent(unlock.getCardId(), unlock);
        }
        this.unlockByCardId = Collections.unmodifiableMap(indexedUnlocks);
        this.packCounts = immutableCounts(packCounts, "packCounts");
        this.duplicateCounts = immutableCounts(
            duplicateCounts,
            "duplicateCounts");
        this.recordedPointsEarned = recordedPointsEarned;
        this.recordedEligibleXp = recordedEligibleXp;
        this.nexusUnlockCount = nexusUnlockCount;
        this.totalCardsDrawn = totalCardsDrawn;
        this.newPackCardCount = newPackCardCount;
        this.duplicateCardCount = duplicateCardCount;
        this.duplicateShards = duplicateShards;
        this.currentNewCardStreak = currentNewCardStreak;
        this.longestNewCardStreak = longestNewCardStreak;
        this.ignoredEventCount = ignoredEventCount;
    }

    public static CollectionActivitySnapshot empty()
    {
        return new CollectionActivitySnapshot(
            true,
            "",
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0);
    }

    public static CollectionActivitySnapshot unavailable(String warning)
    {
        return new CollectionActivitySnapshot(
            false,
            Objects.requireNonNull(warning, "warning"),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0);
    }

    public boolean isAvailable()
    {
        return available;
    }

    public String getWarning()
    {
        return warning;
    }

    public List<PackActivityRecord> getPacks()
    {
        return packs;
    }

    public int getPackCount()
    {
        return packs.size();
    }

    public int getCompletedPackCount()
    {
        return (int) packs.stream()
            .filter(PackActivityRecord::isFullyRevealed)
            .count();
    }

    public List<CardUnlockRecord> getUnlocks()
    {
        return unlocks;
    }

    public List<CardUnlockRecord> getRecentUnlocks(int limit)
    {
        if (limit < 0)
        {
            throw new IllegalArgumentException("limit cannot be negative.");
        }
        int fromIndex = Math.max(0, unlocks.size() - limit);
        List<CardUnlockRecord> recent = new ArrayList<>(
            unlocks.subList(fromIndex, unlocks.size()));
        Collections.reverse(recent);
        return Collections.unmodifiableList(recent);
    }

    public Optional<CardUnlockRecord> findUnlock(String cardId)
    {
        return Optional.ofNullable(unlockByCardId.get(cardId));
    }

    public List<AchievementCompletionRecord> getAchievementCompletions()
    {
        return achievementCompletions;
    }

    public int getAchievementCompletionCount()
    {
        return achievementCompletions.size();
    }

    public List<AchievementCompletionRecord> getRecentAchievementCompletions(
        int limit)
    {
        if (limit < 0)
        {
            throw new IllegalArgumentException("limit cannot be negative.");
        }
        int fromIndex = Math.max(
            0,
            achievementCompletions.size() - limit);
        List<AchievementCompletionRecord> recent = new ArrayList<>(
            achievementCompletions.subList(
                fromIndex,
                achievementCompletions.size()));
        Collections.reverse(recent);
        return Collections.unmodifiableList(recent);
    }

    public Map<String, Integer> getPackCounts()
    {
        return packCounts;
    }

    public Optional<String> getMostOpenedPackId()
    {
        return highestCountKey(packCounts);
    }

    public Map<String, Integer> getDuplicateCounts()
    {
        return duplicateCounts;
    }

    public int getDuplicateCount(String cardId)
    {
        return duplicateCounts.getOrDefault(cardId, 0);
    }

    public Optional<String> getMostDuplicatedCardId()
    {
        return highestCountKey(duplicateCounts);
    }

    public long getRecordedPointsEarned()
    {
        return recordedPointsEarned;
    }

    public long getRecordedEligibleXp()
    {
        return recordedEligibleXp;
    }

    public int getNexusUnlockCount()
    {
        return nexusUnlockCount;
    }

    public int getTotalCardsDrawn()
    {
        return totalCardsDrawn;
    }

    public int getNewPackCardCount()
    {
        return newPackCardCount;
    }

    public int getDuplicateCardCount()
    {
        return duplicateCardCount;
    }

    public long getDuplicateShards()
    {
        return duplicateShards;
    }

    public int getCurrentNewCardStreak()
    {
        return currentNewCardStreak;
    }

    public int getLongestNewCardStreak()
    {
        return longestNewCardStreak;
    }

    public int getIgnoredEventCount()
    {
        return ignoredEventCount;
    }

    private static Map<String, Integer> immutableCounts(
        Map<String, Integer> source,
        String field)
    {
        Objects.requireNonNull(source, field);
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet())
        {
            if (entry.getKey() == null
                || entry.getKey().trim().isEmpty()
                || entry.getValue() == null
                || entry.getValue() < 0)
            {
                throw new IllegalArgumentException(
                    field + " contains an invalid count.");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Optional<String> highestCountKey(
        Map<String, Integer> counts)
    {
        return counts.entrySet().stream()
            .sorted((left, right) -> {
                int byCount = Integer.compare(
                    right.getValue(),
                    left.getValue());
                return byCount != 0
                    ? byCount
                    : left.getKey().compareTo(right.getKey());
            })
            .map(Map.Entry::getKey)
            .findFirst();
    }
}
