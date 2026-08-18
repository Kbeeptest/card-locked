package com.cardrestricted.persistence;

import java.io.IOException;

public final class RevisionConflictException extends IOException
{
    private static final long serialVersionUID = 1L;

    public RevisionConflictException(long expected, long actual)
    {
        super("Expected revision " + expected + " but found " + actual + ".");
    }
}
