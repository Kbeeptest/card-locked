package com.cardrestricted.session;

import com.cardrestricted.PluginBuildInfo;
import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.collection.CollectionCreationRequest;
import com.cardrestricted.collection.CollectionCreationResult;
import com.cardrestricted.collection.CollectionCreationService;
import com.cardrestricted.collection.activity.CollectionActivityService;
import com.cardrestricted.collection.activity.CollectionActivitySnapshot;
import com.cardrestricted.collection.achievement.AchievementCompletionState;
import com.cardrestricted.collection.achievement.AchievementDefinition;
import com.cardrestricted.collection.achievement.AchievementReconciliationResult;
import com.cardrestricted.collection.achievement.AchievementReconciliationService;
import com.cardrestricted.collection.achievement.AchievementRegistry;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.collection.ProfileSetupOptions;
import com.cardrestricted.collection.ProfileStateMarkers;
import com.cardrestricted.identity.CharacterKeyDeriver;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.CommittedStateRecovery;
import com.cardrestricted.persistence.CatalogueMigrationService;
import com.cardrestricted.persistence.SnapshotCodec;
import com.cardrestricted.persistence.TransactionalStateStore;
import com.cardrestricted.runelite.DeveloperTestingRules;
import com.cardrestricted.points.PointAward;
import com.cardrestricted.points.PointsLedgerService;
import com.cardrestricted.points.F2pNoncombatXpPolicy;
import com.cardrestricted.points.NoncombatXpLedgerService;
import com.cardrestricted.points.NoncombatXpObservation;
import com.cardrestricted.points.NoncombatXpProcessResult;
import com.cardrestricted.progression.ProgressionRewardCardService;
import com.cardrestricted.pack.NexusCachePurchaseResult;
import com.cardrestricted.pack.PackPurchaseResult;
import com.cardrestricted.pack.PackRevealResult;
import com.cardrestricted.pack.PendingPackReveal;
import com.cardrestricted.pack.StandardPackService;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.starter.StarterRewardChoice;
import com.cardrestricted.nexus.NexusExchangeResult;
import com.cardrestricted.nexus.NexusExchangeService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public final class CollectionSessionService
{
    private final Path characterRoot;
    private final CardCatalogue catalogue;
    private final CharacterKeyDeriver characterKeyDeriver;
    private final Clock clock;
    private final AchievementRegistry achievementRegistry;
    private final CollectionActivityService activityService;
    private final List<AchievementDefinition> pendingAchievementNotifications =
        new ArrayList<>();

    private long activityJournalRevision = Long.MIN_VALUE;
    private long accountHash = -1L;
    private String displayName = "";
    private TransactionalStateStore stateStore;
    /**
     * Immutable published view of the session. Reads from RuneLite event
     * handlers must never contend with the persistence monitor: a durable
     * save can include file locking, fsync and journal verification.
     */
    private volatile SessionSnapshot snapshot = SessionSnapshot.loggedOut();

    public CollectionSessionService(
        Path characterRoot,
        CardCatalogue catalogue,
        CharacterKeyDeriver characterKeyDeriver,
        Clock clock)
    {
        this.characterRoot =
            Objects.requireNonNull(characterRoot, "characterRoot");
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue");
        this.characterKeyDeriver =
            Objects.requireNonNull(
                characterKeyDeriver, "characterKeyDeriver");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.achievementRegistry = AchievementRegistry.load(
            CollectionSessionService.class.getClassLoader());
        this.activityService = new CollectionActivityService(
            this.catalogue,
            this.achievementRegistry);
    }

    public synchronized SessionSnapshot open(
        long newAccountHash,
        String newDisplayName)
    {
        if (newAccountHash == -1L || newAccountHash == 0L)
        {
            snapshot =
                SessionSnapshot.identityUnavailable(newDisplayName);
            return snapshot;
        }

        TransactionalStateStore newStore = null;
        try
        {
            String characterKey =
                characterKeyDeriver.derive(newAccountHash);
            newStore = new TransactionalStateStore(
                characterRoot.resolve(characterKey),
                new SnapshotCodec());
            Optional<CollectionState> loaded =
                newStore.loadHighestValid();
            if (loaded.isPresent()
                && !loaded.get().getCharacterKey().equals(characterKey))
            {
                // Validate the directory identity before any migration or
                // reconciliation can write to a different character's save.
                throw new IOException(
                    "The saved collection belongs to another character.");
            }
            if (loaded.isPresent())
            {
                CollectionState migrated = new CatalogueMigrationService(
                    catalogue,
                    newStore).migrateIfRequired(
                        java.time.Instant.now(clock)).getState();
                if (!AchievementCompletionState
                        .isTrackingInitialised(migrated)
                    || migrated.getPendingPackReveal().isEmpty())
                {
                    migrated = reconcileAchievements(
                        newStore,
                        migrated).getState();
                }
                loaded = Optional.of(migrated);
            }

            String recoveryNotice = newStore.consumeRecoveryNotice()
                .orElse("");
            accountHash = newAccountHash;
            displayName = requireDisplayName(newDisplayName);
            stateStore = newStore;
            snapshot = loaded.isPresent()
                ? readySnapshot(loaded.orElseThrow(), recoveryNotice)
                : SessionSnapshot.needsSetup(displayName);
        }
        catch (IOException | IllegalArgumentException | IllegalStateException exception)
        {
            // Retain the verified RuneLite identity and store location so the
            // recovery panel can import a validated backup after a load error.
            accountHash = newAccountHash;
            displayName = requireDisplayName(newDisplayName);
            stateStore = newStore;
            activityJournalRevision = Long.MIN_VALUE;
            pendingAchievementNotifications.clear();
            snapshot = SessionSnapshot.error(
                newDisplayName,
                SessionFailureCode.LOAD_FAILED,
                exception);
        }
        return snapshot;
    }

    public synchronized Path exportBackup(Path destination)
        throws IOException
    {
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Backup export requires a ready character session.");
        }
        return stateStore.exportCurrentSnapshot(destination);
    }

    public synchronized SessionSnapshot importBackup(Path source)
        throws IOException
    {
        if (stateStore == null || accountHash == -1L || accountHash == 0L)
        {
            throw new IllegalStateException(
                "Backup import requires a stable character identity.");
        }
        String characterKey = characterKeyDeriver.derive(accountHash);
        stateStore.importSnapshot(
            source,
            characterKey,
            catalogue.getCatalogueVersion(),
            ProfileStateMarkers.INTEGRITY_FORFEITED);
        CollectionState migrated = new CatalogueMigrationService(
            catalogue,
            stateStore).migrateIfRequired(
                java.time.Instant.now(clock)).getState();
        CollectionState reconciled = reconcileAchievements(
            stateStore,
            migrated).getState();
        snapshot = readySnapshot(
            reconciled,
            "Backup imported and validated. Manual recovery permanently disabled integrity for this profile.");
        return snapshot;
    }

    public synchronized SessionSnapshot restorePreviousBackup()
        throws IOException
    {
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Automatic-backup restoration requires a ready profile.");
        }
        stateStore.restorePreviousSnapshot(
            catalogue.getCatalogueVersion(),
            ProfileStateMarkers.INTEGRITY_FORFEITED);
        CollectionState migrated = new CatalogueMigrationService(
            catalogue,
            stateStore).migrateIfRequired(
                java.time.Instant.now(clock)).getState();
        CollectionState reconciled = reconcileAchievements(
            stateStore,
            migrated).getState();
        snapshot = readySnapshot(
            reconciled,
            "Previous automatic backup restored and validated. Manual recovery permanently disabled integrity for this profile.");
        return snapshot;
    }

    public synchronized SessionSnapshot create(
        EconomyMode economyMode,
        IntegrityMode integrityMode,
        StarterRewardChoice starterRewardChoice)
    {
        return create(new ProfileSetupOptions(
            economyMode,
            starterRewardChoice,
            com.cardrestricted.domain.RestrictionPreset.BALANCED,
            true,
            integrityMode));
    }

    public synchronized SessionSnapshot create(ProfileSetupOptions options)
    {
        EconomyMode economyMode = options.getEconomyMode();
        IntegrityMode integrityMode = options.getIntegrityMode();
        StarterRewardChoice starterRewardChoice = options.getStarterRewardChoice();
        if (snapshot.getStatus() != SessionStatus.NEEDS_SETUP
            || stateStore == null)
        {
            return SessionSnapshot.error(
                displayName,
                SessionFailureCode.PROFILE_CREATE_UNAVAILABLE);
        }

        try
        {
            CollectionCreationService creationService =
                new CollectionCreationService(
                    characterKeyDeriver,
                    stateStore,
                    clock,
                    catalogue.getCatalogueVersion());
            CollectionCreationResult result = creationService.create(
                new CollectionCreationRequest(
                    accountHash,
                    displayName,
                    economyMode,
                    integrityMode,
                    starterRewardChoice,
                    ProfileStateMarkers.initialMarkers(options)));
            CollectionState reconciled = reconcileAchievements(
                stateStore,
                result.getState()).getState();
            snapshot = readySnapshot(reconciled);
        }
        catch (IOException | IllegalArgumentException | IllegalStateException exception)
        {
            snapshot = SessionSnapshot.error(
                displayName,
                SessionFailureCode.PROFILE_CREATE_FAILED,
                exception);
        }
        return snapshot;
    }

    public synchronized SessionSnapshot disableIntegrity()
    {
        if (snapshot.getStatus() != SessionStatus.READY || stateStore == null)
        {
            return SessionSnapshot.error(
                displayName,
                SessionFailureCode.NO_ACTIVE_PROFILE);
        }
        CollectionState current = snapshot.getCollectionState().orElseThrow();
        if (!ProfileStateMarkers.isIntegrityProfile(current))
        {
            return snapshot;
        }
        try
        {
            CollectionState updated = current.withIntegrityDisabled(
                ProfileStateMarkers.INTEGRITY_FORFEITED);
            try
            {
                stateStore.save(
                    updated,
                    current.getRevision(),
                    com.cardrestricted.persistence.JournalEventType.STATE_UPDATED,
                    "integrity=forfeited",
                    java.time.Instant.now(clock));
            }
            catch (IOException failure)
            {
                updated = CommittedStateRecovery.recoverIfCommitted(
                    stateStore, updated, failure);
            }
            snapshot = readySnapshot(updated);
        }
        catch (IOException exception)
        {
            snapshot = SessionSnapshot.error(
                displayName,
                SessionFailureCode.INTEGRITY_UPDATE_FAILED,
                exception);
        }
        return snapshot;
    }

    public synchronized SessionSnapshot resetProfile()
    {
        if (stateStore == null)
        {
            return SessionSnapshot.error(
                displayName,
                SessionFailureCode.NO_ACTIVE_PROFILE);
        }
        try
        {
            stateStore.deleteAll();
            snapshot = SessionSnapshot.needsSetup(displayName);
        }
        catch (IOException exception)
        {
            snapshot = SessionSnapshot.error(
                displayName,
                SessionFailureCode.PROFILE_RESET_FAILED,
                exception);
        }
        return snapshot;
    }

    public synchronized SessionSnapshot close()
    {
        clearActiveSession();
        snapshot = SessionSnapshot.loggedOut();
        return snapshot;
    }

    public SessionSnapshot snapshot()
    {
        return snapshot;
    }

    public synchronized List<AchievementDefinition>
        drainAchievementNotifications()
    {
        if (pendingAchievementNotifications.isEmpty())
        {
            return Collections.emptyList();
        }
        List<AchievementDefinition> result = Collections.unmodifiableList(
            new ArrayList<>(pendingAchievementNotifications));
        pendingAchievementNotifications.clear();
        return result;
    }

    public synchronized SessionSnapshot awardPoints(PointAward award)
        throws IOException
    {
        Objects.requireNonNull(award, "award");
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Point awards require a ready character session.");
        }

        CollectionState current = snapshot.getCollectionState().orElseThrow();
        CollectionState updated =
            new PointsLedgerService(stateStore).award(current, award);
        snapshot = readySnapshotPreservingActivity(updated);
        return snapshot;
    }

    public synchronized SessionSnapshot awardPointsBatch(
        List<PointAward> awards)
        throws IOException
    {
        Objects.requireNonNull(awards, "awards");
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Point awards require a ready character session.");
        }
        if (awards.isEmpty())
        {
            return snapshot;
        }

        CollectionState current = snapshot.getCollectionState().orElseThrow();
        CollectionState updated =
            new PointsLedgerService(stateStore).awardAll(current, awards);
        snapshot = readySnapshotPreservingActivity(updated);
        return snapshot;
    }

    public synchronized NoncombatXpProcessResult processNoncombatXp(
        NoncombatXpObservation observation)
        throws IOException
    {
        Objects.requireNonNull(observation, "observation");
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "XP processing requires a ready character session.");
        }

        CollectionState current = snapshot.getCollectionState().orElseThrow();
        NoncombatXpProcessResult result =
            new NoncombatXpLedgerService(
                stateStore,
                new F2pNoncombatXpPolicy()).process(current, observation);
        snapshot = readySnapshotPreservingActivity(result.getState());
        return result;
    }


    public synchronized PackPurchaseResult redeemStarterPack(
        Random random)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Starter pack redemption requires a ready character session.");
        }
        PackPurchaseResult result = standardPackService().redeemStarterPack(
                random,
                java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    public synchronized PackPurchaseResult purchaseStandardPack(
        Random random)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Pack purchasing requires a ready character session.");
        }
        PackPurchaseResult result = standardPackService().purchase(random, java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    public synchronized PackPurchaseResult purchaseUncommonPlusPack(
        Random random)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        requireReadyForPackPurchase();
        PackPurchaseResult result = standardPackService().purchaseUncommonPlusPack(
                random,
                java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    public synchronized PackPurchaseResult purchaseExplorerPack(
        Random random)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        requireReadyForPackPurchase();
        PackPurchaseResult result = standardPackService().purchaseExplorerPack(
                random,
                java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    public synchronized PackPurchaseResult purchaseRareHunterPack(
        Random random)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Pack purchasing requires a ready character session.");
        }
        PackPurchaseResult result = standardPackService().purchaseRareHunterPack(
                random,
                java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    public synchronized PackPurchaseResult purchaseAdventurePack(
        Random random)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        requireReadyForPackPurchase();
        PackPurchaseResult result = standardPackService().purchaseAdventurePack(
                random,
                java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    public synchronized PackPurchaseResult purchaseCollectorPack(
        Random random)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        requireReadyForPackPurchase();
        PackPurchaseResult result = standardPackService().purchaseCollectorPack(
                random,
                java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    public synchronized NexusCachePurchaseResult purchaseNexusCache(
        Random random)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        requireReadyForPackPurchase();
        NexusCachePurchaseResult result = standardPackService().purchaseNexusCache(
                random,
                java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    public synchronized PackPurchaseResult redeemInitiateFoilPack(
        Random random)
        throws IOException
    {
        return redeemMilestonePack(random, MilestonePack.INITIATE);
    }

    public synchronized PackPurchaseResult redeemHeroPack(Random random)
        throws IOException
    {
        return redeemMilestonePack(random, MilestonePack.HERO);
    }

    public synchronized PackPurchaseResult redeemNoblePack(Random random)
        throws IOException
    {
        return redeemMilestonePack(random, MilestonePack.NOBLE);
    }

    public synchronized PackPurchaseResult redeemLegendPack(Random random)
        throws IOException
    {
        return redeemMilestonePack(random, MilestonePack.LEGEND);
    }

    public synchronized PackPurchaseResult redeemMythicalPack(Random random)
        throws IOException
    {
        return redeemMilestonePack(random, MilestonePack.MYTHICAL);
    }

    public synchronized PackPurchaseResult redeemGodsPack(Random random)
        throws IOException
    {
        return redeemMilestonePack(random, MilestonePack.GODS);
    }

    public synchronized PackPurchaseResult purchaseNoncombatNpcPack(
        Random random)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Pack purchasing requires a ready character session.");
        }
        PackPurchaseResult result = standardPackService().purchaseNoncombatNpcPack(
                random,
                java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    public synchronized PackPurchaseResult purchaseAttackableNpcPack(
        Random random)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Pack purchasing requires a ready character session.");
        }
        PackPurchaseResult result = standardPackService().purchaseAttackableNpcPack(
                random,
                java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    public synchronized PackPurchaseResult purchaseFoilTestPack(
        Random random)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Pack purchasing requires a ready character session.");
        }
        requireCasualDeveloperProfile();
        PackPurchaseResult result = standardPackService().purchaseFoilTestPack(
                random,
                java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    public synchronized PackPurchaseResult purchasePremiumFoilTestPack(
        Random random)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Pack purchasing requires a ready character session.");
        }
        requireCasualDeveloperProfile();
        PackPurchaseResult result = standardPackService().purchasePremiumFoilTestPack(
                random,
                java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    public synchronized PackPurchaseResult purchaseTierFoilTestPack(
        Random random)
        throws IOException
    {
        return purchaseMappedFoilTestPack(
            random,
            StandardPackService::purchaseTierFoilTestPack);
    }

    public synchronized PackPurchaseResult purchaseArmourFoilTestPack(
        Random random)
        throws IOException
    {
        return purchaseMappedFoilTestPack(
            random,
            StandardPackService::purchaseArmourFoilTestPack);
    }

    public synchronized PackPurchaseResult purchaseBossFoilTestPack(
        Random random)
        throws IOException
    {
        return purchaseMappedFoilTestPack(
            random,
            StandardPackService::purchaseBossFoilTestPack);
    }

    public synchronized PackPurchaseResult purchaseIngredientFoilTestPack(
        Random random)
        throws IOException
    {
        return purchaseMappedFoilTestPack(
            random,
            StandardPackService::purchaseIngredientFoilTestPack);
    }

    public synchronized PackPurchaseResult purchaseSignatureFoilTestPack(
        Random random)
        throws IOException
    {
        return purchaseMappedFoilTestPack(
            random,
            StandardPackService::purchaseSignatureFoilTestPack);
    }

    public synchronized PackPurchaseResult purchaseNpcRelationshipFoilTestPack(
        Random random)
        throws IOException
    {
        return purchaseMappedFoilTestPack(
            random,
            StandardPackService::purchaseNpcRelationshipFoilTestPack);
    }

    private PackPurchaseResult purchaseMappedFoilTestPack(
        Random random,
        FoilTestPackPurchase purchase)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(purchase, "purchase");
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Pack purchasing requires a ready character session.");
        }
        requireCasualDeveloperProfile();
        PackPurchaseResult result = purchase.purchase(
            standardPackService(),
            random,
            java.time.Instant.now(clock));
        snapshot = readySnapshot(result.getState());
        return result;
    }

    private void requireCasualDeveloperProfile()
    {
        CollectionState current = snapshot.getCollectionState().orElseThrow();
        if (!DeveloperTestingRules.isAllowed(
            PluginBuildInfo.isDeveloperRuntime(),
            current.getIntegrityMode()))
        {
            throw new IllegalStateException(
                "Developer test packs require the dedicated development runtime and a casual profile.");
        }
    }

    @FunctionalInterface
    private interface FoilTestPackPurchase
    {
        PackPurchaseResult purchase(
            StandardPackService service,
            Random random,
            java.time.Instant purchasedAt)
            throws IOException;
    }

    public synchronized SessionSnapshot applyTestingBalances(
        long minimumPoints,
        long minimumShards)
        throws IOException
    {
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Testing balances require a ready character session.");
        }
        CollectionState current = snapshot.getCollectionState().orElseThrow();
        requireCasualDeveloperProfile();
        long points = minimumPoints;
        long shards = minimumShards;
        CollectionState updated = current.withTestingBalances(points, shards);
        if (updated == current)
        {
            return snapshot;
        }
        try
        {
            stateStore.save(
                updated,
                current.getRevision(),
                com.cardrestricted.persistence.JournalEventType.STATE_UPDATED,
                "testingBalance=true;points=" + points + ";shards=" + shards,
                java.time.Instant.now(clock));
        }
        catch (IOException failure)
        {
            updated = CommittedStateRecovery.recoverIfCommitted(
                stateStore, updated, failure);
        }
        snapshot = readySnapshot(updated);
        return snapshot;
    }

    public synchronized NexusExchangeResult exchangeNexusCard(
        Rarity rarity,
        Random random)
        throws IOException
    {
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(random, "random");
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Nexus exchanges require a ready character session.");
        }
        NexusExchangeResult result = new NexusExchangeService(
            catalogue,
            stateStore).exchange(
                rarity,
                random,
                java.time.Instant.now(clock));
        CollectionState reconciled = reconcileAchievements(
            stateStore,
            result.getState()).getState();
        NexusExchangeResult reconciledResult = new NexusExchangeResult(
            reconciled,
            result.getCardId(),
            result.getRarity(),
            result.getShardsSpent());
        snapshot = readySnapshot(reconciled);
        return reconciledResult;
    }

    public synchronized PackRevealResult revealPackCard(int cardPosition)
        throws IOException
    {
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Pack revealing requires a ready character session.");
        }
        CollectionState current = snapshot.getCollectionState()
            .orElseThrow(() -> new IllegalStateException(
                "Ready pack session has no collection state."));
        PackRevealResult result = standardPackService().revealCard(
                current,
                cardPosition,
                java.time.Instant.now(clock));
        PackRevealResult reconciledResult = reconcileCompletedReveal(result);
        snapshot = reconciledResult.isComplete()
            ? readySnapshot(reconciledResult.getState())
            : readySnapshotPreservingActivity(reconciledResult.getState());
        return reconciledResult;
    }

    public synchronized PackRevealResult revealNextPackCard()
        throws IOException
    {
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Pack revealing requires a ready character session.");
        }
        CollectionState current = snapshot.getCollectionState()
            .orElseThrow(() -> new IllegalStateException(
                "Ready pack session has no collection state."));
        PendingPackReveal pending = current.getPendingPackReveal()
            .orElseThrow(() -> new IllegalStateException(
                "There is no pack waiting to be revealed."));
        PackRevealResult result = standardPackService().revealCard(
                current,
                pending.getNextUnrevealedPosition(),
                java.time.Instant.now(clock));
        PackRevealResult reconciledResult = reconcileCompletedReveal(result);
        snapshot = reconciledResult.isComplete()
            ? readySnapshot(reconciledResult.getState())
            : readySnapshotPreservingActivity(reconciledResult.getState());
        return reconciledResult;
    }

    private PackRevealResult reconcileCompletedReveal(
        PackRevealResult result)
        throws IOException
    {
        if (!result.isComplete())
        {
            return result;
        }
        CollectionState reconciled = reconcileAchievements(
            stateStore,
            result.getState()).getState();
        return new PackRevealResult(
            reconciled,
            result.getRevealedCard(),
            result.getRevealNumber(),
            result.getTotalCards(),
            result.getCardPosition());
    }

    private AchievementReconciliationResult reconcileAchievements(
        TransactionalStateStore store,
        CollectionState state)
        throws IOException
    {
        CollectionState progressionReconciled =
            new ProgressionRewardCardService(
                catalogue,
                Objects.requireNonNull(store, "store")).reconcile(
                    state,
                    java.time.Instant.now(clock));
        AchievementReconciliationResult result =
            new AchievementReconciliationService(
                catalogue,
                achievementRegistry,
                store).reconcile(
                    progressionReconciled,
                    java.time.Instant.now(clock));
        pendingAchievementNotifications.addAll(
            result.getNewlyCompleted());
        return result;
    }

    private SessionSnapshot readySnapshot(CollectionState state)
    {
        return readySnapshot(state, "");
    }

    private SessionSnapshot readySnapshotPreservingActivity(
        CollectionState state)
    {
        CollectionActivitySnapshot activity = snapshot.getStatus()
            == SessionStatus.READY
                ? snapshot.getActivitySnapshot()
                : CollectionActivitySnapshot.empty();
        return SessionSnapshot.ready(state, activity);
    }

    private SessionSnapshot readySnapshot(
        CollectionState state,
        String message)
    {
        if (stateStore == null)
        {
            return SessionSnapshot.ready(
                state,
                CollectionActivitySnapshot.empty(),
                message);
        }
        CollectionActivitySnapshot activity;
        try
        {
            CollectionState previousState = snapshot.getStatus()
                == SessionStatus.READY
                    ? snapshot.getCollectionState().orElse(null)
                    : null;
            CollectionActivitySnapshot previousActivity =
                snapshot.getStatus() == SessionStatus.READY
                    ? snapshot.getActivitySnapshot()
                    : null;
            boolean canAdvanceIncrementally = previousState != null
                && previousActivity != null
                && previousActivity.isAvailable()
                && activityJournalRevision != Long.MIN_VALUE
                && previousState.getCollectionId().equals(
                    state.getCollectionId())
                && activityJournalRevision <= state.getRevision();
            if (canAdvanceIncrementally)
            {
                List<com.cardrestricted.persistence.StateJournalEvent> delta =
                    stateStore.loadJournalAfterRevision(
                        activityJournalRevision);
                activity = delta.isEmpty()
                    ? previousActivity
                    : activityService.calculateIncremental(
                        previousActivity,
                        delta);
            }
            else
            {
                activity = activityService.calculate(
                    stateStore.loadJournal());
            }
            activityJournalRevision = state.getRevision();
        }
        catch (IOException | IllegalArgumentException exception)
        {
            activityJournalRevision = Long.MIN_VALUE;
            activity = CollectionActivitySnapshot.unavailable(
                "Activity history is temporarily unavailable. "
                    + "Export a local diagnostic report if this persists.");
        }
        return SessionSnapshot.ready(state, activity, message);
    }

    private void clearActiveSession()
    {
        accountHash = -1L;
        displayName = "";
        stateStore = null;
        activityJournalRevision = Long.MIN_VALUE;
        pendingAchievementNotifications.clear();
    }

    private String requireDisplayName(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return "Unknown character";
        }
        return value;
    }

    private PackPurchaseResult redeemMilestonePack(
        Random random,
        MilestonePack milestonePack)
        throws IOException
    {
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(milestonePack, "milestonePack");
        requireReadyForPackPurchase();
        StandardPackService service = standardPackService();
        PackPurchaseResult result;
        switch (milestonePack)
        {
            case INITIATE:
                result = service.redeemInitiateFoilPack(
                    random, java.time.Instant.now(clock));
                break;
            case HERO:
                result = service.redeemHeroPack(
                    random, java.time.Instant.now(clock));
                break;
            case NOBLE:
                result = service.redeemNoblePack(
                    random, java.time.Instant.now(clock));
                break;
            case LEGEND:
                result = service.redeemLegendPack(
                    random, java.time.Instant.now(clock));
                break;
            case MYTHICAL:
                result = service.redeemMythicalPack(
                    random, java.time.Instant.now(clock));
                break;
            case GODS:
                result = service.redeemGodsPack(
                    random, java.time.Instant.now(clock));
                break;
            default:
                throw new IllegalArgumentException(
                    "Unsupported milestone pack: " + milestonePack);
        }
        snapshot = readySnapshot(result.getState());
        return result;
    }

    private StandardPackService standardPackService()
    {
        TransactionalStateStore store = Objects.requireNonNull(
            stateStore,
            "stateStore");
        CollectionState current = snapshot.getStatus() == SessionStatus.READY
            ? snapshot.getCollectionState().orElse(null)
            : null;
        return new StandardPackService(catalogue, store, current);
    }

    private void requireReadyForPackPurchase()
    {
        if (snapshot.getStatus() != SessionStatus.READY
            || stateStore == null)
        {
            throw new IllegalStateException(
                "Pack purchasing requires a ready character session.");
        }
    }

    private enum MilestonePack
    {
        INITIATE,
        HERO,
        NOBLE,
        LEGEND,
        MYTHICAL,
        GODS
    }

}
