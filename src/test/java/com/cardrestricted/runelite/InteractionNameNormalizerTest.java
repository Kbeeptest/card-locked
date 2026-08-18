package com.cardrestricted.runelite;

import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class InteractionNameNormalizerTest
{
    @Test
    public void itemOnItemRetainsSourceAndTargetNames()
    {
        Set<String> candidates = InteractionNameNormalizer.itemNameCandidates(
            "<col=ff9040>Knife</col> -> <col=ffffff>Logs</col>");
        assertTrue(candidates.contains("knife"));
        assertTrue(candidates.contains("logs"));
    }

    @Test
    public void spellTargetParsingExcludesTheSpellName()
    {
        Set<String> candidates =
            InteractionNameNormalizer.targetItemNameCandidates(
                "High Level Alchemy -> <col=ff9040>Rune platebody</col>");
        assertEquals(Set.of("rune platebody"), candidates);
        assertFalse(candidates.contains("high level alchemy"));
    }

    @Test
    public void targetParsingNormalisesDosesChargesAndQuantities()
    {
        assertEquals(
            Set.of("prayer potion"),
            InteractionNameNormalizer.targetItemNameCandidates(
                "Cast -> Prayer potion(4)"));
        assertTrue(InteractionNameNormalizer.targetItemNameCandidates(
            "Cast -> Games necklace(8)").contains("games necklace"));
        assertTrue(InteractionNameNormalizer.targetItemNameCandidates(
            "Cast -> Coins x 1,000").contains("coins"));
    }
    @Test
    public void npcCombatLevelSuffixIsRemovedForEntrylessFallbacks()
    {
        assertEquals("guard", InteractionNameNormalizer.normaliseEntityName(
            "<col=ffff00>Guard (level-21)</col>"));
        assertEquals("hero", InteractionNameNormalizer.normaliseEntityName(
            "Hero (level 69)"));
    }

    @Test
    public void sourceToNpcTargetFallbackUsesOnlyTheNpcSide()
    {
        assertEquals("guard", InteractionNameNormalizer.targetEntityName(
            "Fire Strike -> <col=ffff00>Guard (level-21)</col>"));
        assertEquals("hero", InteractionNameNormalizer.targetEntityName(
            "Rope → Hero (level 69)"));
    }

}
