package com.cardrestricted.persistence;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.pack.PendingPackReveal;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SnapshotCodecResilienceTest
{
    @Test
    public void completeStateRoundTripsWithoutLosingPackProgress()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        List<String> cardIds = firstCardIds(catalogue, 3);
        PendingPackReveal pending = new PendingPackReveal(
            UUID.randomUUID(),
            "pack.persistence.test",
            Instant.parse("2026-08-03T16:00:00Z"),
            Arrays.asList(
                new PackCardResult(cardIds.get(0), false, 0L, true),
                new PackCardResult(cardIds.get(1), true, 25L, false),
                new PackCardResult(cardIds.get(2), false, 0L, false)),
            new LinkedHashSet<>(Arrays.asList(2, 0)));
        CollectionState state = new CollectionState(
            UUID.randomUUID(),
            "snapshot-roundtrip",
            "Snapshot Roundtrip",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-08-03T15:55:00Z"),
            5,
            catalogue.getCatalogueVersion(),
            3,
            42L,
            123_456L,
            789L,
            Set.copyOf(cardIds),
            Set.of(cardIds.get(0)),
            Set.of("starter.points.choice", "quest.reward.test"),
            999L,
            Map.of("agility", 12345L, "woodcutting", 67890L),
            pending);

        CollectionState decoded = new SnapshotCodec().decode(
            new SnapshotCodec().encode(state));
        assertEquals(state.getCollectionId(), decoded.getCollectionId());
        assertEquals(state.getCharacterKey(), decoded.getCharacterKey());
        assertEquals(state.getRevision(), decoded.getRevision());
        assertEquals(state.getPoints(), decoded.getPoints());
        assertEquals(state.getShards(), decoded.getShards());
        assertEquals(state.getOwnedCardIds(), decoded.getOwnedCardIds());
        assertEquals(state.getFoilCardIds(), decoded.getFoilCardIds());
        assertEquals(state.getClaimedPointSourceIds(),
            decoded.getClaimedPointSourceIds());
        assertEquals(state.getNoncombatXpWatermarks(),
            decoded.getNoncombatXpWatermarks());
        PendingPackReveal decodedPending = decoded.getPendingPackReveal()
            .orElseThrow(AssertionError::new);
        assertEquals(pending.getOpeningId(), decodedPending.getOpeningId());
        assertEquals(Set.of(0, 2), decodedPending.getRevealedPositions());
        assertTrue(decodedPending.getCardAt(0).isFoil());
        assertEquals(25L, decodedPending.getCardAt(1).getShardsAwarded());
    }

    @Test
    public void logicalRevealOrderProducesDeterministicSnapshotBytes()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        List<String> cardIds = firstCardIds(catalogue, 3);
        UUID collectionId = UUID.randomUUID();
        UUID openingId = UUID.randomUUID();
        List<PackCardResult> results = Arrays.asList(
            new PackCardResult(cardIds.get(0), false, 0L),
            new PackCardResult(cardIds.get(1), false, 0L),
            new PackCardResult(cardIds.get(2), false, 0L));
        PendingPackReveal first = new PendingPackReveal(
            openingId,
            "pack.deterministic",
            Instant.parse("2026-08-03T16:10:00Z"),
            results,
            new LinkedHashSet<>(Arrays.asList(2, 0)));
        PendingPackReveal second = new PendingPackReveal(
            openingId,
            "pack.deterministic",
            Instant.parse("2026-08-03T16:10:00Z"),
            results,
            new LinkedHashSet<>(Arrays.asList(0, 2)));

        CollectionState stateA = stateWithPending(
            catalogue, collectionId, cardIds, first);
        CollectionState stateB = stateWithPending(
            catalogue, collectionId, cardIds, second);
        SnapshotCodec codec = new SnapshotCodec();
        assertArrayEquals(codec.encode(stateA), codec.encode(stateB));
    }

    @Test
    public void sortedIdentityCachesPreserveExactSnapshotBytes()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        List<String> cardIds = firstCardIds(catalogue, 20);
        CollectionState initial = new CollectionState(
            UUID.randomUUID(),
            "snapshot-cache",
            "Snapshot Cache",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-08-07T16:00:00Z"),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            10_000L,
            0L,
            new HashSet<>(cardIds.subList(0, 15)),
            new HashSet<>(cardIds.subList(0, 4)),
            Set.of("profile.integrity.eligible"),
            0L,
            Map.of("agility", 1000L),
            null);
        SnapshotCodec warmed = new SnapshotCodec();
        warmed.encode(initial);

        CollectionState pointOnly = initial.withPointsAwarded(
            "clue-completion:v1:easy:1", 25L);
        assertArrayEquals(
            new SnapshotCodec().encode(pointOnly),
            warmed.encode(pointOnly));

        Set<String> expanded = new HashSet<>(pointOnly.getOwnedCardIds());
        expanded.add(cardIds.get(15));
        CollectionState ownershipChanged = pointOnly.withProgress(
            pointOnly.getRevision() + 1L,
            pointOnly.getPoints(),
            pointOnly.getShards(),
            expanded,
            pointOnly.getFoilCardIds());
        assertArrayEquals(
            new SnapshotCodec().encode(ownershipChanged),
            warmed.encode(ownershipChanged));
    }

    @Test
    public void truncationChecksumDamageAndTrailingBytesAreRejected()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        SnapshotCodec codec = new SnapshotCodec();
        byte[] valid = codec.encode(PersistenceTestFixtures.state(
            catalogue,
            "snapshot-corruption",
            100L));

        expectCorrupt(codec, Arrays.copyOf(valid, valid.length - 1));

        byte[] damaged = valid.clone();
        damaged[20] ^= 0x55;
        expectCorrupt(codec, damaged);

        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        trailing[trailing.length - 1] = 7;
        expectCorrupt(codec, trailing);
    }

    @Test
    public void malformedModifiedUtfIsReportedAsCorruptSnapshot()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        SnapshotCodec codec = new SnapshotCodec();
        byte[] encoded = codec.encode(PersistenceTestFixtures.state(
            catalogue,
            "utf-test",
            100L));
        ByteBuffer outer = ByteBuffer.wrap(encoded);
        outer.getInt();
        outer.getInt();
        int payloadLength = outer.getInt();
        int payloadStart = outer.position();
        byte[] payload = Arrays.copyOfRange(
            encoded,
            payloadStart,
            payloadStart + payloadLength);

        // The first UTF field starts after the two UUID longs.
        payload[16] = 0;
        payload[17] = 1;
        payload[18] = (byte) 0xC0;
        System.arraycopy(payload, 0, encoded, payloadStart, payloadLength);
        byte[] checksum = MessageDigest.getInstance("SHA-256")
            .digest(payload);
        int checksumStart = payloadStart + payloadLength + Integer.BYTES;
        System.arraycopy(checksum, 0, encoded, checksumStart, checksum.length);

        expectCorrupt(codec, encoded);
    }

    @Test
    public void oversizedPayloadHeaderIsRejectedBeforeAllocation()
        throws Exception
    {
        ByteBuffer bytes = ByteBuffer.allocate(12);
        bytes.putInt(0x43524131);
        bytes.putInt(6);
        bytes.putInt(16 * 1024 * 1024 + 1);
        expectCorrupt(new SnapshotCodec(), bytes.array());
    }

    private CollectionState stateWithPending(
        CardCatalogue catalogue,
        UUID collectionId,
        List<String> cardIds,
        PendingPackReveal pending)
    {
        return new CollectionState(
            collectionId,
            "deterministic-snapshot",
            "Deterministic Snapshot",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-08-03T16:05:00Z"),
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            10_000L,
            0L,
            Set.copyOf(cardIds),
            Set.of(),
            Set.of("starter.points.choice"),
            0L,
            Map.of(),
            pending);
    }

    private List<String> firstCardIds(
        CardCatalogue catalogue,
        int count)
    {
        List<String> ids = new ArrayList<>();
        for (CardDefinition card : catalogue.getCards())
        {
            ids.add(card.getCardId());
            if (ids.size() == count)
            {
                break;
            }
        }
        return ids;
    }

    private void expectCorrupt(SnapshotCodec codec, byte[] encoded)
        throws Exception
    {
        boolean rejected = false;
        try
        {
            codec.decode(encoded);
        }
        catch (CorruptSnapshotException expected)
        {
            rejected = true;
        }
        assertTrue("Expected corrupt snapshot rejection.", rejected);
    }
}
