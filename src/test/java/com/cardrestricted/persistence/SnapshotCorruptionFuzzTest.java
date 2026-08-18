package com.cardrestricted.persistence;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Random byte-level corruption and truncation checks for snapshot recovery. */
public final class SnapshotCorruptionFuzzTest
{
    @Test
    public void everySingleBitMutationIsRejectedByEnvelopeOrChecksum()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        SnapshotCodec codec = new SnapshotCodec();
        CollectionState state = PersistenceTestFixtures.state(
            catalogue,
            "snapshot-corruption-fuzz",
            987_654_321L);
        byte[] encoded = codec.encode(state);
        Random random = new Random(0x8925A5L);

        for (int iteration = 0; iteration < 2_000; iteration++)
        {
            byte[] mutated = encoded.clone();
            int index = random.nextInt(mutated.length);
            mutated[index] ^= (byte) (1 << random.nextInt(8));
            assertRejected(codec, mutated, "mutation " + iteration);
        }

        // The original remains deterministic and decodes exactly.
        byte[] reencoded = codec.encode(codec.decode(encoded));
        assertArrayEquals(encoded, reencoded);
    }

    @Test
    public void everyTruncationBoundaryIsRejected()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        SnapshotCodec codec = new SnapshotCodec();
        byte[] encoded = codec.encode(PersistenceTestFixtures.state(
            catalogue,
            "snapshot-truncation-fuzz",
            123L));
        for (int length = 0; length < encoded.length; length++)
        {
            assertRejected(codec, Arrays.copyOf(encoded, length),
                "truncation " + length);
        }
        assertEquals(123L, codec.decode(encoded).getPoints());
    }

    @Test
    public void trailingDataIsRejectedEvenWhenPayloadChecksumIsValid()
        throws Exception
    {
        CardCatalogue catalogue = MembersCatalogue.create();
        SnapshotCodec codec = new SnapshotCodec();
        byte[] encoded = codec.encode(PersistenceTestFixtures.state(
            catalogue,
            "snapshot-trailing-data",
            456L));
        byte[] extended = Arrays.copyOf(encoded, encoded.length + 16);
        assertRejected(codec, extended, "trailing data");
    }

    private static void assertRejected(
        SnapshotCodec codec,
        byte[] bytes,
        String context)
        throws Exception
    {
        boolean rejected = false;
        try
        {
            codec.decode(bytes);
        }
        catch (IOException | IllegalArgumentException expected)
        {
            rejected = true;
        }
        assertTrue(context, rejected);
    }
}
