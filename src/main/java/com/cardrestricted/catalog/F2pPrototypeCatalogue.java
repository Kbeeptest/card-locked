package com.cardrestricted.catalog;

public final class F2pPrototypeCatalogue
{
    public static final int VERSION = 10;
    public static final String GOBLIN_FAMILY_ID =
        "npc-family.goblin";
    public static final String NORMAL_TREE_FAMILY_ID =
        "object-family.tree.normal";
    public static final String COPPER_ROCK_FAMILY_ID =
        "object-family.rock.copper";
    public static final String LUMBRIDGE_SWAMP_FISHING_FAMILY_ID =
        "npc-family.fishing-spot.lumbridge-swamp";
    public static final String LUMBRIDGE_CASTLE_RANGE_FAMILY_ID =
        "object-family.range.lumbridge-castle";
    public static final String RESOURCE_ROOT =
        "com/cardrestricted/catalog/f2p";

    private F2pPrototypeCatalogue()
    {
    }

    public static CardCatalogue create()
    {
        CardCatalogue catalogue = new CatalogueDataLoader().load(
            F2pPrototypeCatalogue.class.getClassLoader(),
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
