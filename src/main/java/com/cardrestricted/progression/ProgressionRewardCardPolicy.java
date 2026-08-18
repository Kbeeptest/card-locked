package com.cardrestricted.progression;

import java.util.Objects;

/** Cards granted only by reaching a progression-track milestone. */
public final class ProgressionRewardCardPolicy
{
    public static final String COINS_CARD_ID = "item.coins.995";

    private ProgressionRewardCardPolicy()
    {
    }

    public static boolean isTrackOnlyReward(String cardId)
    {
        return COINS_CARD_ID.equals(
            Objects.requireNonNull(cardId, "cardId"));
    }
}
