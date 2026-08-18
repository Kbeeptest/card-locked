package com.cardrestricted.runelite;

import java.util.Set;
import net.runelite.api.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SpellRuneRequirementResolverTest
{
    @Test
    public void strippedWidgetMetadataStillUsesNamedRequirements()
    {
        SpellRuneRequirementResolver resolver =
            new SpellRuneRequirementResolver(null);
        SpellRuneRequirementResolver.Resolution result = resolver.resolve(
            "Cast Falador Teleport",
            "Falador Teleport");
        assertTrue(result.isResolved());
        assertTrue(result.getRequiredRuneIds().contains(ItemID.LAW_RUNE));
        assertTrue(result.getRequiredRuneIds().contains(ItemID.AIR_RUNE));
        assertTrue(result.getRequiredRuneIds().contains(ItemID.WATER_RUNE));
    }

    @Test
    public void unknownExplicitCastFailsClosed()
    {
        SpellRuneRequirementResolver resolver =
            new SpellRuneRequirementResolver(null);
        SpellRuneRequirementResolver.Resolution result = resolver.resolve(
            "Cast",
            "Future Unknown Spell");
        assertTrue(result.isSpellbookAction());
        assertFalse(result.isResolved());
    }

    @Test
    public void conflictingEntryAndEventSpellsFailClosed()
    {
        SpellRuneRequirementResolver resolver =
            new SpellRuneRequirementResolver(null);
        SpellRuneRequirementResolver.Resolution result =
            resolver.resolveReconciled(
                "Cast",
                "Varrock Teleport",
                "Falador Teleport");
        assertTrue(result.isSpellbookAction());
        assertFalse(result.isResolved());
    }

    @Test
    public void recognisedRuneFreeHomeTeleportRemainsAvailable()
    {
        SpellRuneRequirementResolver resolver =
            new SpellRuneRequirementResolver(null);
        SpellRuneRequirementResolver.Resolution result = resolver.resolve(
            "Cast Lumbridge Home Teleport",
            "Lumbridge Home Teleport");
        assertTrue(result.isResolved());
        assertTrue(result.isRuneFree());
        assertTrue(result.getRequiredRuneIds().equals(Set.of()));
    }
    @Test
    public void literalKnownSpellTargetSurvivesRewrittenOptionMetadata()
    {
        assertTrue(SpellRuneRequirementResolver.isRecognisedSpellTarget(
            "<col=00ff00>Varrock Teleport</col>"));
        assertTrue(SpellbookWidgetRules.isPotentialRewrittenSpellAction(
            net.runelite.api.MenuAction.UNKNOWN));
        assertTrue(SpellbookWidgetRules.isPotentialRewrittenSpellAction(
            net.runelite.api.MenuAction.CC_OP));
        assertFalse(SpellRuneRequirementResolver.isRecognisedSpellTarget(
            "Future Unknown Spell"));
    }

}
