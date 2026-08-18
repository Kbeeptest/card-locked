package com.cardrestricted.catalog;

public final class CatalogueValidationException extends IllegalArgumentException
{
    private static final long serialVersionUID = 1L;

    public CatalogueValidationException(String message)
    {
        super(message);
    }
}
