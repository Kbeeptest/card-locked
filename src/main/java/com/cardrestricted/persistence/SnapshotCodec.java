package com.cardrestricted.persistence;

import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.pack.PendingPackReveal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

public final class SnapshotCodec
{
    private static final int MAGIC = 0x43524131;
    private static final int FORMAT_VERSION = 6;
    private static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;

    private Set<String> cachedOwnedSource;
    private List<String> cachedOwnedSorted = List.of();
    private Set<String> cachedFoilSource;
    private List<String> cachedFoilSorted = List.of();
    private Map<String, Long> cachedWatermarkSource;
    private List<String> cachedWatermarkKeys = List.of();

    public byte[] encode(CollectionState state) throws IOException
    {
        byte[] payload = encodePayload(state);
        byte[] checksum = sha256(payload);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes))
        {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(payload.length);
            output.write(payload);
            output.writeInt(checksum.length);
            output.write(checksum);
        }
        return bytes.toByteArray();
    }

    public CollectionState decode(byte[] encoded) throws IOException
    {
        try (DataInputStream input =
                 new DataInputStream(new ByteArrayInputStream(encoded)))
        {
            if (input.readInt() != MAGIC)
            {
                throw new CorruptSnapshotException("Snapshot magic is invalid.");
            }
            int formatVersion = input.readInt();
            if (formatVersion < 1 || formatVersion > FORMAT_VERSION)
            {
                throw new CorruptSnapshotException(
                    "Snapshot format version is unsupported.");
            }

            int payloadLength = input.readInt();
            if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES)
            {
                throw new CorruptSnapshotException(
                    "Snapshot payload length is invalid.");
            }
            byte[] payload = new byte[payloadLength];
            input.readFully(payload);

            int checksumLength = input.readInt();
            if (checksumLength != 32)
            {
                throw new CorruptSnapshotException(
                    "Snapshot checksum length is invalid.");
            }
            byte[] expectedChecksum = new byte[checksumLength];
            input.readFully(expectedChecksum);

            if (input.available() != 0)
            {
                throw new CorruptSnapshotException(
                    "Snapshot contains trailing data.");
            }
            if (!MessageDigest.isEqual(expectedChecksum, sha256(payload)))
            {
                throw new CorruptSnapshotException(
                    "Snapshot checksum does not match.");
            }
            return decodePayload(payload, formatVersion);
        }
        catch (CorruptSnapshotException exception)
        {
            throw exception;
        }
        catch (EOFException exception)
        {
            throw new CorruptSnapshotException("Snapshot is truncated.", exception);
        }
        catch (IllegalArgumentException exception)
        {
            throw new CorruptSnapshotException(
                "Snapshot state is invalid.", exception);
        }
        catch (IOException exception)
        {
            throw new CorruptSnapshotException(
                "Snapshot encoding is invalid.", exception);
        }
    }

    private byte[] encodePayload(CollectionState state) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes))
        {
            output.writeLong(state.getCollectionId().getMostSignificantBits());
            output.writeLong(state.getCollectionId().getLeastSignificantBits());
            output.writeUTF(state.getCharacterKey());
            output.writeUTF(state.getDisplayName());
            output.writeUTF(state.getEconomyMode().name());
            output.writeUTF(state.getIntegrityMode().name());
            output.writeLong(state.getCreatedAt().toEpochMilli());
            output.writeInt(state.getSchemaVersion());
            output.writeInt(state.getCatalogueVersion());
            output.writeInt(state.getRuleSetVersion());
            output.writeLong(state.getRevision());
            output.writeLong(state.getPoints());
            output.writeLong(state.getShards());
            writeStrings(output, sortedOwnedCardIds(state.getOwnedCardIds()));
            writeStrings(output, sortedFoilCardIds(state.getFoilCardIds()));
            writeSortedStrings(output, state.getClaimedPointSourceIds());
            output.writeLong(state.getNoncombatRewardRemainderUnits());
            writeWatermarks(
                output,
                state.getNoncombatXpWatermarks(),
                sortedWatermarkKeys(state.getNoncombatXpWatermarks()));
            writePendingPackReveal(
                output, state.getPendingPackReveal().orElse(null));
        }
        return bytes.toByteArray();
    }

    private CollectionState decodePayload(byte[] payload, int formatVersion)
        throws IOException
    {
        try (DataInputStream input =
                 new DataInputStream(new ByteArrayInputStream(payload)))
        {
            UUID collectionId = new UUID(input.readLong(), input.readLong());
            String characterKey = input.readUTF();
            String displayName = input.readUTF();
            EconomyMode economyMode = EconomyMode.valueOf(input.readUTF());
            IntegrityMode integrityMode = IntegrityMode.valueOf(input.readUTF());
            Instant createdAt = Instant.ofEpochMilli(input.readLong());
            int schemaVersion = input.readInt();
            int catalogueVersion = input.readInt();
            int ruleSetVersion = input.readInt();
            long revision = input.readLong();
            long points = input.readLong();
            long shards = input.readLong();
            Set<String> ownedCardIds = readStrings(input);
            Set<String> foilCardIds = readStrings(input);
            Set<String> claimedPointSourceIds = formatVersion >= 2
                ? readStrings(input)
                : Set.of();
            long noncombatRewardRemainderUnits = formatVersion >= 3
                ? input.readLong()
                : 0;
            Map<String, Long> noncombatXpWatermarks = formatVersion >= 3
                ? readWatermarks(input)
                : Map.of();
            PendingPackReveal pendingPackReveal = formatVersion >= 4
                ? readPendingPackReveal(input, formatVersion)
                : null;

            if (input.available() != 0)
            {
                throw new CorruptSnapshotException(
                    "Snapshot payload contains trailing data.");
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
                revision,
                points,
                shards,
                ownedCardIds,
                foilCardIds,
                claimedPointSourceIds,
                noncombatRewardRemainderUnits,
                noncombatXpWatermarks,
                pendingPackReveal);
        }
    }

    private void writeSortedStrings(DataOutputStream output, Set<String> values)
        throws IOException
    {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(String::compareTo);
        writeStrings(output, sorted);
    }

    private void writeStrings(DataOutputStream output, List<String> sorted)
        throws IOException
    {
        output.writeInt(sorted.size());
        for (String value : sorted)
        {
            output.writeUTF(value);
        }
    }

    private synchronized List<String> sortedOwnedCardIds(Set<String> values)
    {
        if (values != cachedOwnedSource)
        {
            cachedOwnedSource = values;
            cachedOwnedSorted = sortedCopy(values);
        }
        return cachedOwnedSorted;
    }

    private synchronized List<String> sortedFoilCardIds(Set<String> values)
    {
        if (values != cachedFoilSource)
        {
            cachedFoilSource = values;
            cachedFoilSorted = sortedCopy(values);
        }
        return cachedFoilSorted;
    }

    private synchronized List<String> sortedWatermarkKeys(
        Map<String, Long> watermarks)
    {
        if (watermarks != cachedWatermarkSource)
        {
            cachedWatermarkSource = watermarks;
            cachedWatermarkKeys = sortedCopy(watermarks.keySet());
        }
        return cachedWatermarkKeys;
    }

    private static List<String> sortedCopy(Set<String> values)
    {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(String::compareTo);
        return List.copyOf(sorted);
    }

    private Set<String> readStrings(DataInputStream input) throws IOException
    {
        int size = input.readInt();
        if (size < 0 || size > 1_000_000)
        {
            throw new CorruptSnapshotException(
                "Snapshot collection size is invalid.");
        }
        Set<String> values = new HashSet<>();
        for (int index = 0; index < size; index++)
        {
            String value = input.readUTF();
            if (!values.add(value))
            {
                throw new CorruptSnapshotException(
                    "Snapshot contains a duplicate card ID.");
            }
        }
        return values;
    }

    private void writeWatermarks(
        DataOutputStream output,
        Map<String, Long> watermarks,
        List<String> keys)
        throws IOException
    {
        output.writeInt(keys.size());
        for (String key : keys)
        {
            output.writeUTF(key);
            output.writeLong(watermarks.get(key));
        }
    }

    private Map<String, Long> readWatermarks(DataInputStream input)
        throws IOException
    {
        int size = input.readInt();
        if (size < 0 || size > 1_000)
        {
            throw new CorruptSnapshotException(
                "Snapshot XP watermark size is invalid.");
        }
        Map<String, Long> watermarks = new HashMap<>();
        for (int index = 0; index < size; index++)
        {
            String key = input.readUTF();
            long value = input.readLong();
            if (value < 0 || watermarks.put(key, value) != null)
            {
                throw new CorruptSnapshotException(
                    "Snapshot XP watermarks are invalid.");
            }
        }
        return watermarks;
    }

    private void writePendingPackReveal(
        DataOutputStream output,
        PendingPackReveal reveal)
        throws IOException
    {
        output.writeBoolean(reveal != null);
        if (reveal == null)
        {
            return;
        }
        output.writeLong(
            reveal.getOpeningId().getMostSignificantBits());
        output.writeLong(
            reveal.getOpeningId().getLeastSignificantBits());
        output.writeUTF(reveal.getPackId());
        output.writeLong(reveal.getPurchasedAt().toEpochMilli());
        output.writeInt(reveal.getCardResults().size());
        List<Integer> revealedPositions = new ArrayList<>(
            reveal.getRevealedPositions());
        revealedPositions.sort(Integer::compareTo);
        output.writeInt(revealedPositions.size());
        for (int position : revealedPositions)
        {
            output.writeInt(position);
        }
        for (PackCardResult result : reveal.getCardResults())
        {
            output.writeUTF(result.getCardId());
            output.writeBoolean(result.isDuplicate());
            output.writeLong(result.getShardsAwarded());
            output.writeBoolean(result.isFoil());
        }
    }

    private PendingPackReveal readPendingPackReveal(
        DataInputStream input,
        int formatVersion)
        throws IOException
    {
        if (!input.readBoolean())
        {
            return null;
        }
        UUID openingId = new UUID(input.readLong(), input.readLong());
        String packId = input.readUTF();
        Instant purchasedAt = Instant.ofEpochMilli(input.readLong());
        int resultCount = input.readInt();
        if (resultCount < 1 || resultCount > 100)
        {
            throw new CorruptSnapshotException(
                "Snapshot pack result count is invalid.");
        }

        Set<Integer> revealedPositions = new HashSet<>();
        if (formatVersion >= 5)
        {
            int revealedPositionCount = input.readInt();
            if (revealedPositionCount < 0
                || revealedPositionCount >= resultCount)
            {
                throw new CorruptSnapshotException(
                    "Snapshot revealed position count is invalid.");
            }
            for (int index = 0;
                 index < revealedPositionCount;
                 index++)
            {
                int position = input.readInt();
                if (position < 0
                    || position >= resultCount
                    || !revealedPositions.add(position))
                {
                    throw new CorruptSnapshotException(
                        "Snapshot revealed positions are invalid.");
                }
            }
        }
        else
        {
            int revealedCount = input.readInt();
            if (revealedCount < 0 || revealedCount >= resultCount)
            {
                throw new CorruptSnapshotException(
                    "Snapshot reveal count is invalid.");
            }
            for (int position = 0;
                 position < revealedCount;
                 position++)
            {
                revealedPositions.add(position);
            }
        }

        List<PackCardResult> results = new ArrayList<>();
        for (int index = 0; index < resultCount; index++)
        {
            String cardId = input.readUTF();
            boolean duplicate = input.readBoolean();
            long shardsAwarded = input.readLong();
            boolean foil = formatVersion >= 6 && input.readBoolean();
            results.add(new PackCardResult(
                cardId,
                duplicate,
                shardsAwarded,
                foil));
        }
        return new PendingPackReveal(
            openingId,
            packId,
            purchasedAt,
            results,
            revealedPositions);
    }

    private byte[] sha256(byte[] bytes)
    {
        try
        {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
