package com.cardrestricted.selftest;

import com.cardrestricted.runelite.InteractionRiskClass;
import com.cardrestricted.runelite.InteractionSurface;
import com.cardrestricted.runelite.InteractionSurfacePolicy;
import java.util.EnumSet;
import net.runelite.api.MenuAction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Build-breaker for unreviewed RuneLite menu action additions. */
public final class InteractionSurfacePolicyTest
{
    @Test
    public void everyRuntimeMenuActionHasAnExplicitReviewedPolicy()
    {
        EnumSet<MenuAction> runtime = EnumSet.allOf(MenuAction.class);
        assertEquals(
            "RuneLite added or removed a MenuAction. Review it before releasing.",
            runtime,
            InteractionSurfacePolicy.reviewedActions());
        for (MenuAction action : runtime)
        {
            assertNotNull(action.name(), InteractionSurfacePolicy.surfaceFor(action));
            assertNotNull(action.name(), InteractionSurfacePolicy.riskClassFor(action));
        }
    }

    @Test
    public void unknownAndNullActionsAreFailClosed()
    {
        assertEquals(
            InteractionSurface.UNKNOWN,
            InteractionSurfacePolicy.surfaceFor(MenuAction.UNKNOWN));
        assertEquals(
            InteractionRiskClass.UNKNOWN_FAIL_CLOSED,
            InteractionSurfacePolicy.riskClassFor(MenuAction.UNKNOWN));
        assertEquals(
            InteractionSurface.UNKNOWN,
            InteractionSurfacePolicy.surfaceFor(null));
        assertEquals(
            InteractionRiskClass.UNKNOWN_FAIL_CLOSED,
            InteractionSurfacePolicy.riskClassFor(null));
    }

    @Test
    public void entityAndWidgetActionsRemainDynamicallyRestricted()
    {
        for (MenuAction action : MenuAction.values())
        {
            InteractionSurface surface = InteractionSurfacePolicy.surfaceFor(action);
            if (surface == InteractionSurface.NPC
                || surface == InteractionSurface.ITEM
                || surface == InteractionSurface.GROUND_ITEM
                || surface == InteractionSurface.GAME_OBJECT
                || surface == InteractionSurface.PLAYER
                || surface == InteractionSurface.WORLD_ENTITY)
            {
                assertTrue(
                    action.name(),
                    InteractionSurfacePolicy.riskClassFor(action)
                        == InteractionRiskClass.DYNAMIC_RESTRICTION
                        || InteractionSurfacePolicy.riskClassFor(action)
                            == InteractionRiskClass.OBSERVATION);
            }
        }
    }
}
