package com.cardrestricted.persistence;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Resolves an uncertain save result after an I/O failure. A failure occurring
 * after the journal event became durable must not be surfaced as an apparent
 * failure and then repeated by the caller. The expected state is accepted only
 * when a fresh recovery produces byte-for-byte equivalent snapshot content.
 */
public final class CommittedStateRecovery
{
    private CommittedStateRecovery()
    {
    }

    public static CollectionState recoverIfCommitted(
        TransactionalStateStore store,
        CollectionState expected,
        IOException originalFailure)
        throws IOException
    {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(originalFailure, "originalFailure");
        try
        {
            CollectionState recovered = store.loadHighestValid()
                .orElse(null);
            if (recovered != null && equivalent(expected, recovered))
            {
                return recovered;
            }
        }
        catch (IOException | RuntimeException recoveryFailure)
        {
            originalFailure.addSuppressed(recoveryFailure);
        }
        throw originalFailure;
    }

    public static boolean equivalent(
        CollectionState expected,
        CollectionState actual)
        throws IOException
    {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        SnapshotCodec codec = new SnapshotCodec();
        return Arrays.equals(codec.encode(expected), codec.encode(actual));
    }
}
