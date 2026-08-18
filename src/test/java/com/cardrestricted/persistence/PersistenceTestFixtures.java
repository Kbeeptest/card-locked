package com.cardrestricted.persistence;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.starter.StarterRewardState;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

final class PersistenceTestFixtures
{
    static final Instant CREATED_AT =
        Instant.parse("2026-08-03T14:00:00Z");

    private PersistenceTestFixtures()
    {
    }

    static CollectionState state(
        CardCatalogue catalogue,
        String characterKey,
        long points)
    {
        return state(
            catalogue,
            UUID.randomUUID(),
            characterKey,
            points);
    }

    static CollectionState state(
        CardCatalogue catalogue,
        UUID collectionId,
        String characterKey,
        long points)
    {
        return new CollectionState(
            collectionId,
            characterKey,
            "Persistence Test",
            EconomyMode.STANDARD,
            IntegrityMode.CASUAL,
            CREATED_AT,
            5,
            catalogue.getCatalogueVersion(),
            3,
            0L,
            points,
            0L,
            Collections.emptySet(),
            Collections.emptySet(),
            Set.of(StarterRewardState.POINTS_CHOICE_MARKER));
    }

    static CollectionState advance(CollectionState state, long points)
    {
        return state.withProgress(
            state.getRevision() + 1,
            points,
            state.getShards(),
            state.getOwnedCardIds(),
            state.getFoilCardIds());
    }
}
