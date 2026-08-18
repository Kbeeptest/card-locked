package com.cardrestricted.pack;

import java.util.Objects;
import java.util.Random;

/** Central foil probability policy. */
public final class FoilRollPolicy
{
    public static final int DENOMINATOR = 100;

    private FoilRollPolicy()
    {
    }

    public static boolean roll(Random random)
    {
        Objects.requireNonNull(random, "random");
        return random.nextInt(DENOMINATOR) == 0;
    }
}
