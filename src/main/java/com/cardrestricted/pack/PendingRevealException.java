package com.cardrestricted.pack;

public final class PendingRevealException extends IllegalStateException
{
    private static final long serialVersionUID = 1L;

    public PendingRevealException()
    {
        super("Finish revealing the current pack before purchasing another.");
    }
}
