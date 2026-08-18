package com.cardrestricted.runelite;

/** Session-scoped rules for autocast configurations created outside enforcement. */
public final class AutocastIntegrityRules
{
    public static final int SELECTION_CONFIRM_WINDOW_TICKS = 3;
    private AutocastIntegrityRules()
    {
    }


    public static boolean isPendingSelectionCurrent(
        boolean pending,
        int pendingTick,
        int currentTick)
    {
        if (!pending || pendingTick == Integer.MIN_VALUE)
        {
            return false;
        }
        int elapsed = currentTick - pendingTick;
        return elapsed >= 0 && elapsed <= SELECTION_CONFIRM_WINDOW_TICKS;
    }

    public static boolean shouldBlockAttack(
        boolean autocastSet,
        boolean autocastVerifiedThisSession,
        String option)
    {
        return autocastSet
            && !autocastVerifiedThisSession
            && InteractionIntegrityRules.isAttackOption(option);
    }
}
