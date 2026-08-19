package com.cardrestricted;

import com.cardrestricted.audio.AudioCueManager;
import com.cardrestricted.collection.achievement.AchievementDefinition;
import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.MembersCatalogue;
import com.cardrestricted.foil.FoilEntitlementResolver;
import com.cardrestricted.foil.FoilEntitlementSnapshot;
import com.cardrestricted.foil.FoilRewardRegistry;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.collection.ProfileSetupOptions;
import com.cardrestricted.collection.ProfileStateMarkers;
import com.cardrestricted.domain.RestrictionPresetSettings;
import com.cardrestricted.diagnostics.DiagnosticEventCode;
import com.cardrestricted.diagnostics.DiagnosticOperation;
import com.cardrestricted.diagnostics.DiagnosticReportExporter;
import com.cardrestricted.diagnostics.DiagnosticRuntimeSnapshot;
import com.cardrestricted.diagnostics.IntegrityTraceDecision;
import com.cardrestricted.diagnostics.IntegrityTraceLog;
import com.cardrestricted.diagnostics.IntegrityTraceReason;
import com.cardrestricted.diagnostics.LocalDiagnosticLog;
import com.cardrestricted.lifecycle.LifecycleCleanupRegistry;
import com.cardrestricted.lifecycle.ManagedTaskScope;
import com.cardrestricted.starter.StarterRewardChoice;
import com.cardrestricted.identity.CharacterKeyDeriver;
import com.cardrestricted.nexus.NexusExchangeResult;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.persistence.PluginPaths;
import com.cardrestricted.pack.NexusCachePurchaseResult;
import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.pack.PackPurchaseResult;
import com.cardrestricted.pack.PackRevealResult;
import com.cardrestricted.points.ClueCompletionMessageParser;
import com.cardrestricted.points.ClueCompletionObservation;
import com.cardrestricted.points.ClueCompletionRewardPolicy;
import com.cardrestricted.points.DuplicatePointAwardException;
import com.cardrestricted.points.F2pNpcKillRewardPolicy;
import com.cardrestricted.points.F2pQuestCompletionRewardPolicy;
import com.cardrestricted.points.NpcKillObservation;
import com.cardrestricted.points.NpcKillCreditTracker;
import com.cardrestricted.points.NoncombatSkill;
import com.cardrestricted.points.NoncombatXpObservation;
import com.cardrestricted.points.NoncombatXpProcessResult;
import com.cardrestricted.points.PointAward;
import com.cardrestricted.points.PointBalanceThresholds;
import com.cardrestricted.points.QuestCompletionObservation;
import com.cardrestricted.points.QuestCompletionTracker;
import com.cardrestricted.quest.QuestCompletionIndex;
import com.cardrestricted.points.SkillLevelRewardPolicy;
import com.cardrestricted.presentation.AchievementToastController;
import com.cardrestricted.presentation.PackPresentationAction;
import com.cardrestricted.presentation.PackPresentationController;
import com.cardrestricted.presentation.PackPresentationSelection;
import com.cardrestricted.presentation.PackPresentationState;
import com.cardrestricted.progression.ProgressionMilestonePolicy;
import com.cardrestricted.runelite.AutocastIntegrityRules;
import com.cardrestricted.runelite.CoinMilestoneRules;
import com.cardrestricted.runelite.DeveloperTestingRules;
import com.cardrestricted.runelite.FurnaceInteractionRules;
import com.cardrestricted.runelite.GrandExchangeOfferIntegrityRules;
import com.cardrestricted.runelite.GrandExchangeInteractionAuthorization;
import com.cardrestricted.runelite.EquippedItemActionRules;
import com.cardrestricted.runelite.InteractionFamilyIndex;
import com.cardrestricted.runelite.InteractionContextRules;
import com.cardrestricted.runelite.InteractionItemResolver;
import com.cardrestricted.runelite.InteractionIntegrityRules;
import com.cardrestricted.runelite.ImplicitItemUsageRules;
import com.cardrestricted.runelite.InteractionNameNormalizer;
import com.cardrestricted.runelite.InteractionTargetIntegrityRules;
import com.cardrestricted.runelite.ItemIdentityIntegrityRules;
import com.cardrestricted.runelite.UnverifiedActionCompatibilityRules;
import com.cardrestricted.runelite.InventoryConsumptionIntegrityRules;
import com.cardrestricted.runelite.LockedNpcDialogueGuard;
import com.cardrestricted.runelite.NpcRewardIntegrityRules;
import com.cardrestricted.runelite.NpcServiceInterfaceAuthorization;
import com.cardrestricted.runelite.PassiveInventoryUsageRules;
import com.cardrestricted.runelite.ProductionInventoryIntegrityRules;
import com.cardrestricted.runelite.SimpleRestrictionService;
import com.cardrestricted.runelite.RuneEntitlementPolicy;
import com.cardrestricted.runelite.SpellRuneRequirementResolver;
import com.cardrestricted.runelite.SessionLifecycleRules;
import com.cardrestricted.runelite.ShopCurrencyRules;
import com.cardrestricted.runelite.ShopInteractionAuthorization;
import com.cardrestricted.runelite.RestrictionMessageFormatter;
import com.cardrestricted.runelite.RestrictionMessageLimiter;
import com.cardrestricted.runelite.RestrictionRuntimeGate;
import com.cardrestricted.runelite.RewardContainerIntegrityRules;
import com.cardrestricted.runelite.SpellbookWidgetRules;
import com.cardrestricted.runelite.StorageInteractionRules;
import com.cardrestricted.runelite.StorageInteractionAuthorization;
import com.cardrestricted.runelite.TransactionInterfaceIntegrityRules;
import com.cardrestricted.session.CollectionSessionService;
import com.cardrestricted.session.SessionFailureCode;
import com.cardrestricted.session.SessionSnapshot;
import com.cardrestricted.session.SessionStatus;
import com.cardrestricted.ui.AchievementToastOverlay;
import com.cardrestricted.ui.CardRestrictedAccountPanel;
import com.cardrestricted.ui.CollectionSetupHandler;
import com.cardrestricted.ui.LockedNpcOverlay;
import com.cardrestricted.ui.LockedItemOverlay;
import com.cardrestricted.ui.LockedEntityVisualIndex;
import com.cardrestricted.ui.PackActionHandler;
import com.cardrestricted.ui.PackPresentationMouseAdapter;
import com.cardrestricted.ui.PackPresentationOverlay;
import com.cardrestricted.ui.PluginIcon;
import com.cardrestricted.ui.RuneliteCardArtworkProvider;
import com.cardrestricted.ui.WikiArtworkDiskCache;
import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "Card Locked",
    description = "Card-driven restricted account challenge with collectible progression",
    tags = {"cards", "collection", "restricted", "challenge"})
public final class CardRestrictedAccountPlugin extends Plugin
    implements KeyListener
{
    private static final int QUEST_BASELINE_DELAY_TICKS = 2;
    private static final int QUEST_REWARD_CHECK_INTERVAL_TICKS = 20;
    private static final int QUEST_TRACKER_SCRIPT_BUDGET_PER_TICK = 12;
    private static final int NONCOMBAT_XP_BATCH_SIZE = 1_000;
    /** Packed widget group used by the standard spellbook interface. */

    private final F2pNpcKillRewardPolicy npcKillRewardPolicy =
        new F2pNpcKillRewardPolicy();
    private final NpcKillCreditTracker npcKillCreditTracker =
        new NpcKillCreditTracker();
    private final Map<String, PointAward> pendingNpcPointAwards =
        new LinkedHashMap<>();
    private final F2pQuestCompletionRewardPolicy questRewardPolicy =
        new F2pQuestCompletionRewardPolicy();
    private final SkillLevelRewardPolicy skillLevelRewardPolicy =
        new SkillLevelRewardPolicy();
    private final ClueCompletionMessageParser clueCompletionMessageParser =
        new ClueCompletionMessageParser();
    private final ClueCompletionRewardPolicy clueCompletionRewardPolicy =
        new ClueCompletionRewardPolicy();

    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private ClientThread clientThread;

    @Inject
    private CardRestrictedAccountConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ConfigManager configManager;

    private AudioCueManager audioCueManager;
    private CardCatalogue catalogue;
    private volatile CollectionSessionService sessionService;
    private CardRestrictedAccountPanel panel;
    private NavigationButton navigationButton;
    private PackPresentationController packPresentationController;
    private PackPresentationOverlay packPresentationOverlay;
    private AchievementToastController achievementToastController;
    private AchievementToastOverlay achievementToastOverlay;
    private PackPresentationMouseAdapter packPresentationMouseAdapter;
    private LockedItemOverlay lockedItemOverlay;
    private LockedNpcOverlay lockedNpcOverlay;
    private RuneliteCardArtworkProvider artworkProvider;
    private WikiArtworkDiskCache wikiArtworkDiskCache;
    private java.util.concurrent.ExecutorService artworkWarmupExecutor;
    private PluginPaths pluginPaths;
    private SimpleRestrictionService restrictionService;
    private SpellRuneRequirementResolver spellRuneRequirementResolver;
    private FoilEntitlementResolver foilEntitlementResolver;
    private volatile java.util.Set<String> activeUsableCardIds =
        java.util.Collections.emptySet();
    private UUID appliedCollectionShapeId;
    private java.util.Set<String> appliedOwnedCardIdsIdentity =
        java.util.Collections.emptySet();
    private java.util.Set<String> appliedFoilCardIdsIdentity =
        java.util.Collections.emptySet();
    private volatile int activeUniqueCardCount;
    private volatile boolean activeIntegrityProfile;
    private volatile boolean enforcementActive;
    private volatile boolean restrictionStatePending;
    private volatile boolean autocastVerifiedForSession;
    private volatile boolean autocastSelectionPending;
    private volatile int autocastSelectionPendingTick = Integer.MIN_VALUE;
    private volatile long activeAccountHash = -1L;
    private volatile String activeGameSessionId = "";
    private final Map<Skill, Integer> noncombatSessionBaselines =
        new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> latestNoncombatXp =
        new EnumMap<>(Skill.class);
    private final Set<Skill> pendingNoncombatXpCommits =
        new LinkedHashSet<>();
    private final Map<Skill, Integer> skillLevelSessionBaselines =
        new EnumMap<>(Skill.class);
    private final QuestCompletionTracker questCompletionTracker =
        new QuestCompletionTracker();
    private final ShopInteractionAuthorization shopInteractionAuthorization =
        new ShopInteractionAuthorization();
    private final StorageInteractionAuthorization storageInteractionAuthorization =
        new StorageInteractionAuthorization();
    private final GrandExchangeInteractionAuthorization
        grandExchangeInteractionAuthorization =
            new GrandExchangeInteractionAuthorization();
    private final NpcServiceInterfaceAuthorization
        npcServiceInterfaceAuthorization =
            new NpcServiceInterfaceAuthorization();
    private final LockedNpcDialogueGuard lockedNpcDialogueGuard =
        new LockedNpcDialogueGuard();
    private final RestrictionMessageLimiter restrictionMessageLimiter =
        new RestrictionMessageLimiter();
    private QuestCompletionIndex questCompletionIndex;
    private volatile Set<String> completedQuestKeys = Collections.emptySet();
    private boolean questBaselineReady;
    private int questBaselineTicksRemaining;
    private boolean questRewardStateDirty;
    private int questRewardCheckCooldown;
    private boolean questTrackerScanActive;
    private boolean questTrackerRescanRequested;
    private int questTrackerScanCursor;
    private Set<String> questTrackerScanCompleted = new LinkedHashSet<>();
    private boolean restoringIntegrityConfig;
    private volatile boolean clientSessionSuspended;
    private volatile boolean startupComplete;
    private volatile SessionStatus lastSessionStatus = SessionStatus.LOGGED_OUT;
    private LocalDiagnosticLog diagnosticLog;
    private IntegrityTraceLog integrityTraceLog;
    private DiagnosticReportExporter diagnosticExporter;
    private LifecycleCleanupRegistry lifecycleCleanup;
    private ManagedTaskScope taskScope;
    private ExecutorService mutationExecutor;
    private final AtomicReference<SessionSnapshot> pendingPanelSnapshot =
        new AtomicReference<>();
    private final AtomicBoolean panelRenderScheduled = new AtomicBoolean();
    private final AtomicBoolean nexusExchangeInFlight = new AtomicBoolean();

    @Provides
    CardRestrictedAccountConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(CardRestrictedAccountConfig.class);
    }

    @Override
    protected void startUp()
    {
        if (startupComplete || lifecycleCleanup != null || taskScope != null)
        {
            startupComplete = false;
            releaseRuntimeResources();
            clearRuntimeReferences();
        }
        diagnosticLog = new LocalDiagnosticLog();
        integrityTraceLog = new IntegrityTraceLog();
        lifecycleCleanup = new LifecycleCleanupRegistry();
        mutationExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(
                runnable,
                "card-locked-profile-mutations");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        taskScope = new ManagedTaskScope(
            mutationExecutor,
            this::handleTaskFailure);
        startupComplete = false;
        lastSessionStatus = SessionStatus.LOGGED_OUT;
        recordDiagnostic(DiagnosticEventCode.STARTUP_BEGIN, DiagnosticOperation.STARTUP);
        try
        {
            startUpInternal();
            startupComplete = true;
            recordDiagnostic(
                DiagnosticEventCode.STARTUP_COMPLETE,
                DiagnosticOperation.STARTUP);
        }
        catch (Throwable failure)
        {
            recordDiagnosticFailure(
                DiagnosticEventCode.STARTUP_FAILED,
                DiagnosticOperation.STARTUP,
                failure);
            releaseRuntimeResources();
            attemptEmergencyDiagnosticExport();
            clearRuntimeReferences();
            if (failure instanceof RuntimeException)
            {
                throw (RuntimeException) failure;
            }
            if (failure instanceof Error)
            {
                throw (Error) failure;
            }
            throw new IllegalStateException("Card Locked startup failed.", failure);
        }
    }

    private void startUpInternal()
    {
        audioCueManager = new AudioCueManager();
        AudioCueManager startedAudio = audioCueManager;
        lifecycleCleanup.register("audio", startedAudio::shutdown);
        catalogue = MembersCatalogue.create();
        FoilRewardRegistry foilRewardRegistry = FoilRewardRegistry.load(
            getClass().getClassLoader(),
            catalogue);
        foilEntitlementResolver = new FoilEntitlementResolver(
            catalogue,
            foilRewardRegistry);
        questCompletionIndex = QuestCompletionIndex.load(getClass().getClassLoader());
        restrictionService = new SimpleRestrictionService(
            new InteractionFamilyIndex(catalogue));
        spellRuneRequirementResolver = new SpellRuneRequirementResolver(
            itemManager);
        pluginPaths = new PluginPaths(RuneLite.RUNELITE_DIR.toPath());
        try
        {
            pluginPaths.prepareAndMigrate();
        }
        catch (java.io.IOException exception)
        {
            recordDiagnosticFailure(
                DiagnosticEventCode.STARTUP_FAILED,
                DiagnosticOperation.STORAGE_PREPARE,
                exception);
            throw new IllegalStateException(
                "Unable to prepare Card Locked storage.",
                exception);
        }
        diagnosticExporter = new DiagnosticReportExporter(
            pluginPaths.diagnosticsDirectory());
        packPresentationController = new PackPresentationController();
        PackPresentationController startedPackController =
            packPresentationController;
        lifecycleCleanup.register(
            "pack-controller",
            startedPackController::reset);
        wikiArtworkDiskCache = new WikiArtworkDiskCache(
            pluginPaths.wikiArtworkDirectory(),
            () -> false);
        WikiArtworkDiskCache startedWikiCache = wikiArtworkDiskCache;
        lifecycleCleanup.register("wiki-artwork-cache", startedWikiCache::close);
        artworkWarmupExecutor = java.util.concurrent.Executors
            .newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                    runnable,
                    "card-locked-artwork-warmup");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
        java.util.concurrent.ExecutorService startedArtworkExecutor =
            artworkWarmupExecutor;
        lifecycleCleanup.register(
            "artwork-warmup-executor",
            startedArtworkExecutor::shutdownNow);
        artworkProvider = new RuneliteCardArtworkProvider(
            itemManager,
            wikiArtworkDiskCache,
            catalogue);
        RuneliteCardArtworkProvider startedArtworkProvider = artworkProvider;
        lifecycleCleanup.register(
            "artwork-provider",
            startedArtworkProvider::close);
        panel = new CardRestrictedAccountPanel(
            catalogue,
            foilEntitlementResolver,
            new CollectionSetupHandler()
            {
                @Override
                public void createCollection(ProfileSetupOptions options)
                {
                    CardRestrictedAccountPlugin.this.createCollection(options);
                }

                @Override
                public void disableIntegrity()
                {
                    CardRestrictedAccountPlugin.this.disableIntegrity();
                }

                @Override
                public void resetProfile()
                {
                    CardRestrictedAccountPlugin.this.resetProfile();
                }

                @Override
                public void exportBackup(java.nio.file.Path destination)
                {
                    CardRestrictedAccountPlugin.this.exportBackup(destination);
                }

                @Override
                public void importBackup(java.nio.file.Path source)
                {
                    CardRestrictedAccountPlugin.this.importBackup(source);
                }

                @Override
                public void restorePreviousBackup()
                {
                    CardRestrictedAccountPlugin.this.restorePreviousBackup();
                }

                @Override
                public void exportDiagnostics()
                {
                    CardRestrictedAccountPlugin.this.exportDiagnostics();
                }
            },
            new PackActionHandler()
            {
                @Override
                public void redeemStarterPack()
                {
                    CardRestrictedAccountPlugin.this
                        .redeemStarterPack();
                }

                @Override
                public void purchaseStandardPack()
                {
                    CardRestrictedAccountPlugin.this
                        .purchaseStandardPack();
                }

                @Override
                public void purchaseUncommonPlusPack()
                {
                    CardRestrictedAccountPlugin.this.purchaseUncommonPlusPack();
                }

                @Override
                public void purchaseExplorerPack()
                {
                    CardRestrictedAccountPlugin.this.purchaseExplorerPack();
                }

                @Override
                public void purchaseRareHunterPack()
                {
                    CardRestrictedAccountPlugin.this
                        .purchaseRareHunterPack();
                }

                @Override
                public void purchaseAdventurePack()
                {
                    CardRestrictedAccountPlugin.this.purchaseAdventurePack();
                }

                @Override
                public void purchaseNexusCache()
                {
                    CardRestrictedAccountPlugin.this.purchaseNexusCache();
                }

                @Override
                public void purchaseCollectorPack()
                {
                    CardRestrictedAccountPlugin.this.purchaseCollectorPack();
                }

                @Override
                public void redeemInitiateFoilPack()
                {
                    CardRestrictedAccountPlugin.this.redeemInitiateFoilPack();
                }

                @Override
                public void redeemHeroPack()
                {
                    CardRestrictedAccountPlugin.this.redeemHeroPack();
                }

                @Override
                public void redeemNoblePack()
                {
                    CardRestrictedAccountPlugin.this.redeemNoblePack();
                }

                @Override
                public void redeemLegendPack()
                {
                    CardRestrictedAccountPlugin.this.redeemLegendPack();
                }

                @Override
                public void redeemMythicalPack()
                {
                    CardRestrictedAccountPlugin.this.redeemMythicalPack();
                }

                @Override
                public void redeemGodsPack()
                {
                    CardRestrictedAccountPlugin.this.redeemGodsPack();
                }

                @Override
                public void purchaseNoncombatNpcPack()
                {
                    CardRestrictedAccountPlugin.this
                        .purchaseNoncombatNpcPack();
                }

                @Override
                public void purchaseAttackableNpcPack()
                {
                    CardRestrictedAccountPlugin.this
                        .purchaseAttackableNpcPack();
                }

                @Override
                public void purchaseFoilTestPack()
                {
                    CardRestrictedAccountPlugin.this
                        .purchaseFoilTestPack();
                }

                @Override
                public void purchasePremiumFoilTestPack()
                {
                    CardRestrictedAccountPlugin.this
                        .purchasePremiumFoilTestPack();
                }

                @Override
                public void purchaseTierFoilTestPack()
                {
                    CardRestrictedAccountPlugin.this
                        .purchaseTierFoilTestPack();
                }

                @Override
                public void purchaseArmourFoilTestPack()
                {
                    CardRestrictedAccountPlugin.this
                        .purchaseArmourFoilTestPack();
                }

                @Override
                public void purchaseBossFoilTestPack()
                {
                    CardRestrictedAccountPlugin.this
                        .purchaseBossFoilTestPack();
                }

                @Override
                public void purchaseIngredientFoilTestPack()
                {
                    CardRestrictedAccountPlugin.this
                        .purchaseIngredientFoilTestPack();
                }

                @Override
                public void purchaseSignatureFoilTestPack()
                {
                    CardRestrictedAccountPlugin.this
                        .purchaseSignatureFoilTestPack();
                }

                @Override
                public void purchaseNpcRelationshipFoilTestPack()
                {
                    CardRestrictedAccountPlugin.this
                        .purchaseNpcRelationshipFoilTestPack();
                }

                @Override
                public boolean isTestingMode()
                {
                    return isDeveloperTestingAllowed();
                }

                @Override
                public void refreshQuestStatus()
                {
                    CardRestrictedAccountPlugin.this
                        .requestQuestStatusRefresh();
                }

                @Override
                public void exchangeNexusCard(Rarity rarity)
                {
                    CardRestrictedAccountPlugin.this
                        .exchangeNexusCard(rarity);
                }

                @Override
                public boolean isNexusExchangeBlocked()
                {
                    return CardRestrictedAccountPlugin.this
                        .isNexusExchangeBlocked();
                }
            },
            artworkProvider);
        CardRestrictedAccountPanel startedPanel = panel;
        lifecycleCleanup.register(
            "side-panel",
            startedPanel::closeAuxiliaryWindows);
        artworkProvider.setArtworkAvailableListener(cardId ->
            SwingUtilities.invokeLater(() -> {
                CardRestrictedAccountPanel activePanel = panel;
                if (activePanel != null)
                {
                    activePanel.onArtworkAvailable(cardId);
                }
            }));
        wikiArtworkDiskCache.prepareAsync(
            artworkWarmupExecutor,
            () -> {
                RuneliteCardArtworkProvider activeArtwork = artworkProvider;
                if (activeArtwork != null)
                {
                    activeArtwork.invalidateForOfflinePackReady();
                }
                SwingUtilities.invokeLater(() -> {
                    CardRestrictedAccountPanel activePanel = panel;
                    if (activePanel != null)
                    {
                        activePanel.onArtworkPackReady();
                    }
                });
            });
        packPresentationOverlay = new PackPresentationOverlay(
            client,
            config,
            catalogue,
            packPresentationController,
            artworkProvider,
            executor,
            this::playDealCardCue);
        achievementToastController = new AchievementToastController();
        achievementToastOverlay = new AchievementToastOverlay(
            client,
            config,
            achievementToastController,
            () -> packPresentationOverlay != null
                && packPresentationOverlay.isActive());
        packPresentationMouseAdapter =
            new PackPresentationMouseAdapter(
                packPresentationOverlay,
                this::handlePackPresentationSelection);
        overlayManager.add(packPresentationOverlay);
        PackPresentationOverlay startedPackOverlay = packPresentationOverlay;
        lifecycleCleanup.register("pack-overlay", () -> {
            startedPackOverlay.stopAnimationLoop();
            overlayManager.remove(startedPackOverlay);
        });
        overlayManager.add(achievementToastOverlay);
        AchievementToastOverlay startedToastOverlay = achievementToastOverlay;
        lifecycleCleanup.register("achievement-overlay", () -> {
            startedToastOverlay.stopAnimationLoop();
            startedToastOverlay.clear();
            overlayManager.remove(startedToastOverlay);
        });
        LockedEntityVisualIndex lockedVisualIndex =
            new LockedEntityVisualIndex(catalogue);
        lockedItemOverlay = new LockedItemOverlay(
            lockedVisualIndex,
            () -> activeUsableCardIds,
            () -> isRestrictionRuntimeActive()
                && config.showLockedItemOverlay(),
            itemId -> itemManager.getItemComposition(itemId).getName());
        lockedNpcOverlay = new LockedNpcOverlay(
            client,
            lockedVisualIndex,
            () -> activeUsableCardIds,
            () -> isRestrictionRuntimeActive()
                && config.showLockedNpcOverlay(),
            config::showLockedNpcLabels);
        overlayManager.add(lockedItemOverlay);
        LockedItemOverlay startedItemOverlay = lockedItemOverlay;
        lifecycleCleanup.register("locked-item-overlay", () -> {
            startedItemOverlay.deactivate();
            overlayManager.remove(startedItemOverlay);
        });
        overlayManager.add(lockedNpcOverlay);
        LockedNpcOverlay startedNpcOverlay = lockedNpcOverlay;
        lifecycleCleanup.register("locked-npc-overlay", () -> {
            startedNpcOverlay.deactivate();
            overlayManager.remove(startedNpcOverlay);
        });
        mouseManager.registerMouseListener(
            packPresentationMouseAdapter);
        mouseManager.registerMouseWheelListener(
            packPresentationMouseAdapter);
        PackPresentationMouseAdapter startedMouseAdapter =
            packPresentationMouseAdapter;
        lifecycleCleanup.register("pack-mouse-listeners", () -> {
            mouseManager.unregisterMouseListener(startedMouseAdapter);
            mouseManager.unregisterMouseWheelListener(startedMouseAdapter);
        });
        keyManager.registerKeyListener(this);
        lifecycleCleanup.register(
            "plugin-key-listener",
            () -> keyManager.unregisterKeyListener(this));
        navigationButton = NavigationButton.builder()
            .tooltip("Card Locked")
            .icon(PluginIcon.create())
            .priority(6)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navigationButton);
        NavigationButton startedNavigation = navigationButton;
        lifecycleCleanup.register(
            "navigation-button",
            () -> clientToolbar.removeNavigation(startedNavigation));

        enforcementActive = false;
        restrictionStatePending = false;
        autocastVerifiedForSession = false;
        autocastSelectionPending = false;
        autocastSelectionPendingTick = Integer.MIN_VALUE;
        clientSessionSuspended = false;

        if (client.getGameState() == GameState.LOGGED_IN)
        {
            openLoggedInSession();
        }
    }

    @Override
    protected void shutDown()
    {
        recordDiagnostic(
            DiagnosticEventCode.SHUTDOWN_BEGIN,
            DiagnosticOperation.SHUTDOWN);
        startupComplete = false;
        releaseRuntimeResources();
        recordDiagnostic(
            DiagnosticEventCode.SHUTDOWN_COMPLETE,
            DiagnosticOperation.SHUTDOWN);
        clearRuntimeReferences();
    }

    private void releaseRuntimeResources()
    {
        ManagedTaskScope closingTasks = taskScope;
        taskScope = null;
        if (closingTasks != null)
        {
            // Plugin lifecycle callbacks run on Swing's EDT. Cancel owned work
            // immediately; do not make the UI wait for filesystem/image tasks
            // to acknowledge interruption.
            closingTasks.close(Duration.ZERO);
        }

        boolean mutationsTerminated = true;
        ExecutorService closingMutationExecutor = mutationExecutor;
        mutationExecutor = null;
        if (closingMutationExecutor != null)
        {
            closingMutationExecutor.shutdownNow();
            mutationsTerminated = closingMutationExecutor.isTerminated();
        }

        CollectionSessionService closingSession = sessionService;
        sessionService = null;
        activeAccountHash = -1L;
        activeGameSessionId = "";
        if (closingSession != null && mutationsTerminated)
        {
            try
            {
                closingSession.close();
            }
            catch (RuntimeException failure)
            {
                recordDiagnosticFailure(
                    DiagnosticEventCode.CLEANUP_FAILED,
                    DiagnosticOperation.CLEANUP,
                    failure);
            }
        }

        if (artworkWarmupExecutor != null)
        {
            artworkWarmupExecutor.shutdownNow();
        }

        LifecycleCleanupRegistry cleanup = lifecycleCleanup;
        lifecycleCleanup = null;
        if (cleanup != null)
        {
            for (LifecycleCleanupRegistry.CleanupFailure failure
                : cleanup.closeAndCollect())
            {
                recordDiagnosticFailure(
                    DiagnosticEventCode.CLEANUP_FAILED,
                    DiagnosticOperation.CLEANUP,
                    failure.getFailure());
            }
        }

        resetRuntimeTracking();
        enforcementActive = false;
        restrictionStatePending = false;
        autocastVerifiedForSession = false;
        autocastSelectionPending = false;
        autocastSelectionPendingTick = Integer.MIN_VALUE;
        clientSessionSuspended = false;
        activeUsableCardIds = java.util.Collections.emptySet();
        appliedCollectionShapeId = null;
        appliedOwnedCardIdsIdentity = java.util.Collections.emptySet();
        appliedFoilCardIdsIdentity = java.util.Collections.emptySet();
        lastSessionStatus = SessionStatus.LOGGED_OUT;
        pendingPanelSnapshot.set(null);
        panelRenderScheduled.set(false);
    }

    private void clearRuntimeReferences()
    {
        navigationButton = null;
        panel = null;
        packPresentationController = null;
        packPresentationOverlay = null;
        achievementToastController = null;
        achievementToastOverlay = null;
        packPresentationMouseAdapter = null;
        lockedItemOverlay = null;
        lockedNpcOverlay = null;
        wikiArtworkDiskCache = null;
        artworkWarmupExecutor = null;
        diagnosticExporter = null;
        integrityTraceLog = null;
        pluginPaths = null;
        artworkProvider = null;
        restrictionService = null;
        spellRuneRequirementResolver = null;
        foilEntitlementResolver = null;
        sessionService = null;
        catalogue = null;
        audioCueManager = null;
        questCompletionIndex = null;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        switch (SessionLifecycleRules.transitionFor(event.getGameState()))
        {
            case OPEN_OR_RESUME:
                openLoggedInSession();
                break;
            case CLOSE:
                closeSession();
                break;
            case SUSPEND:
            default:
                suspendLoggedInRuntime();
                break;
        }
    }

    @Subscribe(priority = -1000.0f)
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        SimpleRestrictionService service = restrictionService;
        if (service == null
            || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        RestrictionPresetSettings runtimeSettings =
            runtimeRestrictionSettings();

        MenuEntry entry = event.getMenuEntry();
        String option = InteractionIntegrityRules.effectiveOption(
            entry == null ? null : entry.getOption(),
            event.getMenuOption());
        String entryTarget = entry == null ? null : entry.getTarget();
        String eventTarget = event.getMenuTarget();
        String target = InteractionIntegrityRules.effectiveTarget(
            entryTarget,
            eventTarget);
        MenuAction action = InteractionIntegrityRules.effectiveAction(
            entry == null ? null : entry.getType(),
            event.getMenuAction());
        Widget eventWidget = event.getWidget();
        Widget entryWidget = entry == null ? null : entry.getWidget();
        int entryParam1 = entry == null ? -1 : entry.getParam1();
        boolean bankNavigation = StorageInteractionRules.isBankTabNavigation(
            action,
            option,
            target,
            widgetId(eventWidget),
            event.getWidgetId(),
            widgetId(entryWidget),
            entryParam1,
            event.getParam1());
        boolean pendingStorageRecovery =
            StorageInteractionRules.isPendingRecoveryAction(
                action,
                option,
                widgetId(eventWidget),
                event.getWidgetId(),
                widgetId(entryWidget),
                entryParam1,
                event.getParam1());
        if (bankNavigation)
        {
            recordIntegrityTrace(
                action,
                option,
                IntegrityTraceDecision.BYPASSED_SAFE,
                IntegrityTraceReason.BANK_NAVIGATION,
                event.getWidgetId(),
                event.getItemId() >= 0 ? event.getItemId() : event.getId());
            return;
        }

        InteractionTargetIntegrityRules.NpcTargetResolution npcTarget =
            InteractionTargetIntegrityRules.resolveNpcTarget(
                entryTarget,
                eventTarget,
                service::hasKnownOrAmbiguousNpcIdentity);
        String fallbackNpcName = npcTarget.getSelectedName();
        boolean knownNpcTarget = npcTarget.isKnownTrackedTarget();
        NPC attachedNpc = npcFrom(
            entry,
            action,
            event.getId(),
            fallbackNpcName,
            knownNpcTarget);
        int traceEntityId = attachedNpc != null
            ? attachedNpc.getId()
            : (event.getItemId() >= 0 ? event.getItemId() : event.getId());
        if (restrictionStatePending)
        {
            if (!InteractionIntegrityRules.shouldBlockWhileStatePending(
                action,
                option,
                attachedNpc != null,
                knownNpcTarget,
                pendingStorageRecovery))
            {
                recordIntegrityTrace(
                    action,
                    option,
                    IntegrityTraceDecision.BYPASSED_SAFE,
                    pendingStorageRecovery
                        ? IntegrityTraceReason.STORAGE_RECOVERY
                        : IntegrityTraceReason.STATE_PENDING_SAFE,
                    event.getWidgetId(),
                    traceEntityId);
                return;
            }
            SimpleRestrictionService.RestrictionDecision pendingDecision =
                SimpleRestrictionService.RestrictionDecision.block(
                    java.util.Collections.emptySet(),
                    "Card permissions are still loading or being replaced. This action was blocked to prevent a restriction bypass.");
            // The authoritative profile and therefore its enforcement mode are
            // not known during this window. Functional actions must always be
            // consumed rather than trusting a stale persisted AUDIT/DISABLED
            // preference from a different profile.
            recordIntegrityTrace(
                action,
                option,
                IntegrityTraceDecision.BLOCKED,
                IntegrityTraceReason.STATE_PENDING_BLOCKED,
                event.getWidgetId(),
                traceEntityId);
            event.consume();
            showBlockedMessage(pendingDecision);
            return;
        }

        if (runtimeSettings.getRestrictionMode()
            == com.cardrestricted.domain.RestrictionMode.DISABLED)
        {
            recordIntegrityTrace(
                action,
                option,
                IntegrityTraceDecision.NOT_EVALUATED,
                IntegrityTraceReason.RESTRICTION_DISABLED,
                event.getWidgetId(),
                traceEntityId);
            return;
        }

        if (!isRestrictionRuntimeActive())
        {
            recordIntegrityTrace(
                action,
                option,
                IntegrityTraceDecision.NOT_EVALUATED,
                IntegrityTraceReason.RUNTIME_INACTIVE,
                event.getWidgetId(),
                traceEntityId);
            return;
        }

        SimpleRestrictionService.RestrictionDecision decision =
            evaluateRestriction(
                service,
                runtimeSettings,
                event.getItemId(),
                event.getId(),
                event.getWidgetId(),
                entry,
                event.getWidget(),
                event.isItemOp(),
                action,
                option,
                target,
                entryTarget,
                eventTarget,
                event.getParam1(),
                attachedNpc,
                fallbackNpcName,
                npcTarget.hasConflictingTrackedTargets());
        if (!decision.isBlocked())
        {
            boolean npcInteraction = InteractionIntegrityRules.isNpcInteraction(
                action,
                attachedNpc != null,
                knownNpcTarget);
            boolean npcLocked = npcInteraction
                && (npcTarget.hasConflictingTrackedTargets()
                    || (attachedNpc != null
                        && service.hasConflictingNpcIdentity(
                            attachedNpc.getId(),
                            fallbackNpcName.isEmpty()
                                ? attachedNpc.getName()
                                : fallbackNpcName))
                    || service.isNpcLocked(
                        attachedNpc == null ? -1 : attachedNpc.getId(),
                        attachedNpc == null
                            ? fallbackNpcName
                            : attachedNpc.getName(),
                        activeUsableCardIds));
            if (npcInteraction && SimpleRestrictionService.isTalkOption(option))
            {
                lockedNpcDialogueGuard.observeNpcTalk(
                    npcLocked,
                    client.getTickCount());
            }
            else if (npcInteraction)
            {
                lockedNpcDialogueGuard.observeNonTalkNpcInteraction();
            }
            String dialogueText = dialogueContextText(
                eventWidget,
                entryWidget);
            int currentTick = client.getTickCount();
            int eventWidgetPackedId = widgetId(eventWidget);
            int entryWidgetPackedId = widgetId(entryWidget);
            boolean shopDialogueChoice =
                shopInteractionAuthorization.observeAllowedDialogueChoice(
                    action,
                    option,
                    target,
                    dialogueText,
                    currentTick,
                    eventWidgetPackedId,
                    event.getWidgetId(),
                    entryWidgetPackedId,
                    entryParam1,
                    event.getParam1());
            boolean storageDialogueChoice =
                storageInteractionAuthorization.observeAllowedDialogueChoice(
                    option,
                    target,
                    dialogueText,
                    currentTick,
                    eventWidgetPackedId,
                    event.getWidgetId(),
                    entryWidgetPackedId,
                    entryParam1,
                    event.getParam1());
            boolean exchangeDialogueChoice =
                grandExchangeInteractionAuthorization
                    .observeAllowedDialogueChoice(
                        option,
                        target,
                        dialogueText,
                        currentTick,
                        eventWidgetPackedId,
                        event.getWidgetId(),
                        entryWidgetPackedId,
                        entryParam1,
                        event.getParam1());
            boolean serviceDialogueChoice =
                npcServiceInterfaceAuthorization.observeAllowedDialogueChoice(
                    option,
                    target,
                    dialogueText,
                    currentTick,
                    eventWidgetPackedId,
                    event.getWidgetId(),
                    entryWidgetPackedId,
                    entryParam1,
                    event.getParam1());
            if (!shopDialogueChoice)
            {
                shopInteractionAuthorization.observeAllowedWorldInteraction(
                    action,
                    option,
                    target,
                    currentTick,
                    npcInteraction,
                    !npcLocked);
            }
            if (!storageDialogueChoice)
            {
                storageInteractionAuthorization.observeAllowedWorldInteraction(
                    action,
                    option,
                    target,
                    currentTick,
                    npcInteraction,
                    !npcLocked);
            }
            if (!exchangeDialogueChoice)
            {
                grandExchangeInteractionAuthorization
                    .observeAllowedWorldInteraction(
                        action,
                        option,
                        target,
                        currentTick,
                        npcInteraction,
                        !npcLocked);
            }
            if (!serviceDialogueChoice)
            {
                npcServiceInterfaceAuthorization
                    .observeAllowedWorldInteraction(
                        action,
                        option,
                        target,
                        currentTick,
                        npcInteraction,
                        !npcLocked);
            }
            recordIntegrityTrace(
                action,
                option,
                IntegrityTraceDecision.ALLOWED,
                IntegrityTraceReason.POLICY_ALLOWED,
                event.getWidgetId(),
                traceEntityId);
            return;
        }
        if (runtimeSettings.getRestrictionMode()
            == com.cardrestricted.domain.RestrictionMode.AUDIT_ONLY)
        {
            recordIntegrityTrace(
                action,
                option,
                IntegrityTraceDecision.ALLOWED,
                IntegrityTraceReason.AUDIT_ONLY_WOULD_BLOCK,
                event.getWidgetId(),
                traceEntityId);
            return;
        }

        recordIntegrityTrace(
            action,
            option,
            IntegrityTraceDecision.BLOCKED,
            IntegrityTraceReason.POLICY_BLOCKED,
            event.getWidgetId(),
            traceEntityId);
        event.consume();
        showBlockedMessage(decision);
    }

    private SimpleRestrictionService.RestrictionDecision evaluateRestriction(
        SimpleRestrictionService service,
        RestrictionPresetSettings runtimeSettings,
        int eventItemId,
        int eventIdentifier,
        int eventWidgetId,
        MenuEntry entry,
        Widget explicitWidget,
        boolean itemOperation,
        MenuAction menuAction,
        String option,
        String target,
        String entryTarget,
        String eventTarget,
        int eventParam1,
        NPC resolvedNpc,
        String fallbackNpcName,
        boolean conflictingTrackedNpcTargets)
    {
        Widget entryWidget = entry == null ? null : entry.getWidget();
        int entryParam1 = entry == null ? -1 : entry.getParam1();
        boolean bankNavigation = StorageInteractionRules.isBankTabNavigation(
            menuAction,
            option,
            target,
            widgetId(explicitWidget),
            eventWidgetId,
            widgetId(entryWidget),
            entryParam1,
            eventParam1);
        if (bankNavigation)
        {
            return SimpleRestrictionService.RestrictionDecision.allow();
        }
        boolean safeStorageItemAction =
            StorageInteractionRules.isSafeStorageItemAction(
                menuAction,
                option,
                runtimeSettings.isAllowLockedItemBanking(),
                widgetId(explicitWidget),
                eventWidgetId,
                widgetId(entryWidget),
                entryParam1,
                eventParam1);

        Widget selectedWidget = client.getSelectedWidget();
        boolean directSpellbookContext =
            SpellbookWidgetRules.isDirectSpellbookClick(
                widgetId(explicitWidget),
                eventWidgetId,
                widgetId(entryWidget),
                entry == null ? -1 : entry.getParam1(),
                eventParam1,
                widgetId(selectedWidget));
        boolean recognisedSpellTarget =
            SpellRuneRequirementResolver.isRecognisedSpellTarget(entryTarget)
                || SpellRuneRequirementResolver.isRecognisedSpellTarget(
                    eventTarget);
        boolean spellAction = SpellbookWidgetRules.isSpellCastingOption(option)
            || SpellRuneRequirementResolver.isRecognisedSpellOption(option)
            || (directSpellbookContext
                && InteractionContextRules.isSpellTargetAction(menuAction))
            || (recognisedSpellTarget
                && SpellbookWidgetRules.isPotentialRewrittenSpellAction(
                    menuAction));

        String widgetText = dialogueContextText(
            explicitWidget,
            entryWidget);
        if (runtimeSettings.isRestrictNpcCombat()
            && lockedNpcDialogueGuard.shouldBlock(
                widgetId(explicitWidget),
                eventWidgetId,
                option,
                target,
                widgetText,
                client.getTickCount()))
        {
            return SimpleRestrictionService.RestrictionDecision.block(
                java.util.Collections.emptySet(),
                "This functional dialogue choice came from a locked NPC. Only ordinary conversation is permitted.");
        }

        if (runtimeSettings.isRestrictNpcCombat()
            && !shopInteractionAuthorization.isTransactionAuthorized(
                menuAction,
                option,
                client.getTickCount(),
                widgetId(explicitWidget),
                eventWidgetId,
                widgetId(entryWidget),
                entry == null ? -1 : entry.getParam1(),
                eventParam1))
        {
            return SimpleRestrictionService.RestrictionDecision.block(
                java.util.Collections.emptySet(),
                "This shop was not opened through a Card Locked-verified NPC or object interaction. Close it and reopen it through an unlocked source.");
        }

        if (runtimeSettings.isRestrictNpcCombat()
            && !storageInteractionAuthorization.isTransferAuthorized(
                menuAction,
                option,
                widgetId(explicitWidget),
                eventWidgetId,
                widgetId(entryWidget),
                entryParam1,
                eventParam1))
        {
            return SimpleRestrictionService.RestrictionDecision.block(
                java.util.Collections.emptySet(),
                "This storage interface was not opened through a Card Locked-verified NPC or object interaction. Close it and reopen it through an unlocked source.");
        }

        if (runtimeSettings.isRestrictNpcCombat()
            && !grandExchangeInteractionAuthorization
                .isInterfaceActionAuthorized(
                    menuAction,
                    option,
                    widgetId(explicitWidget),
                    eventWidgetId,
                    widgetId(entryWidget),
                    entryParam1,
                    eventParam1))
        {
            return SimpleRestrictionService.RestrictionDecision.block(
                java.util.Collections.emptySet(),
                "This Grand Exchange interface was not opened through a Card Locked-verified clerk or booth. Close it and reopen it through an unlocked source.");
        }

        if (runtimeSettings.isRestrictNpcCombat()
            && !npcServiceInterfaceAuthorization.isInterfaceActionAuthorized(
                menuAction,
                option,
                widgetId(explicitWidget),
                eventWidgetId,
                widgetId(entryWidget),
                entryParam1,
                eventParam1))
        {
            return SimpleRestrictionService.RestrictionDecision.block(
                java.util.Collections.emptySet(),
                "This specialist service interface was not opened through a Card Locked-verified NPC or object. Close it and reopen it through an unlocked source.");
        }

        if (runtimeSettings.isRestrictLockedItems())
        {
            if (TransactionInterfaceIntegrityRules.isPlayerTradeConfirmation(
                option,
                widgetId(explicitWidget),
                eventWidgetId,
                widgetId(entryWidget),
                entry == null ? -1 : entry.getParam1(),
                eventParam1))
            {
                Set<Integer> rawOfferedItems = itemContainerIds(
                    InventoryID.TRADE);
                if (!CoinMilestoneRules.coinsUnlocked(activeUniqueCardCount)
                    && CoinMilestoneRules.containsCoins(rawOfferedItems))
                {
                    return SimpleRestrictionService.RestrictionDecision.block(
                        java.util.Collections.emptySet(),
                        "Coins cannot be offered in a player trade before the 1,000-card milestone.");
                }
                Set<Integer> incomingTradeItems = itemContainerIds(
                    InventoryID.TRADEOTHER);
                if (!CoinMilestoneRules.coinsUnlocked(activeUniqueCardCount)
                    && CoinMilestoneRules.containsCoins(incomingTradeItems))
                {
                    return SimpleRestrictionService.RestrictionDecision.block(
                        java.util.Collections.emptySet(),
                        "Coins cannot be received in a player trade before the 1,000-card milestone.");
                }
                Set<Integer> offeredItems = CoinMilestoneRules
                    .cardRestrictedItemIds(
                        activeUniqueCardCount,
                        rawOfferedItems);
                SimpleRestrictionService.RestrictionDecision tradeDecision =
                    service.evaluateRequiredItems(
                        offeredItems,
                        activeUsableCardIds,
                        "A locked item remains in your trade offer. Remove it before accepting the trade.");
                if (tradeDecision.isBlocked())
                {
                    return tradeDecision;
                }
            }

            if (TransactionInterfaceIntegrityRules.isGrandExchangeSubmission(
                option,
                widgetId(explicitWidget),
                eventWidgetId,
                widgetId(entryWidget),
                entry == null ? -1 : entry.getParam1(),
                eventParam1))
            {
                if (!GrandExchangeOfferIntegrityRules.coinsPermitSubmission(
                    activeUniqueCardCount))
                {
                    return SimpleRestrictionService.RestrictionDecision.block(
                        java.util.Collections.emptySet(),
                        "Grand Exchange offers unlock with coins at 1,000 unique cards.");
                }
                Set<Integer> exchangeItems = grandExchangeOfferItemIds();
                if (!GrandExchangeOfferIntegrityRules.hasVerifiedSelectedItem(
                    exchangeItems))
                {
                    return SimpleRestrictionService.RestrictionDecision.block(
                        java.util.Collections.emptySet(),
                        "The selected Grand Exchange item could not be verified, so the offer was blocked.");
                }
                SimpleRestrictionService.RestrictionDecision exchangeDecision =
                    service.evaluateRequiredItems(
                        exchangeItems,
                        activeUsableCardIds,
                        "A locked item is selected in this Grand Exchange offer.");
                if (exchangeDecision.isBlocked())
                {
                    return exchangeDecision;
                }
            }

            Set<InventoryID> rewardInventories =
                RewardContainerIntegrityRules.rewardInventoriesForContext(
                    widgetId(explicitWidget),
                    eventWidgetId,
                    widgetId(entryWidget),
                    entryParam1,
                    eventParam1);
            boolean potentialRewardCollection =
                RewardContainerIntegrityRules.isPotentialRewardCollectionAction(
                    menuAction,
                    option);
            if (potentialRewardCollection && rewardInventories.isEmpty()
                && hasLoadedRewardContainer())
            {
                return SimpleRestrictionService.RestrictionDecision.block(
                    java.util.Collections.emptySet(),
                    "The reward interface identity could not be verified, so collection was blocked.");
            }
            if (potentialRewardCollection && !rewardInventories.isEmpty())
            {
                Set<Integer> rewardItems = rewardContainerItemIds(
                    rewardInventories);
                if (CoinMilestoneRules.shouldBlockCoinInteraction(
                    activeUniqueCardCount,
                    rewardItems,
                    option,
                    false))
                {
                    return SimpleRestrictionService.RestrictionDecision.block(
                        java.util.Collections.emptySet(),
                        "Coins unlock at 1,000 unique cards.");
                }
                rewardItems = CoinMilestoneRules.cardRestrictedItemIds(
                    activeUniqueCardCount,
                    rewardItems);
                SimpleRestrictionService.RestrictionDecision rewardDecision =
                    service.evaluateRequiredItems(
                        rewardItems,
                        activeUsableCardIds,
                        "This reward container contains a locked item. Acquire its card before collecting the reward.");
                if (rewardDecision.isBlocked())
                {
                    return rewardDecision;
                }
            }

            if (FurnaceInteractionRules.isFurnaceInteraction(
                option,
                target))
            {
                SimpleRestrictionService.RestrictionDecision furnaceDecision =
                    service.evaluateItems(
                        furnaceIngredientItemIds(),
                        "use",
                        activeUsableCardIds,
                        false);
                if (furnaceDecision.isBlocked())
                {
                    return SimpleRestrictionService.RestrictionDecision.block(
                        furnaceDecision.getRequiredCardIds(),
                        "A furnace ingredient's card has not been acquired.");
                }
            }

            Set<Integer> implicitItemIds = implicitParticipatingItemIds(
                option,
                target);
            if (!implicitItemIds.isEmpty())
            {
                SimpleRestrictionService.RestrictionDecision implicitDecision =
                    service.evaluateRequiredItems(
                        implicitItemIds,
                        activeUsableCardIds,
                        "A locked inventory or equipment item would be used by this action.");
                if (implicitDecision.isBlocked())
                {
                    return implicitDecision;
                }
            }

            Set<Integer> productionCandidates =
                ProductionInventoryIntegrityRules.restrictedCandidates(
                    option,
                    activeUniqueCardCount,
                    inventoryItemIds());
            if (!productionCandidates.isEmpty())
            {
                SimpleRestrictionService.RestrictionDecision productionDecision =
                    service.evaluateRequiredItems(
                        productionCandidates,
                        activeUsableCardIds,
                        "A tracked locked inventory item could be consumed by this production action. Bank unrelated locked items or acquire their cards before continuing.");
                if (productionDecision.isBlocked())
                {
                    return productionDecision;
                }
            }

            Set<Integer> serviceCandidates =
                InventoryConsumptionIntegrityRules.restrictedCandidates(
                    option,
                    target,
                    widgetText,
                    activeUniqueCardCount,
                    inventoryItemIds());
            if (!serviceCandidates.isEmpty())
            {
                SimpleRestrictionService.RestrictionDecision serviceDecision =
                    service.evaluateRequiredItems(
                        serviceCandidates,
                        activeUsableCardIds,
                        "A tracked locked inventory item could be consumed by this service or dialogue action. Bank unrelated locked items or acquire their cards before continuing.");
                if (serviceDecision.isBlocked())
                {
                    return serviceDecision;
                }
            }

            boolean clickedItemMetadata =
                InteractionContextRules.shouldReadClickedItemMetadata(
                    menuAction,
                    itemOperation,
                    directSpellbookContext)
                || (!directSpellbookContext && hasExplicitItemMetadata(
                    eventItemId,
                    entry,
                    explicitWidget));
            Set<Integer> itemIds = new LinkedHashSet<>(participatingItemIds(
                eventItemId,
                eventIdentifier,
                entry,
                explicitWidget,
                clickedItemMetadata,
                menuAction));
            addSelectedItemFallback(itemIds, option, menuAction);
            boolean grandExchangeCollectionAction =
                GrandExchangeInteractionAuthorization.isCollectionAction(
                    option,
                    widgetId(explicitWidget),
                    eventWidgetId,
                    widgetId(entryWidget),
                    entry == null ? -1 : entry.getParam1(),
                    eventParam1);
            if (grandExchangeCollectionAction && itemIds.isEmpty())
            {
                return SimpleRestrictionService.RestrictionDecision.block(
                    java.util.Collections.emptySet(),
                    "The Grand Exchange collection item's identity could not be verified, so collection was blocked.");
            }
            boolean shopTransactionAction =
                ShopInteractionAuthorization.isShopTransaction(
                    option,
                    widgetId(explicitWidget),
                    eventWidgetId,
                    widgetId(entryWidget),
                    entry == null ? -1 : entry.getParam1(),
                    eventParam1)
                || (shopInteractionAuthorization.isShopOpen()
                    && ShopInteractionAuthorization.isBuyOrSellOption(option));
            boolean shopBuyAction = shopTransactionAction
                && ShopInteractionAuthorization.isBuyOption(option);
            if (shopTransactionAction && itemIds.isEmpty())
            {
                return SimpleRestrictionService.RestrictionDecision.block(
                    java.util.Collections.emptySet(),
                    "The shop item's identity could not be verified, so the transaction was blocked.");
            }
            if (shopBuyAction
                && !CoinMilestoneRules.coinsUnlocked(activeUniqueCardCount)
                && inventoryContainsCoins())
            {
                return SimpleRestrictionService.RestrictionDecision.block(
                    java.util.Collections.emptySet(),
                    "Coins unlock at 1,000 unique cards.");
            }
            if (!CoinMilestoneRules.coinsUnlocked(activeUniqueCardCount)
                && inventoryContainsCoins()
                && ShopCurrencyRules.isImplicitCoinSpendOption(option, target))
            {
                return SimpleRestrictionService.RestrictionDecision.block(
                    java.util.Collections.emptySet(),
                    "Coins unlock at 1,000 unique cards.");
            }
            if (shopBuyAction)
            {
                SimpleRestrictionService.RestrictionDecision currencyDecision =
                    service.evaluateRequiredItems(
                        shopCurrencyItemIds(),
                        activeUsableCardIds,
                        "A locked shop-currency card has not been acquired.");
                if (currencyDecision.isBlocked())
                {
                    return currencyDecision;
                }
            }

            if (CoinMilestoneRules.shouldBlockCoinInteraction(
                activeUniqueCardCount,
                itemIds,
                option,
                safeStorageItemAction))
            {
                return SimpleRestrictionService.RestrictionDecision.block(
                    java.util.Collections.emptySet(),
                    "Coins unlock at 1,000 unique cards.");
            }
            Set<Integer> rawItemIds = new LinkedHashSet<>(itemIds);
            itemIds = CoinMilestoneRules.cardRestrictedItemIds(
                activeUniqueCardCount,
                rawItemIds);
            Set<String> itemNames;
            if (!clickedItemMetadata)
            {
                itemNames = java.util.Collections.emptySet();
            }
            else if (directSpellbookContext)
            {
                itemNames = InteractionTargetIntegrityRules
                    .targetItemNameCandidates(entryTarget, eventTarget);
            }
            else if (InteractionContextRules.isSelectedItemSourceAction(
                menuAction)
                && menuAction != MenuAction.ITEM_USE_ON_ITEM)
            {
                itemNames = InteractionTargetIntegrityRules
                    .sourceItemNameCandidates(entryTarget, eventTarget);
            }
            else
            {
                itemNames = InteractionTargetIntegrityRules
                    .allItemNameCandidates(entryTarget, eventTarget);
            }
            itemNames = CoinMilestoneRules.cardRestrictedItemNames(
                activeUniqueCardCount,
                rawItemIds,
                itemNames);
            boolean equipmentRemoval =
                InteractionContextRules.isEquipmentWidget(
                    widgetId(explicitWidget))
                || InteractionContextRules.isEquipmentWidget(eventWidgetId)
                || InteractionContextRules.isEquipmentWidget(
                    widgetId(entryWidget))
                || InteractionContextRules.isEquipmentWidget(eventParam1)
                || InteractionContextRules.isEquipmentWidget(
                    entry == null ? -1 : entry.getParam1());
            boolean unresolvedItemAction =
                ItemIdentityIntegrityRules.shouldBlockUnresolvedItemAction(
                clickedItemMetadata,
                itemIds,
                itemNames,
                option,
                safeStorageItemAction,
                equipmentRemoval);
            if (unresolvedItemAction
                && !ItemIdentityIntegrityRules.mayBypassUnresolvedItemBlock(
                    config.allowUnverifiedItemActions(),
                    activeIntegrityProfile))
            {
                return SimpleRestrictionService.RestrictionDecision.block(
                    java.util.Collections.emptySet(),
                    "This item's identity could not be verified, so the action was blocked.");
            }
            SimpleRestrictionService.RestrictionDecision itemDecision =
                service.evaluateItems(
                    itemIds,
                    itemNames,
                    option,
                    activeUsableCardIds,
                    safeStorageItemAction,
                    equipmentRemoval);
            if (itemDecision.isBlocked())
            {
                return itemDecision;
            }

            if (spellAction)
            {
                SpellRuneRequirementResolver resolver =
                    spellRuneRequirementResolver;
                SpellRuneRequirementResolver.Resolution resolution =
                    resolver == null
                        ? null
                        : resolver.resolveReconciled(
                            option,
                            entryTarget,
                            eventTarget,
                            explicitWidget,
                            entryWidget,
                            selectedWidget);
                if (resolution == null || !resolution.isResolved())
                {
                    return SimpleRestrictionService.RestrictionDecision.block(
                        java.util.Collections.emptySet(),
                        "This spell's rune requirements could not be verified, so the cast was blocked.");
                }
                if (!resolution.isRuneFree())
                {
                    SimpleRestrictionService.RestrictionDecision runeDecision =
                        RuneEntitlementPolicy.evaluate(
                            resolution.getRequiredRuneIds(),
                            activeUsableCardIds,
                            service);
                    if (runeDecision.isBlocked())
                    {
                        return runeDecision;
                    }
                }
            }

            if (spellAction
                && InteractionIntegrityRules.isAutocastSelection(option))
            {
                // The click only creates a short-lived proof candidate. The
                // AUTOCAST_SPELL varbit change must confirm it before attacks
                // are allowed; otherwise an immediate same-tick attack could
                // reuse an unverified pre-existing autocast configuration.
                autocastVerifiedForSession = false;
                autocastSelectionPending = true;
                autocastSelectionPendingTick = client.getTickCount();
            }

            boolean autocastSet = client.getVarbitValue(
                VarbitID.AUTOCAST_SET) != 0;
            if (!autocastSet)
            {
                autocastVerifiedForSession = false;
            }
            if (AutocastIntegrityRules.shouldBlockAttack(
                autocastSet,
                autocastVerifiedForSession,
                option))
            {
                return SimpleRestrictionService.RestrictionDecision.block(
                    java.util.Collections.emptySet(),
                    "Autocast was configured before Card Locked verified its rune entitlements. Reselect autocast while the plugin is active.");
            }

            if (EquippedItemActionRules.shouldGate(
                menuAction,
                option,
                spellAction,
                safeStorageItemAction))
            {
                SimpleRestrictionService.RestrictionDecision equippedDecision =
                    service.evaluateRequiredItems(
                        equippedItemIds(),
                        activeUsableCardIds,
                        "A locked item is currently equipped. Remove it before performing this action.");
                if (equippedDecision.isBlocked())
                {
                    return equippedDecision;
                }
            }

            if (PassiveInventoryUsageRules.shouldCheckInventory(
                menuAction,
                option))
            {
                SimpleRestrictionService.RestrictionDecision passiveDecision =
                    service.evaluateRequiredItems(
                        passiveInventoryItemIds(),
                        activeUsableCardIds,
                        "A locked passive environmental item in your inventory would be used by this movement or transition.");
                if (passiveDecision.isBlocked())
                {
                    return passiveDecision;
                }
            }
        }

        NPC npc = resolvedNpc;
        boolean conflictingResolvedNpcIdentity = npc != null
            && service.hasConflictingNpcIdentity(
                npc.getId(),
                fallbackNpcName.isEmpty()
                    ? npc.getName()
                    : fallbackNpcName);
        boolean knownNpcTarget = service.hasKnownOrAmbiguousNpcIdentity(
            fallbackNpcName);
        boolean genuineWidgetContext = hasPackedWidgetGroup(
            widgetId(explicitWidget),
            eventWidgetId,
            widgetId(entryWidget),
            entryParam1,
            eventParam1);
        if (runtimeSettings.isRestrictNpcCombat()
            && InteractionIntegrityRules
                .shouldBlockUnresolvedNpcFunctionalAction(
                    menuAction,
                    option,
                    npc != null,
                    knownNpcTarget,
                    genuineWidgetContext)
            && !UnverifiedActionCompatibilityRules.mayBypass(
                config.allowUnverifiedNpcActions(),
                activeIntegrityProfile))
        {
            return SimpleRestrictionService.RestrictionDecision.block(
                java.util.Collections.emptySet(),
                "This NPC action lost its actor identity, so it was blocked rather than allowed through rewritten menu metadata.");
        }
        if (runtimeSettings.isRestrictNpcCombat()
            && InteractionIntegrityRules.isNpcInteraction(
                menuAction,
                npc != null,
                knownNpcTarget))
        {
            if ((conflictingTrackedNpcTargets
                    || conflictingResolvedNpcIdentity)
                && !SimpleRestrictionService.isTalkOption(option))
            {
                return SimpleRestrictionService.RestrictionDecision.block(
                    java.util.Collections.emptySet(),
                    "Conflicting NPC target metadata was detected, so the action was blocked.");
            }
            if (npc != null)
            {
                return service.evaluateNpcInteraction(
                    npc.getId(),
                    npc.getName(),
                    option,
                    activeUsableCardIds);
            }
            String fallbackName = fallbackNpcName;
            if (SimpleRestrictionService.isTalkOption(option))
            {
                return SimpleRestrictionService.RestrictionDecision.allow();
            }
            if (!fallbackName.isEmpty())
            {
                SimpleRestrictionService.RestrictionDecision fallbackDecision =
                    service.evaluateNpcInteraction(
                        -1,
                        fallbackName,
                        option,
                        activeUsableCardIds);
                if (fallbackDecision.isBlocked())
                {
                    return fallbackDecision;
                }
                if (!service.requiredCardsForNpc(-1, fallbackName).isEmpty())
                {
                    return fallbackDecision;
                }
            }
            if (UnverifiedActionCompatibilityRules.mayBypass(
                config.allowUnverifiedNpcActions(),
                activeIntegrityProfile))
            {
                return SimpleRestrictionService.RestrictionDecision.allow();
            }
            return SimpleRestrictionService.RestrictionDecision.block(
                java.util.Collections.emptySet(),
                "This NPC's identity could not be verified, so the action was blocked.");
        }
        return SimpleRestrictionService.RestrictionDecision.allow();
    }


    private NPC npcFrom(
        MenuEntry entry,
        MenuAction action,
        int eventIdentifier,
        String normalisedTargetName,
        boolean knownNpcTarget)
    {
        NPC attached = null;
        if (entry != null)
        {
            attached = entry.getNpc();
            if (attached == null && entry.getActor() instanceof NPC)
            {
                attached = (NPC) entry.getActor();
            }
        }
        if (attached != null
            && npcNameMatches(attached, normalisedTargetName))
        {
            return attached;
        }
        // During active combat RuneLite/other menu plugins can rewrite the
        // second Attack click to a generic action and omit the attached actor.
        // If the displayed target is a catalogue-known NPC, still reconcile
        // the event/entry identifier against the live NPC index. Exact name
        // matching below remains mandatory, and explicit item/object/player
        // actions are rejected by isNpcInteraction(...).
        if (!InteractionIntegrityRules.mayResolveNpcActorFromLiveIndex(
            action,
            attached != null,
            knownNpcTarget))
        {
            return normalisedTargetName == null
                || normalisedTargetName.isEmpty()
                ? attached
                : null;
        }

        net.runelite.api.WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null || worldView.npcs() == null)
        {
            return null;
        }
        NPC eventNpc = eventIdentifier < 0
            ? null
            : worldView.npcs().byIndex(eventIdentifier);
        int entryIdentifier = entry == null ? -1 : entry.getIdentifier();
        NPC entryNpc = entryIdentifier < 0
            ? null
            : worldView.npcs().byIndex(entryIdentifier);

        if (npcNameMatches(eventNpc, normalisedTargetName))
        {
            return eventNpc;
        }
        if (npcNameMatches(entryNpc, normalisedTargetName))
        {
            return entryNpc;
        }
        if (normalisedTargetName == null || normalisedTargetName.isEmpty())
        {
            return eventNpc != null ? eventNpc
                : (entryNpc != null ? entryNpc : attached);
        }
        // Conflicting/stale actor metadata is not trusted. The caller will use
        // the catalogue-backed target name and fail closed if it cannot prove
        // the exact NPC family.
        return null;
    }

    private static boolean npcNameMatches(
        NPC npc,
        String normalisedTargetName)
    {
        if (npc == null)
        {
            return false;
        }
        if (normalisedTargetName == null || normalisedTargetName.isEmpty())
        {
            return true;
        }
        return normalisedTargetName.equals(
            InteractionNameNormalizer.normaliseEntityName(npc.getName()));
    }

    private Set<Integer> implicitParticipatingItemIds(
        String option,
        String target)
    {
        if (!ImplicitItemUsageRules.actionCanUseImplicitItems(option, target))
        {
            return java.util.Collections.emptySet();
        }
        Set<Integer> itemIds = new LinkedHashSet<>();
        addImplicitParticipants(
            itemIds,
            client.getItemContainer(InventoryID.INVENTORY),
            option,
            target);
        addImplicitParticipants(
            itemIds,
            client.getItemContainer(InventoryID.EQUIPMENT),
            option,
            target);
        return itemIds;
    }

    private void addImplicitParticipants(
        Set<Integer> destination,
        ItemContainer container,
        String option,
        String target)
    {
        if (container == null)
        {
            return;
        }
        for (Item item : container.getItems())
        {
            if (item == null || item.getId() < 0)
            {
                continue;
            }
            String name = itemManager.getItemComposition(item.getId()).getName();
            if (ImplicitItemUsageRules.isPotentialParticipant(
                option,
                target,
                name))
            {
                destination.add(item.getId());
            }
        }
    }

    private Set<Integer> passiveInventoryItemIds()
    {
        Set<Integer> itemIds = new LinkedHashSet<>();
        ItemContainer inventory = client.getItemContainer(
            InventoryID.INVENTORY);
        if (inventory == null)
        {
            return itemIds;
        }
        for (Item item : inventory.getItems())
        {
            if (item == null || item.getId() < 0)
            {
                continue;
            }
            net.runelite.api.ItemComposition composition =
                itemManager.getItemComposition(item.getId());
            if (PassiveInventoryUsageRules.isPotentialPassiveItem(
                composition.getName(),
                composition.getInventoryActions()))
            {
                itemIds.add(item.getId());
            }
        }
        return itemIds;
    }

    private Set<Integer> itemContainerIds(InventoryID inventoryId)
    {
        Set<Integer> itemIds = new LinkedHashSet<>();
        ItemContainer container = client.getItemContainer(inventoryId);
        if (container == null)
        {
            return itemIds;
        }
        for (Item item : container.getItems())
        {
            if (item != null && item.getId() >= 0)
            {
                itemIds.add(item.getId());
            }
        }
        return itemIds;
    }

    private Set<Integer> grandExchangeOfferItemIds()
    {
        return GrandExchangeOfferIntegrityRules.selectedItemIds(
            client.getVarpValue(VarPlayer.CURRENT_GE_ITEM));
    }

    private static final InventoryID[] REWARD_INVENTORIES = {
        InventoryID.FISHING_TRAWLER_REWARD,
        InventoryID.BARROWS_REWARD,
        InventoryID.DRIFT_NET_FISHING_REWARD,
        InventoryID.KINGDOM_OF_MISCELLANIA,
        InventoryID.CHAMBERS_OF_XERIC_CHEST,
        InventoryID.THEATRE_OF_BLOOD_CHEST,
        InventoryID.WILDERNESS_LOOT_CHEST,
        InventoryID.TOA_REWARD_CHEST,
        InventoryID.LUNAR_CHEST,
        InventoryID.FORTIS_COLOSSEUM_REWARD_CHEST
    };

    private Set<Integer> rewardContainerItemIds(
        Set<InventoryID> inventoryIds)
    {
        Set<Integer> itemIds = new LinkedHashSet<>();
        if (inventoryIds == null)
        {
            return itemIds;
        }
        for (InventoryID inventoryId : inventoryIds)
        {
            itemIds.addAll(itemContainerIds(inventoryId));
        }
        return itemIds;
    }

    private boolean hasLoadedRewardContainer()
    {
        for (InventoryID inventoryId : REWARD_INVENTORIES)
        {
            ItemContainer container = client.getItemContainer(inventoryId);
            if (container == null)
            {
                continue;
            }
            for (Item item : container.getItems())
            {
                if (item != null && item.getId() >= 0)
                {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<Integer> widgetGroupItemIds(int... groupIds)
    {
        Set<Integer> groups = new LinkedHashSet<>();
        if (groupIds != null)
        {
            for (int groupId : groupIds)
            {
                groups.add(groupId);
            }
        }
        Set<Integer> itemIds = new LinkedHashSet<>();
        Widget[] roots = client.getWidgetRoots();
        if (roots == null)
        {
            return itemIds;
        }
        Set<Integer> visited = new java.util.HashSet<>();
        for (Widget root : roots)
        {
            collectWidgetGroupItems(root, groups, itemIds, visited, 0);
        }
        return itemIds;
    }

    private static void collectWidgetGroupItems(
        Widget widget,
        Set<Integer> groups,
        Set<Integer> itemIds,
        Set<Integer> visited,
        int depth)
    {
        if (widget == null || depth > 12 || visited.size() >= 4096
            || !visited.add(widget.getId()))
        {
            return;
        }
        int packedId = widget.getId();
        if (packedId >= 0 && groups.contains(packedId >>> 16)
            && widget.getItemId() >= 0)
        {
            itemIds.add(widget.getItemId());
        }
        Widget[] children = widget.getChildren();
        if (children != null)
        {
            for (Widget child : children)
            {
                collectWidgetGroupItems(
                    child, groups, itemIds, visited, depth + 1);
            }
        }
        Widget[] dynamicChildren = widget.getDynamicChildren();
        if (dynamicChildren != null)
        {
            for (Widget child : dynamicChildren)
            {
                collectWidgetGroupItems(
                    child, groups, itemIds, visited, depth + 1);
            }
        }
        Widget[] staticChildren = widget.getStaticChildren();
        if (staticChildren != null)
        {
            for (Widget child : staticChildren)
            {
                collectWidgetGroupItems(
                    child, groups, itemIds, visited, depth + 1);
            }
        }
        Widget[] nestedChildren = widget.getNestedChildren();
        if (nestedChildren != null)
        {
            for (Widget child : nestedChildren)
            {
                collectWidgetGroupItems(
                    child, groups, itemIds, visited, depth + 1);
            }
        }
    }

    private Set<Integer> inventoryItemIds()
    {
        Set<Integer> itemIds = new LinkedHashSet<>();
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null)
        {
            return itemIds;
        }
        for (Item item : inventory.getItems())
        {
            if (item != null && item.getId() >= 0)
            {
                itemIds.add(item.getId());
            }
        }
        return itemIds;
    }

    private Set<Integer> equippedItemIds()
    {
        Set<Integer> itemIds = new LinkedHashSet<>();
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null)
        {
            return itemIds;
        }
        for (Item item : equipment.getItems())
        {
            if (item != null && item.getId() >= 0)
            {
                itemIds.add(item.getId());
            }
        }
        return itemIds;
    }

    private Set<Integer> shopCurrencyItemIds()
    {
        Set<Integer> itemIds = new LinkedHashSet<>();
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null)
        {
            return itemIds;
        }
        for (Item item : inventory.getItems())
        {
            if (item == null || item.getId() < 0
                || CoinMilestoneRules.isCoinItem(item.getId()))
            {
                continue;
            }
            String name = itemManager.getItemComposition(item.getId()).getName();
            if (ShopCurrencyRules.isPotentialCurrency(name))
            {
                itemIds.add(item.getId());
            }
        }
        return itemIds;
    }

    private boolean inventoryContainsCoins()
    {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null)
        {
            return false;
        }
        for (Item item : inventory.getItems())
        {
            if (item != null && CoinMilestoneRules.isCoinItem(item.getId()))
            {
                return true;
            }
        }
        return false;
    }

    private Set<Integer> furnaceIngredientItemIds()
    {
        Set<Integer> itemIds = new LinkedHashSet<>();
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null)
        {
            return itemIds;
        }
        for (Item item : inventory.getItems())
        {
            if (item == null || item.getId() < 0)
            {
                continue;
            }
            String name = itemManager.getItemComposition(item.getId()).getName();
            if (FurnaceInteractionRules.isPotentialFurnaceIngredient(name))
            {
                itemIds.add(item.getId());
            }
        }
        return itemIds;
    }

    private static boolean hasExplicitItemMetadata(
        int eventItemId,
        MenuEntry entry,
        Widget explicitWidget)
    {
        if (eventItemId >= 0)
        {
            return true;
        }
        if (entry != null)
        {
            if (entry.getItemId() >= 0)
            {
                return true;
            }
            Widget entryWidget = entry.getWidget();
            if (entryWidget != null
                && !SpellbookWidgetRules.isSpellbookPackedId(entryWidget.getId())
                && entryWidget.getItemId() >= 0)
            {
                return true;
            }
        }
        return explicitWidget != null
            && !SpellbookWidgetRules.isSpellbookPackedId(explicitWidget.getId())
            && explicitWidget.getItemId() >= 0;
    }

    private void addSelectedItemFallback(
        Set<Integer> itemIds,
        String option,
        MenuAction menuAction)
    {
        if (!client.isWidgetSelected()
            || SpellbookWidgetRules.isSpellbookPackedId(
                widgetId(client.getSelectedWidget())))
        {
            return;
        }
        String normalised = option == null
            ? ""
            : option.replaceAll("<[^>]*>", "").trim().toLowerCase(java.util.Locale.ROOT);
        boolean itemUseOption = normalised.equals("use")
            || normalised.startsWith("use ")
            || menuAction == MenuAction.ITEM_USE_ON_GAME_OBJECT
            || menuAction == MenuAction.ITEM_USE_ON_NPC
            || menuAction == MenuAction.ITEM_USE_ON_PLAYER
            || menuAction == MenuAction.ITEM_USE_ON_GROUND_ITEM
            || menuAction == MenuAction.ITEM_USE_ON_ITEM;
        Widget selectedWidget = client.getSelectedWidget();
        if (itemUseOption && selectedWidget != null
            && selectedWidget.getItemId() >= 0)
        {
            itemIds.add(selectedWidget.getItemId());
        }
    }

    private Set<Integer> participatingItemIds(
        int eventItemId,
        int eventIdentifier,
        MenuEntry entry,
        Widget explicitWidget,
        boolean clickedItemMetadata,
        MenuAction menuAction)
    {
        Widget entryWidget = entry == null ? null : entry.getWidget();
        Widget selectedWidget = client.getSelectedWidget();
        return InteractionItemResolver.resolve(
            menuAction,
            clickedItemMetadata,
            eventItemId,
            eventIdentifier,
            entry == null ? -1 : entry.getItemId(),
            entry == null ? -1 : entry.getIdentifier(),
            entryWidget == null ? -1 : entryWidget.getItemId(),
            widgetId(entryWidget),
            explicitWidget == null ? -1 : explicitWidget.getItemId(),
            widgetId(explicitWidget),
            client.isWidgetSelected(),
            selectedWidget == null ? -1 : selectedWidget.getItemId(),
            widgetId(selectedWidget));
    }

    private String dialogueContextText(
        Widget explicitWidget,
        Widget entryWidget)
    {
        StringBuilder text = new StringBuilder();
        appendWidgetText(text, explicitWidget, 0, new java.util.HashSet<>());
        if (entryWidget != explicitWidget)
        {
            appendWidgetText(text, entryWidget, 0, new java.util.HashSet<>());
        }
        appendWidgetText(
            text,
            client.getWidget(InterfaceID.CHAT_LEFT, 0),
            0,
            new java.util.HashSet<>());
        appendWidgetText(
            text,
            client.getWidget(InterfaceID.CHAT_RIGHT, 0),
            0,
            new java.util.HashSet<>());
        return text.toString();
    }

    private static void appendWidgetText(
        StringBuilder destination,
        Widget widget,
        int depth,
        Set<Integer> visited)
    {
        if (widget == null || depth > 6 || visited.size() >= 96
            || !visited.add(widget.getId()))
        {
            return;
        }
        appendNonBlank(destination, widget.getText());
        appendNonBlank(destination, widget.getName());
        Widget[] children = widget.getChildren();
        if (children != null)
        {
            for (Widget child : children)
            {
                appendWidgetText(destination, child, depth + 1, visited);
            }
        }
        Widget parent = widget.getParent();
        if (depth == 0 && parent != null)
        {
            appendWidgetText(destination, parent, depth + 1, visited);
        }
    }

    private static void appendNonBlank(
        StringBuilder destination,
        String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return;
        }
        if (destination.length() > 0)
        {
            destination.append(' ');
        }
        destination.append(value);
    }

    private static boolean hasPackedWidgetGroup(int... packedWidgetIds)
    {
        if (packedWidgetIds == null)
        {
            return false;
        }
        for (int packedWidgetId : packedWidgetIds)
        {
            if (packedWidgetId >= 0 && packedWidgetId >>> 16 > 0)
            {
                return true;
            }
        }
        return false;
    }

    private static int widgetId(Widget widget)
    {
        return widget == null ? -1 : widget.getId();
    }

    private String cardDisplayName(String cardId)
    {
        return catalogue.findCard(cardId)
            .map(card -> card.getDisplayName())
            .orElseGet(() -> catalogue.findHistoricalCard(cardId)
                .map(historical -> historical.getDisplayName())
                .orElse(cardId));
    }

    private void showBlockedMessage(
        SimpleRestrictionService.RestrictionDecision decision)
    {
        if (!config.blockedChatMessages())
        {
            return;
        }
        String message = RestrictionMessageFormatter.format(
            decision,
            this::cardDisplayName);
        if (!restrictionMessageLimiter.shouldEmit(
            message,
            client.getTickCount()))
        {
            return;
        }
        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            message,
            null);
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        if (!isCollectionRuntimeActive())
        {
            return;
        }
        if (event.getSource() == client.getLocalPlayer()
            && event.getTarget() instanceof NPC)
        {
            NPC npc = (NPC) event.getTarget();
            npcKillCreditTracker.observeInteraction(
                npc.getIndex(),
                npc.getId(),
                client.getTickCount());
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (!isCollectionRuntimeActive())
        {
            return;
        }
        if (event.getActor() instanceof NPC
            && event.getHitsplat() != null
            && event.getHitsplat().isMine())
        {
            NPC npc = (NPC) event.getActor();
            npcKillCreditTracker.observePlayerHitsplat(
                npc.getIndex(),
                npc.getId(),
                client.getTickCount());
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        if (!isCollectionRuntimeActive()
            || !(event.getActor() instanceof NPC))
        {
            return;
        }
        NPC npc = (NPC) event.getActor();
        if (npcKillCreditTracker.qualifyDeath(
            npc.getIndex(),
            npc.getId(),
            client.getTickCount()))
        {
            awardNpcKill(npc);
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        if (!isCollectionRuntimeActive())
        {
            return;
        }
        NPC npc = event.getNpc();
        if (npcKillCreditTracker.qualifyLootFallback(
            npc.getIndex(),
            npc.getId(),
            client.getTickCount()))
        {
            awardNpcKill(npc);
        }
    }

    private void awardNpcKill(NPC npc)
    {
        CollectionSessionService rewardSession = sessionService;
        long rewardAccountHash = activeAccountHash;
        String gameSessionId = activeGameSessionId;
        if (!isCollectionRuntimeActive()
            || rewardSession == null
            || rewardAccountHash == -1L
            || gameSessionId.isEmpty())
        {
            return;
        }

        SimpleRestrictionService service = restrictionService;
        if (service == null)
        {
            return;
        }
        RestrictionPresetSettings runtimeSettings =
            runtimeRestrictionSettings();
        boolean npcRestrictionsEnforced =
            runtimeSettings.getRestrictionMode()
                == com.cardrestricted.domain.RestrictionMode.ENFORCE
            && runtimeSettings.isRestrictNpcCombat();
        Set<String> requiredNpcCards = service.requiredCardsForNpc(
            npc.getId(),
            npc.getName());
        boolean unresolvedOrConflictingNpcIdentity =
            requiredNpcCards.isEmpty()
                && service.isAmbiguousNpcIdentity(npc.getName())
            || service.hasConflictingNpcIdentity(
                npc.getId(),
                npc.getName());
        if (!NpcRewardIntegrityRules.mayAward(
            npcRestrictionsEnforced,
            unresolvedOrConflictingNpcIdentity,
            requiredNpcCards,
            activeUsableCardIds))
        {
            return;
        }

        WorldPoint location = npc.getWorldLocation();
        SessionSnapshot currentSnapshot = rewardSession.snapshot();
        if (currentSnapshot.getStatus() != SessionStatus.READY)
        {
            return;
        }

        NpcKillObservation observation = new NpcKillObservation(
            gameSessionId,
            client.getWorld(),
            client.getTickCount(),
            npc.getIndex(),
            npc.getId(),
            npc.getCombatLevel(),
            location.getRegionID(),
            Instant.now());
        Optional<PointAward> possibleAward =
            npcKillRewardPolicy.createAward(
                currentSnapshot.getCollectionState().orElseThrow(),
                observation);
        if (!possibleAward.isPresent())
        {
            return;
        }

        PointAward award = possibleAward.get();
        // ActorDeath/NpcLootReceived can produce many eligible kills in the
        // same tick (barrage, cannon, multi-combat). Accumulate unique source
        // IDs on the client thread and persist them as one durable transaction
        // at the GameTick boundary instead of queueing one fsync-heavy save
        // per NPC.
        pendingNpcPointAwards.putIfAbsent(award.getSourceId(), award);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!isCollectionRuntimeActive()
            || event.getType() != ChatMessageType.GAMEMESSAGE)
        {
            return;
        }
        Optional<ClueCompletionObservation> observation =
            clueCompletionMessageParser.parse(
                event.getMessage(),
                Instant.now());
        if (!observation.isPresent())
        {
            return;
        }
        CollectionSessionService clueSession = sessionService;
        long clueAccountHash = activeAccountHash;
        if (clueSession == null || clueAccountHash == -1L)
        {
            return;
        }
        PointAward award = clueCompletionRewardPolicy.createAward(
            observation.get());
        submitPluginTask(DiagnosticOperation.CLUE_REWARD, () ->
            commitClueCompletion(
                clueSession,
                clueAccountHash,
                observation.get(),
                award));
    }

    private void commitClueCompletion(
        CollectionSessionService clueSession,
        long clueAccountHash,
        ClueCompletionObservation observation,
        PointAward award)
    {
        try
        {
            SessionSnapshot updated = clueSession.awardPoints(award);
            if (!isCurrentSession(clueAccountHash, clueSession))
            {
                return;
            }
            renderSnapshot(updated);
            long balance = updated.getCollectionState()
                .orElseThrow()
                .getPoints();
            notifyPointThresholdCrossed(
                balance - award.getAmount(),
                balance);
            clientThread.invokeLater(() -> client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                "[Cards] " + observation.getTier().getPoints()
                    + " points awarded for the "
                    + observation.getTier().key() + " clue completion.",
                null));
        }
        catch (DuplicatePointAwardException ignored)
        {
            // Replayed completion-count messages are idempotent.
        }
        catch (java.io.IOException | IllegalStateException exception)
        {
            if (isCurrentSession(clueAccountHash, clueSession))
            {
                reportCaughtFailure(DiagnosticOperation.CLUE_REWARD, exception);
            }
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        observeSkillLevel(event);

        Optional<NoncombatSkill> possibleSkill =
            toNoncombatSkill(event.getSkill());
        if (!possibleSkill.isPresent())
        {
            return;
        }

        latestNoncombatXp.put(event.getSkill(), event.getXp());

        Integer baseline =
            noncombatSessionBaselines.get(event.getSkill());
        if (baseline == null)
        {
            noncombatSessionBaselines.put(
                event.getSkill(), event.getXp());
            return;
        }
        if (!isCollectionRuntimeActive())
        {
            noncombatSessionBaselines.put(
                event.getSkill(), event.getXp());
            pendingNoncombatXpCommits.remove(event.getSkill());
            return;
        }
        queueNoncombatXpBatch(
            event.getSkill(),
            possibleSkill.get(),
            event.getXp());
    }

    private void queueNoncombatXpBatch(
        Skill skill,
        NoncombatSkill noncombatSkill,
        int observedXp)
    {
        Integer baseline = noncombatSessionBaselines.get(skill);
        if (baseline == null
            || observedXp <= baseline
            || pendingNoncombatXpCommits.contains(skill))
        {
            return;
        }

        int completedBatches =
            (observedXp - baseline) / NONCOMBAT_XP_BATCH_SIZE;
        if (completedBatches <= 0)
        {
            return;
        }
        int targetXp = baseline
            + completedBatches * NONCOMBAT_XP_BATCH_SIZE;

        CollectionSessionService xpSession = sessionService;
        long xpAccountHash = activeAccountHash;
        if (xpSession == null || xpAccountHash == -1L)
        {
            return;
        }

        NoncombatXpObservation observation =
            new NoncombatXpObservation(
                noncombatSkill,
                baseline,
                targetXp,
                Instant.now());
        pendingNoncombatXpCommits.add(skill);
        if (!submitPluginTask(
            DiagnosticOperation.XP_REWARD,
            () -> commitNoncombatXp(
                xpSession,
                xpAccountHash,
                skill,
                targetXp,
                observation)))
        {
            pendingNoncombatXpCommits.remove(skill);
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        int groupId = event.getGroupId();
        int currentTick = client.getTickCount();
        shopInteractionAuthorization.onWidgetLoaded(groupId, currentTick);
        storageInteractionAuthorization.onWidgetLoaded(groupId, currentTick);
        grandExchangeInteractionAuthorization.onWidgetLoaded(
            groupId,
            currentTick);
        npcServiceInterfaceAuthorization.onWidgetLoaded(
            groupId,
            currentTick);
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        int groupId = event.getGroupId();
        shopInteractionAuthorization.onWidgetClosed(groupId);
        storageInteractionAuthorization.onWidgetClosed(groupId);
        grandExchangeInteractionAuthorization.onWidgetClosed(groupId);
        npcServiceInterfaceAuthorization.onWidgetClosed(groupId);
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        int varbitId = event.getVarbitId();
        if (varbitId == VarbitID.AUTOCAST_SET)
        {
            if (event.getValue() == 0)
            {
                autocastVerifiedForSession = false;
                autocastSelectionPending = false;
                autocastSelectionPendingTick = Integer.MIN_VALUE;
            }
        }
        else if (varbitId == VarbitID.AUTOCAST_SPELL)
        {
            if (AutocastIntegrityRules.isPendingSelectionCurrent(
                autocastSelectionPending,
                autocastSelectionPendingTick,
                client.getTickCount()))
            {
                // Only the immediate varbit change following a verified
                // Autocast menu click can establish the session proof.
                autocastVerifiedForSession = true;
            }
            else
            {
                autocastVerifiedForSession = false;
            }
            autocastSelectionPending = false;
            autocastSelectionPendingTick = Integer.MIN_VALUE;
        }

        if (isCollectionRuntimeActive() && questBaselineReady)
        {
            questRewardStateDirty = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        int currentTick = client.getTickCount();
        npcKillCreditTracker.prune(currentTick);
        if (autocastSelectionPending
            && !AutocastIntegrityRules.isPendingSelectionCurrent(
                true,
                autocastSelectionPendingTick,
                currentTick))
        {
            autocastSelectionPending = false;
            autocastSelectionPendingTick = Integer.MIN_VALUE;
        }
        if (!isCollectionRuntimeActive())
        {
            pendingNpcPointAwards.clear();
            return;
        }
        flushPendingNpcKillAwards();
        if (!questBaselineReady)
        {
            if (questBaselineTicksRemaining > 0)
            {
                questBaselineTicksRemaining--;
            }
            if (questBaselineTicksRemaining > 0)
            {
                return;
            }
            captureQuestBaselines();
            questBaselineReady = true;
            questRewardStateDirty = false;
            return;
        }
        if (questRewardCheckCooldown > 0)
        {
            questRewardCheckCooldown--;
        }
        if (questRewardStateDirty && questRewardCheckCooldown <= 0)
        {
            questRewardStateDirty = false;
            questRewardCheckCooldown = QUEST_REWARD_CHECK_INTERVAL_TICKS;
            detectQuestCompletions();
        }
        processQuestTrackerCompletionChunk();
    }

    private void openLoggedInSession()
    {
        long accountHash = client.getAccountHash();
        String displayName = client.getLocalPlayer() == null
            ? client.getLauncherDisplayName()
            : client.getLocalPlayer().getName();
        if (displayName == null || displayName.trim().isEmpty())
        {
            displayName = "Unknown character";
        }

        CollectionSessionService existingSession = sessionService;
        if (existingSession != null && activeAccountHash == accountHash)
        {
            if (!clientSessionSuspended)
            {
                return;
            }
            clientSessionSuspended = false;
            beginLoggedInRuntime();
            applySnapshot(existingSession.snapshot());
            return;
        }
        if (existingSession != null)
        {
            closeDetachedSession(existingSession);
        }

        CollectionSessionService newSessionService = createSessionService();
        sessionService = newSessionService;
        activeAccountHash = accountHash;
        clientSessionSuspended = false;
        beginLoggedInRuntime();
        enforcementActive = false;
        restrictionStatePending = true;
        autocastVerifiedForSession = false;
        autocastSelectionPending = false;
        autocastSelectionPendingTick = Integer.MIN_VALUE;
        activeUsableCardIds = java.util.Collections.emptySet();
        updatePanelBusy("Loading character collection...");
        String resolvedDisplayName = displayName;
        submitPluginTask(DiagnosticOperation.SESSION_OPEN, () -> {
            SessionSnapshot snapshot =
                newSessionService.open(accountHash, resolvedDisplayName);
            try
            {
                snapshot = applyDeveloperTestingBalance(
                    newSessionService,
                    snapshot);
            }
            catch (java.io.IOException exception)
            {
                reportCaughtFailure(DiagnosticOperation.TEST_BALANCE, exception);
            }
            applySnapshotIfCurrent(
                accountHash,
                newSessionService,
                snapshot);
        });
    }

    private void beginLoggedInRuntime()
    {
        activeGameSessionId = UUID.randomUUID().toString();
        autocastVerifiedForSession = false;
        autocastSelectionPending = false;
        autocastSelectionPendingTick = Integer.MIN_VALUE;
        resetRuntimeTracking();
        captureNoncombatBaselines();
        captureSkillLevelBaselines();
    }

    private void suspendLoggedInRuntime()
    {
        if (sessionService == null || clientSessionSuspended)
        {
            return;
        }
        clientSessionSuspended = true;
        enforcementActive = false;
        restrictionStatePending = false;
        autocastVerifiedForSession = false;
        autocastSelectionPending = false;
        autocastSelectionPendingTick = Integer.MIN_VALUE;
        activeUsableCardIds = java.util.Collections.emptySet();
        activeGameSessionId = "";
        resetRuntimeTracking();
        if (achievementToastOverlay != null)
        {
            achievementToastOverlay.clear();
        }
        if (packPresentationController != null)
        {
            packPresentationController.reset();
        }
        if (packPresentationOverlay != null)
        {
            packPresentationOverlay.stopAnimationLoop();
        }
    }

    private void resetRuntimeTracking()
    {
        noncombatSessionBaselines.clear();
        latestNoncombatXp.clear();
        pendingNoncombatXpCommits.clear();
        skillLevelSessionBaselines.clear();
        questCompletionTracker.clear();
        npcKillCreditTracker.clear();
        pendingNpcPointAwards.clear();
        shopInteractionAuthorization.reset();
        storageInteractionAuthorization.reset();
        grandExchangeInteractionAuthorization.reset();
        npcServiceInterfaceAuthorization.reset();
        lockedNpcDialogueGuard.reset();
        restrictionMessageLimiter.clear();
        questBaselineReady = false;
        questBaselineTicksRemaining = 0;
        questRewardStateDirty = false;
        questRewardCheckCooldown = 0;
        questTrackerScanActive = false;
        questTrackerRescanRequested = false;
        questTrackerScanCursor = 0;
        questTrackerScanCompleted.clear();
        completedQuestKeys = Collections.emptySet();
        nexusExchangeInFlight.set(false);
    }

    private void closeSession()
    {
        if (achievementToastOverlay != null)
        {
            achievementToastOverlay.clear();
        }
        activeAccountHash = -1L;
        activeGameSessionId = "";
        resetRuntimeTracking();
        enforcementActive = false;
        clientSessionSuspended = false;
        activeUsableCardIds = java.util.Collections.emptySet();
        CollectionSessionService closingSession = sessionService;
        sessionService = null;
        if (closingSession != null)
        {
            closeDetachedSession(closingSession);
        }
        applySnapshot(SessionSnapshot.loggedOut());
    }

    private void closeDetachedSession(CollectionSessionService closingSession)
    {
        // Never acquire the session mutation monitor from RuneLite's client
        // thread. The dedicated queue serialises this behind any durable save.
        submitPluginTask(
            DiagnosticOperation.CLEANUP,
            closingSession::close);
    }

    private boolean isCollectionRuntimeActive()
    {
        return RestrictionRuntimeGate.isCollectionRuntimeActive(
            enforcementActive,
            client.getGameState());
    }

    private boolean isRestrictionRuntimeActive()
    {
        return RestrictionRuntimeGate.isRestrictionRuntimeActive(
            enforcementActive,
            client.getGameState(),
            runtimeRestrictionSettings().getRestrictionMode());
    }

    /**
     * Integrity-profile settings come from the persisted profile marker, not
     * from mutable RuneLite configuration. This removes the one-event window
     * in which a disabled/tampered config value could otherwise be observed
     * before ConfigChanged restored the profile preset.
     */
    private RestrictionPresetSettings runtimeRestrictionSettings()
    {
        CollectionSessionService active = sessionService;
        CollectionState state = active == null
            ? null
            : active.snapshot().getCollectionState().orElse(null);
        if (state != null && ProfileStateMarkers.isIntegrityProfile(state))
        {
            return RestrictionPresetSettings.forPreset(
                ProfileStateMarkers.restrictionPreset(state));
        }
        return RestrictionPresetSettings.fromConfiguration(
            config.restrictionMode(),
            config.restrictLockedItems(),
            config.restrictNpcCombat(),
            config.allowLockedItemBanking());
    }

    private void applyProfileConfiguration(ProfileSetupOptions options)
    {
        RestrictionPresetSettings settings =
            RestrictionPresetSettings.forPreset(
                options.getRestrictionPreset());
        configManager.setConfiguration(
            CardRestrictedAccountConfig.GROUP,
            "restrictionMode",
            settings.getRestrictionMode().name());
        configManager.setConfiguration(
            CardRestrictedAccountConfig.GROUP,
            "restrictLockedItems",
            settings.isRestrictLockedItems());
        configManager.setConfiguration(
            CardRestrictedAccountConfig.GROUP,
            "restrictNpcCombat",
            settings.isRestrictNpcCombat());
        configManager.setConfiguration(
            CardRestrictedAccountConfig.GROUP,
            "allowLockedItemBanking",
            settings.isAllowLockedItemBanking());
        boolean allowUnverified = options.getIntegrityMode() != IntegrityMode.INTEGRITY
            && options.isAllowUnverifiedActions();
        configManager.setConfiguration(
            CardRestrictedAccountConfig.GROUP,
            "allowUnverifiedItemActions",
            allowUnverified);
        configManager.setConfiguration(
            CardRestrictedAccountConfig.GROUP,
            "allowUnverifiedNpcActions",
            allowUnverified);
        configManager.setConfiguration(
            CardRestrictedAccountConfig.GROUP,
            "showLockedItemOverlay",
            options.isLockedVisuals());
        configManager.setConfiguration(
            CardRestrictedAccountConfig.GROUP,
            "showLockedNpcOverlay",
            options.isLockedVisuals());
    }

    private boolean restoreIntegrityProfileConfiguration(
        CollectionState state)
    {
        if (state == null || !ProfileStateMarkers.isIntegrityProfile(state))
        {
            return false;
        }
        RestrictionPresetSettings expected =
            RestrictionPresetSettings.forPreset(
                ProfileStateMarkers.restrictionPreset(state));
        boolean mismatch = config.restrictionMode()
                != expected.getRestrictionMode()
            || config.restrictLockedItems()
                != expected.isRestrictLockedItems()
            || config.restrictNpcCombat()
                != expected.isRestrictNpcCombat()
            || config.allowLockedItemBanking()
                != expected.isAllowLockedItemBanking()
            || config.allowUnverifiedItemActions()
            || config.allowUnverifiedNpcActions();
        if (!mismatch)
        {
            return false;
        }

        boolean previousRestoreState = restoringIntegrityConfig;
        restoringIntegrityConfig = true;
        try
        {
            applyProfileConfiguration(new ProfileSetupOptions(
                state.getEconomyMode(),
                StarterRewardChoice.POINTS,
                ProfileStateMarkers.restrictionPreset(state),
                state.getClaimedPointSourceIds().contains(
                    ProfileStateMarkers.LOCKED_VISUALS),
                IntegrityMode.INTEGRITY));
        }
        finally
        {
            restoringIntegrityConfig = previousRestoreState;
        }
        return true;
    }

    private void disableIntegrity()
    {
        CollectionSessionService active = sessionService;
        long accountHash = activeAccountHash;
        if (active == null || accountHash == -1L)
        {
            return;
        }
        submitPluginTask(DiagnosticOperation.INTEGRITY_UPDATE, () -> applySnapshotIfCurrent(
            accountHash,
            active,
            active.disableIntegrity()));
    }

    private void resetProfile()
    {
        CollectionSessionService active = sessionService;
        long accountHash = activeAccountHash;
        if (active == null || accountHash == -1L)
        {
            return;
        }
        restrictionStatePending = true;
        enforcementActive = false;
        activeUsableCardIds = java.util.Collections.emptySet();
        if (!submitPluginTask(DiagnosticOperation.PROFILE_RESET, () -> applySnapshotIfCurrent(
            accountHash,
            active,
            active.resetProfile())))
        {
            restrictionStatePending = false;
            applySnapshot(active.snapshot());
        }
    }

    private void exportBackup(java.nio.file.Path destination)
    {
        CollectionSessionService active = sessionService;
        long accountHash = activeAccountHash;
        if (active == null || accountHash == -1L)
        {
            completeDataAction(
                DiagnosticOperation.BACKUP_EXPORT.getUserMessageWithAdvice());
            return;
        }
        if (!submitPluginTask(DiagnosticOperation.BACKUP_EXPORT, () -> {
            try
            {
                java.nio.file.Path exported = active.exportBackup(destination);
                if (isCurrentSession(accountHash, active))
                {
                    completeDataAction(
                        "Save backup verified and exported as "
                            + exported.getFileName() + ".");
                }
            }
            catch (java.io.IOException | IllegalStateException failure)
            {
                if (isCurrentSession(accountHash, active))
                {
                    reportCaughtFailure(
                        DiagnosticOperation.BACKUP_EXPORT,
                        failure);
                    completeDataAction(
                        DiagnosticOperation.BACKUP_EXPORT
                            .getUserMessageWithAdvice());
                }
            }
        }))
        {
            completeDataAction(
                DiagnosticOperation.BACKUP_EXPORT.getUserMessageWithAdvice());
        }
    }

    private void importBackup(java.nio.file.Path source)
    {
        CollectionSessionService active = sessionService;
        long accountHash = activeAccountHash;
        if (active == null || accountHash == -1L)
        {
            completeDataAction(
                DiagnosticOperation.BACKUP_IMPORT.getUserMessageWithAdvice());
            return;
        }
        if (!submitPluginTask(DiagnosticOperation.BACKUP_IMPORT, () -> {
            try
            {
                SessionSnapshot imported = active.importBackup(source);
                applySnapshotIfCurrent(accountHash, active, imported);
            }
            catch (java.io.IOException | IllegalStateException failure)
            {
                if (isCurrentSession(accountHash, active))
                {
                    reportCaughtFailure(
                        DiagnosticOperation.BACKUP_IMPORT,
                        failure);
                    completeDataAction(
                        DiagnosticOperation.BACKUP_IMPORT
                            .getUserMessageWithAdvice());
                }
            }
        }))
        {
            completeDataAction(
                DiagnosticOperation.BACKUP_IMPORT.getUserMessageWithAdvice());
        }
    }

    private void restorePreviousBackup()
    {
        CollectionSessionService active = sessionService;
        long accountHash = activeAccountHash;
        if (active == null || accountHash == -1L)
        {
            completeDataAction(
                DiagnosticOperation.BACKUP_RESTORE.getUserMessageWithAdvice());
            return;
        }
        if (!submitPluginTask(DiagnosticOperation.BACKUP_RESTORE, () -> {
            try
            {
                SessionSnapshot restored = active.restorePreviousBackup();
                applySnapshotIfCurrent(accountHash, active, restored);
            }
            catch (java.io.IOException | IllegalStateException failure)
            {
                if (isCurrentSession(accountHash, active))
                {
                    reportCaughtFailure(
                        DiagnosticOperation.BACKUP_RESTORE,
                        failure);
                    completeDataAction(
                        DiagnosticOperation.BACKUP_RESTORE
                            .getUserMessageWithAdvice());
                }
            }
        }))
        {
            completeDataAction(
                DiagnosticOperation.BACKUP_RESTORE.getUserMessageWithAdvice());
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (restoringIntegrityConfig
            || !CardRestrictedAccountConfig.GROUP.equals(event.getGroup())
            || !isGameplayConfigKey(event.getKey())
            || sessionService == null)
        {
            return;
        }
        SessionSnapshot current = sessionService.snapshot();
        CollectionState state = current.getCollectionState().orElse(null);
        if (state == null || !ProfileStateMarkers.isIntegrityProfile(state))
        {
            return;
        }
        if (restoreIntegrityProfileConfiguration(state))
        {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "[Cards] Integrity mode restored the locked gameplay setting.", null);
        }
    }

    private static boolean isGameplayConfigKey(String key)
    {
        return java.util.Set.of(
            "restrictionMode",
            "restrictLockedItems",
            "allowLockedItemBanking",
            "allowUnverifiedItemActions",
            "allowUnverifiedNpcActions",
            "restrictNpcCombat").contains(key);
    }

    private void createCollection(ProfileSetupOptions options)
    {
        applyProfileConfiguration(options);
        long requestedAccountHash = activeAccountHash;
        CollectionSessionService requestedSessionService = sessionService;
        if (requestedAccountHash == -1L
            || requestedSessionService == null)
        {
            return;
        }

        restrictionStatePending = true;
        enforcementActive = false;
        activeUsableCardIds = java.util.Collections.emptySet();
        if (!submitPluginTask(DiagnosticOperation.PROFILE_CREATE, () -> {
            SessionSnapshot snapshot = requestedSessionService.create(options);
            try
            {
                snapshot = applyDeveloperTestingBalance(
                    requestedSessionService,
                    snapshot);
            }
            catch (java.io.IOException exception)
            {
                reportCaughtFailure(DiagnosticOperation.TEST_BALANCE, exception);
            }
            if (activeAccountHash == requestedAccountHash
                && sessionService == requestedSessionService)
            {
                applySnapshot(snapshot);
            }
        }))
        {
            restrictionStatePending = false;
            applySnapshot(requestedSessionService.snapshot());
        }
    }

    private SessionSnapshot applyDeveloperTestingBalance(
        CollectionSessionService targetSession,
        SessionSnapshot snapshot)
        throws java.io.IOException
    {
        if (snapshot.getStatus() != SessionStatus.READY
            || !isDeveloperTestingAllowed(snapshot))
        {
            return snapshot;
        }
        return targetSession.applyTestingBalances(
            1_000_000L,
            1_000_000L);
    }

    @FunctionalInterface
    private interface PackPurchaseOperation
    {
        PackPurchaseResult purchase(
            CollectionSessionService session,
            Random random)
            throws java.io.IOException;
    }

    private void purchasePack(PackPurchaseOperation operation)
    {
        long requestedAccountHash = activeAccountHash;
        CollectionSessionService requestedSession = sessionService;
        if (requestedAccountHash == -1L || requestedSession == null)
        {
            return;
        }

        submitPluginTask(DiagnosticOperation.PACK_PURCHASE, () -> {
            try
            {
                PackPurchaseResult result = operation.purchase(
                    requestedSession,
                    new Random());
                if (!isCurrentSession(
                    requestedAccountHash,
                    requestedSession))
                {
                    return;
                }
                applySnapshot(requestedSession.snapshot());
            }
            catch (java.io.IOException | IllegalStateException exception)
            {
                handlePackFailure(
                    requestedAccountHash,
                    requestedSession,
                    DiagnosticOperation.PACK_PURCHASE,
                    exception);
            }
        });
    }


    private void redeemStarterPack()
    {
        purchasePack(
            (requestedSession, random) ->
                requestedSession.redeemStarterPack(random));
    }

    private void purchaseStandardPack()
    {
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseStandardPack(random));
    }

    private void purchaseUncommonPlusPack()
    {
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseUncommonPlusPack(random));
    }

    private void purchaseExplorerPack()
    {
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseExplorerPack(random));
    }

    private void purchaseRareHunterPack()
    {
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseRareHunterPack(random));
    }

    private void purchaseAdventurePack()
    {
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseAdventurePack(random));
    }

    private void purchaseCollectorPack()
    {
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseCollectorPack(random));
    }

    private void redeemInitiateFoilPack()
    {
        purchasePack((session, random) -> session.redeemInitiateFoilPack(random));
    }

    private void redeemHeroPack()
    {
        purchasePack((session, random) -> session.redeemHeroPack(random));
    }

    private void redeemNoblePack()
    {
        purchasePack((session, random) -> session.redeemNoblePack(random));
    }

    private void redeemLegendPack()
    {
        purchasePack((session, random) -> session.redeemLegendPack(random));
    }

    private void redeemMythicalPack()
    {
        purchasePack((session, random) -> session.redeemMythicalPack(random));
    }

    private void redeemGodsPack()
    {
        purchasePack((session, random) -> session.redeemGodsPack(random));
    }

    private void purchaseNexusCache()
    {
        long requestedAccountHash = activeAccountHash;
        CollectionSessionService requestedSession = sessionService;
        if (requestedAccountHash == -1L || requestedSession == null)
        {
            return;
        }
        submitPluginTask(DiagnosticOperation.NEXUS_EXCHANGE, () -> {
            try
            {
                NexusCachePurchaseResult result =
                    requestedSession.purchaseNexusCache(new Random());
                if (!isCurrentSession(requestedAccountHash, requestedSession))
                {
                    return;
                }
                applySnapshot(requestedSession.snapshot());
                CardRestrictedAccountPanel currentPanel = panel;
                if (currentPanel != null)
                {
                    currentPanel.setNotice(
                        "Nexus Cache awarded " + result.getShardsAwarded()
                            + " shards.");
                }
            }
            catch (java.io.IOException | IllegalStateException exception)
            {
                handlePackFailure(
                    requestedAccountHash,
                    requestedSession,
                    DiagnosticOperation.NEXUS_EXCHANGE,
                    exception);
            }
        });
    }

    private void purchaseNoncombatNpcPack()
    {
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseNoncombatNpcPack(random));
    }

    private void purchaseAttackableNpcPack()
    {
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseAttackableNpcPack(random));
    }

    private void purchaseFoilTestPack()
    {
        if (!testingPackEnabled())
        {
            return;
        }
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseFoilTestPack(random));
    }

    private void purchasePremiumFoilTestPack()
    {
        if (!testingPackEnabled())
        {
            return;
        }
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchasePremiumFoilTestPack(random));
    }

    private void purchaseTierFoilTestPack()
    {
        if (!testingPackEnabled())
        {
            return;
        }
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseTierFoilTestPack(random));
    }

    private void purchaseArmourFoilTestPack()
    {
        if (!testingPackEnabled())
        {
            return;
        }
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseArmourFoilTestPack(random));
    }

    private void purchaseBossFoilTestPack()
    {
        if (!testingPackEnabled())
        {
            return;
        }
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseBossFoilTestPack(random));
    }

    private void purchaseIngredientFoilTestPack()
    {
        if (!testingPackEnabled())
        {
            return;
        }
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseIngredientFoilTestPack(random));
    }

    private void purchaseSignatureFoilTestPack()
    {
        if (!testingPackEnabled())
        {
            return;
        }
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseSignatureFoilTestPack(random));
    }

    private void purchaseNpcRelationshipFoilTestPack()
    {
        if (!testingPackEnabled())
        {
            return;
        }
        purchasePack(
            (requestedSession, random) ->
                requestedSession.purchaseNpcRelationshipFoilTestPack(random));
    }

    private boolean testingPackEnabled()
    {
        if (isDeveloperTestingAllowed())
        {
            return true;
        }
        CardRestrictedAccountPanel currentPanel = panel;
        if (currentPanel != null)
        {
            currentPanel.setNotice(isIntegritySnapshot(
                sessionService == null
                    ? SessionSnapshot.loggedOut()
                    : sessionService.snapshot())
                ? "Testing packs are unavailable on integrity profiles."
                : "Testing packs require the dedicated development runtime.");
        }
        return false;
    }

    private boolean isDeveloperTestingAllowed()
    {
        CollectionSessionService active = sessionService;
        return isDeveloperTestingAllowed(active == null
            ? SessionSnapshot.loggedOut()
            : active.snapshot());
    }

    private boolean isDeveloperTestingAllowed(SessionSnapshot snapshot)
    {
        IntegrityMode mode = snapshot == null
            ? null
            : snapshot.getCollectionState()
                .map(CollectionState::getIntegrityMode)
                .orElse(null);
        return DeveloperTestingRules.isAllowed(
            PluginBuildInfo.isDeveloperRuntime(),
            mode);
    }

    private static boolean isIntegritySnapshot(SessionSnapshot snapshot)
    {
        return snapshot != null
            && snapshot.getCollectionState()
                .map(ProfileStateMarkers::isIntegrityProfile)
                .orElse(false);
    }

    private void exchangeNexusCard(Rarity rarity)
    {
        long requestedAccountHash = activeAccountHash;
        CollectionSessionService requestedSession = sessionService;
        PackPresentationController controller = packPresentationController;
        if (requestedAccountHash == -1L || requestedSession == null)
        {
            return;
        }
        if ((controller != null
                && controller.getState() != PackPresentationState.IDLE)
            || !nexusExchangeInFlight.compareAndSet(false, true))
        {
            CardRestrictedAccountPanel activePanel = panel;
            if (activePanel != null)
            {
                activePanel.setNotice(
                    "Finish the current Nexus/card reveal before unlocking another card.");
            }
            return;
        }

        if (!submitPluginTask(DiagnosticOperation.NEXUS_EXCHANGE, () -> {
            try
            {
                NexusExchangeResult result =
                    requestedSession.exchangeNexusCard(rarity, new Random());
                if (!isCurrentSession(
                    requestedAccountHash,
                    requestedSession))
                {
                    return;
                }
                applySnapshot(requestedSession.snapshot());
                PackPresentationController activeController =
                    packPresentationController;
                PackPresentationOverlay overlay = packPresentationOverlay;
                if (activeController != null)
                {
                    activeController.startSingleCardReveal(
                        new PackCardResult(result.getCardId(), false, 0));
                    if (overlay != null)
                    {
                        overlay.startAnimationLoop();
                    }
                    playRevealCueForCard(result.getCardId());
                }
            }
            catch (java.io.IOException | IllegalStateException exception)
            {
                handlePackFailure(
                    requestedAccountHash,
                    requestedSession,
                    DiagnosticOperation.NEXUS_EXCHANGE,
                    exception);
            }
            finally
            {
                nexusExchangeInFlight.set(false);
            }
        }))
        {
            nexusExchangeInFlight.set(false);
        }
    }

    private boolean isNexusExchangeBlocked()
    {
        PackPresentationController controller = packPresentationController;
        return nexusExchangeInFlight.get()
            || (controller != null
                && controller.getState() != PackPresentationState.IDLE);
    }

    private void handlePackPresentationSelection(
        PackPresentationSelection selection)
    {
        PackPresentationController controller =
            packPresentationController;
        if (controller == null)
        {
            return;
        }
        if (selection.getAction() == PackPresentationAction.SKIP)
        {
            controller.skipTransition();
            return;
        }
        if (selection.getAction() == PackPresentationAction.OPEN_PACK)
        {
            if (controller.openPack())
            {
                PackPresentationOverlay overlay = packPresentationOverlay;
                if (overlay != null)
                {
                    overlay.startAnimationLoop();
                }
            }
            return;
        }
        if (selection.getAction()
            == PackPresentationAction.CLOSE_SUMMARY)
        {
            controller.closeSummary();
            CollectionSessionService active = sessionService;
            if (active != null)
            {
                renderSnapshot(active.snapshot());
            }
            return;
        }
        if (selection.getAction()
            == PackPresentationAction.REVEAL_CARD)
        {
            revealPackCard(selection.getCardPosition());
        }
    }

    private void revealPackCard(int cardPosition)
    {
        long requestedAccountHash = activeAccountHash;
        CollectionSessionService requestedSession = sessionService;
        PackPresentationController controller =
            packPresentationController;
        if (requestedAccountHash == -1L
            || requestedSession == null
            || controller == null
            || !controller.markRevealRequested(cardPosition))
        {
            return;
        }

        PackPresentationOverlay overlay = packPresentationOverlay;
        if (overlay != null)
        {
            overlay.startAnimationLoop();
        }
        playRevealCueForPackPosition(controller, cardPosition);
        submitPluginTask(DiagnosticOperation.PACK_REVEAL, () -> {
            try
            {
                PackRevealResult result =
                    requestedSession.revealPackCard(cardPosition);
                if (!isCurrentSession(
                    requestedAccountHash,
                    requestedSession))
                {
                    return;
                }
                controller.onRevealCommitted(result);
                SessionSnapshot revealSnapshot = requestedSession.snapshot();
                // Pack purchase already committed ownership, foils and shards.
                // Intermediate reveal commits only change presentation/resume
                // progress, so avoid rebuilding restriction/foil/progression
                // state after every card. The final reveal still performs the
                // normal full apply because completion reconciliation can add
                // progression/achievement state.
                if (result.isComplete())
                {
                    applySnapshot(revealSnapshot);
                }
                else
                {
                    renderSnapshot(revealSnapshot);
                }
            }
            catch (java.io.IOException | IllegalStateException exception)
            {
                controller.clearRevealRequest(cardPosition);
                handlePackFailure(
                    requestedAccountHash,
                    requestedSession,
                    DiagnosticOperation.PACK_REVEAL,
                    exception);
            }
        });
    }

    private void playDealCardCue(int cardPosition)
    {
        AudioCueManager cues = audioCueManager;
        if (cues != null && config.packRevealSounds())
        {
            cues.playDealCard(cardPosition);
        }
    }

    private void playRevealCueForCard(String cardId)
    {
        AudioCueManager cues = audioCueManager;
        if (cues == null || cardId == null || !config.packRevealSounds())
        {
            return;
        }
        try
        {
            cues.playReveal(catalogue.requireCard(cardId).getRarity());
        }
        catch (IllegalArgumentException ignored)
        {
            // Cosmetic only.
        }
    }

    private void playRevealCueForPackPosition(
        PackPresentationController controller,
        int cardPosition)
    {
        AudioCueManager cues = audioCueManager;
        if (cues == null || controller == null || !config.packRevealSounds())
        {
            return;
        }
        try
        {
            com.cardrestricted.presentation.PackPresentationSnapshot snapshot =
                controller.snapshot();
            if (cardPosition < 0
                || cardPosition >= snapshot.getCardResults().size())
            {
                return;
            }
            PackCardResult card = snapshot.getCardResults().get(cardPosition);
            cues.playReveal(
                catalogue.requireCard(card.getCardId()).getRarity(),
                card.isFoil());
        }
        catch (IllegalArgumentException ignored)
        {
            // Cosmetic only.
        }
    }

    private void playRevealCue(PackRevealResult result)
    {
        AudioCueManager cues = audioCueManager;
        if (cues == null || result == null || !config.packRevealSounds())
        {
            return;
        }
        try
        {
            cues.playReveal(
                catalogue.requireCard(result.getRevealedCard().getCardId())
                    .getRarity(),
                result.getRevealedCard().isFoil());
        }
        catch (IllegalArgumentException ignored)
        {
            // Cosmetic only.
        }
    }

    private void revealAllPackCards()
    {
        long requestedAccountHash = activeAccountHash;
        CollectionSessionService requestedSession = sessionService;
        PackPresentationController controller =
            packPresentationController;
        if (requestedAccountHash == -1L
            || requestedSession == null
            || controller == null)
        {
            return;
        }

        controller.skipTransition();
        List<Integer> positions = controller.markAllRevealRequested();
        if (positions.isEmpty())
        {
            return;
        }
        PackPresentationOverlay overlay = packPresentationOverlay;
        if (overlay != null)
        {
            overlay.startAnimationLoop();
        }

        submitPluginTask(DiagnosticOperation.PACK_REVEAL, () -> {
            SessionSnapshot completedSnapshot = null;
            for (int index = 0; index < positions.size(); index++)
            {
                int cardPosition = positions.get(index);
                try
                {
                    PackRevealResult result =
                        requestedSession.revealPackCard(cardPosition);
                    if (!isCurrentSession(
                        requestedAccountHash,
                        requestedSession))
                    {
                        return;
                    }
                    controller.onRevealCommitted(result);
                    playRevealCue(result);
                    completedSnapshot = requestedSession.snapshot();
                }
                catch (java.io.IOException | IllegalStateException exception)
                {
                    for (int pendingIndex = index;
                         pendingIndex < positions.size();
                         pendingIndex++)
                    {
                        controller.clearRevealRequest(
                            positions.get(pendingIndex));
                    }
                    handlePackFailure(
                        requestedAccountHash,
                        requestedSession,
                        DiagnosticOperation.PACK_REVEAL,
                        exception);
                    return;
                }
            }
            if (completedSnapshot != null
                && isCurrentSession(requestedAccountHash, requestedSession))
            {
                applySnapshot(completedSnapshot);
            }
        });
    }

    @Override
    public void keyPressed(KeyEvent event)
    {
        PackPresentationOverlay overlay = packPresentationOverlay;
        PackPresentationController controller =
            packPresentationController;
        if (overlay == null || controller == null || !overlay.isActive())
        {
            return;
        }

        if (event.getKeyCode() == KeyEvent.VK_SPACE)
        {
            revealAllPackCards();
        }
        else if (event.getKeyCode() == KeyEvent.VK_ESCAPE)
        {
            if (controller.getState()
                == com.cardrestricted.presentation.PackPresentationState.SUMMARY)
            {
                controller.closeSummary();
            }
            else
            {
                controller.skipTransition();
            }
        }
        event.consume();
    }

    @Override
    public void keyReleased(KeyEvent event)
    {
        PackPresentationOverlay overlay = packPresentationOverlay;
        if (overlay != null && overlay.isActive())
        {
            event.consume();
        }
    }

    @Override
    public void keyTyped(KeyEvent event)
    {
        PackPresentationOverlay overlay = packPresentationOverlay;
        if (overlay != null && overlay.isActive())
        {
            event.consume();
        }
    }

    private boolean isCurrentSession(
        long expectedAccountHash,
        CollectionSessionService expectedSession)
    {
        return activeAccountHash == expectedAccountHash
            && sessionService == expectedSession;
    }

    private void applySnapshotIfCurrent(
        long expectedAccountHash,
        CollectionSessionService expectedSession,
        SessionSnapshot snapshot)
    {
        if (!isCurrentSession(expectedAccountHash, expectedSession))
        {
            return;
        }
        applySnapshot(snapshot);
    }

    private void handlePackFailure(
        long expectedAccountHash,
        CollectionSessionService expectedSession,
        DiagnosticOperation operation,
        Exception exception)
    {
        if (!isCurrentSession(expectedAccountHash, expectedSession))
        {
            return;
        }

        renderSnapshot(expectedSession.snapshot());
        reportCaughtFailure(operation, exception);
    }

    private void applySnapshot(SessionSnapshot snapshot)
    {
        lastSessionStatus = snapshot.getStatus();
        restrictionStatePending = snapshot.getStatus() == SessionStatus.ERROR;
        recordSessionSnapshotDiagnostics(snapshot);
        PackPresentationController controller =
            packPresentationController;
        PackPresentationOverlay overlay = packPresentationOverlay;
        if (controller != null)
        {
            if (snapshot.getStatus() == SessionStatus.READY)
            {
                controller.synchronise(
                    snapshot.getCollectionState()
                        .flatMap(CollectionState::getPendingPackReveal));
            }
            else
            {
                controller.reset();
            }
        }
        if (overlay != null)
        {
            if (controller != null
                && controller.getState()
                    != com.cardrestricted.presentation.PackPresentationState.IDLE)
            {
                overlay.startAnimationLoop();
            }
            else
            {
                overlay.stopAnimationLoop();
            }
        }
        boolean wasRuntimeActive = enforcementActive
            && !clientSessionSuspended;
        if (snapshot.getStatus() == SessionStatus.READY)
        {
            CollectionState state =
                snapshot.getCollectionState().orElseThrow();
            activeIntegrityProfile =
                ProfileStateMarkers.isIntegrityProfile(state);
            restoreIntegrityProfileConfiguration(state);
            boolean collectionShapeChanged =
                !state.getCollectionId().equals(appliedCollectionShapeId)
                || state.getOwnedCardIds() != appliedOwnedCardIdsIdentity
                || state.getFoilCardIds() != appliedFoilCardIdsIdentity;
            if (collectionShapeChanged)
            {
                FoilEntitlementSnapshot foilEntitlements =
                    foilEntitlementResolver.resolve(
                        state.getOwnedCardIds(),
                        state.getFoilCardIds());
                activeUsableCardIds = foilEntitlements.getUsableCardIds();
                activeUniqueCardCount =
                    ProgressionMilestonePolicy.uniqueOwnedCardCount(
                        catalogue,
                        state);
                appliedCollectionShapeId = state.getCollectionId();
                appliedOwnedCardIdsIdentity = state.getOwnedCardIds();
                appliedFoilCardIdsIdentity = state.getFoilCardIds();
            }
            enforcementActive = !clientSessionSuspended;
            restrictionStatePending = false;
        }
        else
        {
            activeUsableCardIds = java.util.Collections.emptySet();
            appliedCollectionShapeId = null;
            appliedOwnedCardIdsIdentity = java.util.Collections.emptySet();
            appliedFoilCardIdsIdentity = java.util.Collections.emptySet();
            activeUniqueCardCount = 0;
            activeIntegrityProfile = false;
            enforcementActive = false;
            if (snapshot.getStatus() != SessionStatus.ERROR)
            {
                restrictionStatePending = false;
            }
            questBaselineReady = false;
            questBaselineTicksRemaining = 0;
            questRewardStateDirty = false;
        }

        renderSnapshot(snapshot);
        if (snapshot.getStatus() == SessionStatus.READY
            && enforcementActive
            && !clientSessionSuspended)
        {
            publishAchievementNotifications();
            initializeSkillLevelBaselines();
            if (!wasRuntimeActive)
            {
                initializeNoncombatBaselines(
                    snapshot.getCollectionState().orElseThrow());
                initializeQuestBaselines();
            }
        }
    }

    private void recordSessionSnapshotDiagnostics(SessionSnapshot snapshot)
    {
        if (snapshot.getStatus() == SessionStatus.ERROR)
        {
            SessionFailureCode failureCode = snapshot.getFailureCode()
                .orElse(SessionFailureCode.LOAD_FAILED);
            LocalDiagnosticLog log = diagnosticLog;
            if (log != null)
            {
                log.recordFailureType(
                    DiagnosticEventCode.SESSION_FAILURE,
                    diagnosticOperationFor(failureCode),
                    snapshot.getFailureType());
            }
        }
        else if (snapshot.getStatus() == SessionStatus.READY
            && (snapshot.getMessage().contains("local backup")
                || snapshot.getMessage().contains("recovery-quarantine")))
        {
            recordDiagnostic(
                DiagnosticEventCode.RECOVERY_APPLIED,
                DiagnosticOperation.SESSION_OPEN);
        }
    }

    private static DiagnosticOperation diagnosticOperationFor(
        SessionFailureCode failureCode)
    {
        switch (failureCode)
        {
            case PROFILE_CREATE_UNAVAILABLE:
            case PROFILE_CREATE_FAILED:
                return DiagnosticOperation.PROFILE_CREATE;
            case INTEGRITY_UPDATE_FAILED:
                return DiagnosticOperation.INTEGRITY_UPDATE;
            case PROFILE_RESET_FAILED:
                return DiagnosticOperation.PROFILE_RESET;
            case NO_ACTIVE_PROFILE:
                return DiagnosticOperation.SESSION_OPEN;
            case LOAD_FAILED:
            default:
                return DiagnosticOperation.SESSION_OPEN;
        }
    }

    private void publishAchievementNotifications()
    {
        CollectionSessionService activeSession = sessionService;
        if (activeSession == null)
        {
            return;
        }
        List<AchievementDefinition> completions =
            activeSession.drainAchievementNotifications();
        if (completions.isEmpty())
        {
            return;
        }
        AchievementToastOverlay toastOverlay = achievementToastOverlay;
        if (config.achievementToasts() && toastOverlay != null)
        {
            toastOverlay.enqueue(completions);
        }
        if (!config.achievementNotifications())
        {
            return;
        }
        clientThread.invokeLater(() -> {
            for (AchievementDefinition completion : completions)
            {
                client.addChatMessage(
                    ChatMessageType.GAMEMESSAGE,
                    "",
                    "[Cards] Collection goal complete: "
                        + completion.getDisplayName() + " — "
                        + completion.getDescription(),
                    null);
            }
        });
    }

    private void renderSnapshot(SessionSnapshot snapshot)
    {
        pendingPanelSnapshot.set(Objects.requireNonNull(snapshot, "snapshot"));
        schedulePanelRender();
    }

    private void schedulePanelRender()
    {
        if (!panelRenderScheduled.compareAndSet(false, true))
        {
            return;
        }
        SwingUtilities.invokeLater(this::drainPanelRender);
    }

    private void drainPanelRender()
    {
        SessionSnapshot latest = pendingPanelSnapshot.getAndSet(null);
        try
        {
            CardRestrictedAccountPanel activePanel = panel;
            if (activePanel != null && latest != null)
            {
                if (latest.getStatus() == SessionStatus.READY)
                {
                    activePanel.updateQuestCompletions(completedQuestKeys);
                }
                activePanel.render(latest);
            }
        }
        finally
        {
            panelRenderScheduled.set(false);
            if (pendingPanelSnapshot.get() != null)
            {
                schedulePanelRender();
            }
        }
    }

    private CollectionSessionService createSessionService()
    {
        return new CollectionSessionService(
            Objects.requireNonNull(
                pluginPaths,
                "pluginPaths").charactersDirectory(),
            catalogue,
            new CharacterKeyDeriver(),
            Clock.systemUTC());
    }

    private void flushPendingNpcKillAwards()
    {
        if (pendingNpcPointAwards.isEmpty())
        {
            return;
        }
        CollectionSessionService rewardSession = sessionService;
        long rewardAccountHash = activeAccountHash;
        if (rewardSession == null || rewardAccountHash == -1L)
        {
            pendingNpcPointAwards.clear();
            return;
        }

        List<PointAward> batch = new java.util.ArrayList<>(
            pendingNpcPointAwards.values());
        pendingNpcPointAwards.clear();
        submitPluginTask(
            DiagnosticOperation.NPC_REWARD,
            () -> commitNpcKillAwardBatch(
                rewardSession,
                rewardAccountHash,
                batch));
    }

    private void commitNpcKillAwardBatch(
        CollectionSessionService rewardSession,
        long rewardAccountHash,
        List<PointAward> awards)
    {
        try
        {
            SessionSnapshot before = rewardSession.snapshot();
            long previousBalance = before.getCollectionState()
                .orElseThrow()
                .getPoints();
            SessionSnapshot updated = rewardSession.awardPointsBatch(awards);
            if (activeAccountHash != rewardAccountHash
                || sessionService != rewardSession)
            {
                return;
            }

            renderSnapshot(updated);
            long balance = updated.getCollectionState()
                .orElseThrow()
                .getPoints();
            notifyPointThresholdCrossed(previousBalance, balance);
        }
        catch (DuplicatePointAwardException ignored)
        {
            // The whole batch was already durable; repeated callbacks must not
            // create another reward.
        }
        catch (java.io.IOException | IllegalStateException exception)
        {
            if (activeAccountHash == rewardAccountHash
                && sessionService == rewardSession)
            {
                reportCaughtFailure(
                    DiagnosticOperation.NPC_REWARD,
                    exception);
            }
        }
    }

    private void commitNoncombatXp(
        CollectionSessionService xpSession,
        long xpAccountHash,
        Skill skill,
        int targetXp,
        NoncombatXpObservation observation)
    {
        try
        {
            NoncombatXpProcessResult result =
                xpSession.processNoncombatXp(observation);
            if (activeAccountHash != xpAccountHash
                || sessionService != xpSession)
            {
                return;
            }

            renderSnapshot(SessionSnapshot.ready(result.getState()));
            notifyPointThresholdCrossed(
                result.getState().getPoints() - result.getPointsAwarded(),
                result.getState().getPoints());
            completeNoncombatXpBatch(
                xpSession,
                xpAccountHash,
                skill,
                targetXp,
                true);
        }
        catch (java.io.IOException | IllegalStateException exception)
        {
            completeNoncombatXpBatch(
                xpSession,
                xpAccountHash,
                skill,
                targetXp,
                false);
            if (activeAccountHash == xpAccountHash
                && sessionService == xpSession)
            {
                reportCaughtFailure(
                    DiagnosticOperation.XP_REWARD,
                    exception);
            }
        }
    }

    private void completeNoncombatXpBatch(
        CollectionSessionService xpSession,
        long xpAccountHash,
        Skill skill,
        int targetXp,
        boolean committed)
    {
        clientThread.invokeLater(() -> {
            if (!isCurrentSession(xpAccountHash, xpSession))
            {
                return;
            }
            pendingNoncombatXpCommits.remove(skill);
            if (!committed)
            {
                return;
            }
            noncombatSessionBaselines.put(skill, targetXp);
            Integer latestXp = latestNoncombatXp.get(skill);
            Optional<NoncombatSkill> noncombatSkill = toNoncombatSkill(skill);
            if (latestXp != null && noncombatSkill.isPresent())
            {
                queueNoncombatXpBatch(
                    skill,
                    noncombatSkill.get(),
                    latestXp);
            }
        });
    }

    private void observeSkillLevel(StatChanged event)
    {
        Skill skill = event.getSkill();
        if (isOverallSkill(skill))
        {
            return;
        }

        Integer baseline = skillLevelSessionBaselines.get(skill);
        if (baseline == null)
        {
            skillLevelSessionBaselines.put(skill, event.getLevel());
            return;
        }
        if (!isCollectionRuntimeActive())
        {
            skillLevelSessionBaselines.put(skill, event.getLevel());
            return;
        }
        if (event.getLevel() <= baseline)
        {
            return;
        }

        skillLevelSessionBaselines.put(skill, event.getLevel());
        CollectionSessionService levelSession = sessionService;
        long levelAccountHash = activeAccountHash;
        if (levelSession == null || levelAccountHash == -1L)
        {
            return;
        }

        PointAward award = skillLevelRewardPolicy.createAward(
            skill.name(),
            baseline,
            event.getLevel(),
            Instant.now());
        submitPluginTask(DiagnosticOperation.LEVEL_REWARD, () -> commitSkillLevelAward(
            levelSession,
            levelAccountHash,
            skill,
            event.getLevel(),
            award));
    }

    private void commitSkillLevelAward(
        CollectionSessionService levelSession,
        long levelAccountHash,
        Skill skill,
        int newLevel,
        PointAward award)
    {
        try
        {
            SessionSnapshot updated = levelSession.awardPoints(award);
            if (!isCurrentSession(levelAccountHash, levelSession))
            {
                return;
            }

            renderSnapshot(updated);
            long balance = updated.getCollectionState()
                .orElseThrow()
                .getPoints();
            notifyPointThresholdCrossed(
                balance - award.getAmount(),
                balance);
        }
        catch (DuplicatePointAwardException ignored)
        {
            // A repeated stat callback must not award the same level twice.
        }
        catch (java.io.IOException | IllegalStateException exception)
        {
            if (isCurrentSession(levelAccountHash, levelSession))
            {
                reportCaughtFailure(
                    DiagnosticOperation.LEVEL_REWARD,
                    exception);
            }
        }
    }

    private void detectQuestCompletions()
    {
        CollectionSessionService questSession = sessionService;
        long questAccountHash = activeAccountHash;
        if (questSession == null || questAccountHash == -1L)
        {
            return;
        }

        for (Quest quest : supportedF2pQuests())
        {
            QuestState current = quest.getState(client);
            if (questCompletionTracker.observe(
                quest.name(),
                current == QuestState.FINISHED))
            {
                QuestCompletionObservation observation =
                    new QuestCompletionObservation(
                        quest.name(),
                        quest.getName(),
                        Instant.now());
                PointAward award =
                    questRewardPolicy.createAward(observation);
                submitPluginTask(DiagnosticOperation.QUEST_REWARD, () -> commitQuestCompletion(
                    questSession,
                    questAccountHash,
                    quest,
                    award));
            }
        }
    }

    private void commitQuestCompletion(
        CollectionSessionService questSession,
        long questAccountHash,
        Quest quest,
        PointAward award)
    {
        try
        {
            SessionSnapshot updated = questSession.awardPoints(award);
            if (!isCurrentSession(questAccountHash, questSession))
            {
                return;
            }

            renderSnapshot(updated);
            long balance = updated.getCollectionState()
                .orElseThrow()
                .getPoints();
            clientThread.invokeLater(() -> markQuestAwardFinished(quest));
            notifyPointThresholdCrossed(
                balance - award.getAmount(),
                balance);
        }
        catch (DuplicatePointAwardException ignored)
        {
            if (isCurrentSession(questAccountHash, questSession))
            {
                clientThread.invokeLater(() ->
                    markQuestAwardFinished(quest));
            }
        }
        catch (java.io.IOException | IllegalStateException exception)
        {
            if (isCurrentSession(questAccountHash, questSession))
            {
                recordDiagnosticFailure(
                    DiagnosticEventCode.TASK_FAILED,
                    DiagnosticOperation.QUEST_REWARD,
                    exception);
                clientThread.invokeLater(() -> {
                    questCompletionTracker.markFailed(quest.name());
                    questRewardStateDirty = true;
                    client.addChatMessage(
                        ChatMessageType.GAMEMESSAGE,
                        "",
                        "[Cards] " + DiagnosticOperation.QUEST_REWARD
                            .getUserMessageWithAdvice(),
                        null);
                });
            }
        }
    }

    private void notifyPointThresholdCrossed(
        long previousBalance,
        long currentBalance)
    {
        java.util.OptionalLong crossed =
            PointBalanceThresholds.highestCrossedThreshold(
                previousBalance,
                currentBalance);
        if (!crossed.isPresent())
        {
            return;
        }
        long reached = crossed.getAsLong();
        clientThread.invokeLater(() ->
            client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                "[Cards] Point balance reached "
                    + String.format(java.util.Locale.UK, "%,d", reached)
                    + ".",
                null));
    }

    private void markQuestAwardFinished(Quest quest)
    {
        questCompletionTracker.markCommitted(quest.name());
    }

    private void initializeNoncombatBaselines(CollectionState state)
    {
        clientThread.invokeLater(() -> {
            if (!isCollectionRuntimeActive())
            {
                return;
            }
            for (Skill skill : supportedNoncombatSkills())
            {
                int currentXp = client.getSkillExperience(skill);
                int baseline = currentXp;
                Optional<NoncombatSkill> noncombatSkill =
                    toNoncombatSkill(skill);
                if (noncombatSkill.isPresent())
                {
                    Long watermark = state.getNoncombatXpWatermarks().get(
                        noncombatSkill.get().name());
                    if (watermark != null
                        && watermark >= 0L
                        && watermark <= currentXp
                        && currentXp - watermark < NONCOMBAT_XP_BATCH_SIZE)
                    {
                        baseline = watermark.intValue();
                    }
                }
                noncombatSessionBaselines.put(skill, baseline);
                latestNoncombatXp.put(skill, currentXp);
            }
        });
    }

    private void initializeSkillLevelBaselines()
    {
        clientThread.invokeLater(() -> {
            if (!isCollectionRuntimeActive())
            {
                return;
            }
            for (Skill skill : supportedRewardSkills())
            {
                skillLevelSessionBaselines.putIfAbsent(
                    skill,
                    client.getRealSkillLevel(skill));
            }
        });
    }

    private void captureNoncombatBaselines()
    {
        pendingNoncombatXpCommits.clear();
        for (Skill skill : supportedNoncombatSkills())
        {
            int xp = client.getSkillExperience(skill);
            noncombatSessionBaselines.put(skill, xp);
            latestNoncombatXp.put(skill, xp);
        }
    }

    private void captureSkillLevelBaselines()
    {
        for (Skill skill : supportedRewardSkills())
        {
            skillLevelSessionBaselines.put(
                skill,
                client.getRealSkillLevel(skill));
        }
    }

    private void captureQuestBaselines()
    {
        for (Quest quest : supportedF2pQuests())
        {
            questCompletionTracker.establishBaseline(
                quest.name(),
                quest.getState(client) == QuestState.FINISHED);
        }
    }

    private void requestQuestStatusRefresh()
    {
        clientThread.invokeLater(() -> {
            if (!isCollectionRuntimeActive() || questCompletionIndex == null)
            {
                SwingUtilities.invokeLater(() -> {
                    CardRestrictedAccountPanel activePanel = panel;
                    if (activePanel != null)
                    {
                        activePanel.cancelQuestStatusRefresh();
                    }
                });
                return;
            }
            refreshQuestTrackerCompletionState();
        });
    }

    private void refreshQuestTrackerCompletionState()
    {
        if (questCompletionIndex == null)
        {
            return;
        }
        if (questTrackerScanActive)
        {
            questTrackerRescanRequested = true;
            return;
        }
        questTrackerScanActive = true;
        questTrackerScanCursor = 0;
        questTrackerScanCompleted = new LinkedHashSet<>(completedQuestKeys);
    }

    private void processQuestTrackerCompletionChunk()
    {
        if (!questTrackerScanActive)
        {
            return;
        }
        QuestCompletionIndex index = questCompletionIndex;
        if (index == null)
        {
            questTrackerScanActive = false;
            questTrackerScanCursor = 0;
            return;
        }

        Quest[] quests = Quest.values();
        int scriptChecks = 0;
        while (questTrackerScanCursor < quests.length
            && scriptChecks < QUEST_TRACKER_SCRIPT_BUDGET_PER_TICK)
        {
            Quest quest = quests[questTrackerScanCursor++];
            Optional<String> questKey = index.findQuestKey(quest.getName());
            if (!questKey.isPresent())
            {
                continue;
            }
            scriptChecks++;
            if (quest.getState(client) == QuestState.FINISHED)
            {
                questTrackerScanCompleted.add(questKey.get());
            }
            else
            {
                questTrackerScanCompleted.remove(questKey.get());
            }
        }

        if (questTrackerScanCursor < quests.length)
        {
            return;
        }

        Set<String> snapshot = Collections.unmodifiableSet(
            new LinkedHashSet<>(questTrackerScanCompleted));
        questTrackerScanActive = false;
        questTrackerScanCursor = 0;
        questTrackerScanCompleted = new LinkedHashSet<>();
        completedQuestKeys = snapshot;
        SwingUtilities.invokeLater(() ->
        {
            CardRestrictedAccountPanel activePanel = panel;
            if (activePanel != null)
            {
                activePanel.completeQuestStatusRefresh(snapshot);
            }
        });

        if (questTrackerRescanRequested)
        {
            questTrackerRescanRequested = false;
            refreshQuestTrackerCompletionState();
        }
    }

    private void initializeQuestBaselines()
    {
        clientThread.invokeLater(() -> {
            if (!isCollectionRuntimeActive())
            {
                return;
            }
            questBaselineReady = false;
            questBaselineTicksRemaining =
                QUEST_BASELINE_DELAY_TICKS;
            questRewardStateDirty = false;
            questRewardCheckCooldown = 0;
            questTrackerScanActive = false;
            questTrackerRescanRequested = false;
            questTrackerScanCursor = 0;
            questTrackerScanCompleted.clear();
        });
    }

    private Quest[] supportedF2pQuests()
    {
        return new Quest[] {
            Quest.BELOW_ICE_MOUNTAIN,
            Quest.BLACK_KNIGHTS_FORTRESS,
            Quest.COOKS_ASSISTANT,
            Quest.THE_CORSAIR_CURSE,
            Quest.DEMON_SLAYER,
            Quest.DORICS_QUEST,
            Quest.DRAGON_SLAYER_I,
            Quest.ERNEST_THE_CHICKEN,
            Quest.GOBLIN_DIPLOMACY,
            Quest.IMP_CATCHER,
            Quest.THE_KNIGHTS_SWORD,
            Quest.MISTHALIN_MYSTERY,
            Quest.PIRATES_TREASURE,
            Quest.PRINCE_ALI_RESCUE,
            Quest.THE_RESTLESS_GHOST,
            Quest.ROMEO__JULIET,
            Quest.RUNE_MYSTERIES,
            Quest.SHEEP_SHEARER,
            Quest.SHIELD_OF_ARRAV,
            Quest.VAMPYRE_SLAYER,
            Quest.WITCHS_POTION,
            Quest.X_MARKS_THE_SPOT
        };
    }

    private Skill[] supportedNoncombatSkills()
    {
        return new Skill[] {
            Skill.AGILITY,
            Skill.CONSTRUCTION,
            Skill.COOKING,
            Skill.CRAFTING,
            Skill.FARMING,
            Skill.FIREMAKING,
            Skill.FISHING,
            Skill.FLETCHING,
            Skill.HERBLORE,
            Skill.HUNTER,
            Skill.MINING,
            Skill.RUNECRAFT,
            Skill.SAILING,
            Skill.SLAYER,
            Skill.SMITHING,
            Skill.THIEVING,
            Skill.WOODCUTTING
        };
    }

    private Skill[] supportedRewardSkills()
    {
        return java.util.Arrays.stream(Skill.values())
            .filter(skill -> !isOverallSkill(skill))
            .toArray(Skill[]::new);
    }

    private boolean isOverallSkill(Skill skill)
    {
        return "Overall".equals(skill.getName());
    }

    private Optional<NoncombatSkill> toNoncombatSkill(Skill skill)
    {
        switch (skill)
        {
            case AGILITY:
                return Optional.of(NoncombatSkill.AGILITY);
            case CONSTRUCTION:
                return Optional.of(NoncombatSkill.CONSTRUCTION);
            case COOKING:
                return Optional.of(NoncombatSkill.COOKING);
            case CRAFTING:
                return Optional.of(NoncombatSkill.CRAFTING);
            case FARMING:
                return Optional.of(NoncombatSkill.FARMING);
            case FIREMAKING:
                return Optional.of(NoncombatSkill.FIREMAKING);
            case FISHING:
                return Optional.of(NoncombatSkill.FISHING);
            case FLETCHING:
                return Optional.of(NoncombatSkill.FLETCHING);
            case HERBLORE:
                return Optional.of(NoncombatSkill.HERBLORE);
            case HUNTER:
                return Optional.of(NoncombatSkill.HUNTER);
            case MINING:
                return Optional.of(NoncombatSkill.MINING);
            case RUNECRAFT:
                return Optional.of(NoncombatSkill.RUNECRAFT);
            case SAILING:
                return Optional.of(NoncombatSkill.SAILING);
            case SLAYER:
                return Optional.of(NoncombatSkill.SLAYER);
            case SMITHING:
                return Optional.of(NoncombatSkill.SMITHING);
            case THIEVING:
                return Optional.of(NoncombatSkill.THIEVING);
            case WOODCUTTING:
                return Optional.of(NoncombatSkill.WOODCUTTING);
            default:
                return Optional.empty();
        }
    }


    private void updatePanelBusy(String message)
    {
        SwingUtilities.invokeLater(() -> {
            CardRestrictedAccountPanel activePanel = panel;
            if (activePanel != null)
            {
                activePanel.setBusy(message);
            }
        });
    }
    private boolean submitPluginTask(
        DiagnosticOperation operation,
        Runnable action)
    {
        ManagedTaskScope scope = taskScope;
        if (scope == null || !scope.submit(operation, action))
        {
            recordDiagnostic(
                DiagnosticEventCode.TASK_REJECTED,
                operation);
            if (startupComplete)
            {
                notifyPlayerFailure(operation);
            }
            return false;
        }
        return true;
    }

    private void handleTaskFailure(
        DiagnosticOperation operation,
        Throwable failure)
    {
        recordDiagnosticFailure(
            DiagnosticEventCode.TASK_FAILED,
            operation,
            failure);
        if (startupComplete)
        {
            notifyPlayerFailure(operation);
        }
    }

    private void reportCaughtFailure(
        DiagnosticOperation operation,
        Throwable failure)
    {
        recordDiagnosticFailure(
            DiagnosticEventCode.TASK_FAILED,
            operation,
            failure);
        notifyPlayerFailure(operation);
    }

    private void notifyPlayerFailure(DiagnosticOperation operation)
    {
        String message = operation.getUserMessageWithAdvice();
        updatePanelNotice(message);
        if (clientThread != null && client != null)
        {
            clientThread.invokeLater(() -> client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                "[Cards] " + message,
                null));
        }
    }

    private void exportDiagnostics()
    {
        DiagnosticReportExporter exporter = diagnosticExporter;
        LocalDiagnosticLog log = diagnosticLog;
        if (exporter == null || log == null)
        {
            completeDiagnosticExport(
                DiagnosticOperation.DIAGNOSTIC_EXPORT
                    .getUserMessageWithAdvice());
            return;
        }
        DiagnosticRuntimeSnapshot runtime = captureDiagnosticRuntime();
        submitPluginTask(DiagnosticOperation.DIAGNOSTIC_EXPORT, () -> {
            try
            {
                IntegrityTraceLog trace = integrityTraceLog;
                java.nio.file.Path report = trace == null
                    ? exporter.export(log, runtime)
                    : exporter.export(log, trace, runtime);
                recordDiagnostic(
                    DiagnosticEventCode.DIAGNOSTIC_EXPORT_COMPLETE,
                    DiagnosticOperation.DIAGNOSTIC_EXPORT);
                completeDiagnosticExport(
                    "Diagnostic report saved locally as "
                        + report.getFileName()
                        + ". It was not transmitted anywhere.");
            }
            catch (java.io.IOException failure)
            {
                recordDiagnosticFailure(
                    DiagnosticEventCode.DIAGNOSTIC_EXPORT_FAILED,
                    DiagnosticOperation.DIAGNOSTIC_EXPORT,
                    failure);
                completeDiagnosticExport(
                    DiagnosticOperation.DIAGNOSTIC_EXPORT
                        .getUserMessageWithAdvice());
            }
        });
    }

    private void completeDiagnosticExport(String message)
    {
        SwingUtilities.invokeLater(() -> {
            CardRestrictedAccountPanel activePanel = panel;
            if (activePanel != null)
            {
                activePanel.completeDiagnosticExport(message);
            }
        });
    }

    private void completeDataAction(String message)
    {
        SwingUtilities.invokeLater(() -> {
            CardRestrictedAccountPanel activePanel = panel;
            if (activePanel != null)
            {
                activePanel.completeDataAction(message);
            }
        });
    }

    private void updatePanelNotice(String message)
    {
        SwingUtilities.invokeLater(() -> {
            CardRestrictedAccountPanel activePanel = panel;
            if (activePanel != null)
            {
                activePanel.setNotice(message);
            }
        });
    }

    private DiagnosticRuntimeSnapshot captureDiagnosticRuntime()
    {
        ManagedTaskScope scope = taskScope;
        GameState gameState = client == null ? null : client.getGameState();
        boolean artworkActive = artworkWarmupExecutor != null
            && !artworkWarmupExecutor.isShutdown();
        return new DiagnosticRuntimeSnapshot(
            gameState == null ? "UNKNOWN" : gameState.name(),
            lastSessionStatus.name(),
            startupComplete,
            clientSessionSuspended,
            safeCollectionRuntimeActive(),
            safeRestrictionRuntimeActive(),
            panel != null,
            lockedItemOverlay != null || lockedNpcOverlay != null,
            artworkActive,
            scope != null && scope.isAccepting(),
            scope == null ? 0 : scope.trackedTaskCount(),
            restrictionStatePending,
            autocastVerifiedForSession,
            shopInteractionAuthorization.isShopOpen(),
            shopInteractionAuthorization.isShopAuthorized(),
            storageInteractionAuthorization.isStorageOpen(),
            storageInteractionAuthorization.isStorageAuthorized(),
            grandExchangeInteractionAuthorization.isExchangeOpen(),
            grandExchangeInteractionAuthorization.isExchangeAuthorized(),
            npcServiceInterfaceAuthorization.isServiceOpen(),
            npcServiceInterfaceAuthorization.isServiceAuthorized(),
            integrityTraceLog == null ? 0 : integrityTraceLog.size());
    }

    private boolean safeCollectionRuntimeActive()
    {
        try
        {
            return client != null && isCollectionRuntimeActive();
        }
        catch (RuntimeException ignored)
        {
            return false;
        }
    }

    private boolean safeRestrictionRuntimeActive()
    {
        try
        {
            return client != null && config != null
                && isRestrictionRuntimeActive();
        }
        catch (RuntimeException ignored)
        {
            return false;
        }
    }

    private void attemptEmergencyDiagnosticExport()
    {
        DiagnosticReportExporter exporter = diagnosticExporter;
        LocalDiagnosticLog log = diagnosticLog;
        if (exporter == null || log == null)
        {
            return;
        }
        try
        {
            IntegrityTraceLog trace = integrityTraceLog;
            if (trace == null)
            {
                exporter.export(log, captureDiagnosticRuntime());
            }
            else
            {
                exporter.export(log, trace, captureDiagnosticRuntime());
            }
        }
        catch (java.io.IOException ignored)
        {
            // Startup failure reporting is best effort and remains local.
        }
    }

    private void recordIntegrityTrace(
        MenuAction action,
        String option,
        IntegrityTraceDecision decision,
        IntegrityTraceReason reason,
        int packedWidgetId,
        int entityId)
    {
        IntegrityTraceLog trace = integrityTraceLog;
        if (trace == null)
        {
            return;
        }
        int tick = -1;
        try
        {
            tick = client == null ? -1 : client.getTickCount();
        }
        catch (RuntimeException ignored)
        {
            tick = -1;
        }
        trace.record(
            tick,
            action,
            option,
            decision,
            reason,
            packedWidgetId,
            entityId);
    }

    private void recordDiagnostic(
        DiagnosticEventCode eventCode,
        DiagnosticOperation operation)
    {
        LocalDiagnosticLog log = diagnosticLog;
        if (log != null)
        {
            log.record(eventCode, operation);
        }
    }

    private void recordDiagnosticFailure(
        DiagnosticEventCode eventCode,
        DiagnosticOperation operation,
        Throwable failure)
    {
        LocalDiagnosticLog log = diagnosticLog;
        if (log != null)
        {
            log.recordFailure(eventCode, operation, failure);
        }
    }


}
