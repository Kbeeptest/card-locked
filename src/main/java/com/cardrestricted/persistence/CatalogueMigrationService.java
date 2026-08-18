package com.cardrestricted.persistence;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.pack.PendingPackReveal;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Atomically upgrades saved ownership to the active catalogue aliases. */
public final class CatalogueMigrationService
{
    private final CardCatalogue catalogue;
    private final TransactionalStateStore stateStore;

    public CatalogueMigrationService(
        CardCatalogue catalogue,
        TransactionalStateStore stateStore)
    {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
    }

    public synchronized CatalogueMigrationResult migrateIfRequired(
        Instant migratedAt)
        throws IOException
    {
        Objects.requireNonNull(migratedAt, "migratedAt");
        CollectionState current = stateStore.loadHighestValid()
            .orElseThrow(() -> new IllegalStateException(
                "A collection must exist before catalogue migration."));

        if (current.getCatalogueVersion() > catalogue.getCatalogueVersion())
        {
            throw new IllegalStateException(
                "The save uses catalogue version "
                    + current.getCatalogueVersion()
                    + ", but this plugin only supports version "
                    + catalogue.getCatalogueVersion() + ".");
        }

        Set<String> canonicalOwned = catalogue.canonicalizeCardIds(
            current.getOwnedCardIds());
        Set<String> owned = retainActiveCards(canonicalOwned);
        Set<String> canonicalFoils = catalogue.canonicalizeCardIds(
            current.getFoilCardIds());
        Set<String> foils = retainActiveCards(canonicalFoils);
        foils.retainAll(owned);
        PendingMigration pending = migratePending(
            current.getPendingPackReveal().orElse(null));

        int aliasesResolved = countAliases(current.getOwnedCardIds());
        int foilAliasesResolved = countAliases(current.getFoilCardIds());
        int ownershipCollisions = current.getOwnedCardIds().size()
            - canonicalOwned.size();
        int retiredOwnedCards = canonicalOwned.size() - owned.size();
        int retiredFoilCards = canonicalFoils.size() - foils.size();
        boolean changed = current.getCatalogueVersion()
                < catalogue.getCatalogueVersion()
            || !owned.equals(current.getOwnedCardIds())
            || !foils.equals(current.getFoilCardIds())
            || pending.changed;
        if (!changed)
        {
            return new CatalogueMigrationResult(
                current,
                false,
                0,
                0,
                0,
                0);
        }

        CollectionState updated = new CollectionState(
            current.getCollectionId(),
            current.getCharacterKey(),
            current.getDisplayName(),
            current.getEconomyMode(),
            current.getIntegrityMode(),
            current.getCreatedAt(),
            current.getSchemaVersion(),
            catalogue.getCatalogueVersion(),
            current.getRuleSetVersion(),
            current.getRevision() + 1,
            current.getPoints(),
            current.getShards(),
            owned,
            foils,
            current.getClaimedPointSourceIds(),
            current.getNoncombatRewardRemainderUnits(),
            current.getNoncombatXpWatermarks(),
            pending.reveal);
        String payload = "fromCatalogueVersion="
            + current.getCatalogueVersion()
            + ";toCatalogueVersion=" + catalogue.getCatalogueVersion()
            + ";aliasesResolved=" + aliasesResolved
            + ";ownershipCollisions=" + ownershipCollisions
            + ";foilAliasesResolved=" + foilAliasesResolved
            + ";retiredOwnedCards=" + retiredOwnedCards
            + ";retiredFoilCards=" + retiredFoilCards
            + ";pendingAliasesResolved=" + pending.aliasesResolved
            + ";pendingRetiredCards=" + pending.retiredCards;
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                JournalEventType.CATALOGUE_MIGRATED,
                payload,
                migratedAt);
        }
        catch (IOException failure)
        {
            updated = CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
        return new CatalogueMigrationResult(
            updated,
            true,
            aliasesResolved,
            ownershipCollisions,
            foilAliasesResolved,
            pending.aliasesResolved);
    }

    private Set<String> retainActiveCards(Set<String> cardIds)
    {
        Set<String> retained = new HashSet<>();
        for (String cardId : cardIds)
        {
            if (catalogue.containsCard(cardId))
            {
                retained.add(cardId);
            }
        }
        return retained;
    }

    private int countAliases(Set<String> cardIds)
    {
        int count = 0;
        for (String cardId : cardIds)
        {
            if (!catalogue.resolveCardId(cardId).equals(cardId))
            {
                count++;
            }
        }
        return count;
    }

    private PendingMigration migratePending(PendingPackReveal reveal)
    {
        if (reveal == null)
        {
            return new PendingMigration(null, false, 0, 0);
        }
        List<PackCardResult> results = new ArrayList<>();
        Set<Integer> revealedPositions = new HashSet<>();
        int aliasesResolved = 0;
        int retiredCards = 0;
        for (int oldPosition = 0;
            oldPosition < reveal.getCardResults().size();
            oldPosition++)
        {
            PackCardResult result = reveal.getCardAt(oldPosition);
            String canonical = catalogue.resolveCardId(result.getCardId());
            if (!canonical.equals(result.getCardId()))
            {
                aliasesResolved++;
            }
            if (!catalogue.containsCard(canonical))
            {
                retiredCards++;
                continue;
            }
            int newPosition = results.size();
            results.add(new PackCardResult(
                canonical,
                result.isDuplicate(),
                result.getShardsAwarded(),
                result.isFoil()));
            if (reveal.isRevealed(oldPosition))
            {
                revealedPositions.add(newPosition);
            }
        }
        if (results.isEmpty())
        {
            return new PendingMigration(
                null,
                true,
                aliasesResolved,
                retiredCards);
        }
        if (revealedPositions.size() >= results.size())
        {
            revealedPositions.remove(results.size() - 1);
        }
        if (aliasesResolved == 0 && retiredCards == 0)
        {
            return new PendingMigration(reveal, false, 0, 0);
        }
        PendingPackReveal migrated = new PendingPackReveal(
            reveal.getOpeningId(),
            reveal.getPackId(),
            reveal.getPurchasedAt(),
            results,
            revealedPositions);
        return new PendingMigration(
            migrated,
            true,
            aliasesResolved,
            retiredCards);
    }

    private static final class PendingMigration
    {
        private final PendingPackReveal reveal;
        private final boolean changed;
        private final int aliasesResolved;
        private final int retiredCards;

        private PendingMigration(
            PendingPackReveal reveal,
            boolean changed,
            int aliasesResolved,
            int retiredCards)
        {
            this.reveal = reveal;
            this.changed = changed;
            this.aliasesResolved = aliasesResolved;
            this.retiredCards = retiredCards;
        }
    }
}
