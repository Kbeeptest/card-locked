package com.cardrestricted.selftest;

import com.cardrestricted.diagnostics.IntegrityOptionClass;
import com.cardrestricted.runelite.InteractionIntegrityRules;
import com.cardrestricted.runelite.InteractionNameNormalizer;
import com.cardrestricted.runelite.InteractionSurface;
import com.cardrestricted.runelite.InteractionSurfacePolicy;
import java.util.Random;
import net.runelite.api.MenuAction;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Deterministic adversarial metadata generation for menu-entry rewrites. */
public final class InteractionMetadataFuzzTest
{
    private static final String[] DANGEROUS = {
        "Attack", "Pickpocket", "Trade", "Buy-50", "Sell 10",
        "Cast", "Autocast", "Travel", "Claim", "Tan-all", "Make-X"
    };
    private static final String[] SAFE = {
        "Talk-to", "Examine", "Cancel", "Close", "Back", "Continue"
    };
    private static final String[] TAGS = {
        "", "<col=ff0000>", "<col=00ff00>", "<shad=000000>", "<u>"
    };

    @Test
    public void higherRiskOptionSurvivesTwentyThousandRewriteConflicts()
    {
        Random random = new Random(0xC1A892L);
        for (int index = 0; index < 20_000; index++)
        {
            String dangerous = decorate(
                DANGEROUS[random.nextInt(DANGEROUS.length)], random);
            String safe = decorate(SAFE[random.nextInt(SAFE.length)], random);
            boolean dangerousIsEntry = random.nextBoolean();
            String result = InteractionIntegrityRules.effectiveOption(
                dangerousIsEntry ? dangerous : safe,
                dangerousIsEntry ? safe : dangerous);
            IntegrityOptionClass optionClass =
                IntegrityOptionClass.classify(result);
            assertFalse("Dangerous option was downgraded at case " + index,
                optionClass == IntegrityOptionClass.TALK
                    || optionClass == IntegrityOptionClass.EXAMINE
                    || optionClass == IntegrityOptionClass.CLOSE_CANCEL
                    || optionClass == IntegrityOptionClass.CONTINUE);
        }
    }

    @Test
    public void npcActionWinsEveryLowerRiskActionConflict()
    {
        MenuAction[] lowerRisk = {
            MenuAction.CC_OP,
            MenuAction.RUNELITE,
            MenuAction.WALK,
            MenuAction.EXAMINE_ITEM,
            MenuAction.ITEM_FIRST_OPTION,
            MenuAction.GAME_OBJECT_FIRST_OPTION,
            MenuAction.PLAYER_FIRST_OPTION,
            MenuAction.UNKNOWN
        };
        for (MenuAction npc : new MenuAction[]{
            MenuAction.NPC_FIRST_OPTION,
            MenuAction.NPC_SECOND_OPTION,
            MenuAction.ITEM_USE_ON_NPC,
            MenuAction.WIDGET_TARGET_ON_NPC})
        {
            for (MenuAction other : lowerRisk)
            {
                MenuAction effectiveA =
                    InteractionIntegrityRules.effectiveAction(npc, other);
                MenuAction effectiveB =
                    InteractionIntegrityRules.effectiveAction(other, npc);
                assertTrue(npc + " vs " + other,
                    InteractionSurfacePolicy.surfaceFor(effectiveA)
                        == InteractionSurface.NPC);
                assertTrue(other + " vs " + npc,
                    InteractionSurfacePolicy.surfaceFor(effectiveB)
                        == InteractionSurface.NPC);
            }
        }
    }

    @Test
    public void arbitraryTargetsAndUnicodeNeverBreakNormalisation()
    {
        Random random = new Random(892_2026L);
        for (int index = 0; index < 50_000; index++)
        {
            StringBuilder input = new StringBuilder();
            int length = random.nextInt(80);
            for (int character = 0; character < length; character++)
            {
                switch (random.nextInt(8))
                {
                    case 0:
                        input.append((char) ('A' + random.nextInt(26)));
                        break;
                    case 1:
                        input.append((char) ('a' + random.nextInt(26)));
                        break;
                    case 2:
                        input.append((char) ('0' + random.nextInt(10)));
                        break;
                    case 3:
                        input.append("<col=ff00ff>");
                        break;
                    case 4:
                        input.append('\u00a0');
                        break;
                    case 5:
                        input.append(" -> ");
                        break;
                    case 6:
                        input.append((char) (0x0370 + random.nextInt(80)));
                        break;
                    default:
                        input.append("  ");
                        break;
                }
            }
            String value = input.toString();
            assertNotNull(InteractionNameNormalizer.normaliseEntityName(value));
            assertNotNull(InteractionNameNormalizer.targetItemNameCandidates(value));
            assertNotNull(IntegrityOptionClass.classify(value));
        }
    }

    @Test
    public void pendingStateBlocksFunctionalUnknownAndWidgetActions()
    {
        String[] functional = {
            "Attack", "Trade", "Pickpocket", "Buy-1", "Sell-1",
            "Cast", "Travel", "Claim", "Make-X", "Use"
        };
        MenuAction[] rewritten = {
            MenuAction.UNKNOWN,
            MenuAction.RUNELITE,
            MenuAction.RUNELITE_HIGH_PRIORITY,
            MenuAction.CC_OP,
            MenuAction.WIDGET_FIRST_OPTION
        };
        for (MenuAction action : rewritten)
        {
            for (String option : functional)
            {
                assertTrue(action + " " + option,
                    InteractionIntegrityRules.shouldBlockWhileStatePending(
                        action, option, false, true, false));
            }
        }
    }

    private static String decorate(String value, Random random)
    {
        String tag = TAGS[random.nextInt(TAGS.length)];
        String close = tag.isEmpty() ? "" : "</col>";
        String padded = random.nextBoolean() ? "  " + value + "  " : value;
        return tag + padded + close;
    }
}
