package com.cardrestricted.ui;

import com.cardrestricted.catalog.Rarity;

public interface PackActionHandler
{
    void redeemStarterPack();

    void purchaseStandardPack();

    default void purchaseUncommonPlusPack()
    {
    }

    default void purchaseExplorerPack()
    {
    }

    void purchaseRareHunterPack();

    default void purchaseAdventurePack()
    {
    }

    default void purchaseNexusCache()
    {
    }

    default void purchaseCollectorPack()
    {
    }

    default void redeemInitiateFoilPack()
    {
    }

    default void redeemHeroPack()
    {
    }

    default void redeemNoblePack()
    {
    }

    default void redeemLegendPack()
    {
    }

    default void redeemMythicalPack()
    {
    }

    default void redeemGodsPack()
    {
    }

    void purchaseNoncombatNpcPack();

    void purchaseAttackableNpcPack();

    default void purchaseFoilTestPack()
    {
    }

    default void purchasePremiumFoilTestPack()
    {
    }

    default void purchaseTierFoilTestPack()
    {
    }

    default void purchaseArmourFoilTestPack()
    {
    }

    default void purchaseBossFoilTestPack()
    {
    }

    default void purchaseIngredientFoilTestPack()
    {
    }

    default void purchaseSignatureFoilTestPack()
    {
    }

    default void purchaseNpcRelationshipFoilTestPack()
    {
    }

    default boolean isTestingMode()
    {
        return false;
    }

    default boolean isNexusExchangeBlocked()
    {
        return false;
    }

    /**
     * Requests a manual refresh of the Quest tab's RuneLite completion state.
     * Quest display scanning is deliberately user-driven so the plugin does not
     * continuously execute quest-state scripts during unrelated gameplay.
     */
    default void refreshQuestStatus()
    {
    }

    void exchangeNexusCard(Rarity rarity);
}
