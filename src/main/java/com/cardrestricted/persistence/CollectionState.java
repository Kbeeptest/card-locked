package com.cardrestricted.persistence;

import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.pack.InsufficientPointsException;
import com.cardrestricted.pack.PendingPackReveal;
import com.cardrestricted.pack.PendingRevealException;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CollectionState
{
    private static final String NPC_KILL_SOURCE_PREFIX = "npc-kill:v2:";
    // NpcKillCreditTracker retains duplicate evidence for 20 ticks. Keep a
    // wider durable window so delayed/queued callbacks remain idempotent
    // without allowing per-kill markers to grow for the lifetime of a profile.
    private static final int NPC_KILL_SOURCE_RETENTION_TICKS = 64;
    private final UUID collectionId;
    private final String characterKey;
    private final String displayName;
    private final EconomyMode economyMode;
    private final IntegrityMode integrityMode;
    private final Instant createdAt;
    private final int schemaVersion;
    private final int catalogueVersion;
    private final int ruleSetVersion;
    private final long revision;
    private final long points;
    private final long shards;
    private final Set<String> ownedCardIds;
    private final Set<String> foilCardIds;
    private final Set<String> claimedPointSourceIds;
    private final long noncombatRewardRemainderUnits;
    private final Map<String, Long> noncombatXpWatermarks;
    private final PendingPackReveal pendingPackReveal;

    public CollectionState(
        UUID collectionId,
        String characterKey,
        String displayName,
        EconomyMode economyMode,
        IntegrityMode integrityMode,
        Instant createdAt,
        int schemaVersion,
        int catalogueVersion,
        int ruleSetVersion,
        long revision,
        long points,
        long shards,
        Set<String> ownedCardIds,
        Set<String> foilCardIds)
    {
        this(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            schemaVersion,
            catalogueVersion,
            ruleSetVersion,
            revision,
            points,
            shards,
            ownedCardIds,
            foilCardIds,
            Collections.emptySet(),
            0,
            Collections.emptyMap(),
            null);
    }

    public CollectionState(
        UUID collectionId,
        String characterKey,
        String displayName,
        EconomyMode economyMode,
        IntegrityMode integrityMode,
        Instant createdAt,
        int schemaVersion,
        int catalogueVersion,
        int ruleSetVersion,
        long revision,
        long points,
        long shards,
        Set<String> ownedCardIds,
        Set<String> foilCardIds,
        Set<String> claimedPointSourceIds)
    {
        this(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            schemaVersion,
            catalogueVersion,
            ruleSetVersion,
            revision,
            points,
            shards,
            ownedCardIds,
            foilCardIds,
            claimedPointSourceIds,
            0,
            Collections.emptyMap(),
            null);
    }

    public CollectionState(
        UUID collectionId,
        String characterKey,
        String displayName,
        EconomyMode economyMode,
        IntegrityMode integrityMode,
        Instant createdAt,
        int schemaVersion,
        int catalogueVersion,
        int ruleSetVersion,
        long revision,
        long points,
        long shards,
        Set<String> ownedCardIds,
        Set<String> foilCardIds,
        Set<String> claimedPointSourceIds,
        long noncombatRewardRemainderUnits,
        Map<String, Long> noncombatXpWatermarks)
    {
        this(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            schemaVersion,
            catalogueVersion,
            ruleSetVersion,
            revision,
            points,
            shards,
            ownedCardIds,
            foilCardIds,
            claimedPointSourceIds,
            noncombatRewardRemainderUnits,
            noncombatXpWatermarks,
            null);
    }

    public CollectionState(
        UUID collectionId,
        String characterKey,
        String displayName,
        EconomyMode economyMode,
        IntegrityMode integrityMode,
        Instant createdAt,
        int schemaVersion,
        int catalogueVersion,
        int ruleSetVersion,
        long revision,
        long points,
        long shards,
        Set<String> ownedCardIds,
        Set<String> foilCardIds,
        Set<String> claimedPointSourceIds,
        long noncombatRewardRemainderUnits,
        Map<String, Long> noncombatXpWatermarks,
        PendingPackReveal pendingPackReveal)
    {
        this.collectionId = Objects.requireNonNull(collectionId, "collectionId");
        this.characterKey = requireText(characterKey, "characterKey");
        this.displayName = requireText(displayName, "displayName");
        this.economyMode = Objects.requireNonNull(economyMode, "economyMode");
        this.integrityMode = Objects.requireNonNull(integrityMode, "integrityMode");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.schemaVersion = schemaVersion;
        this.catalogueVersion = catalogueVersion;
        this.ruleSetVersion = ruleSetVersion;
        this.revision = revision;
        this.points = points;
        this.shards = shards;
        this.ownedCardIds = immutableCopy(ownedCardIds);
        this.foilCardIds = immutableCopy(foilCardIds);
        this.claimedPointSourceIds =
            immutableCopy(claimedPointSourceIds, "pointSourceId");
        this.noncombatRewardRemainderUnits =
            noncombatRewardRemainderUnits;
        this.noncombatXpWatermarks =
            immutableWatermarks(noncombatXpWatermarks);
        this.pendingPackReveal = pendingPackReveal;

        if (schemaVersion < 1 || catalogueVersion < 1 || ruleSetVersion < 1)
        {
            throw new IllegalArgumentException("Versions must be positive.");
        }
        if (revision < 0 || points < 0 || shards < 0)
        {
            throw new IllegalArgumentException(
                "Revision and currency values cannot be negative.");
        }
        if (noncombatRewardRemainderUnits < 0
            || noncombatRewardRemainderUnits >= 1_000)
        {
            throw new IllegalArgumentException(
                "Noncombat reward remainder must be from 0 through 999.");
        }
        if (!this.ownedCardIds.containsAll(this.foilCardIds))
        {
            throw new IllegalArgumentException(
                "Every foil card must also be an owned card.");
        }
        if (pendingPackReveal != null
            && pendingPackReveal.getCardResults().stream()
                .anyMatch(result ->
                    !this.ownedCardIds.contains(result.getCardId())))
        {
            throw new IllegalArgumentException(
                "Every committed pack result must be owned.");
        }
    }

    public CollectionState withProgress(
        long newRevision,
        long newPoints,
        long newShards,
        Set<String> newOwnedCardIds,
        Set<String> newFoilCardIds)
    {
        if (newRevision != revision + 1)
        {
            throw new IllegalArgumentException(
                "State mutations must advance the revision by exactly one.");
        }
        return new CollectionState(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            schemaVersion,
            catalogueVersion,
            ruleSetVersion,
            newRevision,
            newPoints,
            newShards,
            newOwnedCardIds,
            newFoilCardIds,
            claimedPointSourceIds,
            noncombatRewardRemainderUnits,
            noncombatXpWatermarks,
            pendingPackReveal);
    }

    public CollectionState withPointsAwarded(
        String pointSourceId,
        long amount)
    {
        String sourceId = requireText(pointSourceId, "pointSourceId");
        if (amount <= 0)
        {
            throw new IllegalArgumentException(
                "Point awards must be positive.");
        }
        if (claimedPointSourceIds.contains(sourceId))
        {
            throw new IllegalArgumentException(
                "The point source has already been claimed: " + sourceId);
        }

        Set<String> newClaimedSourceIds =
            compactClaimedPointSources(
                Collections.singleton(sourceId));
        newClaimedSourceIds.add(sourceId);
        return new CollectionState(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            schemaVersion,
            catalogueVersion,
            ruleSetVersion,
            revision + 1,
            Math.addExact(points, amount),
            shards,
            ownedCardIds,
            foilCardIds,
            newClaimedSourceIds,
            noncombatRewardRemainderUnits,
            noncombatXpWatermarks,
            pendingPackReveal);
    }

    public CollectionState withPointsAwardedBatch(
        Set<String> pointSourceIds,
        long amount)
    {
        Objects.requireNonNull(pointSourceIds, "pointSourceIds");
        if (pointSourceIds.isEmpty())
        {
            throw new IllegalArgumentException(
                "At least one point source is required.");
        }
        if (amount <= 0)
        {
            throw new IllegalArgumentException(
                "Point awards must be positive.");
        }

        Set<String> additions = new HashSet<>();
        for (String pointSourceId : pointSourceIds)
        {
            String sourceId = requireText(pointSourceId, "pointSourceId");
            if (claimedPointSourceIds.contains(sourceId)
                || !additions.add(sourceId))
            {
                throw new IllegalArgumentException(
                    "The point source has already been claimed: " + sourceId);
            }
        }
        Set<String> newClaimedSourceIds =
            compactClaimedPointSources(additions);
        newClaimedSourceIds.addAll(additions);
        return new CollectionState(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            schemaVersion,
            catalogueVersion,
            ruleSetVersion,
            revision + 1,
            Math.addExact(points, amount),
            shards,
            ownedCardIds,
            foilCardIds,
            newClaimedSourceIds,
            noncombatRewardRemainderUnits,
            noncombatXpWatermarks,
            pendingPackReveal);
    }

    public CollectionState withNoncombatXpProcessed(
        String skillKey,
        long totalXp,
        long eligibleXp,
        long rewardUnitsPerXp)
    {
        String resolvedSkillKey = requireText(skillKey, "skillKey");
        if (totalXp < 0 || eligibleXp < 0 || rewardUnitsPerXp < 0)
        {
            throw new IllegalArgumentException(
                "Noncombat XP values cannot be negative.");
        }
        Long previousWatermark =
            noncombatXpWatermarks.get(resolvedSkillKey);
        if (previousWatermark != null && totalXp <= previousWatermark)
        {
            throw new IllegalArgumentException(
                "Noncombat XP watermark must advance.");
        }

        long addedUnits = Math.multiplyExact(
            eligibleXp,
            rewardUnitsPerXp);
        long combinedUnits = Math.addExact(
            noncombatRewardRemainderUnits,
            addedUnits);
        long awardedPoints = combinedUnits / 1_000L;
        long newRemainder = combinedUnits % 1_000L;
        Map<String, Long> newWatermarks =
            new HashMap<>(noncombatXpWatermarks);
        newWatermarks.put(resolvedSkillKey, totalXp);

        return new CollectionState(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            Math.max(schemaVersion, 3),
            catalogueVersion,
            ruleSetVersion,
            revision + 1,
            Math.addExact(points, awardedPoints),
            shards,
            ownedCardIds,
            foilCardIds,
            claimedPointSourceIds,
            newRemainder,
            newWatermarks,
            pendingPackReveal);
    }

    public CollectionState withPackPurchased(
        long price,
        long newShards,
        Set<String> newOwnedCardIds,
        PendingPackReveal reveal,
        int newCatalogueVersion)
    {
        return withPackPurchased(
            price,
            newShards,
            newOwnedCardIds,
            foilCardIds,
            reveal,
            newCatalogueVersion);
    }

    public CollectionState withPackPurchased(
        long price,
        long newShards,
        Set<String> newOwnedCardIds,
        Set<String> newFoilCardIds,
        PendingPackReveal reveal,
        int newCatalogueVersion)
    {
        if (pendingPackReveal != null)
        {
            throw new PendingRevealException();
        }
        if (price <= 0)
        {
            throw new IllegalArgumentException(
                "Pack price must be positive.");
        }
        if (points < price)
        {
            throw new InsufficientPointsException(price, points);
        }
        if (newShards < shards)
        {
            throw new IllegalArgumentException(
                "Pack purchase cannot reduce shards.");
        }
        if (newCatalogueVersion < catalogueVersion)
        {
            throw new IllegalArgumentException(
                "Pack purchase cannot reduce the catalogue version.");
        }
        Objects.requireNonNull(reveal, "reveal");
        return new CollectionState(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            Math.max(schemaVersion, 5),
            newCatalogueVersion,
            ruleSetVersion,
            revision + 1,
            points - price,
            newShards,
            newOwnedCardIds,
            newFoilCardIds,
            claimedPointSourceIds,
            noncombatRewardRemainderUnits,
            noncombatXpWatermarks,
            reveal);
    }


    public CollectionState withMilestonePackRedeemed(
        Set<String> newOwnedCardIds,
        Set<String> newFoilCardIds,
        long newShards,
        PendingPackReveal reveal,
        int newCatalogueVersion,
        String redeemedMarker)
    {
        if (pendingPackReveal != null)
        {
            throw new PendingRevealException();
        }
        Objects.requireNonNull(reveal, "reveal");
        String marker = requireText(redeemedMarker, "redeemedMarker");
        if (claimedPointSourceIds.contains(marker))
        {
            throw new IllegalStateException(
                "This one-time milestone pack has already been redeemed.");
        }
        if (newShards < shards)
        {
            throw new IllegalArgumentException(
                "Milestone pack redemption cannot reduce shards.");
        }
        if (newCatalogueVersion < catalogueVersion)
        {
            throw new IllegalArgumentException(
                "Milestone redemption cannot reduce the catalogue version.");
        }
        Set<String> newMarkers = new HashSet<>(claimedPointSourceIds);
        newMarkers.add(marker);
        return new CollectionState(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            Math.max(schemaVersion, 5),
            newCatalogueVersion,
            ruleSetVersion,
            revision + 1,
            points,
            newShards,
            newOwnedCardIds,
            newFoilCardIds,
            newMarkers,
            noncombatRewardRemainderUnits,
            noncombatXpWatermarks,
            reveal);
    }

    public CollectionState withNexusCachePurchased(
        long price,
        long awardedShards)
    {
        if (pendingPackReveal != null)
        {
            throw new PendingRevealException();
        }
        if (price <= 0 || awardedShards <= 0)
        {
            throw new IllegalArgumentException(
                "Nexus Cache price and shard award must be positive.");
        }
        if (points < price)
        {
            throw new InsufficientPointsException(price, points);
        }
        return new CollectionState(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            schemaVersion,
            catalogueVersion,
            ruleSetVersion,
            revision + 1,
            points - price,
            Math.addExact(shards, awardedShards),
            ownedCardIds,
            foilCardIds,
            claimedPointSourceIds,
            noncombatRewardRemainderUnits,
            noncombatXpWatermarks,
            pendingPackReveal);
    }

    public CollectionState withStarterPackRedeemed(
        Set<String> newOwnedCardIds,
        PendingPackReveal reveal,
        int newCatalogueVersion,
        String choiceMarker,
        String redeemedMarker)
    {
        return withStarterPackRedeemed(
            newOwnedCardIds,
            foilCardIds,
            reveal,
            newCatalogueVersion,
            choiceMarker,
            redeemedMarker);
    }

    public CollectionState withStarterPackRedeemed(
        Set<String> newOwnedCardIds,
        Set<String> newFoilCardIds,
        PendingPackReveal reveal,
        int newCatalogueVersion,
        String choiceMarker,
        String redeemedMarker)
    {
        if (pendingPackReveal != null)
        {
            throw new PendingRevealException();
        }
        Objects.requireNonNull(reveal, "reveal");
        String requiredChoice = requireText(choiceMarker, "choiceMarker");
        String marker = requireText(redeemedMarker, "redeemedMarker");
        if (!claimedPointSourceIds.contains(requiredChoice))
        {
            throw new IllegalStateException(
                "This collection did not choose the starter pack reward.");
        }
        if (claimedPointSourceIds.contains(marker))
        {
            throw new IllegalStateException(
                "The one-time starter pack has already been redeemed.");
        }
        if (newCatalogueVersion < catalogueVersion)
        {
            throw new IllegalArgumentException(
                "Starter redemption cannot reduce the catalogue version.");
        }
        Set<String> newMarkers = new HashSet<>(claimedPointSourceIds);
        newMarkers.add(marker);
        return new CollectionState(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            Math.max(schemaVersion, 5),
            newCatalogueVersion,
            ruleSetVersion,
            revision + 1,
            points,
            shards,
            newOwnedCardIds,
            newFoilCardIds,
            newMarkers,
            noncombatRewardRemainderUnits,
            noncombatXpWatermarks,
            reveal);
    }

    public CollectionState withMarkersAdded(Set<String> markers)
    {
        Objects.requireNonNull(markers, "markers");
        if (markers.isEmpty())
        {
            throw new IllegalArgumentException(
                "At least one persistent marker is required.");
        }
        Set<String> nextMarkers = new HashSet<>(claimedPointSourceIds);
        for (String marker : markers)
        {
            String value = requireText(marker, "marker");
            if (!nextMarkers.add(value))
            {
                throw new IllegalArgumentException(
                    "The persistent marker already exists: " + value);
            }
        }
        return new CollectionState(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            schemaVersion,
            catalogueVersion,
            ruleSetVersion,
            revision + 1,
            points,
            shards,
            ownedCardIds,
            foilCardIds,
            nextMarkers,
            noncombatRewardRemainderUnits,
            noncombatXpWatermarks,
            pendingPackReveal);
    }

    public CollectionState withIntegrityDisabled(String forfeitedMarker)
    {
        String marker = requireText(forfeitedMarker, "forfeitedMarker");
        Set<String> nextMarkers = new HashSet<>(claimedPointSourceIds);
        nextMarkers.add(marker);
        return new CollectionState(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            IntegrityMode.CASUAL,
            createdAt,
            schemaVersion,
            catalogueVersion,
            ruleSetVersion,
            revision + 1,
            points,
            shards,
            ownedCardIds,
            foilCardIds,
            nextMarkers,
            noncombatRewardRemainderUnits,
            noncombatXpWatermarks,
            pendingPackReveal);
    }

    public CollectionState withPackRevealAdvanced()
    {
        if (pendingPackReveal == null)
        {
            throw new IllegalStateException(
                "There is no pack waiting to be revealed.");
        }
        return withPackCardRevealed(
            pendingPackReveal.getNextUnrevealedPosition());
    }

    public CollectionState withPackCardRevealed(int cardPosition)
    {
        if (pendingPackReveal == null)
        {
            throw new IllegalStateException(
                "There is no pack waiting to be revealed.");
        }
        if (pendingPackReveal.isRevealed(cardPosition))
        {
            throw new IllegalStateException(
                "That pack card has already been revealed.");
        }
        PendingPackReveal next = pendingPackReveal.isFinalCard()
            ? null
            : pendingPackReveal.reveal(cardPosition);
        return new CollectionState(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            Math.max(schemaVersion, 5),
            catalogueVersion,
            ruleSetVersion,
            revision + 1,
            points,
            shards,
            ownedCardIds,
            foilCardIds,
            claimedPointSourceIds,
            noncombatRewardRemainderUnits,
            noncombatXpWatermarks,
            next);
    }

    public CollectionState withTestingBalances(
        long newPoints,
        long newShards)
    {
        if (newPoints < 0 || newShards < 0)
        {
            throw new IllegalArgumentException(
                "Testing balances cannot be negative.");
        }
        if (newPoints == points && newShards == shards)
        {
            return this;
        }
        return new CollectionState(
            collectionId,
            characterKey,
            displayName,
            economyMode,
            integrityMode,
            createdAt,
            schemaVersion,
            catalogueVersion,
            ruleSetVersion,
            revision + 1,
            newPoints,
            newShards,
            ownedCardIds,
            foilCardIds,
            claimedPointSourceIds,
            noncombatRewardRemainderUnits,
            noncombatXpWatermarks,
            pendingPackReveal);
    }

    private Set<String> compactClaimedPointSources(
        Set<String> additions)
    {
        NpcKillSourceWindow window = NpcKillSourceWindow.from(additions);
        if (window == null)
        {
            return new HashSet<>(claimedPointSourceIds);
        }

        Set<String> compacted = new HashSet<>();
        int minimumTick = Math.max(
            0,
            window.latestTick - NPC_KILL_SOURCE_RETENTION_TICKS);
        for (String existing : claimedPointSourceIds)
        {
            NpcKillSource parsed = NpcKillSource.parse(existing);
            if (parsed == null
                || parsed.gameSessionId.equals(window.gameSessionId)
                    && parsed.tick >= minimumTick)
            {
                compacted.add(existing);
            }
        }
        return compacted;
    }

    private static final class NpcKillSourceWindow
    {
        private final String gameSessionId;
        private final int latestTick;

        private NpcKillSourceWindow(String gameSessionId, int latestTick)
        {
            this.gameSessionId = gameSessionId;
            this.latestTick = latestTick;
        }

        private static NpcKillSourceWindow from(Set<String> sourceIds)
        {
            String session = null;
            int latestTick = Integer.MIN_VALUE;
            boolean foundNpc = false;
            for (String sourceId : sourceIds)
            {
                NpcKillSource parsed = NpcKillSource.parse(sourceId);
                if (parsed == null)
                {
                    continue;
                }
                if (session != null
                    && !session.equals(parsed.gameSessionId))
                {
                    // A mixed-session batch should never be produced by the
                    // plugin. Avoid destructive compaction if it is supplied
                    // by an import/test/integration caller.
                    return null;
                }
                session = parsed.gameSessionId;
                latestTick = Math.max(latestTick, parsed.tick);
                foundNpc = true;
            }
            return foundNpc
                ? new NpcKillSourceWindow(session, latestTick)
                : null;
        }
    }

    private static final class NpcKillSource
    {
        private final String gameSessionId;
        private final int tick;

        private NpcKillSource(String gameSessionId, int tick)
        {
            this.gameSessionId = gameSessionId;
            this.tick = tick;
        }

        private static NpcKillSource parse(String sourceId)
        {
            if (sourceId == null
                || !sourceId.startsWith(NPC_KILL_SOURCE_PREFIX))
            {
                return null;
            }
            String[] parts = sourceId.split(":", 7);
            if (parts.length != 7
                || parts[2].isEmpty())
            {
                return null;
            }
            try
            {
                int tick = Integer.parseInt(parts[4]);
                return tick < 0
                    ? null
                    : new NpcKillSource(parts[2], tick);
            }
            catch (NumberFormatException exception)
            {
                return null;
            }
        }
    }

    public UUID getCollectionId()
    {
        return collectionId;
    }

    public String getCharacterKey()
    {
        return characterKey;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public EconomyMode getEconomyMode()
    {
        return economyMode;
    }

    public IntegrityMode getIntegrityMode()
    {
        return integrityMode;
    }

    public Instant getCreatedAt()
    {
        return createdAt;
    }

    public int getSchemaVersion()
    {
        return schemaVersion;
    }

    public int getCatalogueVersion()
    {
        return catalogueVersion;
    }

    public int getRuleSetVersion()
    {
        return ruleSetVersion;
    }

    public long getRevision()
    {
        return revision;
    }

    public long getPoints()
    {
        return points;
    }

    public long getShards()
    {
        return shards;
    }

    public Set<String> getOwnedCardIds()
    {
        return ownedCardIds;
    }

    public Set<String> getFoilCardIds()
    {
        return foilCardIds;
    }

    public Set<String> getClaimedPointSourceIds()
    {
        return claimedPointSourceIds;
    }

    public long getNoncombatRewardRemainderUnits()
    {
        return noncombatRewardRemainderUnits;
    }

    public Map<String, Long> getNoncombatXpWatermarks()
    {
        return noncombatXpWatermarks;
    }

    public Optional<PendingPackReveal> getPendingPackReveal()
    {
        return Optional.ofNullable(pendingPackReveal);
    }

    private static String requireText(String value, String field)
    {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value;
    }

    private static Set<String> immutableCopy(Set<String> source)
    {
        return immutableCopy(source, "cardId");
    }

    private static Set<String> immutableCopy(
        Set<String> source,
        String field)
    {
        Objects.requireNonNull(source, "source");
        if (source instanceof ImmutableStringSet)
        {
            return source;
        }
        Set<String> copy = new HashSet<>();
        for (String value : source)
        {
            copy.add(requireText(value, field));
        }
        return new ImmutableStringSet(copy);
    }

    private static Map<String, Long> immutableWatermarks(
        Map<String, Long> source)
    {
        Objects.requireNonNull(source, "source");
        if (source instanceof ImmutableStringLongMap)
        {
            return source;
        }
        Map<String, Long> copy = new HashMap<>();
        for (Map.Entry<String, Long> entry : source.entrySet())
        {
            String key = requireText(entry.getKey(), "skillKey");
            Long value = Objects.requireNonNull(
                entry.getValue(), "xpWatermark");
            if (value < 0 || copy.put(key, value) != null)
            {
                throw new IllegalArgumentException(
                    "Noncombat XP watermarks are invalid.");
            }
        }
        return new ImmutableStringLongMap(copy);
    }

    /**
     * Marker wrapper used so immutable state collections can be structurally
     * shared by subsequent CollectionState revisions. Public construction
     * still defensively copies arbitrary caller-owned sets.
     */
    private static final class ImmutableStringSet extends AbstractSet<String>
    {
        private final Set<String> values;

        private ImmutableStringSet(Set<String> source)
        {
            this.values = Collections.unmodifiableSet(
                new HashSet<>(Objects.requireNonNull(source, "source")));
        }

        @Override
        public Iterator<String> iterator()
        {
            return values.iterator();
        }

        @Override
        public int size()
        {
            return values.size();
        }

        @Override
        public boolean contains(Object value)
        {
            return values.contains(value);
        }
    }

    /**
     * Immutable watermark map with the same structural-sharing behaviour as
     * ImmutableStringSet.
     */
    private static final class ImmutableStringLongMap
        extends AbstractMap<String, Long>
    {
        private final Map<String, Long> values;

        private ImmutableStringLongMap(Map<String, Long> source)
        {
            this.values = Collections.unmodifiableMap(
                new HashMap<>(Objects.requireNonNull(source, "source")));
        }

        @Override
        public Set<Entry<String, Long>> entrySet()
        {
            return values.entrySet();
        }

        @Override
        public Long get(Object key)
        {
            return values.get(key);
        }

        @Override
        public boolean containsKey(Object key)
        {
            return values.containsKey(key);
        }

        @Override
        public int size()
        {
            return values.size();
        }
    }
}
