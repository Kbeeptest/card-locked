package com.cardrestricted.runelite;

import net.runelite.api.widgets.WidgetID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TransactionInterfaceIntegrityRulesTest
{
    @Test
    public void tradeConfirmationRequiresTradeWidgetProvenance()
    {
        assertTrue(TransactionInterfaceIntegrityRules.isPlayerTradeConfirmation(
            "Accept",
            packed(WidgetID.PLAYER_TRADE_SCREEN_GROUP_ID, 10)));
        assertFalse(TransactionInterfaceIntegrityRules.isPlayerTradeConfirmation(
            "Accept",
            packed(WidgetID.INVENTORY_GROUP_ID, 10)));
    }

    @Test
    public void reconcilesFormattedOptionsAcrossEntryAndEventWidgets()
    {
        assertTrue(TransactionInterfaceIntegrityRules.isPlayerTradeConfirmation(
            "<col=00ff00>Confirm</col>",
            packed(WidgetID.INVENTORY_GROUP_ID, 4),
            packed(WidgetID.PLAYER_TRADE_SCREEN_GROUP_ID, 12)));
        assertTrue(TransactionInterfaceIntegrityRules.isGrandExchangeSubmission(
            "  <col=ff9040>Offer</col>  ",
            -1,
            packed(WidgetID.GRAND_EXCHANGE_GROUP_ID, 7)));
        assertFalse(TransactionInterfaceIntegrityRules.isGrandExchangeSubmission(
            "Collect",
            packed(WidgetID.GRAND_EXCHANGE_GROUP_ID, 7)));
    }

    @Test
    public void grandExchangeSubmissionRequiresGeWidgetProvenance()
    {
        assertTrue(TransactionInterfaceIntegrityRules.isGrandExchangeSubmission(
            "Confirm offer",
            packed(WidgetID.GRAND_EXCHANGE_GROUP_ID, 10)));
        assertTrue(TransactionInterfaceIntegrityRules.isGrandExchangeSubmission(
            "Sell",
            packed(WidgetID.GRAND_EXCHANGE_INVENTORY_GROUP_ID, 10)));
        assertFalse(TransactionInterfaceIntegrityRules.isGrandExchangeSubmission(
            "Sell",
            packed(WidgetID.SHOP_GROUP_ID, 10)));
    }

    private static int packed(int groupId, int childId)
    {
        return groupId << 16 | childId;
    }
}
