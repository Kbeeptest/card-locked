package com.cardrestricted.collection;

import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.domain.RestrictionPreset;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.starter.StarterRewardChoice;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ProfileStateMarkersTest
{
    @Test
    public void setupMarkersRoundTripRestrictionPresetAndVisualChoice()
    {
        ProfileSetupOptions options = new ProfileSetupOptions(
            EconomyMode.STANDARD,
            StarterRewardChoice.POINTS,
            RestrictionPreset.STRICT,
            true,
            IntegrityMode.INTEGRITY);
        Set<String> markers = ProfileStateMarkers.initialMarkers(options);
        CollectionState state = state(IntegrityMode.INTEGRITY, markers);

        assertEquals(
            RestrictionPreset.STRICT,
            ProfileStateMarkers.restrictionPreset(state));
        assertTrue(markers.contains(ProfileStateMarkers.LOCKED_VISUALS));
        assertTrue(ProfileStateMarkers.isIntegrityProfile(state));
        assertFalse(ProfileStateMarkers.isIntegrityForfeited(state));
    }

    @Test
    public void forfeitedMarkerPermanentlyDisablesIntegrityEligibility()
    {
        Set<String> markers = Set.of(
            ProfileStateMarkers.INTEGRITY_ELIGIBLE,
            ProfileStateMarkers.INTEGRITY_FORFEITED);
        CollectionState state = state(IntegrityMode.INTEGRITY, markers);
        assertFalse(ProfileStateMarkers.isIntegrityProfile(state));
        assertTrue(ProfileStateMarkers.isIntegrityForfeited(state));
    }

    private static CollectionState state(
        IntegrityMode integrityMode,
        Set<String> markers)
    {
        return new CollectionState(
            UUID.randomUUID(),
            "profile-test",
            "Profile Test",
            EconomyMode.STANDARD,
            integrityMode,
            Instant.parse("2026-08-03T12:00:00Z"),
            1,
            15,
            1,
            0L,
            0L,
            0L,
            Collections.emptySet(),
            Collections.emptySet(),
            markers);
    }
}
