package com.cardrestricted.collection;

import java.io.IOException;

public final class CollectionAlreadyExistsException extends IOException
{
    private static final long serialVersionUID = 1L;

    public CollectionAlreadyExistsException()
    {
        super("A collection already exists for this local save.");
    }
}
