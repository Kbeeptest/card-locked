package com.cardrestricted.collection.activity;

import com.cardrestricted.collection.achievement.AchievementCompletionRecord;
import com.cardrestricted.collection.achievement.AchievementRegistry;
import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.HistoricalCardDefinition;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.pack.DuplicateShardValues;
import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.persistence.JournalEventType;
import com.cardrestricted.persistence.StateJournalEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CollectionActivityService
{
    private static final String DUPLICATE_SUFFIX = ":duplicate";
    private static final String FOIL_SUFFIX = ":foil";

    private final CardCatalogue catalogue;
    private final AchievementRegistry achievementRegistry;

    public CollectionActivityService(CardCatalogue catalogue)
    {
        this(
            catalogue,
            AchievementRegistry.load(
                CollectionActivityService.class.getClassLoader()));
    }

    public CollectionActivityService(
        CardCatalogue catalogue,
        AchievementRegistry achievementRegistry)
    {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.achievementRegistry = Objects.requireNonNull(
            achievementRegistry,
            "achievementRegistry");
    }

    public CollectionActivitySnapshot calculate(
        List<StateJournalEvent> events)
    {
        return calculateFrom(CollectionActivitySnapshot.empty(), events);
    }

    /**
     * Applies only newly committed journal events to an existing activity
     * snapshot. This keeps pack/Nexus UI refresh cost proportional to recent
     * activity rather than total profile lifetime.
     */
    public CollectionActivitySnapshot calculateIncremental(
        CollectionActivitySnapshot previous,
        List<StateJournalEvent> events)
    {
        Objects.requireNonNull(previous, "previous");
        if (!previous.isAvailable())
        {
            throw new IllegalArgumentException(
                "Incremental activity requires an available baseline.");
        }
        return calculateFrom(previous, events);
    }

    private CollectionActivitySnapshot calculateFrom(
        CollectionActivitySnapshot previous,
        List<StateJournalEvent> events)
    {
        Objects.requireNonNull(events, "events");
        List<StateJournalEvent> orderedEvents;
        if (isRevisionOrdered(events))
        {
            orderedEvents = events;
        }
        else
        {
            orderedEvents = new ArrayList<>(events);
            orderedEvents.sort((left, right) -> Long.compare(
                left.getRevision(),
                right.getRevision()));
        }
        Map<UUID, MutablePack> packsByOpening = new LinkedHashMap<>();
        for (PackActivityRecord pack : previous.getPacks())
        {
            packsByOpening.put(pack.getOpeningId(), MutablePack.from(pack));
        }
        List<CardUnlockRecord> unlocks = new ArrayList<>(previous.getUnlocks());
        List<AchievementCompletionRecord> achievementCompletions =
            new ArrayList<>(previous.getAchievementCompletions());
        Set<String> recordedAchievementIds = new LinkedHashSet<>();
        for (AchievementCompletionRecord completion : achievementCompletions)
        {
            recordedAchievementIds.add(completion.getAchievementId());
        }
        Set<String> recordedUnlockIds = new LinkedHashSet<>();
        for (CardUnlockRecord unlock : unlocks)
        {
            recordedUnlockIds.add(unlock.getCardId());
        }
        Map<String, Integer> packCounts =
            new LinkedHashMap<>(previous.getPackCounts());
        Map<String, Integer> duplicateCounts =
            new LinkedHashMap<>(previous.getDuplicateCounts());
        long recordedPoints = previous.getRecordedPointsEarned();
        long recordedEligibleXp = previous.getRecordedEligibleXp();
        int nexusUnlocks = previous.getNexusUnlockCount();
        int totalCardsDrawn = previous.getTotalCardsDrawn();
        int newPackCards = previous.getNewPackCardCount();
        int duplicateCards = previous.getDuplicateCardCount();
        long duplicateShards = previous.getDuplicateShards();
        int currentNewCardStreak = previous.getCurrentNewCardStreak();
        int longestNewCardStreak = previous.getLongestNewCardStreak();
        int ignoredEvents = previous.getIgnoredEventCount();

        for (StateJournalEvent event : orderedEvents)
        {
            Map<String, String> payload = parsePayload(event.getPayload());
            try
            {
                switch (event.getType())
                {
                    case COLLECTION_CREATED:
                        List<String> starterCards = splitCsv(
                            payload.get("starterCards"));
                        boolean modernStarterChoice =
                            payload.containsKey("starterChoice");
                        if (starterCards.isEmpty() && !modernStarterChoice)
                        {
                            ignoredEvents++;
                        }
                        for (String cardId : starterCards)
                        {
                            recordUnlock(
                                unlocks,
                                recordedUnlockIds,
                                cardId,
                                CardUnlockSource.STARTER,
                                event.getOccurredAt(),
                                payload.getOrDefault("starterRoute", "starter"));
                        }
                        if (modernStarterChoice)
                        {
                            recordedPoints = Math.addExact(
                                recordedPoints,
                                parseNonNegativeLong(payload.getOrDefault(
                                    "starterBonusPoints",
                                    "0")));
                        }
                        break;
                    case PACK_PURCHASED:
                        MutablePack pack = parsePack(event, payload);
                        if (packsByOpening.putIfAbsent(
                            pack.openingId,
                            pack) != null)
                        {
                            ignoredEvents++;
                            break;
                        }
                        packCounts.merge(pack.packId, 1, Integer::sum);
                        CardUnlockSource packUnlockSource =
                            pack.packId.startsWith("pack.starter-randomised.")
                                ? CardUnlockSource.STARTER
                                : CardUnlockSource.PACK;
                        for (PackCardResult result : pack.cardResults)
                        {
                            totalCardsDrawn++;
                            if (result.isDuplicate())
                            {
                                duplicateCards++;
                                currentNewCardStreak = 0;
                                duplicateCounts.merge(
                                    result.getCardId(),
                                    1,
                                    Integer::sum);
                                duplicateShards = Math.addExact(
                                    duplicateShards,
                                    result.getShardsAwarded());
                            }
                            else
                            {
                                newPackCards++;
                                currentNewCardStreak++;
                                longestNewCardStreak = Math.max(
                                    longestNewCardStreak,
                                    currentNewCardStreak);
                                recordUnlock(
                                    unlocks,
                                    recordedUnlockIds,
                                    result.getCardId(),
                                    packUnlockSource,
                                    event.getOccurredAt(),
                                    pack.packId);
                            }
                        }
                        break;
                    case PACK_REVEAL_ADVANCED:
                        UUID openingId = parseUuid(payload.get("openingId"));
                        MutablePack pending = packsByOpening.get(openingId);
                        if (pending == null)
                        {
                            ignoredEvents++;
                        }
                        else
                        {
                            pending.revealedCount = Math.max(
                                pending.revealedCount,
                                parsePositiveInt(payload.get("reveal")));
                            pending.revealedCount = Math.min(
                                pending.revealedCount,
                                pending.cardResults.size());
                        }
                        break;
                    case NEXUS_EXCHANGE:
                        String nexusCardId = payload.get("cardId");
                        if (nexusCardId == null)
                        {
                            ignoredEvents++;
                            break;
                        }
                        if (recordUnlock(
                            unlocks,
                            recordedUnlockIds,
                            nexusCardId,
                            CardUnlockSource.NEXUS,
                            event.getOccurredAt(),
                            payload.getOrDefault("rarity", "NEXUS")))
                        {
                            nexusUnlocks++;
                        }
                        break;
                    case PROGRESSION_REWARD_GRANTED:
                        String progressionCardId = payload.get("cardId");
                        if (progressionCardId == null)
                        {
                            ignoredEvents++;
                            break;
                        }
                        recordUnlock(
                            unlocks,
                            recordedUnlockIds,
                            progressionCardId,
                            CardUnlockSource.PROGRESSION_TRACK,
                            event.getOccurredAt(),
                            payload.getOrDefault("threshold", "progression"));
                        break;
                    case POINTS_AWARDED:
                        recordedPoints = Math.addExact(
                            recordedPoints,
                            parseNonNegativeLong(payload.get("amount")));
                        break;
                    case NONCOMBAT_XP_PROCESSED:
                        recordedPoints = Math.addExact(
                            recordedPoints,
                            parseNonNegativeLong(payload.get("points")));
                        recordedEligibleXp = Math.addExact(
                            recordedEligibleXp,
                            parseNonNegativeLong(payload.get("eligibleXp")));
                        break;
                    case ACHIEVEMENTS_RECONCILED:
                        if ("earned".equals(payload.get("mode")))
                        {
                            List<String> achievementIds = splitCsv(
                                payload.get("completed"));
                            for (String achievementId : achievementIds)
                            {
                                achievementRegistry.require(achievementId);
                            }
                            for (String achievementId : achievementIds)
                            {
                                if (recordedAchievementIds.add(achievementId))
                                {
                                    achievementCompletions.add(
                                        new AchievementCompletionRecord(
                                            achievementId,
                                            event.getOccurredAt()));
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
            catch (IllegalArgumentException | ArithmeticException exception)
            {
                ignoredEvents++;
            }
        }

        List<PackActivityRecord> packs = new ArrayList<>();
        for (MutablePack pack : packsByOpening.values())
        {
            packs.add(pack.toRecord());
        }
        Collections.sort(
            unlocks,
            (left, right) -> left.getOccurredAt().compareTo(
                right.getOccurredAt()));
        Collections.sort(
            achievementCompletions,
            (left, right) -> left.getCompletedAt().compareTo(
                right.getCompletedAt()));
        return new CollectionActivitySnapshot(
            true,
            ignoredEvents == 0
                ? ""
                : ignoredEvents + " legacy or malformed journal event"
                    + (ignoredEvents == 1 ? " was" : "s were")
                    + " excluded from activity statistics.",
            packs,
            unlocks,
            achievementCompletions,
            packCounts,
            duplicateCounts,
            recordedPoints,
            recordedEligibleXp,
            nexusUnlocks,
            totalCardsDrawn,
            newPackCards,
            duplicateCards,
            duplicateShards,
            currentNewCardStreak,
            longestNewCardStreak,
            ignoredEvents);
    }

    private static boolean isRevisionOrdered(
        List<StateJournalEvent> events)
    {
        long previous = Long.MIN_VALUE;
        for (StateJournalEvent event : events)
        {
            if (event.getRevision() < previous)
            {
                return false;
            }
            previous = event.getRevision();
        }
        return true;
    }

    private MutablePack parsePack(
        StateJournalEvent event,
        Map<String, String> payload)
    {
        UUID openingId = parseUuid(payload.get("openingId"));
        String packId = requireText(payload.get("packId"), "packId");
        long price = parseNonNegativeLong(payload.get("price"));
        List<String> encodedResults = splitCsv(payload.get("results"));
        if (encodedResults.isEmpty())
        {
            throw new IllegalArgumentException(
                "Pack journal entry has no card results.");
        }
        List<PackCardResult> results = new ArrayList<>();
        long packDuplicateShards = 0;
        for (String encoded : encodedResults)
        {
            String rawCardId = encoded;
            boolean foil = false;
            boolean duplicate = false;
            boolean suffixRemoved;
            do
            {
                suffixRemoved = false;
                if (rawCardId.endsWith(FOIL_SUFFIX))
                {
                    foil = true;
                    rawCardId = rawCardId.substring(
                        0,
                        rawCardId.length() - FOIL_SUFFIX.length());
                    suffixRemoved = true;
                }
                if (rawCardId.endsWith(DUPLICATE_SUFFIX))
                {
                    duplicate = true;
                    rawCardId = rawCardId.substring(
                        0,
                        rawCardId.length() - DUPLICATE_SUFFIX.length());
                    suffixRemoved = true;
                }
            }
            while (suffixRemoved);
            ActivityCard card = resolveActivityCard(
                rawCardId,
                "journal card");
            long shards = duplicate
                ? DuplicateShardValues.forRarity(card.rarity)
                : 0;
            packDuplicateShards = Math.addExact(
                packDuplicateShards,
                shards);
            results.add(new PackCardResult(
                card.cardId,
                duplicate,
                shards,
                foil));
        }
        return new MutablePack(
            openingId,
            packId,
            event.getOccurredAt(),
            price,
            results,
            packDuplicateShards);
    }

    private boolean recordUnlock(
        List<CardUnlockRecord> unlocks,
        Set<String> recordedUnlockIds,
        String rawCardId,
        CardUnlockSource source,
        Instant occurredAt,
        String sourceId)
    {
        ActivityCard card = resolveActivityCard(
            rawCardId,
            "unlock card");
        if (!recordedUnlockIds.add(card.cardId))
        {
            return false;
        }
        unlocks.add(new CardUnlockRecord(
            card.cardId,
            source,
            occurredAt,
            sourceId));
        return true;
    }

    private ActivityCard resolveActivityCard(
        String rawCardId,
        String context)
    {
        CardDefinition active = catalogue.findCard(rawCardId).orElse(null);
        if (active != null)
        {
            return new ActivityCard(
                active.getCardId(),
                active.getRarity());
        }
        HistoricalCardDefinition historical =
            catalogue.findHistoricalCard(rawCardId).orElseThrow(() ->
                new IllegalArgumentException(
                    "Unknown " + context + " " + rawCardId + "."));
        return new ActivityCard(
            historical.getCardId(),
            historical.getRarity());
    }

    private static Map<String, String> parsePayload(String payload)
    {
        Map<String, String> values = new LinkedHashMap<>();
        if (payload == null || payload.isEmpty())
        {
            return values;
        }
        for (String field : payload.split(";", -1))
        {
            int separator = field.indexOf('=');
            if (separator <= 0)
            {
                continue;
            }
            values.put(
                field.substring(0, separator),
                field.substring(separator + 1));
        }
        return values;
    }

    private static List<String> splitCsv(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (String part : value.split(","))
        {
            if (!part.trim().isEmpty())
            {
                values.add(part.trim());
            }
        }
        return values;
    }

    private static UUID parseUuid(String value)
    {
        return UUID.fromString(requireText(value, "UUID"));
    }

    private static int parsePositiveInt(String value)
    {
        int parsed = Integer.parseInt(requireText(value, "integer"));
        if (parsed <= 0)
        {
            throw new IllegalArgumentException(
                "Journal integer must be positive.");
        }
        return parsed;
    }

    private static long parseNonNegativeLong(String value)
    {
        long parsed = Long.parseLong(requireText(value, "number"));
        if (parsed < 0)
        {
            throw new IllegalArgumentException(
                "Journal number cannot be negative.");
        }
        return parsed;
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value.trim();
    }

    private static final class ActivityCard
    {
        private final String cardId;
        private final Rarity rarity;

        private ActivityCard(String cardId, Rarity rarity)
        {
            this.cardId = cardId;
            this.rarity = rarity;
        }
    }

    private static final class MutablePack
    {
        private final UUID openingId;
        private final String packId;
        private final Instant purchasedAt;
        private final long price;
        private final List<PackCardResult> cardResults;
        private final long duplicateShards;
        private int revealedCount;

        private MutablePack(
            UUID openingId,
            String packId,
            Instant purchasedAt,
            long price,
            List<PackCardResult> cardResults,
            long duplicateShards)
        {
            this.openingId = openingId;
            this.packId = packId;
            this.purchasedAt = purchasedAt;
            this.price = price;
            this.cardResults = cardResults;
            this.duplicateShards = duplicateShards;
        }

        private static MutablePack from(PackActivityRecord record)
        {
            MutablePack pack = new MutablePack(
                record.getOpeningId(),
                record.getPackId(),
                record.getPurchasedAt(),
                record.getPrice(),
                record.getCardResults(),
                record.getDuplicateShards());
            pack.revealedCount = record.getRevealedCount();
            return pack;
        }

        private PackActivityRecord toRecord()
        {
            return new PackActivityRecord(
                openingId,
                packId,
                purchasedAt,
                price,
                cardResults,
                revealedCount,
                duplicateShards);
        }
    }
}
