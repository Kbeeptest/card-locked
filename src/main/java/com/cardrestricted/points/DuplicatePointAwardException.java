package com.cardrestricted.points;

public final class DuplicatePointAwardException extends IllegalStateException
{
    private static final long serialVersionUID = 1L;

    public DuplicatePointAwardException(String sourceId)
    {
        super("Point source has already been awarded: " + sourceId);
    }
}
