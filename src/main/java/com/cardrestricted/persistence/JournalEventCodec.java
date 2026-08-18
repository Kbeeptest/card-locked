package com.cardrestricted.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

public final class JournalEventCodec
{
    private static final int MAGIC = 0x43524531;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_EVENT_BYTES = 1024 * 1024;

    public StateJournalEvent create(
        CollectionState state,
        long previousRevision,
        JournalEventType type,
        String payload,
        Instant occurredAt,
        String previousEventHash,
        byte[] encodedState)
        throws IOException
    {
        String stateHash = sha256Hex(encodedState);
        StateJournalEvent unhashed = new StateJournalEvent(
            UUID.randomUUID(),
            state.getCollectionId(),
            state.getCharacterKey(),
            previousRevision,
            state.getRevision(),
            type,
            payload,
            occurredAt,
            previousEventHash,
            stateHash,
            "pending");
        String eventHash = sha256Hex(encodeHashMaterial(unhashed));
        return new StateJournalEvent(
            unhashed.getEventId(),
            unhashed.getCollectionId(),
            unhashed.getCharacterKey(),
            unhashed.getPreviousRevision(),
            unhashed.getRevision(),
            unhashed.getType(),
            unhashed.getPayload(),
            unhashed.getOccurredAt(),
            unhashed.getPreviousEventHash(),
            unhashed.getStateHash(),
            eventHash);
    }

    public byte[] encode(StateJournalEvent event) throws IOException
    {
        byte[] hashMaterial = encodeHashMaterial(event);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes))
        {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(hashMaterial.length);
            output.write(hashMaterial);
            output.writeUTF(event.getEventHash());
        }
        return bytes.toByteArray();
    }

    public StateJournalEvent decode(byte[] encoded) throws IOException
    {
        try (DataInputStream input =
                 new DataInputStream(new ByteArrayInputStream(encoded)))
        {
            if (input.readInt() != MAGIC)
            {
                throw new CorruptSnapshotException(
                    "Journal event magic is invalid.");
            }
            if (input.readInt() != FORMAT_VERSION)
            {
                throw new CorruptSnapshotException(
                    "Journal event format is unsupported.");
            }
            int materialLength = input.readInt();
            if (materialLength < 0 || materialLength > MAX_EVENT_BYTES)
            {
                throw new CorruptSnapshotException(
                    "Journal event length is invalid.");
            }
            byte[] hashMaterial = new byte[materialLength];
            input.readFully(hashMaterial);
            String eventHash = input.readUTF();
            if (input.available() != 0)
            {
                throw new CorruptSnapshotException(
                    "Journal event contains trailing data.");
            }
            String actualHash = sha256Hex(hashMaterial);
            if (!MessageDigest.isEqual(
                eventHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actualHash.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            {
                throw new CorruptSnapshotException(
                    "Journal event hash does not match.");
            }
            return decodeHashMaterial(hashMaterial, eventHash);
        }
        catch (EOFException exception)
        {
            throw new CorruptSnapshotException(
                "Journal event is truncated.", exception);
        }
        catch (IllegalArgumentException exception)
        {
            throw new CorruptSnapshotException(
                "Journal event is invalid.", exception);
        }
    }

    public String sha256Hex(byte[] bytes)
    {
        byte[] digest;
        try
        {
            digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException(
                "SHA-256 is unavailable.", exception);
        }
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest)
        {
            hex.append(String.format("%02x", value & 0xff));
        }
        return hex.toString();
    }

    private byte[] encodeHashMaterial(StateJournalEvent event)
        throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes))
        {
            output.writeLong(event.getEventId().getMostSignificantBits());
            output.writeLong(event.getEventId().getLeastSignificantBits());
            output.writeLong(
                event.getCollectionId().getMostSignificantBits());
            output.writeLong(
                event.getCollectionId().getLeastSignificantBits());
            output.writeUTF(event.getCharacterKey());
            output.writeLong(event.getPreviousRevision());
            output.writeLong(event.getRevision());
            output.writeUTF(event.getType().name());
            output.writeUTF(event.getPayload());
            output.writeLong(event.getOccurredAt().toEpochMilli());
            output.writeUTF(event.getPreviousEventHash());
            output.writeUTF(event.getStateHash());
        }
        return bytes.toByteArray();
    }

    private StateJournalEvent decodeHashMaterial(
        byte[] material,
        String eventHash)
        throws IOException
    {
        try (DataInputStream input =
                 new DataInputStream(new ByteArrayInputStream(material)))
        {
            StateJournalEvent event = new StateJournalEvent(
                new UUID(input.readLong(), input.readLong()),
                new UUID(input.readLong(), input.readLong()),
                input.readUTF(),
                input.readLong(),
                input.readLong(),
                JournalEventType.valueOf(input.readUTF()),
                input.readUTF(),
                Instant.ofEpochMilli(input.readLong()),
                input.readUTF(),
                input.readUTF(),
                eventHash);
            if (input.available() != 0)
            {
                throw new CorruptSnapshotException(
                    "Journal hash material contains trailing data.");
            }
            return event;
        }
    }
}
