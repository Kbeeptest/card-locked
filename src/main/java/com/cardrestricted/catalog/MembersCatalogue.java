package com.cardrestricted.catalog;

/**
 * Members-first catalogue entry point. Version 21 retains the collection-balanced rarity model and non-combat service-tier classification while preserving foil safety
 * reclassification while preserving aliases, historical retirement metadata,
 * and catalogue-authoritative card identities.
 */
public final class MembersCatalogue
{
    public static final int VERSION = 21;
    public static final String RESOURCE_ROOT =
        "com/cardrestricted/catalog/members";

    private MembersCatalogue()
    {
    }

    public static CardCatalogue create()
    {
        CardCatalogue catalogue = new CatalogueDataLoader().load(
            MembersCatalogue.class.getClassLoader(),
            RESOURCE_ROOT);
        if (catalogue.getCatalogueVersion() != VERSION)
        {
            throw new CatalogueValidationException(
                "Catalogue metadata version "
                    + catalogue.getCatalogueVersion()
                    + " does not match code version " + VERSION + ".");
        }
        return catalogue;
    }
}
