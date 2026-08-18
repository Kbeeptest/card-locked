package com.cardrestricted.persistence;

import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class CollectionStatePerformanceTest
{
    @Test
    public void pointOnlyRevisionStructurallySharesCollectionShape()
    {
        Set<String> owned = new HashSet<>();
        for (int index = 0; index < 7000; index++)
        {
            owned.add("card." + index);
        }
        Set<String> foils = new HashSet<>();
        for (int index = 0; index < 500; index++)
        {
            foils.add("card." + index);
        }
        CollectionState initial = state(owned, foils, Collections.emptySet());
        CollectionState awarded = initial.withPointsAwarded(
            "clue-completion:v1:easy:1", 10L);

        assertSame(initial.getOwnedCardIds(), awarded.getOwnedCardIds());
        assertSame(initial.getFoilCardIds(), awarded.getFoilCardIds());
        assertSame(initial.getNoncombatXpWatermarks(),
            awarded.getNoncombatXpWatermarks());
    }

    @Test
    public void npcKillMarkersRemainBoundedWhilePermanentMarkersSurvive()
    {
        Set<String> markers = new LinkedHashSet<>();
        markers.add("profile.integrity.eligible");
        markers.add("clue-completion:v1:hard:42");
        for (int tick = 0; tick < 1000; tick++)
        {
            markers.add(npcSource("old-session", tick, tick % 64));
        }
        CollectionState initial = state(
            Collections.emptySet(), Collections.emptySet(), markers);

        Set<String> batch = new LinkedHashSet<>();
        batch.add(npcSource("new-session", 5000, 1));
        batch.add(npcSource("new-session", 5000, 2));
        CollectionState updated = initial.withPointsAwardedBatch(batch, 2L);

        assertTrue(updated.getClaimedPointSourceIds().contains(
            "profile.integrity.eligible"));
        assertTrue(updated.getClaimedPointSourceIds().contains(
            "clue-completion:v1:hard:42"));
        assertTrue(updated.getClaimedPointSourceIds().containsAll(batch));
        assertFalse(updated.getClaimedPointSourceIds().stream()
            .anyMatch(value -> value.startsWith(
                "npc-kill:v2:old-session:")));
    }

    @Test
    public void recentNpcKillMarkerStillRejectsDuplicate()
    {
        String source = npcSource("session-a", 200, 4);
        CollectionState initial = state(
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.singleton(source));
        boolean rejected = false;
        try
        {
            initial.withPointsAwarded(source, 1L);
        }
        catch (IllegalArgumentException expected)
        {
            rejected = true;
        }
        assertTrue(rejected);
    }

    private static String npcSource(String session, int tick, int npcIndex)
    {
        return "npc-kill:v2:" + session + ":301:" + tick + ":"
            + npcIndex + ":3028";
    }

    private static CollectionState state(
        Set<String> owned,
        Set<String> foils,
        Set<String> markers)
    {
        return new CollectionState(
            UUID.randomUUID(),
            "character-key",
            "Tester",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            Instant.parse("2026-08-07T12:00:00Z"),
            5,
            20,
            1,
            0L,
            10_000L,
            0L,
            owned,
            foils,
            markers,
            0L,
            Collections.emptyMap(),
            null);
    }
}
