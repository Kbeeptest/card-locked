package com.cardrestricted.audio;

import com.cardrestricted.catalog.Rarity;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class AudioCueManagerLifecycleTest
{
    @Test
    public void shutdownIsIdempotentAndRejectsCosmeticWorkSilently()
    {
        AudioCueManager manager = new AudioCueManager();
        manager.shutdown();
        manager.shutdown();
        manager.playDealCard(0);
        manager.playReveal(Rarity.LEGENDARY, true);
        assertTrue(manager.isShutdownForTesting());
    }
}
