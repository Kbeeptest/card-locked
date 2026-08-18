package com.cardrestricted.pack;

public final class InsufficientPointsException extends IllegalStateException
{
    private static final long serialVersionUID = 1L;

    public InsufficientPointsException(long required, long available)
    {
        this("Standard Pack", required, available);
    }

    public InsufficientPointsException(
        String packName,
        long required,
        long available)
    {
        super(packName + " requires " + required
            + " points, but only " + available + " are available.");
    }
}
