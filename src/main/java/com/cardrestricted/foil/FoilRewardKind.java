package com.cardrestricted.foil;

/** The controlled relationship used by a foil card's gameplay reward. */
public enum FoilRewardKind
{
    /** Unlocks lower cards in the same ordered item-slot or resource chain. */
    TIER_CASCADE("Vertical tier"),

    /** Unlocks direct ingredients or components used to make the source item. */
    RECIPE_COMPONENTS("Recipe components"),

    /** Unlocks the remaining pieces of a distinctive reviewed item set. */
    SIGNATURE_SET("Signature set"),

    /** Unlocks direct, non-shared unique items received from an NPC or boss. */
    SOURCE_UNIQUES("Boss uniques"),

    /** Unlocks a tool specifically required to fight, expose, or finish an NPC. */
    NPC_REQUIRED_TOOL("Required tool"),

    /** Permits use of a signature item earned by completing an encounter. */
    ACHIEVEMENT_REWARD("Achievement reward"),

    /** Unlocks a coherent equipment set associated with one NPC encounter. */
    SOURCE_EQUIPMENT_SET("Encounter equipment"),

    /** Unlocks the corresponding seed for a directly harvested Farming product. */
    FARMING_SEED("Farming seed"),

    /** Unlocks the canonical component cards contained in a reviewed item-set package. */
    PACKAGE_CONTENTS("Package contents"),

    /** Unlocks a direct, reversible or tightly coupled processing material. */
    MATERIAL_CONVERSION("Material conversion"),

    /** Unlocks strictly lower-combat NPCs in one curated wave encounter. */
    ENCOUNTER_TIER_CASCADE("Encounter tier"),

    /** Unlocks a completed item only when every reviewed foil source is owned. */
    MULTI_SOURCE_COMPLETION("Multi-source completion");

    private final String displayLabel;

    FoilRewardKind(String displayLabel)
    {
        this.displayLabel = displayLabel;
    }

    public String getDisplayLabel()
    {
        return displayLabel;
    }
}
