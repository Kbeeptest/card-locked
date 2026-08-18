package com.cardrestricted.collection;

import com.cardrestricted.catalog.F2pPrototypeCatalogue;
import com.cardrestricted.identity.CharacterKeyDeriver;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.CommittedStateRecovery;
import com.cardrestricted.persistence.JournalEventType;
import com.cardrestricted.persistence.TransactionalStateStore;
import com.cardrestricted.starter.StarterRewardChoice;
import com.cardrestricted.starter.StarterRewardState;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;

public final class CollectionCreationService
{
    public static final int SCHEMA_VERSION = 5;
    public static final int RULE_SET_VERSION = 3;

    private final CharacterKeyDeriver characterKeyDeriver;
    private final TransactionalStateStore stateStore;
    private final Clock clock;
    private final int catalogueVersion;

    public CollectionCreationService(
        CharacterKeyDeriver characterKeyDeriver,
        TransactionalStateStore stateStore,
        Clock clock)
    {
        this(
            characterKeyDeriver,
            stateStore,
            clock,
            F2pPrototypeCatalogue.VERSION);
    }

    public CollectionCreationService(
        CharacterKeyDeriver characterKeyDeriver,
        TransactionalStateStore stateStore,
        Clock clock,
        int catalogueVersion)
    {
        this.characterKeyDeriver = Objects.requireNonNull(
            characterKeyDeriver,
            "characterKeyDeriver");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (catalogueVersion < 1)
        {
            throw new IllegalArgumentException(
                "catalogueVersion must be positive.");
        }
        this.catalogueVersion = catalogueVersion;
    }

    public synchronized CollectionCreationResult create(
        CollectionCreationRequest request)
        throws IOException
    {
        Objects.requireNonNull(request, "request");
        if (stateStore.loadHighestValid().isPresent())
        {
            throw new CollectionAlreadyExistsException();
        }

        StarterRewardChoice choice = request.getStarterRewardChoice();
        long startingPoints = choice == StarterRewardChoice.POINTS
            ? StarterRewardState.POINTS_BONUS
            : 0L;
        Instant createdAt = Instant.now(clock);
        Set<String> initialMarkers = new HashSet<>(
            StarterRewardState.initialMarkers(choice));
        initialMarkers.addAll(request.getProfileMarkers());
        CollectionState state = new CollectionState(
            UUID.randomUUID(),
            characterKeyDeriver.derive(request.getAccountHash()),
            request.getDisplayName(),
            request.getEconomyMode(),
            request.getIntegrityMode(),
            createdAt,
            SCHEMA_VERSION,
            catalogueVersion,
            RULE_SET_VERSION,
            0,
            startingPoints,
            0,
            Collections.emptySet(),
            Collections.emptySet(),
            initialMarkers);

        try
        {
            stateStore.save(
                state,
                -1,
                JournalEventType.COLLECTION_CREATED,
                "starterChoice=" + choice.name()
                    + ";starterBonusPoints=" + startingPoints
                    + ";starterPackId="
                    + (choice == StarterRewardChoice.RANDOMISED_PACK
                        ? com.cardrestricted.pack.StandardPackService.STARTER_PACK_ID
                        : "none"),
                createdAt);
        }
        catch (IOException failure)
        {
            state = CommittedStateRecovery.recoverIfCommitted(
                stateStore, state, failure);
        }
        return new CollectionCreationResult(state, choice);
    }
}
