package com.cardrestricted.persistence;

import java.io.IOException;

public final class CorruptSnapshotException extends IOException
{
    private static final long serialVersionUID = 1L;

    public CorruptSnapshotException(String message)
    {
        super(message);
    }

    public CorruptSnapshotException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
