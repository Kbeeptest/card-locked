package com.cardrestricted.nexus;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.CommittedStateRecovery;
import com.cardrestricted.persistence.JournalEventType;
import com.cardrestricted.persistence.TransactionalStateStore;
import com.cardrestricted.progression.ProgressionRewardCardPolicy;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public final class NexusExchangeService
{
    private final CardCatalogue catalogue;
    private final TransactionalStateStore stateStore;

    public NexusExchangeService(
        CardCatalogue catalogue,
        TransactionalStateStore stateStore)
    {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
    }

    public synchronized NexusExchangeResult exchange(
        Rarity rarity,
        Random random,
        Instant exchangedAt)
        throws IOException
    {
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(exchangedAt, "exchangedAt");

        CollectionState current = stateStore.loadHighestValid()
            .orElseThrow(() -> new IllegalStateException(
                "A collection must exist before using the Nexus."));
        if (current.getPendingPackReveal().isPresent())
        {
            throw new IllegalStateException(
                "Finish revealing the current pack before using the Nexus.");
        }

        Set<String> canonicalOwned = catalogue.canonicalizeCardIds(
            current.getOwnedCardIds());
        List<CardDefinition> missing = new ArrayList<>();
        for (CardDefinition card : catalogue.getCards())
        {
            if (card.getRarity() == rarity
                && !ProgressionRewardCardPolicy.isTrackOnlyReward(
                    card.getCardId())
                && !canonicalOwned.contains(card.getCardId()))
            {
                missing.add(card);
            }
        }
        missing.sort(Comparator.comparing(CardDefinition::getCardId));
        if (missing.isEmpty())
        {
            throw new IllegalStateException(
                "The " + readable(rarity)
                    + " collection is already complete.");
        }

        long cost = NexusExchangeCosts.forRarity(rarity);
        if (current.getShards() < cost)
        {
            throw new IllegalStateException(
                "The " + readable(rarity) + " Nexus exchange requires "
                    + cost + " shards, but only " + current.getShards()
                    + " are available.");
        }

        CardDefinition selected = missing.get(random.nextInt(missing.size()));
        Set<String> owned = new HashSet<>(canonicalOwned);
        owned.add(selected.getCardId());
        CollectionState updated = current.withProgress(
            current.getRevision() + 1,
            current.getPoints(),
            current.getShards() - cost,
            owned,
            current.getFoilCardIds());
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                JournalEventType.NEXUS_EXCHANGE,
                "rarity=" + rarity.name()
                    + ";cost=" + cost
                    + ";cardId=" + selected.getCardId(),
                exchangedAt);
        }
        catch (IOException failure)
        {
            updated = CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
        return new NexusExchangeResult(
            updated,
            selected.getCardId(),
            rarity,
            cost);
    }

    private static String readable(Rarity rarity)
    {
        String value = rarity.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
