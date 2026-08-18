package com.cardrestricted.ui;

import com.cardrestricted.PluginBuildInfo;
import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.collection.ProfileSetupOptions;
import com.cardrestricted.collection.ProfileStateMarkers;
import com.cardrestricted.domain.RestrictionPreset;
import com.cardrestricted.foil.FoilEntitlementResolver;
import com.cardrestricted.foil.FoilEntitlementSnapshot;
import com.cardrestricted.foil.FoilRewardRegistry;
import com.cardrestricted.foil.FoilRewardText;
import com.cardrestricted.catalog.CardCategory;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.catalog.CatalogueTextQuality;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.collection.achievement.AchievementCompletionRecord;
import com.cardrestricted.collection.achievement.AchievementCompletionState;
import com.cardrestricted.collection.achievement.AchievementDefinition;
import com.cardrestricted.collection.achievement.AchievementProgress;
import com.cardrestricted.collection.achievement.AchievementRegistry;
import com.cardrestricted.collection.achievement.AchievementService;
import com.cardrestricted.collection.achievement.AchievementSnapshot;
import com.cardrestricted.collection.activity.CardUnlockRecord;
import com.cardrestricted.collection.activity.CardUnlockSource;
import com.cardrestricted.collection.activity.CollectionActivitySnapshot;
import com.cardrestricted.collection.activity.PackActivityRecord;
import com.cardrestricted.collection.activity.PackNames;
import com.cardrestricted.collection.progress.CollectionProgress;
import com.cardrestricted.collection.progress.CollectionProgressService;
import com.cardrestricted.collection.progress.CollectionProgressSnapshot;
import com.cardrestricted.domain.ActionType;
import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.nexus.NexusExchangeCosts;
import com.cardrestricted.progression.ProgressionRewardCardPolicy;
import com.cardrestricted.pack.PackCardResult;
import com.cardrestricted.pack.PackRevealResult;
import com.cardrestricted.pack.PendingPackReveal;
import com.cardrestricted.pack.StandardPackService;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.presentation.CardArtworkProvider;
import com.cardrestricted.presentation.CardArtworkProvider.ArtworkSource;
import com.cardrestricted.points.F2pQuestCompletionRewardPolicy;
import com.cardrestricted.points.SkillLevelRewardPolicy;
import com.cardrestricted.progression.ProgressionMilestoneDefinition;
import com.cardrestricted.progression.ProgressionMilestonePolicy;
import com.cardrestricted.quest.QuestReadinessEntry;
import com.cardrestricted.quest.QuestReadinessService;
import com.cardrestricted.quest.QuestReadinessSnapshot;
import com.cardrestricted.quest.QuestRequirementRegistry;
import com.cardrestricted.quest.QuestStatus;
import com.cardrestricted.session.SessionSnapshot;
import com.cardrestricted.session.SessionStatus;
import com.cardrestricted.starter.StarterRewardChoice;
import com.cardrestricted.starter.StarterRewardState;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@SuppressWarnings("serial")
public final class CardRestrictedAccountPanel extends PluginPanel implements Scrollable
{
    private static final long serialVersionUID = 1L;
    private static final Color MUTED_TEXT = CardUiTheme.MUTED_TEXT;
    private static final Color OWNED_COLOR = CardUiTheme.OWNED;
    private static final Color LOCKED_COLOR = CardUiTheme.LOCKED;
    private static final Color COMMON_COLOR = new Color(184, 141, 92);
    private static final Color QUEST_COMPLETE_COLOR = CardUiTheme.COMPLETE;
    private static final Color QUEST_READY_COLOR = CardUiTheme.AVAILABLE;
    private static final Color QUEST_BLOCKED_COLOR = CardUiTheme.BLOCKED;
    private static final Color QUEST_UNLOCKED_COLOR = CardUiTheme.COMPLETE;
    private static final Color FOIL_ACCESS_COLOR = CardUiTheme.FOIL_ACCESS;
    private static final Font COMPACT_BODY_FONT = CardUiTheme.BODY;
    private static final Font COMPACT_TITLE_FONT = CardUiTheme.HEADING;
    private static final Font COMPACT_META_FONT = CardUiTheme.META;
    private static final int SIDEBAR_FALLBACK_HEIGHT = 650;
    private static final int SIDEBAR_MIN_READY_HEIGHT = 320;
    private static final int READY_TABS_FALLBACK_HEIGHT = 540;
    private static final DateTimeFormatter ACTIVITY_TIME =
        DateTimeFormatter.ofPattern("d MMM uuuu HH:mm")
            .withZone(ZoneId.systemDefault());

    private final CardCatalogue catalogue;
    private final FoilEntitlementResolver foilEntitlementResolver;
    private final FoilRewardRegistry foilRewardRegistry;
    private final CollectionProgressService progressService;
    private final AchievementRegistry achievementRegistry;
    private final AchievementService achievementService;
    private final QuestRequirementRegistry questRequirementRegistry;
    private final QuestReadinessService questReadinessService;
    private final CollectionSetupHandler setupHandler;
    private final PackActionHandler packActionHandler;
    private final CardArtworkProvider artworkProvider;
    // The album is intentionally lazy. Loading detail metadata and indexing
    // thousands of cards during startup would add UI-thread and memory pressure
    // for players who never open it in that session.
    private CollectionAlbumWindow collectionAlbumWindow;
    private MilestoneProgressWindow milestoneProgressWindow;

    private final JTextArea characterLabel =
        new SidebarWrappedTextArea("Not logged in");
    private final JTextArea statusLabel = new SidebarWrappedTextArea("");
    private final JPanel setupPanel = new JPanel();
    private final JPanel dataControlsPanel = new JPanel();
    private final JPanel recoveryAccessPanel = new JPanel();
    private final JTabbedPane readyTabs = new JTabbedPane();
    private final Set<Component> sidePanelScrollTargets =
        Collections.newSetFromMap(new WeakHashMap<>());
    private final MouseWheelListener sidePanelWheelListener =
        this::scrollActiveTab;
    private final ComponentAdapter responsiveHeightListener = new ComponentAdapter()
    {
        @Override
        public void componentResized(ComponentEvent event)
        {
            revalidate();
            readyTabs.revalidate();
        }
    };
    private Component responsiveHeightTarget;
    private final Timer questSearchDebounceTimer =
        new Timer(160, event -> rebuildQuestReadinessList(false));

    private final JLabel modeValue = new JLabel();
    private final JLabel integrityValue = new JLabel();
    private final JLabel cardsValue = new JLabel();
    private final JLabel buildValue = new JLabel(PluginBuildInfo.VERSION);
    private final JLabel pointsValue = new JLabel();
    private final JLabel shardsValue = new JLabel();
    private final JPanel persistentBalancePanel = new JPanel();
    private final JPanel milestoneRewardSection = new JPanel();
    private final Map<String, JPanel> milestoneRewardTiles =
        new java.util.LinkedHashMap<>();
    private final JLabel npcKillRewardsValue = new JLabel();
    private final JLabel noncombatRewardsValue = new JLabel();
    private final JLabel skillLevelRewardsValue = new JLabel();
    private final JLabel questMilestonesValue = new JLabel();
    private final JLabel collectionPercentValue = new JLabel();
    private final JLabel missingCardsValue = new JLabel();
    private final JLabel foilCardsValue = new JLabel();
    private final JLabel foilUnlocksValue = new JLabel();
    private final JLabel accountAgeValue = new JLabel();
    private final JLabel rewardSourcesValue = new JLabel();
    private final JLabel saveRevisionValue = new JLabel();
    private final JLabel achievementsValue = new JLabel();
    private final JLabel nextGoalValue = new JLabel();
    private final JLabel packsOpenedValue = new JLabel();
    private final JLabel cardsDrawnValue = new JLabel();
    private final JLabel duplicatePullsValue = new JLabel();
    private final JLabel nexusUnlocksValue = new JLabel();
    private final JPanel rarityStatisticsPanel = new JPanel();
    private final JPanel typeStatisticsPanel = new JPanel();
    private final JPanel categoryStatisticsPanel = new JPanel();
    private final JPanel accessStatisticsPanel = new JPanel();
    private final JPanel achievementStatisticsPanel = new JPanel();
    private final JPanel activityStatisticsPanel = new JPanel();
    private final JPanel libraryStatisticsPanel = new JPanel();
    private final JButton openAlbumButton = new JButton("Open Collection Album");
    private final JButton progressionTrackButton =
        new JButton("Progression Track");
    private final JButton exportDiagnosticsButton =
        new JButton("Export diagnostics");
    private final JButton exportBackupButton =
        new JButton("Export save");
    private final JButton importBackupButton =
        new JButton("Import save");
    private final JButton restoreBackupButton =
        new JButton("Restore backup");
    private final JButton recoveryImportButton =
        new JButton("Import save");
    private final JButton recoveryDiagnosticsButton =
        new JButton("Export diagnostics");
    private final JProgressBar collectionProgress = new JProgressBar();
    private final JProgressBar nextGoalProgress = new JProgressBar();
    private final JLabel nextGoalDescription = new JLabel();
    private final JProgressBar packPointProgress =
        new JProgressBar(0, (int) StandardPackService.PRICE);


    private final JPanel revealCardPanel = new JPanel();
    private final JLabel revealCardName = new JLabel();
    private final JLabel revealCardMeta = new JLabel();
    private final JLabel revealCardOutcome = new JLabel();
    private final JLabel packStatusLabel = new JLabel();
    private final JLabel packBalanceLabel = new JLabel();
    private final JPanel recentPackActivityPanel = new JPanel();
    private final JPanel starterPackTileContainer = new JPanel();
    private final JButton redeemStarterPackButton =
        new JButton("Redeem Starter Pack");
    private final JLabel[] packSlotLabels =
        new JLabel[StandardPackService.CARD_COUNT];
    private final JButton purchasePackButton =
        new JButton("Buy Standard Pack");
    private final JButton purchaseUncommonPlusPackButton =
        new JButton("Buy Uncommon+ Pack");
    private final JButton purchaseExplorerPackButton =
        new JButton("Buy Explorer Pack");
    private final JButton purchaseRareHunterPackButton =
        new JButton("Buy Rare+ Item Pack");
    private final JButton purchaseAdventurePackButton =
        new JButton("Buy Adventure Pack");
    private final JButton purchaseNexusCacheButton =
        new JButton("Buy Nexus Cache");
    private final JButton purchaseCollectorPackButton =
        new JButton("Buy Collector Pack");
    private final JButton redeemInitiateFoilPackButton =
        new JButton("Redeem Initiate's Foil Pack");
    private final JButton redeemHeroPackButton =
        new JButton("Redeem Hero's Pack");
    private final JButton redeemNoblePackButton =
        new JButton("Redeem Noble's Pack");
    private final JButton redeemLegendPackButton =
        new JButton("Redeem Legend's Pack");
    private final JButton redeemMythicalPackButton =
        new JButton("Redeem Mythical Pack");
    private final JButton redeemGodsPackButton =
        new JButton("Redeem Pack of the Gods");
    private final JButton purchaseNoncombatNpcPackButton =
        new JButton("Buy Noncombat NPC Pack");
    private final JButton purchaseAttackableNpcPackButton =
        new JButton("Buy Attackable NPC Pack");
    private final JButton purchaseFoilTestPackButton =
        new JButton("Buy Temporary Foil Pack");
    private final JButton purchasePremiumFoilTestPackButton =
        new JButton("Buy Legendary+ Foil Pack");
    private final JButton purchaseTierFoilTestPackButton =
        new JButton("Buy Tier Foil Test Pack");
    private final JButton purchaseArmourFoilTestPackButton =
        new JButton("Buy Armour Foil Test Pack");
    private final JButton purchaseBossFoilTestPackButton =
        new JButton("Buy Boss Foil Test Pack");
    private final JButton purchaseIngredientFoilTestPackButton =
        new JButton("Buy Item-relationship Foil Test Pack");
    private final JButton purchaseSignatureFoilTestPackButton =
        new JButton("Buy Signature-set Foil Test Pack");
    private final JButton purchaseNpcRelationshipFoilTestPackButton =
        new JButton("Buy NPC Relationship Foil Test Pack");
    private JPanel foilTestPackTile;
    private JPanel premiumFoilTestPackTile;
    private JPanel tierFoilTestPackTile;
    private JPanel armourFoilTestPackTile;
    private JPanel bossFoilTestPackTile;
    private JPanel ingredientFoilTestPackTile;
    private JPanel signatureFoilTestPackTile;
    private JPanel npcRelationshipFoilTestPackTile;
    private final Map<Rarity, JButton> nexusButtons =
        new EnumMap<>(Rarity.class);
    private final Map<Rarity, JProgressBar> nexusProgressBars =
        new EnumMap<>(Rarity.class);
    private final Map<Rarity, Integer> nexusEligibleTotals =
        new EnumMap<>(Rarity.class);
    private final Map<Rarity, Integer> cachedNexusOwnedCounts =
        new EnumMap<>(Rarity.class);
    private final JTextField questSearchField = new JTextField();
    private final JButton questSearchClearButton = new JButton("×");
    private final JButton questStatusRefreshButton =
        new JButton("Refresh Quest Status");
    private final JLabel questStatusRefreshLabel =
        new JLabel("Status refresh is manual");
    private final JComboBox<QuestReadinessFilter> questReadinessFilter =
        new JComboBox<>(QuestReadinessFilter.values());
    private final JLabel questReadinessSummary = new JLabel();
    private final JPanel questReadinessList = new SidebarRowPanel();
    private final JScrollPane questReadinessScroll = new JScrollPane();
    private final Map<String, JPanel> questReadinessRows = new java.util.LinkedHashMap<>();
    private final Set<String> expandedQuestRows = new LinkedHashSet<>();
    private QuestReadinessSnapshot questReadinessSnapshot;
    private boolean questStatusRefreshInProgress;
    private Set<String> completedQuestKeys = Collections.emptySet();
    private Set<String> renderedQuestOwnedCardIds = Collections.emptySet();
    private Set<String> renderedQuestFoilCardIds = Collections.emptySet();
    private Set<String> renderedQuestCompletionKeys = Collections.emptySet();
    private int questReadinessRenderRevision;
    private int questToggleRevision;

    private final JRadioButton standardMode =
        new JRadioButton("Standard", true);
    private final JRadioButton selfSufficientMode =
        new JRadioButton("Self Sufficient");
    private final JCheckBox integrityMode =
        new JCheckBox("Enable Integrity tracking");
    private final JRadioButton randomisedStarterPack =
        new JRadioButton("Randomised Starter Pack", true);
    private final JRadioButton starterPoints =
        new JRadioButton("No pack — receive 3,000 points");
    private final JButton createButton =
        new JButton("Begin profile setup");
    private final JButton disableIntegrityButton =
        new JButton("Disable integrity");
    private final JButton resetProfileButton =
        new JButton("Reset profile");

    private CollectionState lastState;
    private CollectionProgressSnapshot lastProgressSnapshot;
    private CollectionActivitySnapshot lastActivity =
        CollectionActivitySnapshot.empty();
    private CollectionActivitySnapshot renderedRecentPackActivity;
    private UUID cachedCollectionShapeId;
    private Set<String> cachedOwnedCardIdsIdentity = Collections.emptySet();
    private int cachedUniqueOwnedCardCount;
    private UUID renderedCollectionId;
    private UUID displayedOpeningId;
    private PackCardResult displayedPackCard;
    private int displayedRevealNumber;
    private SessionStatus renderedStatus = SessionStatus.LOGGED_OUT;

    public CardRestrictedAccountPanel(
        CardCatalogue catalogue,
        CollectionSetupHandler setupHandler,
        PackActionHandler packActionHandler,
        CardArtworkProvider artworkProvider)
    {
        this(
            catalogue,
            new FoilEntitlementResolver(
                catalogue,
                FoilRewardRegistry.load(
                    CardRestrictedAccountPanel.class.getClassLoader(),
                    catalogue)),
            setupHandler,
            packActionHandler,
            artworkProvider);
    }

    public CardRestrictedAccountPanel(
        CardCatalogue catalogue,
        FoilEntitlementResolver foilEntitlementResolver,
        CollectionSetupHandler setupHandler,
        PackActionHandler packActionHandler,
        CardArtworkProvider artworkProvider)
    {
        this.catalogue = catalogue;
        initialiseNexusTotals();
        this.foilEntitlementResolver = java.util.Objects.requireNonNull(
            foilEntitlementResolver,
            "foilEntitlementResolver");
        this.foilRewardRegistry = foilEntitlementResolver.getRegistry();
        this.progressService = new CollectionProgressService(catalogue);
        this.achievementRegistry = AchievementRegistry.load(
            CardRestrictedAccountPanel.class.getClassLoader());
        this.achievementService = new AchievementService(
            catalogue,
            achievementRegistry);
        this.questRequirementRegistry = QuestRequirementRegistry.load(
            CardRestrictedAccountPanel.class.getClassLoader(),
            catalogue);
        this.questReadinessService = new QuestReadinessService(
            catalogue,
            questRequirementRegistry,
            foilEntitlementResolver);
        this.setupHandler = setupHandler;
        this.packActionHandler = packActionHandler;
        this.artworkProvider = artworkProvider;
        buildValue.setText(
            PluginBuildInfo.VERSION + " / C" + catalogue.getCatalogueVersion());
        questSearchDebounceTimer.setRepeats(false);
        questStatusRefreshButton.addActionListener(event -> {
            questStatusRefreshInProgress = true;
            questStatusRefreshButton.setEnabled(false);
            questStatusRefreshButton.setText("Refreshing...");
            questStatusRefreshLabel.setText("Reading quest completion state...");
            packActionHandler.refreshQuestStatus();
        });
        redeemStarterPackButton.addActionListener(event -> {
            setBusy("Redeeming one-time starter pack...");
            packActionHandler.redeemStarterPack();
        });
        purchasePackButton.addActionListener(event -> {
            setBusy("Purchasing Standard Item Pack...");
            packActionHandler.purchaseStandardPack();
        });
        purchaseUncommonPlusPackButton.addActionListener(event -> {
            setBusy("Purchasing Uncommon+ Pack...");
            packActionHandler.purchaseUncommonPlusPack();
        });
        purchaseExplorerPackButton.addActionListener(event -> {
            setBusy("Purchasing Explorer Pack...");
            packActionHandler.purchaseExplorerPack();
        });
        purchaseRareHunterPackButton.addActionListener(event -> {
            setBusy("Purchasing Rare+ Item Pack...");
            packActionHandler.purchaseRareHunterPack();
        });
        purchaseAdventurePackButton.addActionListener(event -> {
            setBusy("Purchasing Adventure Pack...");
            packActionHandler.purchaseAdventurePack();
        });
        purchaseNexusCacheButton.addActionListener(event -> {
            setBusy("Opening Nexus Cache...");
            packActionHandler.purchaseNexusCache();
        });
        purchaseCollectorPackButton.addActionListener(event -> {
            setBusy("Purchasing Collector Pack...");
            packActionHandler.purchaseCollectorPack();
        });
        redeemInitiateFoilPackButton.addActionListener(event -> {
            setBusy("Redeeming Initiate's Foil Pack...");
            packActionHandler.redeemInitiateFoilPack();
        });
        redeemHeroPackButton.addActionListener(event -> {
            setBusy("Redeeming Hero's Pack...");
            packActionHandler.redeemHeroPack();
        });
        redeemNoblePackButton.addActionListener(event -> {
            setBusy("Redeeming Noble's Pack...");
            packActionHandler.redeemNoblePack();
        });
        redeemLegendPackButton.addActionListener(event -> {
            setBusy("Redeeming Legend's Pack...");
            packActionHandler.redeemLegendPack();
        });
        redeemMythicalPackButton.addActionListener(event -> {
            setBusy("Redeeming Mythical Pack...");
            packActionHandler.redeemMythicalPack();
        });
        redeemGodsPackButton.addActionListener(event -> {
            setBusy("Redeeming Pack of the Gods...");
            packActionHandler.redeemGodsPack();
        });
        purchaseNoncombatNpcPackButton.addActionListener(event -> {
            setBusy("Purchasing Noncombat NPC Pack...");
            packActionHandler.purchaseNoncombatNpcPack();
        });
        purchaseAttackableNpcPackButton.addActionListener(event -> {
            setBusy("Purchasing Attackable NPC Pack...");
            packActionHandler.purchaseAttackableNpcPack();
        });
        purchaseFoilTestPackButton.addActionListener(event -> {
            setBusy("Purchasing Temporary Foil Pack...");
            packActionHandler.purchaseFoilTestPack();
        });
        purchasePremiumFoilTestPackButton.addActionListener(event -> {
            setBusy("Purchasing Temporary Legendary+ Foil Pack...");
            packActionHandler.purchasePremiumFoilTestPack();
        });
        purchaseTierFoilTestPackButton.addActionListener(event -> {
            setBusy("Purchasing Tier-chain Foil Test Pack...");
            packActionHandler.purchaseTierFoilTestPack();
        });
        purchaseArmourFoilTestPackButton.addActionListener(event -> {
            setBusy("Purchasing Armour-slot Foil Test Pack...");
            packActionHandler.purchaseArmourFoilTestPack();
        });
        purchaseBossFoilTestPackButton.addActionListener(event -> {
            setBusy("Purchasing Boss-reward Foil Test Pack...");
            packActionHandler.purchaseBossFoilTestPack();
        });
        purchaseIngredientFoilTestPackButton.addActionListener(event -> {
            setBusy("Purchasing Item-relationship Foil Test Pack...");
            packActionHandler.purchaseIngredientFoilTestPack();
        });
        purchaseSignatureFoilTestPackButton.addActionListener(event -> {
            setBusy("Purchasing Signature-set Foil Test Pack...");
            packActionHandler.purchaseSignatureFoilTestPack();
        });
        purchaseNpcRelationshipFoilTestPackButton.addActionListener(event -> {
            setBusy("Purchasing NPC-relationship Foil Test Pack...");
            packActionHandler.purchaseNpcRelationshipFoilTestPack();
        });

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(buildHeader(), BorderLayout.NORTH);

        buildSetupPanel();
        buildReadyTabs();
        JPanel body = new JPanel(new BorderLayout());
        body.add(setupPanel, BorderLayout.NORTH);
        body.setMinimumSize(new Dimension(0, 0));
        body.add(readyTabs, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        add(buildRecoveryAccessPanel(), BorderLayout.SOUTH);
        installSidePanelScrollForwarding(this);

        render(SessionSnapshot.loggedOut());
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        SwingUtilities.invokeLater(this::bindResponsiveHeightTarget);
    }

    @Override
    public void removeNotify()
    {
        unbindResponsiveHeightTarget();
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize()
    {
        Dimension preferred = super.getPreferredSize();
        if (!readyTabs.isVisible())
        {
            return preferred;
        }
        int availableHeight = availableSidebarHeight();
        int responsiveHeight = availableHeight > 0
            ? Math.max(SIDEBAR_MIN_READY_HEIGHT, availableHeight)
            : Math.max(SIDEBAR_FALLBACK_HEIGHT, preferred.height);
        return new Dimension(preferred.width, responsiveHeight);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize()
    {
        return getPreferredSize();
    }

    private void bindResponsiveHeightTarget()
    {
        Component target = findResponsiveHeightTarget();
        if (responsiveHeightTarget == target)
        {
            revalidate();
            return;
        }
        unbindResponsiveHeightTarget();
        responsiveHeightTarget = target;
        if (responsiveHeightTarget != null)
        {
            responsiveHeightTarget.addComponentListener(responsiveHeightListener);
        }
        revalidate();
    }

    private void unbindResponsiveHeightTarget()
    {
        if (responsiveHeightTarget != null)
        {
            responsiveHeightTarget.removeComponentListener(responsiveHeightListener);
            responsiveHeightTarget = null;
        }
    }

    private Component findResponsiveHeightTarget()
    {
        Container viewport = SwingUtilities.getAncestorOfClass(JViewport.class, this);
        if (viewport != null)
        {
            return viewport;
        }
        Component largest = null;
        int largestHeight = 0;
        for (Container ancestor = getParent(); ancestor != null; ancestor = ancestor.getParent())
        {
            if (ancestor.getHeight() > largestHeight)
            {
                largest = ancestor;
                largestHeight = ancestor.getHeight();
            }
        }
        return largest;
    }

    private int availableSidebarHeight()
    {
        Component target = responsiveHeightTarget;
        if (target == null || target.getHeight() <= 0)
        {
            target = findResponsiveHeightTarget();
        }
        if (target instanceof JViewport)
        {
            return ((JViewport) target).getExtentSize().height;
        }
        return target == null ? 0 : target.getHeight();
    }

    @Override
    public int getScrollableUnitIncrement(
        Rectangle visibleRect,
        int orientation,
        int direction)
    {
        return 18;
    }

    @Override
    public int getScrollableBlockIncrement(
        Rectangle visibleRect,
        int orientation,
        int direction)
    {
        return Math.max(18, visibleRect.height - 18);
    }

    @Override
    public boolean getScrollableTracksViewportWidth()
    {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight()
    {
        if (!readyTabs.isVisible())
        {
            return false;
        }
        int availableHeight = availableSidebarHeight();
        if (availableHeight <= 0)
        {
            availableHeight = getHeight();
        }
        // Keep the tab strip fixed at ordinary RuneLite heights. A genuinely
        // short viewport must instead let the outer scroller honour this
        // panel's minimum height; otherwise BorderLayout can assign a
        // negative height to the active tab below the persistent header.
        return availableHeight <= 0
            || availableHeight >= SIDEBAR_MIN_READY_HEIGHT;
    }

    public void closeAuxiliaryWindows()
    {
        questSearchDebounceTimer.stop();
        if (collectionAlbumWindow != null)
        {
            collectionAlbumWindow.close();
            collectionAlbumWindow = null;
        }
        if (milestoneProgressWindow != null)
        {
            milestoneProgressWindow.close();
            milestoneProgressWindow = null;
        }
    }

    public void onArtworkAvailable(String cardId)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> onArtworkAvailable(cardId));
            return;
        }
        CollectionAlbumWindow album = collectionAlbumWindow;
        if (album != null)
        {
            album.onArtworkAvailable(cardId);
        }
    }

    public void onArtworkPackReady()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::onArtworkPackReady);
            return;
        }
        CollectionAlbumWindow album = collectionAlbumWindow;
        if (album != null)
        {
            album.onArtworkPackReady();
        }
    }

    public void render(SessionSnapshot snapshot)
    {
        renderedStatus = snapshot.getStatus();
        characterLabel.setText(
            snapshot.getDisplayName().isEmpty()
                ? "Not logged in"
                : snapshot.getDisplayName());
        statusLabel.setText(snapshot.getMessage());

        boolean needsSetup =
            snapshot.getStatus() == SessionStatus.NEEDS_SETUP;
        boolean ready = snapshot.getStatus() == SessionStatus.READY;
        setupPanel.setVisible(needsSetup);
        readyTabs.setVisible(ready);
        persistentBalancePanel.setVisible(ready);
        createButton.setEnabled(needsSetup);
        if (!ready)
        {
            questStatusRefreshInProgress = false;
            questStatusRefreshButton.setText("Refresh Quest Status");
            questStatusRefreshLabel.setText("Status refresh is manual");
        }
        questStatusRefreshButton.setEnabled(
            ready && !questStatusRefreshInProgress);
        configureDataControls(snapshot.getStatus());
        if (foilTestPackTile != null)
        {
            foilTestPackTile.setVisible(packActionHandler.isTestingMode());
        }
        if (premiumFoilTestPackTile != null)
        {
            premiumFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        }
        if (tierFoilTestPackTile != null)
        {
            tierFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        }
        if (armourFoilTestPackTile != null)
        {
            armourFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        }
        if (bossFoilTestPackTile != null)
        {
            bossFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        }
        if (ingredientFoilTestPackTile != null)
        {
            ingredientFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        }
        if (signatureFoilTestPackTile != null)
        {
            signatureFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        }
        if (npcRelationshipFoilTestPackTile != null)
        {
            npcRelationshipFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        }

        if (ready)
        {
            CollectionState state =
                snapshot.getCollectionState().orElseThrow();
            if (!state.getCollectionId().equals(renderedCollectionId))
            {
                clearDisplayedPack();
                renderedCollectionId = state.getCollectionId();
            }
            CollectionState previousState = lastState;
            CollectionActivitySnapshot previousActivity = lastActivity;
            CollectionActivitySnapshot activity =
                snapshot.getActivitySnapshot();
            boolean collectionShapeUnchanged = previousState != null
                && previousState.getCollectionId().equals(state.getCollectionId())
                && previousState.getOwnedCardIds() == state.getOwnedCardIds()
                && previousState.getFoilCardIds() == state.getFoilCardIds();
            boolean activityUnchanged = activity == previousActivity;

            CollectionProgressSnapshot progress = collectionShapeUnchanged
                && lastProgressSnapshot != null
                    ? lastProgressSnapshot
                    : progressService.calculate(state);
            lastProgressSnapshot = progress;
            lastState = state;
            lastActivity = activity;
            if (!collectionShapeUnchanged)
            {
                refreshCollectionShapeCaches(state);
            }

            // Point-only and pack-reveal checkpoint updates are frequent but do
            // not change collection ownership. Avoid rebuilding Album models,
            // rarity/category statistics, milestones and quest readiness when
            // the expensive derived collection shape is unchanged. Economy,
            // pack and Nexus controls are still refreshed every time.
            if (!collectionShapeUnchanged || !activityUnchanged)
            {
                CollectionAlbumWindow album = collectionAlbumWindow;
                if (album != null)
                {
                    album.update(state, activity);
                }
                MilestoneProgressWindow milestoneWindow = milestoneProgressWindow;
                if (milestoneWindow != null)
                {
                    milestoneWindow.update(state);
                }
                updateStatistics(state, progress, activity);
                updateQuestReadiness(state);
            }
            updateOverview(state, progress);
            updatePackView(state, activity);
            updateNexus(state, progress);
        }
        else
        {
            lastState = null;
            lastProgressSnapshot = null;
            lastActivity = CollectionActivitySnapshot.empty();
            renderedRecentPackActivity = null;
            cachedCollectionShapeId = null;
            cachedOwnedCardIdsIdentity = Collections.emptySet();
            cachedUniqueOwnedCardCount = 0;
            cachedNexusOwnedCounts.clear();
            renderedCollectionId = null;
            questReadinessSnapshot = null;
            renderedQuestOwnedCardIds = Collections.emptySet();
            renderedQuestCompletionKeys = Collections.emptySet();
            questReadinessList.removeAll();
            questReadinessSummary.setText("<html>Complete 0 · Ready 0<br>Blocked 0 · Shown 0</html>");
            clearDisplayedPack();
            redeemStarterPackButton.setEnabled(false);
            purchasePackButton.setEnabled(false);
            purchaseRareHunterPackButton.setEnabled(false);
            purchaseNoncombatNpcPackButton.setEnabled(false);
            purchaseAttackableNpcPackButton.setEnabled(false);
            purchaseFoilTestPackButton.setEnabled(false);
            purchasePremiumFoilTestPackButton.setEnabled(false);
            purchaseTierFoilTestPackButton.setEnabled(false);
            purchaseArmourFoilTestPackButton.setEnabled(false);
            purchaseBossFoilTestPackButton.setEnabled(false);
            purchaseIngredientFoilTestPackButton.setEnabled(false);
            purchaseSignatureFoilTestPackButton.setEnabled(false);
            purchaseNpcRelationshipFoilTestPackButton.setEnabled(false);
            for (JButton button : nexusButtons.values())
            {
                button.setEnabled(false);
            }
        }

        revalidate();
        repaint();
    }

    public void showPackReveal(PackRevealResult result)
    {
        Runnable update = () -> {
            if (!result.getState().getCollectionId()
                .equals(renderedCollectionId))
            {
                return;
            }
            displayedPackCard = result.getRevealedCard();
            displayedRevealNumber = result.getRevealNumber();
            updatePackView(result.getState(), lastActivity);
        };
        if (SwingUtilities.isEventDispatchThread())
        {
            update.run();
        }
        else
        {
            SwingUtilities.invokeLater(update);
        }
    }

    public void setBusy(String message)
    {
        setNotice(message);
        exportBackupButton.setEnabled(false);
        importBackupButton.setEnabled(false);
        restoreBackupButton.setEnabled(false);
        exportDiagnosticsButton.setEnabled(false);
        createButton.setEnabled(false);
        redeemStarterPackButton.setEnabled(false);
        purchasePackButton.setEnabled(false);
        purchaseRareHunterPackButton.setEnabled(false);
        purchaseNoncombatNpcPackButton.setEnabled(false);
        purchaseAttackableNpcPackButton.setEnabled(false);
        purchaseFoilTestPackButton.setEnabled(false);
        purchasePremiumFoilTestPackButton.setEnabled(false);
        purchaseTierFoilTestPackButton.setEnabled(false);
        purchaseArmourFoilTestPackButton.setEnabled(false);
        purchaseBossFoilTestPackButton.setEnabled(false);
        purchaseIngredientFoilTestPackButton.setEnabled(false);
        purchaseSignatureFoilTestPackButton.setEnabled(false);
        purchaseNpcRelationshipFoilTestPackButton.setEnabled(false);
        for (JButton button : nexusButtons.values())
        {
            button.setEnabled(false);
        }
    }

    public void setNotice(String message)
    {
        Runnable update = () -> statusLabel.setText(message);
        if (SwingUtilities.isEventDispatchThread())
        {
            update.run();
        }
        else
        {
            SwingUtilities.invokeLater(update);
        }
    }

    public void completeDiagnosticExport(String message)
    {
        completeDataAction(message);
    }

    public void completeDataAction(String message)
    {
        Runnable update = () -> {
            configureDataControls(renderedStatus);
            setNotice(message);
        };
        if (SwingUtilities.isEventDispatchThread())
        {
            update.run();
        }
        else
        {
            SwingUtilities.invokeLater(update);
        }
    }

    private JPanel buildDataControlsPanel()
    {
        dataControlsPanel.setLayout(new GridLayout(4, 1, 0, 5));
        dataControlsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
                "Save & recovery"),
            BorderFactory.createEmptyBorder(2, 5, 5, 5)));

        configureDataButton(
            exportBackupButton,
            "Export a verified snapshot without changing the profile.");
        exportBackupButton.addActionListener(event -> chooseBackupExport());
        dataControlsPanel.add(exportBackupButton);

        configureDataButton(
            importBackupButton,
            "Import a validated snapshot for this character. Manual recovery disables integrity.");
        importBackupButton.addActionListener(event -> chooseBackupImport());
        dataControlsPanel.add(importBackupButton);

        configureDataButton(
            restoreBackupButton,
            "Restore the newest earlier automatic snapshot as a new revision.");
        restoreBackupButton.addActionListener(
            event -> confirmAutomaticBackupRestore());
        dataControlsPanel.add(restoreBackupButton);

        configureDataButton(
            exportDiagnosticsButton,
            "Write a bounded local report without account or collection data.");
        exportDiagnosticsButton.addActionListener(
            event -> beginDiagnosticExport());
        dataControlsPanel.add(exportDiagnosticsButton);
        return dataControlsPanel;
    }

    private JPanel buildRecoveryAccessPanel()
    {
        recoveryAccessPanel.setLayout(new GridLayout(2, 1, 0, 5));
        recoveryAccessPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
                "Recovery"),
            BorderFactory.createEmptyBorder(2, 5, 5, 5)));
        configureDataButton(
            recoveryImportButton,
            "Import a validated snapshot for this character.");
        recoveryImportButton.addActionListener(
            event -> chooseBackupImport());
        recoveryAccessPanel.add(recoveryImportButton);
        configureDataButton(
            recoveryDiagnosticsButton,
            "Write a bounded private local diagnostic report.");
        recoveryDiagnosticsButton.addActionListener(
            event -> beginDiagnosticExport());
        recoveryAccessPanel.add(recoveryDiagnosticsButton);
        return recoveryAccessPanel;
    }

    private void configureDataButton(JButton button, String description)
    {
        CardUiTheme.styleCompactButton(button);
        button.getAccessibleContext().setAccessibleDescription(description);
    }

    private void configureDataControls(SessionStatus status)
    {
        boolean stableIdentity = status == SessionStatus.NEEDS_SETUP
            || status == SessionStatus.READY
            || status == SessionStatus.ERROR;
        boolean ready = status == SessionStatus.READY;
        dataControlsPanel.setVisible(ready);
        recoveryAccessPanel.setVisible(stableIdentity && !ready);
        exportBackupButton.setEnabled(ready);
        importBackupButton.setEnabled(stableIdentity);
        restoreBackupButton.setEnabled(ready);
        exportDiagnosticsButton.setEnabled(
            status != SessionStatus.LOGGED_OUT);
        recoveryImportButton.setEnabled(stableIdentity);
        recoveryDiagnosticsButton.setEnabled(stableIdentity);
    }

    private void setDataControlsBusy(String message)
    {
        exportBackupButton.setEnabled(false);
        importBackupButton.setEnabled(false);
        restoreBackupButton.setEnabled(false);
        exportDiagnosticsButton.setEnabled(false);
        recoveryImportButton.setEnabled(false);
        recoveryDiagnosticsButton.setEnabled(false);
        setNotice(message);
    }

    private void beginDiagnosticExport()
    {
        setDataControlsBusy(
            "Exporting a private local diagnostic report...");
        setupHandler.exportDiagnostics();
    }

    private void chooseBackupExport()
    {
        JFileChooser chooser = backupChooser(
            "Export Card Locked save backup");
        chooser.setSelectedFile(new java.io.File(
            "card-locked-save-" + System.currentTimeMillis()
                + ".cardlocked-backup"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
        {
            return;
        }
        Path destination = withBackupExtension(
            chooser.getSelectedFile().toPath());
        if (Files.exists(destination))
        {
            javax.swing.JOptionPane.showMessageDialog(
                this,
                "That file already exists. Choose a new filename; Card Locked will not overwrite a backup implicitly.",
                "Backup not exported",
                javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        setDataControlsBusy("Exporting and verifying save backup...");
        setupHandler.exportBackup(destination);
    }

    private void chooseBackupImport()
    {
        JFileChooser chooser = backupChooser(
            "Import Card Locked save backup");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
        {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        int answer = javax.swing.JOptionPane.showConfirmDialog(
            this,
            "Importing replaces current collection progress with the selected validated snapshot. It preserves one-time claim history, creates a new revision, and permanently disables integrity for this profile. Continue?",
            "Import save backup",
            javax.swing.JOptionPane.YES_NO_OPTION,
            javax.swing.JOptionPane.WARNING_MESSAGE);
        if (answer != javax.swing.JOptionPane.YES_OPTION)
        {
            return;
        }
        setDataControlsBusy("Validating and importing save backup...");
        setupHandler.importBackup(source);
    }

    private void confirmAutomaticBackupRestore()
    {
        int answer = javax.swing.JOptionPane.showConfirmDialog(
            this,
            "Restore the newest valid earlier automatic snapshot? Current progress is replaced, one-time claim history is retained, a new revision is created, and integrity is permanently disabled.",
            "Restore automatic backup",
            javax.swing.JOptionPane.YES_NO_OPTION,
            javax.swing.JOptionPane.WARNING_MESSAGE);
        if (answer != javax.swing.JOptionPane.YES_OPTION)
        {
            return;
        }
        setDataControlsBusy("Validating and restoring automatic backup...");
        setupHandler.restorePreviousBackup();
    }

    private JFileChooser backupChooser(String title)
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter(
            "Card Locked backup (*.cardlocked-backup)",
            "cardlocked-backup"));
        return chooser;
    }

    private Path withBackupExtension(Path path)
    {
        String name = path.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(
            ".cardlocked-backup"))
        {
            return path;
        }
        return path.resolveSibling(name + ".cardlocked-backup");
    }

    private JPanel buildHeader()
    {
        JPanel header = new SidebarRowPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        openAlbumButton.setText("Open Collection Album");
        openAlbumButton.setIcon(CardUiAssets.icon(
            "/com/cardrestricted/ui/card-back.png",
            22,
            32));
        styleHeaderActionButton(
            openAlbumButton,
            new Color(255, 205, 99),
            new Color(48, 40, 27),
            new Color(190, 137, 49));
        openAlbumButton.addActionListener(event -> openCollectionAlbum());
        header.add(openAlbumButton);
        header.add(Box.createRigidArea(new Dimension(0, 5)));

        progressionTrackButton.setIcon(CardUiAssets.icon(
            "/com/cardrestricted/ui/card-back.png",
            22,
            32));
        styleHeaderActionButton(
            progressionTrackButton,
            new Color(154, 214, 255),
            new Color(27, 39, 50),
            new Color(66, 145, 197));
        progressionTrackButton.getAccessibleContext().setAccessibleDescription(
            "Open the complete collection milestone progression track.");
        progressionTrackButton.addActionListener(event -> openProgressionTrack());
        header.add(progressionTrackButton);
        header.add(Box.createRigidArea(new Dimension(0, 4)));

        configureWrappedTextArea(
            characterLabel,
            characterLabel.getFont(),
            MUTED_TEXT);
        characterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(characterLabel);

        configureWrappedTextArea(
            statusLabel,
            statusLabel.getFont(),
            ColorScheme.TEXT_COLOR);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(statusLabel);
        header.add(Box.createRigidArea(new Dimension(0, 3)));
        header.add(buildPersistentBalanceStrip());
        return header;
    }

    private void styleHeaderActionButton(
        JButton button,
        Color foreground,
        Color background,
        Color border)
    {
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        button.setMinimumSize(new Dimension(0, 40));
        button.setPreferredSize(new Dimension(0, 40));
        button.setMargin(new Insets(3, 7, 3, 7));
        button.setFont(button.getFont().deriveFont(Font.BOLD, 11.5f));
        button.setForeground(foreground);
        button.setBackground(background);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border),
            BorderFactory.createEmptyBorder(3, 6, 3, 6)));
    }

    private JPanel buildPersistentBalanceStrip()
    {
        persistentBalancePanel.setLayout(new GridLayout(1, 2, 5, 0));
        persistentBalancePanel.setOpaque(true);
        persistentBalancePanel.setBackground(new Color(34, 35, 38));
        persistentBalancePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(87, 72, 48)),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        persistentBalancePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        // The nested borders and two font lines require 46px at the default
        // RuneLite font metrics. Keep a small buffer for platform/DPI font
        // differences so neither balance is vertically clipped.
        persistentBalancePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        persistentBalancePanel.setMinimumSize(new Dimension(0, 48));
        persistentBalancePanel.setPreferredSize(new Dimension(190, 48));
        persistentBalancePanel.add(balanceCard(
            "POINTS", pointsValue, new Color(238, 181, 73)));
        persistentBalancePanel.add(balanceCard(
            "NEXUS SHARDS", shardsValue, new Color(190, 126, 236)));
        persistentBalancePanel.setVisible(false);
        return persistentBalancePanel;
    }

    private JPanel balanceCard(String heading, JLabel value, Color accent)
    {
        JPanel card = new JPanel(new BorderLayout(4, 0));
        card.setOpaque(true);
        card.setBackground(new Color(43, 44, 48));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
            BorderFactory.createEmptyBorder(4, 6, 4, 5)));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(heading);
        label.setFont(new Font(Font.DIALOG, Font.BOLD, 9));
        label.setForeground(accent);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        value.setForeground(Color.WHITE);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(label);
        text.add(value);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private void openCollectionAlbum()
    {
        if (lastState != null)
        {
            ensureCollectionAlbumWindow().show(lastState, lastActivity);
        }
    }

    private void openProgressionTrack()
    {
        if (lastState != null)
        {
            ensureMilestoneProgressWindow().show(lastState);
        }
    }

    private CollectionAlbumWindow ensureCollectionAlbumWindow()
    {
        if (collectionAlbumWindow == null)
        {
            collectionAlbumWindow = new CollectionAlbumWindow(
                catalogue,
                foilEntitlementResolver,
                artworkProvider);
        }
        return collectionAlbumWindow;
    }

    private MilestoneProgressWindow ensureMilestoneProgressWindow()
    {
        if (milestoneProgressWindow == null)
        {
            milestoneProgressWindow = new MilestoneProgressWindow(
                catalogue,
                this::showStoreTab);
        }
        return milestoneProgressWindow;
    }

    private void showStoreTab()
    {
        for (int index = 0; index < readyTabs.getTabCount(); index++)
        {
            if ("Store".equals(readyTabs.getTitleAt(index)))
            {
                readyTabs.setSelectedIndex(index);
                scrollTabToTop(index);
                break;
            }
        }
    }

    boolean areAlbumWindowsInitializedForTesting()
    {
        return collectionAlbumWindow != null;
    }

    private void buildSetupPanel()
    {
        setupPanel.setLayout(new BoxLayout(setupPanel, BoxLayout.Y_AXIS));
        setupPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
            BorderFactory.createEmptyBorder(12, 10, 12, 10)));

        JLabel heading = sectionTitle("Create profile");
        setupPanel.add(heading);
        setupPanel.add(Box.createRigidArea(new Dimension(0, 7)));
        JLabel explanation = new JLabel(
            "<html><div style='width:156px'>A short setup guide will choose "
                + "your account mode, starter reward, restriction preset, "
                + "visual markers, beta compatibility and final integrity status.</div></html>");
        explanation.setForeground(MUTED_TEXT);
        explanation.setAlignmentX(Component.LEFT_ALIGNMENT);
        setupPanel.add(explanation);
        setupPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        createButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        createButton.addActionListener(event -> runSetupWizard());
        setupPanel.add(createButton);
    }

    private void runSetupWizard()
    {
        Object economy = chooseSetupOption(
            "Account mode",
            "Choose how trading and external item access should be treated.",
            new Object[]{"Standard", "Self sufficient"},
            "Standard");
        if (economy == null) return;

        Object starter = chooseSetupOption(
            "Starter reward",
            "Choose the permanent starting reward for this profile.",
            new Object[]{"Randomised starter pack", "3,000 points and no cards"},
            "Randomised starter pack");
        if (starter == null) return;

        Object preset = chooseSetupOption(
            "Restriction preset",
            "Balanced allows locked items to be stored in banks. Strict blocks all locked-item movement except Examine, Drop and removing equipped items. Collection only records progress without blocking gameplay.",
            new Object[]{"Balanced", "Strict", "Collection only"},
            "Balanced");
        if (preset == null) return;

        Object visuals = chooseSetupOption(
            "Locked-content markers",
            "Show light grey and blocked-card markers on inaccessible items and NPCs?",
            new Object[]{"Show markers", "Hide markers"},
            "Show markers");
        if (visuals == null) return;

        Object integrity = chooseSetupOption(
            "Integrity",
            "Integrity profiles lock every gameplay-affecting configuration to the setup choices above. Visual and convenience settings remain changeable. Integrity can be disabled later, but doing so permanently changes this profile to non-integrity until the entire profile and collection are reset.",
            new Object[]{"Enable integrity", "Create non-integrity profile"},
            "Create non-integrity profile");
        if (integrity == null) return;

        boolean integrityEnabled = "Enable integrity".equals(integrity);
        Object compatibility = integrityEnabled
            ? "Fail closed (required by integrity)"
            : chooseSetupOption(
                "Beta compatibility",
                "<b>Recommended during public beta:</b> allow actions to proceed only when Card Locked cannot verify an item/NPC identity. Known verified locked content is still restricted. This reduces the risk of an identification bug temporarily blocking legitimate gameplay. Choose fail closed for stricter restriction behaviour.",
                new Object[]{
                    "Allow unverified actions (recommended for beta)",
                    "Fail closed on unverified actions"},
                "Allow unverified actions (recommended for beta)");
        if (compatibility == null) return;

        ProfileSetupOptions options = new ProfileSetupOptions(
            "Self sufficient".equals(economy)
                ? EconomyMode.SELF_SUFFICIENT : EconomyMode.STANDARD,
            "3,000 points and no cards".equals(starter)
                ? StarterRewardChoice.POINTS : StarterRewardChoice.RANDOMISED_PACK,
            "Strict".equals(preset)
                ? RestrictionPreset.STRICT
                : ("Collection only".equals(preset)
                    ? RestrictionPreset.COLLECTION_ONLY
                    : RestrictionPreset.BALANCED),
            "Show markers".equals(visuals),
            integrityEnabled
                ? IntegrityMode.INTEGRITY : IntegrityMode.CASUAL,
            !integrityEnabled
                && compatibility.toString().startsWith("Allow unverified"));
        int confirmation = javax.swing.JOptionPane.showConfirmDialog(
            this,
            "<html><div style='width:350px'><b>Review profile setup</b><br><br>"
                + "Account mode: " + escape(economy.toString()) + "<br>"
                + "Starter reward: " + escape(starter.toString()) + "<br>"
                + "Restrictions: " + escape(preset.toString()) + "<br>"
                + "Locked markers: " + escape(visuals.toString()) + "<br>"
                + "Integrity: " + escape(integrity.toString()) + "<br>"
                + "Unverified actions: " + escape(compatibility.toString()) + "<br><br>"
                + "<b>Rules summary:</b> tracked locked items and functional "
                + "NPC actions are blocked by the selected preset; Talk-to, "
                + "Examine and removing equipped items remain safe. Verified "
                + "foil access can satisfy a card requirement. With beta "
                + "compatibility enabled, only actions whose identity cannot "
                + "be verified may pass; known verified locked content remains blocked."
                + "<br><br>Use <b>Export save</b> after creation. Manual "
                + "backup recovery permanently disables integrity.</div></html>",
            "Confirm Card Locked profile",
            javax.swing.JOptionPane.OK_CANCEL_OPTION,
            javax.swing.JOptionPane.WARNING_MESSAGE);
        if (confirmation != javax.swing.JOptionPane.OK_OPTION)
        {
            return;
        }
        setBusy("Creating profile from setup choices...");
        setupHandler.createCollection(options);
    }

    private Object chooseSetupOption(
        String title,
        String message,
        Object[] options,
        Object initial)
    {
        int choice = javax.swing.JOptionPane.showOptionDialog(
            this,
            "<html><div style='width:330px'>" + escape(message) + "</div></html>",
            title,
            javax.swing.JOptionPane.DEFAULT_OPTION,
            javax.swing.JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            initial);
        return choice < 0 ? null : options[choice];
    }

    private void buildReadyTabs()
    {
        readyTabs.setUI(new SidebarTabbedPaneUI());
        readyTabs.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
        readyTabs.setTabPlacement(JTabbedPane.TOP);
        readyTabs.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent event)
            {
                resizeTopLevelTabHeaders();
            }
        });
        readyTabs.setMinimumSize(new Dimension(0, 0));
        readyTabs.setPreferredSize(new Dimension(205, READY_TABS_FALLBACK_HEIGHT));
        readyTabs.setOpaque(true);
        readyTabs.setFocusable(false);
        readyTabs.setBackground(new Color(29, 30, 33));
        readyTabs.setForeground(new Color(220, 220, 220));
        readyTabs.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(61, 62, 66)),
            BorderFactory.createEmptyBorder(3, 3, 3, 3)));

        configureTabScrollPane(questReadinessScroll, buildQuestTab());
        JScrollPane homeScroll = createTabScrollPane(buildOverviewTab());
        readyTabs.addTab("Home", homeScroll);
        readyTabs.addTab("Store", createTabScrollPane(buildPackTab()));
        readyTabs.addTab("The Nexus", createTabScrollPane(buildNexusTab()));
        readyTabs.addTab("Quest", questReadinessScroll);
        readyTabs.addTab("Stats", createTabScrollPane(buildStatisticsTab()));
        readyTabs.setSelectedComponent(homeScroll);
        styleTabHeaders(readyTabs);
    }

    private JScrollPane createTabScrollPane(JComponent content)
    {
        JScrollPane scroll = new JScrollPane();
        configureTabScrollPane(scroll, content);
        return scroll;
    }

    private void configureTabScrollPane(
        JScrollPane scroll,
        JComponent content)
    {
        content.setMinimumSize(new Dimension(0, 0));
        scroll.setViewportView(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setMinimumSize(new Dimension(0, 0));
        scroll.setPreferredSize(new Dimension(195, READY_TABS_FALLBACK_HEIGHT - 30));
        scroll.setHorizontalScrollBarPolicy(
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // Reserving the thin scrollbar from the first layout pass keeps the
        // available text width identical before and after a long list is
        // measured. This prevents login-time quest sizing jumps and repeated
        // expand/collapse width feedback.
        scroll.setVerticalScrollBarPolicy(
            ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setWheelScrollingEnabled(false);
        JScrollBar vertical = scroll.getVerticalScrollBar();
        vertical.setUI(new SidebarScrollBarUI());
        vertical.setPreferredSize(new Dimension(9, 0));
        vertical.setUnitIncrement(18);
        vertical.setBlockIncrement(108);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getViewport().setScrollMode(javax.swing.JViewport.SIMPLE_SCROLL_MODE);
    }

    private JLabel createTabHeader(String title)
    {
        String displayTitle = "The Nexus".equals(title) ? "Nexus" : title;
        SidebarTabHeader label = new SidebarTabHeader(displayTitle);
        int textWidth = label.getFontMetrics(label.getFont()).stringWidth(displayTitle);
        int width = Math.max(30, Math.min(44, textWidth + 8));
        setTabHeaderSize(label, width);
        return label;
    }

    private void styleTabHeaders(JTabbedPane tabs)
    {
        for (int index = 0; index < tabs.getTabCount(); index++)
        {
            final int tabIndex = index;
            SidebarTabHeader header = (SidebarTabHeader) createTabHeader(
                tabs.getTitleAt(index));
            header.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent event)
                {
                    header.setHovered(true);
                }

                @Override
                public void mouseExited(MouseEvent event)
                {
                    header.setHovered(false);
                }

                @Override
                public void mousePressed(MouseEvent event)
                {
                    if (!SwingUtilities.isLeftMouseButton(event))
                    {
                        return;
                    }
                    boolean alreadySelected = tabs.getSelectedIndex() == tabIndex;
                    tabs.setSelectedIndex(tabIndex);
                    if (tabs == readyTabs && alreadySelected)
                    {
                        scrollTabToTop(tabIndex);
                    }
                }
            });
            tabs.setTabComponentAt(index, header);
        }
        tabs.addChangeListener(event ->
        {
            updateTabHeaders(tabs);
            if (tabs == readyTabs)
            {
                resizeTopLevelTabHeaders();
            }
            repaint();
        });
        updateTabHeaders(tabs);
        if (tabs == readyTabs)
        {
            resizeTopLevelTabHeaders();
        }
    }

    private void resizeTopLevelTabHeaders()
    {
        if (readyTabs.getTabCount() == 0)
        {
            return;
        }
        int available = readyTabs.getWidth() > 0
            ? readyTabs.getWidth() - 30
            : readyTabs.getPreferredSize().width - 30;
        int equalWidth = Math.max(30, Math.min(38,
            available / readyTabs.getTabCount()));
        for (int index = 0; index < readyTabs.getTabCount(); index++)
        {
            Component component = readyTabs.getTabComponentAt(index);
            if (component instanceof SidebarTabHeader)
            {
                setTabHeaderSize((SidebarTabHeader) component, equalWidth);
            }
        }
        readyTabs.revalidate();
    }

    private static void setTabHeaderSize(JComponent header, int width)
    {
        Dimension size = new Dimension(width, 28);
        header.setMinimumSize(size);
        header.setPreferredSize(size);
        header.setMaximumSize(size);
    }

    private void scrollTabToTop(int index)
    {
        if (index < 0 || index >= readyTabs.getTabCount())
        {
            return;
        }
        Component content = readyTabs.getComponentAt(index);
        if (content instanceof JScrollPane)
        {
            ((JScrollPane) content).getVerticalScrollBar().setValue(0);
        }
    }

    private void updateTabHeaders(JTabbedPane tabs)
    {
        int selected = tabs.getSelectedIndex();
        for (int index = 0; index < tabs.getTabCount(); index++)
        {
            Component component = tabs.getTabComponentAt(index);
            if (component instanceof SidebarTabHeader)
            {
                ((SidebarTabHeader) component).setActive(index == selected);
            }
        }
    }

    private JPanel buildOverviewTab()
    {
        JPanel overview = new SidebarTabPanel();
        overview.setBorder(BorderFactory.createEmptyBorder(9, 2, 5, 2));
        overview.setLayout(new BoxLayout(overview, BoxLayout.Y_AXIS));
        overview.setMinimumSize(new Dimension(0, 0));

        JPanel hero = new JPanel(new BorderLayout(10, 0));
        hero.setBackground(new Color(48, 39, 25));
        hero.setOpaque(true);
        hero.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(205, 148, 49), 2),
            BorderFactory.createEmptyBorder(9, 9, 9, 9)));
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        hero.setMinimumSize(new Dimension(0, 92));
        hero.setPreferredSize(new Dimension(0, 92));
        hero.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel emblem = new JLabel(CardUiAssets.icon(
            "/com/cardrestricted/ui/card-back.png",
            34,
            51));
        hero.add(emblem, BorderLayout.WEST);
        JPanel heroText = new JPanel();
        heroText.setOpaque(false);
        heroText.setLayout(new BoxLayout(heroText, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("CARD LOCKED");
        title.setForeground(new Color(255, 199, 83));
        title.setFont(new Font(Font.DIALOG, Font.BOLD, 13));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        heroText.add(title);
        JTextArea subtitle = compactWrappedText(
            "Build your collection, unlock new routes and complete the track.",
            new Font(Font.DIALOG, Font.PLAIN, 10),
            new Color(222, 207, 177));
        subtitle.setRows(3);
        subtitle.setMinimumSize(new Dimension(0, 43));
        heroText.add(subtitle);
        hero.add(heroText, BorderLayout.CENTER);
        overview.add(hero);
        overview.add(Box.createRigidArea(new Dimension(0, 10)));

        overview.add(sectionTitle("Collection progress"));
        overview.add(Box.createRigidArea(new Dimension(0, 5)));
        configureProgressBar(collectionProgress);
        collectionProgress.setForeground(new Color(229, 164, 54));
        overview.add(collectionProgress);
        overview.add(Box.createRigidArea(new Dimension(0, 9)));

        JPanel milestone = new JPanel();
        milestone.setLayout(new BoxLayout(milestone, BoxLayout.Y_AXIS));
        milestone.setOpaque(true);
        milestone.setBackground(new Color(39, 43, 48));
        milestone.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(90, 160, 220)),
            BorderFactory.createEmptyBorder(7, 8, 7, 8)));
        milestone.setAlignmentX(Component.LEFT_ALIGNMENT);
        milestone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        JLabel nextHeading = new JLabel("NEXT MILESTONE");
        nextHeading.setFont(new Font(Font.DIALOG, Font.BOLD, 9));
        nextHeading.setForeground(new Color(112, 184, 238));
        nextHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
        milestone.add(nextHeading);
        nextGoalValue.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        nextGoalValue.setForeground(Color.WHITE);
        nextGoalValue.setAlignmentX(Component.LEFT_ALIGNMENT);
        milestone.add(nextGoalValue);
        nextGoalDescription.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
        nextGoalDescription.setForeground(MUTED_TEXT);
        nextGoalDescription.setAlignmentX(Component.LEFT_ALIGNMENT);
        milestone.add(nextGoalDescription);
        nextGoalProgress.setStringPainted(true);
        nextGoalProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 15));
        nextGoalProgress.setAlignmentX(Component.LEFT_ALIGNMENT);
        milestone.add(Box.createRigidArea(new Dimension(0, 4)));
        milestone.add(nextGoalProgress);
        overview.add(milestone);
        overview.add(Box.createRigidArea(new Dimension(0, 11)));

        overview.add(sectionTitle("Challenge status"));
        overview.add(Box.createRigidArea(new Dimension(0, 5)));
        JPanel challengeGrid = new JPanel(new GridLayout(2, 2, 5, 5));
        challengeGrid.add(colourStatusCard("ECONOMY", modeValue, new Color(82, 154, 217)));
        challengeGrid.add(colourStatusCard("INTEGRITY", integrityValue, new Color(221, 173, 70)));
        challengeGrid.add(colourStatusCard("CARDS", cardsValue, new Color(93, 184, 112)));
        challengeGrid.add(colourStatusCard("BUILD", buildValue, new Color(177, 113, 213)));
        challengeGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
        challengeGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        overview.add(challengeGrid);
        overview.add(Box.createRigidArea(new Dimension(0, 10)));
        disableIntegrityButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        disableIntegrityButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        disableIntegrityButton.addActionListener(event -> {
            int answer = javax.swing.JOptionPane.showConfirmDialog(
                this,
                "Disabling integrity is permanent for this profile. The profile will remain non-integrity until a full profile reset. Continue?",
                "Disable integrity",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
            if (answer == javax.swing.JOptionPane.YES_OPTION)
            {
                setupHandler.disableIntegrity();
            }
        });
        overview.add(disableIntegrityButton);
        overview.add(Box.createRigidArea(new Dimension(0, 6)));
        resetProfileButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetProfileButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        resetProfileButton.addActionListener(event -> {
            int answer = javax.swing.JOptionPane.showConfirmDialog(
                this,
                "This permanently deletes the collection, points, shards, unlock history and integrity history for this character. Continue?",
                "Reset Card Locked profile",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
            if (answer == javax.swing.JOptionPane.YES_OPTION)
            {
                setupHandler.resetProfile();
            }
        });
        overview.add(resetProfileButton);
        overview.add(Box.createRigidArea(new Dimension(0, 10)));
        dataControlsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dataControlsPanel.setMaximumSize(
            new Dimension(Integer.MAX_VALUE, 160));
        overview.add(buildDataControlsPanel());
        overview.add(Box.createVerticalGlue());
        return overview;
    }

    private JPanel colourStatusCard(String heading, JLabel value, Color accent)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
            BorderFactory.createEmptyBorder(6, 6, 6, 5)));
        JLabel label = new JLabel(heading);
        label.setFont(new Font(Font.DIALOG, Font.BOLD, 9));
        label.setForeground(accent);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setFont(new Font(Font.DIALOG, Font.BOLD, 10));
        value.setForeground(Color.WHITE);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(label);
        card.add(value);
        return card;
    }

    private JPanel buildStatisticsTab()
    {
        JPanel statistics = new SidebarTabPanel();
        statistics.setBorder(BorderFactory.createEmptyBorder(10, 2, 4, 2));
        statistics.setLayout(new BoxLayout(statistics, BoxLayout.Y_AXIS));
        statistics.setMinimumSize(new Dimension(0, 0));

        statistics.add(sectionTitle("Collection statistics"));
        statistics.add(Box.createRigidArea(new Dimension(0, 7)));
        JPanel summary = new JPanel(new GridLayout(5, 2, 6, 6));
        summary.add(metricCard("COMPLETION", collectionPercentValue));
        summary.add(metricCard("MISSING", missingCardsValue));
        summary.add(metricCard("FOILS", foilCardsValue));
        summary.add(metricCard("FOIL ACCESS", foilUnlocksValue));
        summary.add(metricCard("AGE", accountAgeValue));
        summary.add(metricCard("PACKS", packsOpenedValue));
        summary.add(metricCard("DRAWS", cardsDrawnValue));
        summary.add(metricCard("DUPLICATES", duplicatePullsValue));
        summary.add(metricCard("NEXUS", nexusUnlocksValue));
        summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        summary.setPreferredSize(new Dimension(176, 300));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        statistics.add(summary);
        statistics.add(Box.createRigidArea(new Dimension(0, 12)));

        statistics.add(sectionTitle("Completion breakdown"));
        statistics.add(Box.createRigidArea(new Dimension(0, 7)));
        JTabbedPane breakdown = new JTabbedPane();
        breakdown.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
        breakdown.setMinimumSize(new Dimension(0, 0));
        configureStatisticsPanel(rarityStatisticsPanel);
        configureStatisticsPanel(categoryStatisticsPanel);
        breakdown.addTab("Rarity", rarityStatisticsPanel);
        breakdown.addTab("Category", categoryStatisticsPanel);
        styleTabHeaders(breakdown);
        for (int index = 0; index < breakdown.getTabCount(); index++)
        {
            Component header = breakdown.getTabComponentAt(index);
            if (header instanceof JComponent)
            {
                setTabHeaderSize((JComponent) header, 76);
            }
        }
        breakdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        statistics.add(breakdown);
        statistics.add(Box.createVerticalGlue());
        return statistics;
    }

    private JPanel buildCollectionTab()
    {
        JPanel collection = new SidebarTabPanel();
        collection.setLayout(new BoxLayout(collection, BoxLayout.Y_AXIS));
        collection.setBorder(BorderFactory.createEmptyBorder(14, 6, 14, 6));
        collection.setMinimumSize(new Dimension(0, 0));

        collection.add(sectionTitle("Collection album"));
        collection.add(Box.createRigidArea(new Dimension(0, 10)));

        JLabel guidance = new JLabel(
            "<html><div style='width:142px'>Open the full album to browse, "
                + "search and filter your collection.</div></html>");
        guidance.setForeground(new Color(202, 202, 202));
        guidance.setAlignmentX(Component.LEFT_ALIGNMENT);
        guidance.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        collection.add(guidance);
        collection.add(Box.createRigidArea(new Dimension(0, 14)));

        JButton album = new JButton("Open Collection Album");
        album.setFont(album.getFont().deriveFont(Font.BOLD, 11f));
        album.setAlignmentX(Component.LEFT_ALIGNMENT);
        album.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        album.addActionListener(event -> {
            if (lastState != null)
            {
                ensureCollectionAlbumWindow().show(lastState, lastActivity);
            }
        });
        collection.add(album);
        collection.add(Box.createVerticalGlue());
        return collection;
    }

    private JPanel buildPackTab()
    {
        JPanel store = new SidebarTabPanel();
        store.setBorder(BorderFactory.createEmptyBorder(8, 2, 4, 2));
        store.setLayout(new BoxLayout(store, BoxLayout.Y_AXIS));
        store.setMinimumSize(new Dimension(0, 0));

        JTextArea storeIntro = compactWrappedText(
            "Open packs to expand the collection. Locked packs become available through the progression track.",
            new Font(Font.DIALOG, Font.PLAIN, 10),
            MUTED_TEXT);
        store.add(storeIntro);
        store.add(Box.createRigidArea(new Dimension(0, 8)));

        store.add(sectionTitle("Pack store"));
        store.add(Box.createRigidArea(new Dimension(0, 7)));
        starterPackTileContainer.setLayout(
            new BoxLayout(starterPackTileContainer, BoxLayout.Y_AXIS));
        starterPackTileContainer.setOpaque(false);
        starterPackTileContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        starterPackTileContainer.setMaximumSize(
            new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        starterPackTileContainer.add(packStoreTile(
            "ONE-TIME STARTER PACK",
            0,
            "One random spawn-accessible melee weapon, two low-level NPCs and two foods healing up to 3 HP. Redeem before other packs.",
            redeemStarterPackButton,
            new Color(217, 164, 65)));
        starterPackTileContainer.add(
            Box.createRigidArea(new Dimension(0, 6)));
        store.add(starterPackTileContainer);
        store.add(packStoreTile(
            "STANDARD PACK",
            StandardPackService.PRICE,
            "Three Common cards, one Uncommon card and one unrestricted rarity slot. General pulls favour items/equipment and reduce NPC frequency; rarity odds are unchanged. Available from profile creation.",
            purchasePackButton,
            new Color(179, 126, 43)));
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        store.add(packStoreTile(
            "UNCOMMON+ PACK",
            StandardPackService.UNCOMMON_PLUS_PRICE,
            "Two Uncommon cards, two Uncommon-or-better cards and one unrestricted rarity slot. Unlocks at 250 unique cards.",
            purchaseUncommonPlusPackButton,
            new Color(121, 176, 96)));
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        store.add(packStoreTile(
            "EXPLORER PACK",
            StandardPackService.EXPLORER_PRICE,
            "Five non-combat NPC cards. Strongly favours currently unowned NPCs (2.5x weight); Common/Uncommon focused with a small Rare discovery chance. Unlocks at 500 unique cards.",
            purchaseExplorerPackButton,
            new Color(77, 153, 102)));
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        store.add(packStoreTile(
            "RARE+ PACK",
            StandardPackService.RARE_PLUS_PRICE,
            "Three guaranteed Rare cards plus two improved Rare+ rolls (42% Rare, 31% Epic, 16% Legendary, 8% Mythic, 3% Godly). Unlocks at 750 unique cards.",
            purchaseRareHunterPackButton,
            new Color(90, 150, 230)));
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        store.add(packStoreTile(
            "ADVENTURE PACK",
            StandardPackService.ADVENTURE_PRICE,
            "Five combat NPC cards: one Uncommon+, three Rare+, and one guaranteed Epic+ slot. Favours unowned combat cards (1.5x weight). Unlocks at 1,250 unique cards.",
            purchaseAdventurePackButton,
            new Color(184, 83, 68)));
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        store.add(packStoreTile(
            "NEXUS CACHE",
            StandardPackService.NEXUS_CACHE_PRICE,
            "Awards 225–375 Nexus Shards and contains no cards. Unlocks at 1,750 unique cards.",
            purchaseNexusCacheButton,
            new Color(117, 100, 204)));
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        store.add(packStoreTile(
            "COLLECTOR PACK",
            StandardPackService.COLLECTOR_PRICE,
            "One Rare, Epic, Legendary, Mythic and Godly card. At least one randomly selected eligible tier is guaranteed to produce a new card. Unlocks at 2,500 unique cards.",
            purchaseCollectorPackButton,
            new Color(215, 165, 67)));
        store.add(Box.createRigidArea(new Dimension(0, 12)));
        milestoneRewardSection.setLayout(
            new BoxLayout(milestoneRewardSection, BoxLayout.Y_AXIS));
        milestoneRewardSection.setOpaque(false);
        milestoneRewardSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        milestoneRewardSection.setMaximumSize(
            new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        milestoneRewardSection.add(sectionTitle("Milestone rewards"));
        milestoneRewardSection.add(Box.createRigidArea(new Dimension(0, 6)));
        addMilestoneRewardTile(
            "INITIATE'S FOIL PACK",
            "Five guaranteed foil cards selected from Common and Uncommon. Claim once at 500 unique cards.",
            redeemInitiateFoilPackButton,
            new Color(166, 126, 205),
            ProgressionMilestonePolicy.INITIATE_FOIL_MARKER);
        addMilestoneRewardTile(
            "HERO'S PACK",
            "Three guaranteed Rare foils and two non-foil Rare cards. Claim once at 1,500 unique cards.",
            redeemHeroPackButton,
            new Color(83, 145, 214),
            ProgressionMilestonePolicy.HERO_PACK_MARKER);
        addMilestoneRewardTile(
            "NOBLE'S PACK",
            "Three guaranteed Epic foils and two non-foil Epic cards. Claim once at 2,250 unique cards.",
            redeemNoblePackButton,
            new Color(153, 99, 205),
            ProgressionMilestonePolicy.NOBLE_PACK_MARKER);
        addMilestoneRewardTile(
            "LEGEND'S PACK",
            "Three guaranteed Legendary foils and two non-foil Legendary cards. Claim once at 3,000 unique cards.",
            redeemLegendPackButton,
            new Color(222, 151, 58),
            ProgressionMilestonePolicy.LEGEND_PACK_MARKER);
        addMilestoneRewardTile(
            "MYTHICAL PACK",
            "Three guaranteed Mythic foils and two non-foil Mythic cards. Claim once at 3,750 unique cards.",
            redeemMythicalPackButton,
            new Color(208, 82, 155),
            ProgressionMilestonePolicy.MYTHICAL_PACK_MARKER);
        addMilestoneRewardTile(
            "PACK OF THE GODS",
            "Three guaranteed Godly foils and two non-foil Godly cards. Claim once at 4,500 unique cards.",
            redeemGodsPackButton,
            new Color(239, 195, 69),
            ProgressionMilestonePolicy.GODS_PACK_MARKER);
        store.add(milestoneRewardSection);
        store.add(Box.createRigidArea(new Dimension(0, 8)));
        foilTestPackTile = packStoreTile(
            "TEMPORARY FOIL PACK",
            StandardPackService.FOIL_TEST_PRICE,
            "Five cards with guaranteed Common, Uncommon, Rare, Epic and Legendary rarities. At least one card is guaranteed to be foil.",
            purchaseFoilTestPackButton,
            new Color(188, 113, 232));
        foilTestPackTile.setVisible(packActionHandler.isTestingMode());
        store.add(foilTestPackTile);
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        premiumFoilTestPackTile = packStoreTile(
            "TEMPORARY LEGENDARY+ FOIL PACK",
            StandardPackService.PREMIUM_FOIL_TEST_PRICE,
            "Five cards selected from Legendary, Mythic and Godly rarities. At least one card is guaranteed to be foil.",
            purchasePremiumFoilTestPackButton,
            new Color(239, 172, 72));
        premiumFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        store.add(premiumFoilTestPackTile);
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        tierFoilTestPackTile = packStoreTile(
            "TIER-CHAIN FOIL TEST PACK",
            StandardPackService.MAPPED_FOIL_TEST_PRICE,
            "Five distinct mapped tier-chain reward sources. Every card is foil and grants reviewed lower-tier usage permissions.",
            purchaseTierFoilTestPackButton,
            new Color(88, 162, 214));
        tierFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        store.add(tierFoilTestPackTile);
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        armourFoilTestPackTile = packStoreTile(
            "ARMOUR-SLOT FOIL TEST PACK",
            StandardPackService.MAPPED_FOIL_TEST_PRICE,
            "Five distinct mapped armour-slot tier sources. Every card is foil and grants lower tiers of the same equipment slot only.",
            purchaseArmourFoilTestPackButton,
            new Color(104, 178, 126));
        armourFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        store.add(armourFoilTestPackTile);
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        bossFoilTestPackTile = packStoreTile(
            "BOSS FOIL TEST PACK",
            StandardPackService.MAPPED_FOIL_TEST_PRICE,
            "Five distinct mapped boss cards. Every card is foil and grants its reviewed direct reward-table permissions.",
            purchaseBossFoilTestPackButton,
            new Color(204, 92, 78));
        bossFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        store.add(bossFoilTestPackTile);
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        ingredientFoilTestPackTile = packStoreTile(
            "ITEM RELATIONSHIP FOIL TEST PACK",
            StandardPackService.MAPPED_FOIL_TEST_PRICE,
            "Five distinct recipe, Farming, package or material foil sources. Every card is foil and grants only its reviewed direct item relationships.",
            purchaseIngredientFoilTestPackButton,
            new Color(118, 176, 92));
        ingredientFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        store.add(ingredientFoilTestPackTile);
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        signatureFoilTestPackTile = packStoreTile(
            "SIGNATURE-SET FOIL TEST PACK",
            StandardPackService.MAPPED_FOIL_TEST_PRICE,
            "Five distinct high-tier named-set foil sources. Every card is foil and grants the other reviewed pieces of its own signature set.",
            purchaseSignatureFoilTestPackButton,
            new Color(155, 106, 205));
        signatureFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        store.add(signatureFoilTestPackTile);
        store.add(Box.createRigidArea(new Dimension(0, 6)));
        npcRelationshipFoilTestPackTile = packStoreTile(
            "NPC RELATIONSHIP FOIL TEST PACK",
            StandardPackService.MAPPED_FOIL_TEST_PRICE,
            "Five distinct NPC foils covering required tools, earned rewards, source-linked equipment and curated wave tiers. Unrelated NPC-family cascades remain excluded.",
            purchaseNpcRelationshipFoilTestPackButton,
            new Color(199, 133, 61));
        npcRelationshipFoilTestPackTile.setVisible(packActionHandler.isTestingMode());
        store.add(npcRelationshipFoilTestPackTile);
        store.add(Box.createRigidArea(new Dimension(0, 6)));

        return store;
    }

    private JPanel packStoreTile(
        String name,
        long price,
        String description,
        JButton button,
        Color accent)
    {
        JPanel tile = new SidebarRowPanel(new BorderLayout(7, 0));
        tile.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tile.setOpaque(true);
        tile.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accent, 1),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        tile.setAlignmentX(Component.LEFT_ALIGNMENT);

        button.setIcon(CardUiAssets.icon(
            "/com/cardrestricted/ui/booster-sealed.png",
            38,
            57));
        button.setText("");
        button.setFocusable(false);
        button.getAccessibleContext().setAccessibleName(
            price == 0 ? "Redeem " + name : "Purchase " + name);
        button.setMargin(new Insets(2, 3, 2, 3));
        Dimension packButtonSize = new Dimension(48, 67);
        button.setMinimumSize(packButtonSize);
        button.setPreferredSize(packButtonSize);
        button.setMaximumSize(packButtonSize);
        tile.add(button, BorderLayout.WEST);

        JPanel information = new SidebarRowPanel();
        information.setOpaque(false);
        information.setLayout(new BoxLayout(information, BoxLayout.Y_AXIS));
        JTextArea title = compactWrappedText(
            name,
            new Font(Font.DIALOG, Font.BOLD, 11),
            accent.brighter());
        information.add(title);
        JLabel priceLabel = new JLabel(
            price == 0 ? "FREE · ONE TIME" : formatNumber(price) + " points");
        priceLabel.setForeground(new Color(247, 207, 92));
        priceLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 10));
        priceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        information.add(priceLabel);
        JTextArea detail = compactWrappedText(
            description,
            new Font(Font.DIALOG, Font.PLAIN, 10),
            MUTED_TEXT);
        detail.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        information.add(detail);
        tile.add(information, BorderLayout.CENTER);
        return tile;
    }

    private void addMilestoneRewardTile(
        String name,
        String description,
        JButton button,
        Color accent,
        String marker)
    {
        JPanel tile = packStoreTile(name, 0, description, button, accent);
        milestoneRewardTiles.put(marker, tile);
        milestoneRewardSection.add(tile);
        milestoneRewardSection.add(Box.createRigidArea(new Dimension(0, 6)));
    }

    private JPanel buildQuestTab()
    {
        JPanel quests = new SidebarTabPanel(new BorderLayout(0, 8));
        quests.setBorder(BorderFactory.createEmptyBorder(8, 2, 4, 2));
        quests.setMinimumSize(new Dimension(0, 0));

        JPanel controls = new SidebarRowPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("QUEST CARD READINESS");
        title.setFont(COMPACT_TITLE_FONT);
        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(title);
        controls.add(Box.createRigidArea(new Dimension(0, 4)));

        JTextArea description = compactWrappedText(
            "Green means complete. Amber means every mandatory item and combat card "
                + "is unlocked, or no card requirements are tracked. Red means a mandatory card is missing. "
                + "Expand a quest and select any card to open it in the Collection Album.",
            COMPACT_BODY_FONT,
            new Color(202, 202, 202));
        controls.add(description);
        controls.add(Box.createRigidArea(new Dimension(0, 6)));

        questStatusRefreshButton.setFont(COMPACT_BODY_FONT);
        questStatusRefreshButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        questStatusRefreshButton.setMaximumSize(
            new Dimension(Integer.MAX_VALUE, 28));
        questStatusRefreshButton.getAccessibleContext().setAccessibleDescription(
            "Refresh RuneLite quest completion states for the Quest tab.");
        controls.add(questStatusRefreshButton);
        controls.add(Box.createRigidArea(new Dimension(0, 3)));

        questStatusRefreshLabel.setFont(COMPACT_META_FONT);
        questStatusRefreshLabel.setForeground(MUTED_TEXT);
        questStatusRefreshLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(questStatusRefreshLabel);
        controls.add(Box.createRigidArea(new Dimension(0, 6)));

        questSearchField.setFont(COMPACT_BODY_FONT);
        questSearchField.getAccessibleContext().setAccessibleDescription(
            "Search quests, unlocked cards or missing card names.");
        questSearchField.setMinimumSize(new Dimension(0, 28));
        questSearchField.setPreferredSize(new Dimension(140, 28));

        questSearchClearButton.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        questSearchClearButton.setMargin(new Insets(1, 6, 1, 6));
        questSearchClearButton.setFocusable(false);
        questSearchClearButton.setEnabled(false);
        questSearchClearButton.getAccessibleContext().setAccessibleDescription(
            "Clear quest search.");
        questSearchClearButton.addActionListener(event ->
            questSearchField.setText(""));

        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.setOpaque(false);
        searchRow.setMinimumSize(new Dimension(0, 28));
        searchRow.setPreferredSize(new Dimension(176, 28));
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchRow.add(questSearchField, BorderLayout.CENTER);
        searchRow.add(questSearchClearButton, BorderLayout.EAST);
        controls.add(searchRow);
        controls.add(Box.createRigidArea(new Dimension(0, 5)));

        questReadinessFilter.setFont(COMPACT_BODY_FONT);
        questReadinessFilter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        questReadinessFilter.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(questReadinessFilter);
        controls.add(Box.createRigidArea(new Dimension(0, 5)));

        questReadinessSummary.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
        questReadinessSummary.setForeground(new Color(224, 224, 224));
        questReadinessSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(questReadinessSummary);
        quests.add(controls, BorderLayout.NORTH);

        questReadinessList.setLayout(
            new BoxLayout(questReadinessList, BoxLayout.Y_AXIS));
        questReadinessList.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        questReadinessList.setMinimumSize(new Dimension(0, 0));
        questReadinessList.setAlignmentX(Component.LEFT_ALIGNMENT);
        quests.add(questReadinessList, BorderLayout.CENTER);

        questSearchField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent event)
            {
                updateQuestSearchState();
            }

            @Override
            public void removeUpdate(DocumentEvent event)
            {
                updateQuestSearchState();
            }

            @Override
            public void changedUpdate(DocumentEvent event)
            {
                updateQuestSearchState();
            }
        });
        questReadinessFilter.addActionListener(event -> rebuildQuestReadinessList(false));
        questReadinessSummary.setText("<html>Complete 0 · Ready 0<br>Blocked 0 · Shown 0</html>");
        return quests;
    }

    private void updateQuestSearchState()
    {
        questSearchClearButton.setEnabled(!questSearchField.getText().isEmpty());
        questSearchDebounceTimer.restart();
    }

    public void updateQuestCompletions(Set<String> questKeys)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> updateQuestCompletions(questKeys));
            return;
        }
        Set<String> next = Collections.unmodifiableSet(
            new LinkedHashSet<>(questKeys == null
                ? Collections.emptySet() : questKeys));
        if (next.equals(completedQuestKeys))
        {
            return;
        }
        completedQuestKeys = next;
        if (lastState != null)
        {
            updateQuestReadiness(lastState);
        }
    }

    public void completeQuestStatusRefresh(Set<String> questKeys)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> completeQuestStatusRefresh(questKeys));
            return;
        }
        updateQuestCompletions(questKeys);
        questStatusRefreshInProgress = false;
        questStatusRefreshButton.setEnabled(true);
        questStatusRefreshButton.setText("Refresh Quest Status");
        questStatusRefreshLabel.setText("Status refreshed");
    }

    Set<String> questCompletionSnapshotForTesting()
    {
        return completedQuestKeys;
    }

    public void cancelQuestStatusRefresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::cancelQuestStatusRefresh);
            return;
        }
        questStatusRefreshInProgress = false;
        questStatusRefreshButton.setEnabled(renderedStatus == SessionStatus.READY);
        questStatusRefreshButton.setText("Refresh Quest Status");
        questStatusRefreshLabel.setText("Status refresh is manual");
    }

    private void updateQuestReadiness(CollectionState state)
    {
        Set<String> owned = catalogue.canonicalizeCardIds(state.getOwnedCardIds());
        Set<String> foils = catalogue.canonicalizeCardIds(state.getFoilCardIds());
        if (questReadinessSnapshot != null
            && owned.equals(renderedQuestOwnedCardIds)
            && foils.equals(renderedQuestFoilCardIds)
            && completedQuestKeys.equals(renderedQuestCompletionKeys))
        {
            return;
        }
        renderedQuestOwnedCardIds = Collections.unmodifiableSet(
            new LinkedHashSet<>(owned));
        renderedQuestFoilCardIds = Collections.unmodifiableSet(
            new LinkedHashSet<>(foils));
        renderedQuestCompletionKeys = completedQuestKeys;
        questReadinessSnapshot = questReadinessService.calculate(
            state, completedQuestKeys);
        rebuildQuestReadinessList(true);
    }

    private void rebuildQuestReadinessList(boolean preserveViewport)
    {
        QuestScrollAnchor anchor = preserveViewport
            ? captureQuestScrollAnchor(null)
            : null;
        rebuildQuestReadinessList(preserveViewport, anchor);
    }

    private void rebuildQuestReadinessList(
        boolean preserveViewport,
        QuestScrollAnchor anchor)
    {
        questToggleRevision++;
        int revision = ++questReadinessRenderRevision;
        questReadinessRows.clear();
        questReadinessList.removeAll();
        if (questReadinessSnapshot == null)
        {
            questReadinessSummary.setText("<html>Complete 0 · Ready 0<br>Blocked 0 · Shown 0</html>");
            installSidePanelScrollForwarding(questReadinessList);
            questReadinessList.revalidate();
            questReadinessList.repaint();
            restoreQuestScrollAnchor(anchor, preserveViewport, revision);
            return;
        }

        String summary = "<html>Complete " + questReadinessSnapshot.getCompleteCount()
            + " · Ready " + questReadinessSnapshot.getReadyCount()
            + "<br>Blocked " + questReadinessSnapshot.getBlockedCount();
        String query = questSearchField.getText().trim()
            .toLowerCase(Locale.ROOT);
        QuestReadinessFilter filter =
            (QuestReadinessFilter) questReadinessFilter.getSelectedItem();
        int shown = 0;
        for (QuestReadinessEntry entry : questReadinessSnapshot.getEntries())
        {
            if (filter != null && !filter.includes(entry))
            {
                continue;
            }
            if (!entry.matchesNormalized(query))
            {
                continue;
            }
            JPanel row = buildQuestReadinessRow(entry);
            questReadinessRows.put(entry.getDefinition().getQuestKey(), row);
            questReadinessList.add(row);
            questReadinessList.add(Box.createRigidArea(new Dimension(0, 5)));
            shown++;
        }
        if (shown == 0)
        {
            JLabel empty = new JLabel("No quests match the current filter.");
            empty.setFont(COMPACT_BODY_FONT);
            empty.setForeground(MUTED_TEXT);
            empty.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            questReadinessList.add(empty);
        }
        questReadinessSummary.setText(summary + " · Shown " + shown + "</html>");
        installSidePanelScrollForwarding(questReadinessList);
        questReadinessList.revalidate();
        questReadinessList.repaint();
        restoreQuestScrollAnchor(anchor, preserveViewport, revision);
    }

    private JPanel buildQuestReadinessRow(QuestReadinessEntry entry)
    {
        String questKey = entry.getDefinition().getQuestKey();
        boolean expanded = expandedQuestRows.contains(questKey);
        Color statusColor;
        if (entry.getStatus() == QuestStatus.COMPLETE)
        {
            statusColor = QUEST_COMPLETE_COLOR;
        }
        else if (entry.getStatus() == QuestStatus.READY)
        {
            statusColor = QUEST_READY_COLOR;
        }
        else
        {
            statusColor = QUEST_BLOCKED_COLOR;
        }

        JPanel tile = new SidebarRowPanel();
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        tile.setOpaque(true);
        tile.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tile.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(statusColor.darker()),
            BorderFactory.createEmptyBorder(6, 7, 6, 7)));
        tile.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new SidebarRowPanel(new BorderLayout(5, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel text = new SidebarRowPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JTextArea questName = compactWrappedText(
            entry.getDefinition().getQuestName(),
            COMPACT_TITLE_FONT,
            statusColor);
        text.add(questName);
        String countText;
        if (entry.isComplete())
        {
            countText = "Quest complete";
        }
        else if (entry.getTotalCards() == 0)
        {
            countText = "No tracked card requirements";
        }
        else
        {
            countText = "Items " + entry.getAvailableItemCards() + "/"
                + entry.getTotalItemCards() + " · Combat "
                + entry.getAvailableCombatCards() + "/" + entry.getTotalCombatCards();
        }
        JTextArea count = compactWrappedText(
            countText,
            COMPACT_META_FONT,
            new Color(188, 188, 188));
        text.add(count);
        header.add(text, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));

        JButton wiki = new JButton("Wiki");
        wiki.setFont(new Font(Font.DIALOG, Font.BOLD, 10));
        wiki.setMargin(new Insets(1, 4, 1, 4));
        wiki.setFocusable(false);
        wiki.getAccessibleContext().setAccessibleName(
            "Open " + entry.getDefinition().getQuestName() + " on the OSRS Wiki");
        wiki.addActionListener(event -> openQuestWiki(entry));
        actions.add(wiki);
        actions.add(Box.createRigidArea(new Dimension(3, 0)));

        JButton toggle = new JButton(expanded ? "−" : "+");
        toggle.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
        toggle.setMargin(new Insets(1, 6, 1, 6));
        toggle.setFocusable(false);
        actions.add(toggle);
        header.add(actions, BorderLayout.EAST);
        tile.add(header);

        JPanel details = new SidebarRowPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setAlignmentX(Component.LEFT_ALIGNMENT);
        details.add(Box.createRigidArea(new Dimension(0, 6)));
        JTextArea stateLabel = compactWrappedText(
            entry.isComplete()
                ? "Completed"
                : entry.isCardReady()
                    ? "Card requirements ready"
                    : "Card requirements blocked",
            new Font(Font.DIALOG, Font.BOLD, 11),
            statusColor);
        details.add(stateLabel);
        details.add(Box.createRigidArea(new Dimension(0, 6)));
        addQuestRequirementSection(
            details,
            "Item cards",
            entry.getAvailableItemCards(),
            entry.getTotalItemCards(),
            entry.getOwnedItemCardIds(),
            entry.getFoilUnlockedItemCardIds(),
            entry.getMissingItemCardIds());
        details.add(Box.createRigidArea(new Dimension(0, 7)));
        addQuestRequirementSection(
            details,
            "Combat cards",
            entry.getAvailableCombatCards(),
            entry.getTotalCombatCards(),
            entry.getOwnedCombatCardIds(),
            entry.getFoilUnlockedCombatCardIds(),
            entry.getMissingCombatCardIds());
        details.setVisible(expanded);
        tile.add(details);

        Runnable toggleAction = () -> toggleQuestDetailsInPlace(
            questKey,
            tile,
            details,
            toggle);
        toggle.addActionListener(event -> toggleAction.run());
        installQuestHeaderClickHandler(header, toggleAction);
        toggle.getAccessibleContext().setAccessibleName(
            (expanded ? "Collapse " : "Expand ")
                + entry.getDefinition().getQuestName() + " card requirements");
        installSidePanelScrollForwarding(tile);
        return tile;
    }

    private void toggleQuestDetailsInPlace(
        String questKey,
        JPanel tile,
        JPanel details,
        JButton toggle)
    {
        JViewport viewport = questReadinessScroll.getViewport();
        int viewportOffset = tile.getY() - viewport.getViewPosition().y;
        boolean expanded = !details.isVisible();
        if (expanded)
        {
            expandedQuestRows.add(questKey);
        }
        else
        {
            expandedQuestRows.remove(questKey);
        }
        details.setVisible(expanded);
        toggle.setText(expanded ? "−" : "+");
        toggle.getAccessibleContext().setAccessibleName(
            (expanded ? "Collapse " : "Expand ") + questKey + " card requirements");
        tile.revalidate();
        questReadinessList.revalidate();
        questReadinessList.repaint();
        int revision = ++questToggleRevision;
        SwingUtilities.invokeLater(() ->
        {
            if (revision != questToggleRevision)
            {
                return;
            }
            JScrollBar bar = questReadinessScroll.getVerticalScrollBar();
            int maximum = Math.max(
                bar.getMinimum(),
                bar.getMaximum() - bar.getModel().getExtent());
            int target = tile.getY() - viewportOffset;
            bar.setValue(Math.max(bar.getMinimum(), Math.min(maximum, target)));
        });
    }

    private void addQuestRequirementSection(
        JPanel tile,
        String title,
        int availableCount,
        int totalCount,
        List<String> ownedCardIds,
        List<String> foilUnlockedCardIds,
        List<String> missingCardIds)
    {
        JTextArea heading = compactWrappedText(
            title + " — " + availableCount + " / " + totalCount,
            new Font(Font.DIALOG, Font.BOLD, 11),
            new Color(224, 224, 224));
        tile.add(heading);
        if (totalCount == 0)
        {
            JLabel none = new JLabel("No tracked mandatory "
                + title.toLowerCase(Locale.ROOT) + ".");
            none.setFont(COMPACT_BODY_FONT);
            none.setForeground(MUTED_TEXT);
            none.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 0));
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            tile.add(none);
            return;
        }

        JLabel unlocked = new JLabel("Unlocked:");
        unlocked.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
        unlocked.setForeground(QUEST_UNLOCKED_COLOR);
        unlocked.setAlignmentX(Component.LEFT_ALIGNMENT);
        tile.add(unlocked);
        if (ownedCardIds.isEmpty())
        {
            JLabel none = new JLabel("None");
            none.setFont(COMPACT_BODY_FONT);
            none.setForeground(MUTED_TEXT);
            none.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 0));
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            tile.add(none);
        }
        else
        {
            addQuestCardList(tile, ownedCardIds, QUEST_UNLOCKED_COLOR);
        }

        if (!foilUnlockedCardIds.isEmpty())
        {
            JTextArea foilUnlocked = compactWrappedText(
                "Foil unlocked (usable, not owned):",
                new Font(Font.DIALOG, Font.BOLD, 11),
                FOIL_ACCESS_COLOR);
            foilUnlocked.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            tile.add(foilUnlocked);
            addQuestCardList(tile, foilUnlockedCardIds, FOIL_ACCESS_COLOR);
        }

        if (!missingCardIds.isEmpty())
        {
            JLabel missing = new JLabel("Missing:");
            missing.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
            missing.setForeground(QUEST_BLOCKED_COLOR);
            missing.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            missing.setAlignmentX(Component.LEFT_ALIGNMENT);
            tile.add(missing);
            addQuestCardList(tile, missingCardIds, new Color(220, 220, 220));
        }
    }

    private void addQuestCardList(
        JPanel tile,
        List<String> cardIds,
        Color textColor)
    {
        for (String cardId : cardIds)
        {
            CardDefinition definition = catalogue.requireCard(cardId);
            JTextArea card = compactWrappedText(
                "• " + definition.getDisplayName() + "  ›",
                COMPACT_BODY_FONT,
                textColor);
            card.setFocusable(true);
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.setName("quest-album-link");
            card.getAccessibleContext().setAccessibleName(
                "Open " + definition.getDisplayName() + " in the Collection Album");
            card.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 2));
            Runnable openAction = () -> openQuestRequirementCard(cardId);
            card.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent event)
                {
                    card.setOpaque(true);
                    card.setBackground(new Color(255, 255, 255, 14));
                    card.repaint();
                }

                @Override
                public void mouseExited(MouseEvent event)
                {
                    card.setOpaque(false);
                    card.repaint();
                }

                @Override
                public void mouseReleased(MouseEvent event)
                {
                    if (SwingUtilities.isLeftMouseButton(event))
                    {
                        openAction.run();
                    }
                }
            });
            card.getInputMap(JComponent.WHEN_FOCUSED).put(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0),
                "open-quest-card");
            card.getInputMap(JComponent.WHEN_FOCUSED).put(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0),
                "open-quest-card");
            card.getActionMap().put("open-quest-card", new javax.swing.AbstractAction()
            {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(java.awt.event.ActionEvent event)
                {
                    openAction.run();
                }
            });
            tile.add(card);
        }
    }

    private void openQuestRequirementCard(String cardId)
    {
        if (lastState == null)
        {
            return;
        }
        CardDefinition card = catalogue.requireCard(cardId);
        ensureCollectionAlbumWindow().showCard(lastState, lastActivity, card);
    }

    private void openQuestWiki(QuestReadinessEntry entry)
    {
        String questName = entry.getDefinition().getQuestName();
        try
        {
            String query = URLEncoder.encode(
                questName,
                StandardCharsets.UTF_8.name());
            URI uri = URI.create(
                "https://oldschool.runescape.wiki/w/Special:Search?search=" + query);
            if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            {
                throw new IllegalStateException("Desktop browsing is unavailable.");
            }
            Desktop.getDesktop().browse(uri);
        }
        catch (Exception exception)
        {
            javax.swing.JOptionPane.showMessageDialog(
                this,
                "Unable to open the OSRS Wiki in your default browser.",
                "OSRS Wiki",
                javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    private void installSidePanelScrollForwarding(Component component)
    {
        if (sidePanelScrollTargets.add(component))
        {
            component.addMouseWheelListener(sidePanelWheelListener);
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                installSidePanelScrollForwarding(child);
            }
        }
    }

    private void scrollActiveTab(MouseWheelEvent event)
    {
        JScrollPane scroll = activeTabScrollPane();
        if (scroll == null || !readyTabs.isVisible())
        {
            return;
        }
        JScrollBar bar = scroll.getVerticalScrollBar();
        int minimum = bar.getMinimum();
        int maximum = Math.max(minimum,
            bar.getMaximum() - bar.getModel().getExtent());
        if (maximum <= minimum)
        {
            event.consume();
            return;
        }

        double rotation = event.getPreciseWheelRotation();
        if (rotation == 0.0)
        {
            event.consume();
            return;
        }
        int direction = rotation < 0.0 ? -1 : 1;
        int increment = event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL
            ? Math.max(1, bar.getBlockIncrement(direction))
            : Math.max(1, bar.getUnitIncrement(direction))
                * Math.max(1, event.getScrollAmount());
        int delta = (int) Math.round(increment * rotation);
        if (delta == 0)
        {
            delta = direction * Math.max(1, bar.getUnitIncrement(direction));
        }
        bar.setValue(Math.max(minimum, Math.min(maximum, bar.getValue() + delta)));
        event.consume();
    }

    private JScrollPane activeTabScrollPane()
    {
        Component active = readyTabs.getSelectedComponent();
        return active instanceof JScrollPane ? (JScrollPane) active : null;
    }

    private JTextArea compactWrappedText(String value, Font font, Color color)
    {
        JTextArea text = new SidebarWrappedTextArea(value);
        configureWrappedTextArea(text, font, color);
        return text;
    }

    private void configureWrappedTextArea(
        JTextArea text,
        Font font,
        Color color)
    {
        text.setEditable(false);
        text.setFocusable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setFont(font);
        text.setForeground(color);
        text.setBorder(BorderFactory.createEmptyBorder());
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void installQuestHeaderClickHandler(
        Component component,
        Runnable action)
    {
        if (!(component instanceof JButton))
        {
            component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            component.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseReleased(MouseEvent event)
                {
                    if (SwingUtilities.isLeftMouseButton(event))
                    {
                        action.run();
                    }
                }
            });
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                installQuestHeaderClickHandler(child, action);
            }
        }
    }

    private QuestScrollAnchor captureQuestScrollAnchor(String preferredQuestKey)
    {
        JViewport viewport = questReadinessScroll.getViewport();
        int viewY = viewport.getViewPosition().y;
        JPanel preferred = preferredQuestKey == null
            ? null : questReadinessRows.get(preferredQuestKey);
        if (preferred != null)
        {
            return new QuestScrollAnchor(
                preferredQuestKey,
                preferred.getY() - viewY,
                viewY);
        }
        for (Map.Entry<String, JPanel> row : questReadinessRows.entrySet())
        {
            JPanel component = row.getValue();
            if (component.getY() + component.getHeight() >= viewY)
            {
                return new QuestScrollAnchor(
                    row.getKey(),
                    component.getY() - viewY,
                    viewY);
            }
        }
        return new QuestScrollAnchor(null, 0, viewY);
    }

    private void restoreQuestScrollAnchor(
        QuestScrollAnchor anchor,
        boolean preserveViewport,
        int revision)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (revision != questReadinessRenderRevision)
            {
                return;
            }
            JScrollBar bar = questReadinessScroll.getVerticalScrollBar();
            int maximum = Math.max(bar.getMinimum(),
                bar.getMaximum() - bar.getModel().getExtent());
            int value = 0;
            if (preserveViewport && anchor != null)
            {
                JPanel row = anchor.questKey == null
                    ? null : questReadinessRows.get(anchor.questKey);
                value = row == null
                    ? anchor.fallbackValue
                    : row.getY() - anchor.viewportOffset;
            }
            bar.setValue(Math.max(bar.getMinimum(), Math.min(maximum, value)));
        });
    }

    private JPanel buildNexusTab()
    {
        JPanel nexus = new SidebarTabPanel();
        nexus.setBorder(BorderFactory.createEmptyBorder(8, 2, 10, 2));
        nexus.setLayout(new BoxLayout(nexus, BoxLayout.Y_AXIS));
        nexus.setMinimumSize(new Dimension(0, 0));

        JTextArea description = compactWrappedText(
            "Choose a rarity to unlock one random missing card. Nexus exchanges never produce duplicates.",
            new Font(Font.DIALOG, Font.PLAIN, 11),
            new Color(215, 215, 215));
        description.setBorder(BorderFactory.createEmptyBorder(0, 1, 2, 1));
        nexus.add(description);
        nexus.add(Box.createRigidArea(new Dimension(0, 7)));

        for (Rarity rarity : Rarity.values())
        {
            nexus.add(nexusTier(rarity));
            nexus.add(Box.createRigidArea(new Dimension(0, 6)));
        }
        return nexus;
    }

    private JPanel nexusTier(Rarity rarity)
    {
        JPanel tier = new SidebarRowPanel(new BorderLayout(7, 5));
        tier.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tier.setOpaque(true);
        tier.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(rarityColor(rarity), 2),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        tier.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel frame = new JLabel(CardUiAssets.icon(
            "/com/cardrestricted/ui/card-frame-"
                + rarity.name().toLowerCase(Locale.ROOT) + ".png",
            31,
            47));
        frame.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 1));
        tier.add(frame, BorderLayout.WEST);

        JPanel centre = new SidebarRowPanel();
        centre.setOpaque(false);
        centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(readable(rarity));
        name.setForeground(rarityColor(rarity));
        name.setFont(new Font(Font.DIALOG, Font.BOLD, 13));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        centre.add(name);
        centre.add(Box.createRigidArea(new Dimension(0, 4)));
        JProgressBar progress = new JProgressBar();
        progress.setStringPainted(true);
        progress.setFont(new Font(Font.DIALOG, Font.BOLD, 10));
        progress.setMaximumSize(new Dimension(88, 18));
        progress.setPreferredSize(new Dimension(88, 18));
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        centre.add(progress);
        tier.add(centre, BorderLayout.CENTER);

        JButton exchange = new JButton(
            "Unlock " + formatNumber(NexusExchangeCosts.forRarity(rarity)));
        exchange.getAccessibleContext().setAccessibleDescription(
            "Spend " + formatNumber(NexusExchangeCosts.forRarity(rarity))
                + " shards to unlock one missing " + readable(rarity) + " card.");
        exchange.setMargin(new Insets(2, 4, 2, 4));
        exchange.setMinimumSize(new Dimension(0, 32));
        exchange.setPreferredSize(new Dimension(120, 32));
        exchange.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        exchange.setFont(new Font(Font.DIALOG, Font.BOLD, 10));
        exchange.addActionListener(event -> {
            setBusy("Completing " + readable(rarity) + " Nexus exchange...");
            packActionHandler.exchangeNexusCard(rarity);
        });
        tier.add(exchange, BorderLayout.SOUTH);
        nexusButtons.put(rarity, exchange);
        nexusProgressBars.put(rarity, progress);
        return tier;
    }

    private void setPackStatus(String text)
    {
        packStatusLabel.setText(
            "<html><div style='width:140px'>" + escape(text)
                + "</div></html>");
    }

    private void configureRevealCard()
    {
        revealCardPanel.setLayout(
            new BoxLayout(revealCardPanel, BoxLayout.Y_AXIS));
        revealCardPanel.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COMMON_COLOR, 2),
                BorderFactory.createEmptyBorder(18, 12, 18, 12)));
        revealCardPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        revealCardPanel.setOpaque(true);
        revealCardPanel.setMaximumSize(
            new Dimension(Integer.MAX_VALUE, 150));
        revealCardPanel.setPreferredSize(new Dimension(210, 150));
        revealCardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        revealCardName.setFont(
            revealCardName.getFont().deriveFont(Font.BOLD, 18f));
        revealCardName.setForeground(ColorScheme.TEXT_COLOR);
        revealCardName.setAlignmentX(Component.CENTER_ALIGNMENT);
        revealCardName.setHorizontalAlignment(SwingConstants.CENTER);
        revealCardMeta.setForeground(MUTED_TEXT);
        revealCardMeta.setAlignmentX(Component.CENTER_ALIGNMENT);
        revealCardOutcome.setAlignmentX(Component.CENTER_ALIGNMENT);
        revealCardOutcome.setHorizontalAlignment(SwingConstants.CENTER);
        revealCardPanel.add(Box.createVerticalGlue());
        revealCardPanel.add(revealCardName);
        revealCardPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        revealCardPanel.add(revealCardMeta);
        revealCardPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        revealCardPanel.add(revealCardOutcome);
        revealCardPanel.add(Box.createVerticalGlue());
    }

    private JPanel buildPackSlots()
    {
        JPanel slots = new JPanel(
            new GridLayout(1, StandardPackService.CARD_COUNT, 5, 0));
        slots.setMaximumSize(
            new Dimension(Integer.MAX_VALUE, 26));
        slots.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int index = 0;
             index < StandardPackService.CARD_COUNT;
             index++)
        {
            JLabel slot = new JLabel(
                Integer.toString(index + 1),
                SwingConstants.CENTER);
            slot.setOpaque(true);
            slot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            slot.setForeground(MUTED_TEXT);
            slot.setBorder(BorderFactory.createLineBorder(
                ColorScheme.BORDER_COLOR));
            packSlotLabels[index] = slot;
            slots.add(slot);
        }
        return slots;
    }

    private void updateOverview(
        CollectionState state,
        CollectionProgressSnapshot progress)
    {
        int owned = progress.getOverall().getOwned();
        int total = progress.getOverall().getTotal();
        modeValue.setText(readable(state.getEconomyMode()));
        boolean integrityProfile = ProfileStateMarkers.isIntegrityProfile(state);
        integrityValue.setText(integrityProfile
            ? "Integrity profile"
            : (ProfileStateMarkers.isIntegrityForfeited(state)
                ? "Non-integrity (forfeited)"
                : "Non-integrity profile"));
        integrityValue.setForeground(integrityProfile
            ? new Color(224, 184, 64)
            : new Color(190, 196, 204));
        disableIntegrityButton.setVisible(integrityProfile);
        cardsValue.setText(owned + " / " + total);
        pointsValue.setText(formatNumber(state.getPoints()));
        shardsValue.setText(formatNumber(state.getShards()));

        collectionProgress.setMinimum(0);
        collectionProgress.setMaximum(total);
        collectionProgress.setValue(owned);
        collectionProgress.setString(
            owned + " of " + total + " cards unlocked");

        ProgressionMilestoneDefinition nextMilestone = null;
        for (ProgressionMilestoneDefinition milestone :
            ProgressionMilestonePolicy.track())
        {
            if (milestone.getRequiredCards() > owned)
            {
                nextMilestone = milestone;
                break;
            }
        }
        if (nextMilestone == null)
        {
            nextGoalValue.setText("Progression track complete");
            nextGoalDescription.setText("All collection milestones reached");
            nextGoalProgress.setMinimum(0);
            nextGoalProgress.setMaximum(1);
            nextGoalProgress.setValue(1);
            nextGoalProgress.setString("Complete");
        }
        else
        {
            int requirement = nextMilestone.getRequiredCards();
            int remaining = Math.max(0, requirement - owned);
            nextGoalValue.setText(nextMilestone.getTitle());
            nextGoalDescription.setText(
                formatNumber(remaining) + " cards remaining");
            nextGoalProgress.setMinimum(0);
            nextGoalProgress.setMaximum(Math.max(1, requirement));
            nextGoalProgress.setValue(Math.min(owned, requirement));
            nextGoalProgress.setString(
                formatNumber(owned) + " / " + formatNumber(requirement));
        }

        npcKillRewardsValue.setText("Combat level in points");
        noncombatRewardsValue.setText("100 per 1,000 XP");
        skillLevelRewardsValue.setText(
            formatNumber(SkillLevelRewardPolicy.LEVEL_1_REWARD)
                + " at level 1 → "
                + formatNumber(SkillLevelRewardPolicy.LEVEL_99_REWARD)
                + " at level 99");
        long questMilestones =
            state.getClaimedPointSourceIds().stream()
                .filter(sourceId -> sourceId.startsWith(
                    F2pQuestCompletionRewardPolicy.SOURCE_PREFIX))
                .count();
        questMilestonesValue.setText(
            questMilestones + " claimed");
    }

    private void updateStatistics(
        CollectionState state,
        CollectionProgressSnapshot progress,
        CollectionActivitySnapshot activity)
    {
        CollectionProgress overall = progress.getOverall();
        collectionPercentValue.setText(overall.formatPercent());
        missingCardsValue.setText(Integer.toString(overall.getMissing()));
        foilCardsValue.setText(Integer.toString(overall.getFoil()));
        FoilEntitlementSnapshot foilEntitlements =
            foilEntitlementResolver.resolve(
                state.getOwnedCardIds(),
                state.getFoilCardIds());
        foilUnlocksValue.setText(Integer.toString(
            foilEntitlements.getDerivedCardIds().size()));
        long days = Math.max(
            0L,
            java.time.Duration.between(
                state.getCreatedAt(),
                java.time.Instant.now()).toDays());
        accountAgeValue.setText(days + (days == 1 ? " day" : " days"));
        packsOpenedValue.setText(
            formatNumber(activity.getCompletedPackCount()));
        cardsDrawnValue.setText(
            formatNumber(activity.getTotalCardsDrawn()));
        duplicatePullsValue.setText(
            formatNumber(activity.getDuplicateCardCount()));
        nexusUnlocksValue.setText(
            formatNumber(activity.getNexusUnlockCount()));

        rarityStatisticsPanel.removeAll();
        for (Rarity rarity : Rarity.values())
        {
            addProgressRow(
                rarityStatisticsPanel,
                readable(rarity),
                progress.getProgress(rarity),
                rarityColor(rarity));
        }

        categoryStatisticsPanel.removeAll();
        for (CardCategory category : CardCategory.values())
        {
            addProgressRow(
                categoryStatisticsPanel,
                readable(category),
                progress.getProgress(category),
                ColorScheme.BRAND_ORANGE);
        }

        if (progress.hasUnknownCardIds())
        {
            JLabel warning = new JLabel(
                "Unknown save IDs: "
                    + progress.getUnknownOwnedCardIds().size());
            warning.setForeground(new Color(220, 165, 80));
            warning.setBorder(BorderFactory.createEmptyBorder(5, 4, 0, 4));
            categoryStatisticsPanel.add(warning);
        }

        refreshStatisticsPanel(rarityStatisticsPanel);
        refreshStatisticsPanel(categoryStatisticsPanel);
    }

    private void updateLibraryStatistics()
    {
        libraryStatisticsPanel.removeAll();
        int officialExamine = 0;
        int wikiArtwork = 0;
        int itemSprites = 0;
        int builtInFallbacks = 0;
        int missingNpc = 0;
        for (CardDefinition card : catalogue.getCards())
        {
            if (CatalogueTextQuality.isVerifiedExamine(card))
            {
                officialExamine++;
            }
            ArtworkSource source = artworkProvider.getArtworkSource(card);
            switch (source)
            {
                case OSRS_WIKI:
                    wikiArtwork++;
                    break;
                case ITEM_SPRITE:
                    itemSprites++;
                    break;
                case BUILT_IN_FALLBACK:
                    builtInFallbacks++;
                    break;
                case NONE:
                case OTHER:
                default:
                    if (card.getCardType() == CardType.NPC)
                    {
                        missingNpc++;
                    }
                    break;
            }
        }
        int total = catalogue.getCards().size();
        addActivityRow(
            libraryStatisticsPanel,
            "Official examine",
            officialExamine + " / " + total,
            officialExamine == total ? OWNED_COLOR : ColorScheme.BRAND_ORANGE);
        addActivityRow(
            libraryStatisticsPanel,
            "Pending examine",
            Integer.toString(total - officialExamine),
            total == officialExamine ? OWNED_COLOR : new Color(220, 165, 80));
        addActivityRow(
            libraryStatisticsPanel,
            "OSRS Wiki artwork",
            wikiArtwork + " / " + total,
            wikiArtwork == total ? OWNED_COLOR : ColorScheme.BRAND_ORANGE);
        int mappedArtwork = artworkProvider.mappedArtworkCount();
        if (mappedArtwork >= 0)
        {
            addActivityRow(
                libraryStatisticsPanel,
                "Wiki mappings",
                mappedArtwork + " / " + total,
                mappedArtwork == total
                    ? OWNED_COLOR
                    : ColorScheme.BRAND_ORANGE);
        }
        addActivityRow(
            libraryStatisticsPanel,
            "RuneLite item fallback",
            Integer.toString(itemSprites),
            OWNED_COLOR);
        addActivityRow(
            libraryStatisticsPanel,
            "Built-in NPC fallback",
            Integer.toString(builtInFallbacks),
            builtInFallbacks == 0 ? OWNED_COLOR : ColorScheme.BRAND_ORANGE);
        addActivityRow(
            libraryStatisticsPanel,
            "Missing NPC art",
            Integer.toString(missingNpc),
            missingNpc == 0 ? OWNED_COLOR : new Color(220, 165, 80));
    }

    private void updateActivityStatistics(
        CollectionActivitySnapshot activity)
    {
        activityStatisticsPanel.removeAll();
        if (!activity.isAvailable())
        {
            addActivityRow(
                activityStatisticsPanel,
                "Activity unavailable",
                activity.getWarning(),
                new Color(220, 165, 80));
            return;
        }

        String mostOpened = activity.getMostOpenedPackId()
            .map(packId -> PackNames.displayName(packId) + "  ×"
                + activity.getPackCounts().get(packId))
            .orElse("No recorded packs");
        addActivityRow(
            activityStatisticsPanel,
            "Most opened pack",
            mostOpened,
            ColorScheme.BRAND_ORANGE);

        String mostDuplicated = activity.getMostDuplicatedCardId()
            .map(cardId -> cardDisplayName(cardId) + "  ×"
                + activity.getDuplicateCount(cardId)
                + "  |  " + activity.getDuplicateCardCount()
                + " total  |  "
                + formatNumber(activity.getDuplicateShards())
                + " shards")
            .orElse("No recorded duplicates");
        addActivityRow(
            activityStatisticsPanel,
            "Most duplicated card",
            mostDuplicated,
            new Color(210, 105, 90));

        addActivityRow(
            activityStatisticsPanel,
            "New-card streak",
            "Current " + activity.getCurrentNewCardStreak()
                + "  |  Longest " + activity.getLongestNewCardStreak(),
            OWNED_COLOR);

        addActivityRow(
            activityStatisticsPanel,
            "Recorded goal completions",
            Integer.toString(activity.getAchievementCompletionCount()),
            new Color(221, 177, 74));
        List<AchievementCompletionRecord> recentGoals =
            activity.getRecentAchievementCompletions(2);
        for (AchievementCompletionRecord completion : recentGoals)
        {
            AchievementDefinition definition = achievementRegistry.require(
                completion.getAchievementId());
            addActivityRow(
                activityStatisticsPanel,
                definition.getDisplayName(),
                "Goal complete  |  "
                    + ACTIVITY_TIME.format(completion.getCompletedAt()),
                new Color(221, 177, 74));
        }

        int recentLimit = recentGoals.isEmpty()
            ? (activity.getWarning().isEmpty() ? 2 : 1)
            : 1;
        List<CardUnlockRecord> recent = activity.getRecentUnlocks(recentLimit);
        for (CardUnlockRecord unlock : recent)
        {
            addActivityRow(
                activityStatisticsPanel,
                cardDisplayName(unlock.getCardId()),
                unlockSource(unlock.getSource()) + "  |  "
                    + ACTIVITY_TIME.format(unlock.getOccurredAt()),
                rarityColor(cardRarity(unlock.getCardId())));
        }
        if (!activity.getWarning().isEmpty())
        {
            addActivityRow(
                activityStatisticsPanel,
                "History notice",
                activity.getWarning(),
                new Color(220, 165, 80));
        }
    }

    private void addActivityRow(
        JPanel panel,
        String heading,
        String value,
        Color accent)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setOpaque(true);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel(heading);
        title.setForeground(accent);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 10f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(title);
        JLabel detail = new JLabel("<html>" + escape(value) + "</html>");
        detail.setForeground(MUTED_TEXT);
        detail.setFont(detail.getFont().deriveFont(9f));
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(detail);
        panel.add(row);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
    }

    private String cardDisplayName(String cardId)
    {
        return catalogue.findCard(cardId)
            .map(CardDefinition::getDisplayName)
            .orElseGet(() -> catalogue.findHistoricalCard(cardId)
                .map(historical -> historical.getDisplayName())
                .orElse(cardId));
    }

    private Rarity cardRarity(String cardId)
    {
        return catalogue.findCard(cardId)
            .map(CardDefinition::getRarity)
            .orElseGet(() -> catalogue.findHistoricalCard(cardId)
                .map(historical -> historical.getRarity())
                .orElse(Rarity.COMMON));
    }

    private static String unlockSource(CardUnlockSource source)
    {
        switch (source)
        {
            case STARTER:
                return "Starter deck";
            case NEXUS:
                return "Nexus";
            case PACK:
            default:
                return "Booster pack";
        }
    }

    private void configureStatisticsPanel(JPanel panel)
    {
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(5, 2, 2, 2));
    }

    private void addProgressRow(
        JPanel panel,
        String label,
        CollectionProgress progress,
        Color accent)
    {
        JPanel row = new SidebarRowPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setOpaque(true);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextArea name = compactWrappedText(
            label,
            new Font(Font.DIALOG, Font.BOLD, 10),
            accent);
        row.add(name);
        JLabel value = new JLabel(
            progress.formatRatio() + "  (" + progress.formatPercent() + ")");
        value.setFont(new Font(Font.DIALOG, Font.PLAIN, 10));
        value.setForeground(Color.WHITE);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(value);
        panel.add(row);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
    }

    private void addAchievementRow(
        JPanel panel,
        AchievementProgress achievement)
    {
        AchievementDefinition definition = achievement.getDefinition();
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setOpaque(true);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(
                0,
                4,
                0,
                0,
                ColorScheme.BRAND_ORANGE),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(definition.getDisplayName());
        name.setForeground(ColorScheme.BRAND_ORANGE);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 10f));
        text.add(name);
        JLabel progress = new JLabel(
            achievement.getCurrentCount() + " / "
                + achievement.getRequiredCount());
        progress.getAccessibleContext().setAccessibleDescription(
            achievement.formatProgress());
        progress.setForeground(MUTED_TEXT);
        progress.setFont(progress.getFont().deriveFont(9f));
        text.add(progress);
        row.add(text, BorderLayout.CENTER);

        JLabel remaining = new JLabel(
            Integer.toString(achievement.getRemainingCount()));
        remaining.getAccessibleContext().setAccessibleDescription(
            "Cards remaining for this goal.");
        remaining.setForeground(Color.WHITE);
        row.add(remaining, BorderLayout.EAST);
        panel.add(row);
        panel.add(Box.createRigidArea(new Dimension(0, 4)));
    }

    private void refreshStatisticsPanel(JPanel panel)
    {
        panel.revalidate();
        panel.repaint();
    }

    private void initialiseNexusTotals()
    {
        for (Rarity rarity : Rarity.values())
        {
            int total = 0;
            for (CardDefinition card : catalogue.getCards(rarity))
            {
                if (!ProgressionRewardCardPolicy.isTrackOnlyReward(
                    card.getCardId()))
                {
                    total++;
                }
            }
            nexusEligibleTotals.put(rarity, total);
            cachedNexusOwnedCounts.put(rarity, 0);
        }
    }

    private void refreshCollectionShapeCaches(CollectionState state)
    {
        if (state.getCollectionId().equals(cachedCollectionShapeId)
            && state.getOwnedCardIds() == cachedOwnedCardIdsIdentity)
        {
            return;
        }

        cachedCollectionShapeId = state.getCollectionId();
        cachedOwnedCardIdsIdentity = state.getOwnedCardIds();
        cachedUniqueOwnedCardCount =
            ProgressionMilestonePolicy.uniqueOwnedCardCount(catalogue, state);

        for (Rarity rarity : Rarity.values())
        {
            int owned = 0;
            for (CardDefinition card : catalogue.getCards(rarity))
            {
                if (!ProgressionRewardCardPolicy.isTrackOnlyReward(
                        card.getCardId())
                    && state.getOwnedCardIds().contains(card.getCardId()))
                {
                    owned++;
                }
            }
            cachedNexusOwnedCounts.put(rarity, owned);
        }
    }

    private void updateNexus(
        CollectionState state,
        CollectionProgressSnapshot collectionProgressSnapshot)
    {
        refreshCollectionShapeCaches(state);
        for (Rarity rarity : Rarity.values())
        {
            int total = nexusEligibleTotals.getOrDefault(rarity, 0);
            int owned = cachedNexusOwnedCounts.getOrDefault(rarity, 0);
            JProgressBar progress = nexusProgressBars.get(rarity);
            JButton button = nexusButtons.get(rarity);
            if (progress == null || button == null)
            {
                continue;
            }
            progress.setMaximum(Math.max(1, total));
            progress.setValue(owned);
            progress.setString(owned + " / " + total);
            boolean complete = total == 0 || owned >= total;
            long cost = NexusExchangeCosts.forRarity(rarity);
            button.setText(
                "<html><center>Unlock<br>" + formatNumber(cost)
                    + "</center></html>");
            button.setEnabled(
                !complete
                    && state.getShards() >= cost
                    && !state.getPendingPackReveal().isPresent()
                    && !packActionHandler.isNexusExchangeBlocked());
        }
    }

    private void updateRecentPacks(CollectionActivitySnapshot activity)
    {
        if (activity == renderedRecentPackActivity)
        {
            return;
        }
        renderedRecentPackActivity = activity;
        recentPackActivityPanel.removeAll();
        List<PackActivityRecord> packs = activity.getPacks();
        if (!activity.isAvailable())
        {
            addActivityRow(
                recentPackActivityPanel,
                "History unavailable",
                activity.getWarning(),
                new Color(220, 165, 80));
        }
        else if (packs.isEmpty())
        {
            JLabel empty = new JLabel("No packs purchased yet.");
            empty.setForeground(MUTED_TEXT);
            empty.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            recentPackActivityPanel.add(empty);
        }
        else
        {
            int displayed = 0;
            for (int index = packs.size() - 1;
                 index >= 0 && displayed < 3;
                 index--)
            {
                PackActivityRecord pack = packs.get(index);
                String status = pack.getNewCardCount() + " new  |  "
                    + pack.getDuplicateCount() + " duplicate"
                    + (pack.getDuplicateCount() == 1 ? "" : "s")
                    + "  |  " + pack.getRevealedCount() + "/"
                    + pack.getCardResults().size() + " revealed";
                addActivityRow(
                    recentPackActivityPanel,
                    PackNames.displayName(pack.getPackId()),
                    ACTIVITY_TIME.format(pack.getPurchasedAt())
                        + "  |  " + status,
                    pack.isFullyRevealed()
                        ? OWNED_COLOR
                        : ColorScheme.BRAND_ORANGE);
                displayed++;
            }
        }
        recentPackActivityPanel.revalidate();
        recentPackActivityPanel.repaint();
    }

    private void updatePackView(
        CollectionState state,
        CollectionActivitySnapshot activity)
    {
        updateRecentPacks(activity);
        boolean starterPending = StarterRewardState.hasPendingStarterPack(state);
        starterPackTileContainer.setVisible(starterPending);
        boolean hasVisibleMilestoneReward = false;
        for (Map.Entry<String, JPanel> reward : milestoneRewardTiles.entrySet())
        {
            boolean visible = !ProgressionMilestonePolicy.hasClaimed(
                state,
                reward.getKey());
            reward.getValue().setVisible(visible);
            hasVisibleMilestoneReward |= visible;
        }
        milestoneRewardSection.setVisible(hasVisibleMilestoneReward);
        long pointProgress = Math.min(state.getPoints(), StandardPackService.PRICE);
        packPointProgress.setValue((int) pointProgress);
        packPointProgress.setString(
            formatNumber(state.getPoints()) + " / "
                + formatNumber(StandardPackService.PRICE) + " points");
        packBalanceLabel.setText(formatNumber(state.getPoints()) + " points");

        if (state.getPendingPackReveal().isPresent())
        {
            PendingPackReveal reveal = state.getPendingPackReveal().orElseThrow();
            if (!reveal.getOpeningId().equals(displayedOpeningId))
            {
                displayedOpeningId = reveal.getOpeningId();
                displayedPackCard = null;
                displayedRevealNumber = 0;
            }
            displayedRevealNumber = reveal.getRevealedCount();
            int remaining = reveal.getCardResults().size()
                - reveal.getRevealedCount();
            revealCardName.setText(
                PackNames.displayName(reveal.getPackId()).toUpperCase(Locale.ROOT));
            revealCardMeta.setText(
                "OPEN IN GAME VIEW  |  " + reveal.getRevealedCount()
                    + " OF " + reveal.getCardResults().size() + " REVEALED");
            revealCardOutcome.setText(
                "<html><center>Left click any of the " + remaining
                    + " unrevealed card" + (remaining == 1 ? "" : "s")
                    + " in the game view, or press Space to reveal all."
                    + "</center></html>");
            revealCardOutcome.setForeground(MUTED_TEXT);
            setPackStatus(
                "Reveal the remaining " + remaining + " card"
                    + (remaining == 1 ? "" : "s")
                    + " in any order, with no reveal delay.");
            setAllPackButtonsEnabled(false);
            updatePackSlots(reveal.getRevealedPositions());
        }
        else
        {
            refreshCollectionShapeCaches(state);
            int uniqueCards = cachedUniqueOwnedCardCount;
            boolean purchasesAllowed = !starterPending;
            redeemStarterPackButton.setEnabled(starterPending);
            purchasePackButton.setEnabled(
                purchasesAllowed && state.getPoints() >= StandardPackService.PRICE);
            purchaseUncommonPlusPackButton.setEnabled(
                purchasesAllowed
                    && uniqueCards >= ProgressionMilestonePolicy.UNCOMMON_PLUS_PACK
                    && state.getPoints() >= StandardPackService.UNCOMMON_PLUS_PRICE);
            purchaseExplorerPackButton.setEnabled(
                purchasesAllowed
                    && uniqueCards >= ProgressionMilestonePolicy.EXPLORER_PACK
                    && state.getPoints() >= StandardPackService.EXPLORER_PRICE);
            purchaseRareHunterPackButton.setEnabled(
                purchasesAllowed
                    && uniqueCards >= ProgressionMilestonePolicy.RARE_PLUS_PACK
                    && state.getPoints() >= StandardPackService.RARE_PLUS_PRICE);
            purchaseAdventurePackButton.setEnabled(
                purchasesAllowed
                    && uniqueCards >= ProgressionMilestonePolicy.ADVENTURE_PACK
                    && state.getPoints() >= StandardPackService.ADVENTURE_PRICE);
            purchaseNexusCacheButton.setEnabled(
                purchasesAllowed
                    && uniqueCards >= ProgressionMilestonePolicy.NEXUS_CACHE
                    && state.getPoints() >= StandardPackService.NEXUS_CACHE_PRICE);
            purchaseCollectorPackButton.setEnabled(
                purchasesAllowed
                    && uniqueCards >= ProgressionMilestonePolicy.COLLECTOR_PACK
                    && state.getPoints() >= StandardPackService.COLLECTOR_PRICE);

            redeemInitiateFoilPackButton.setEnabled(
                milestoneRewardAvailable(
                    state,
                    uniqueCards,
                    ProgressionMilestonePolicy.INITIATE_FOIL_PACK,
                    ProgressionMilestonePolicy.INITIATE_FOIL_MARKER,
                    purchasesAllowed));
            redeemHeroPackButton.setEnabled(
                milestoneRewardAvailable(
                    state,
                    uniqueCards,
                    ProgressionMilestonePolicy.HERO_PACK,
                    ProgressionMilestonePolicy.HERO_PACK_MARKER,
                    purchasesAllowed));
            redeemNoblePackButton.setEnabled(
                milestoneRewardAvailable(
                    state,
                    uniqueCards,
                    ProgressionMilestonePolicy.NOBLE_PACK,
                    ProgressionMilestonePolicy.NOBLE_PACK_MARKER,
                    purchasesAllowed));
            redeemLegendPackButton.setEnabled(
                milestoneRewardAvailable(
                    state,
                    uniqueCards,
                    ProgressionMilestonePolicy.LEGEND_PACK,
                    ProgressionMilestonePolicy.LEGEND_PACK_MARKER,
                    purchasesAllowed));
            redeemMythicalPackButton.setEnabled(
                milestoneRewardAvailable(
                    state,
                    uniqueCards,
                    ProgressionMilestonePolicy.MYTHICAL_PACK,
                    ProgressionMilestonePolicy.MYTHICAL_PACK_MARKER,
                    purchasesAllowed));
            redeemGodsPackButton.setEnabled(
                milestoneRewardAvailable(
                    state,
                    uniqueCards,
                    ProgressionMilestonePolicy.GODS_PACK,
                    ProgressionMilestonePolicy.GODS_PACK_MARKER,
                    purchasesAllowed));

            long foilTestRemaining = Math.max(
                0,
                StandardPackService.FOIL_TEST_PRICE - state.getPoints());
            long premiumFoilTestRemaining = Math.max(
                0,
                StandardPackService.PREMIUM_FOIL_TEST_PRICE - state.getPoints());
            long mappedFoilTestRemaining = Math.max(
                0,
                StandardPackService.MAPPED_FOIL_TEST_PRICE - state.getPoints());
            purchaseFoilTestPackButton.setEnabled(
                packActionHandler.isTestingMode()
                    && purchasesAllowed
                    && foilTestRemaining == 0);
            purchasePremiumFoilTestPackButton.setEnabled(
                packActionHandler.isTestingMode()
                    && purchasesAllowed
                    && premiumFoilTestRemaining == 0);
            boolean mappedFoilTestEnabled =
                packActionHandler.isTestingMode()
                    && purchasesAllowed
                    && mappedFoilTestRemaining == 0;
            purchaseTierFoilTestPackButton.setEnabled(mappedFoilTestEnabled);
            purchaseArmourFoilTestPackButton.setEnabled(mappedFoilTestEnabled);
            purchaseBossFoilTestPackButton.setEnabled(mappedFoilTestEnabled);
            purchaseIngredientFoilTestPackButton.setEnabled(mappedFoilTestEnabled);
            purchaseSignatureFoilTestPackButton.setEnabled(mappedFoilTestEnabled);
            purchaseNpcRelationshipFoilTestPackButton.setEnabled(mappedFoilTestEnabled);

            // Legacy buttons are retained only for old handler compatibility.
            purchaseNoncombatNpcPackButton.setEnabled(false);
            purchaseAttackableNpcPackButton.setEnabled(false);

            if (starterPending)
            {
                setPackStatus(
                    "Redeem the free one-time starter pack before other packs.");
            }
            else if (state.getPoints() < StandardPackService.PRICE)
            {
                setPackStatus(
                    "Earn " + formatNumber(
                        StandardPackService.PRICE - state.getPoints())
                        + " more points for a Standard Pack.");
            }
            else
            {
                setPackStatus(
                    formatNumber(uniqueCards)
                        + " unique cards — milestone eligibility updates immediately.");
            }
            updatePackSlots(java.util.Collections.emptySet());
            if (displayedOpeningId == null)
            {
                displayedPackCard = null;
                displayedRevealNumber = 0;
            }
        }
        if (!state.getPendingPackReveal().isPresent())
        {
            updateRevealCard();
        }
    }

    private boolean milestoneRewardAvailable(
        CollectionState state,
        int uniqueCards,
        int requiredCards,
        String marker,
        boolean purchasesAllowed)
    {
        return purchasesAllowed
            && uniqueCards >= requiredCards
            && !state.getClaimedPointSourceIds().contains(marker);
    }

    private void setAllPackButtonsEnabled(boolean enabled)
    {
        redeemStarterPackButton.setEnabled(enabled);
        purchasePackButton.setEnabled(enabled);
        purchaseUncommonPlusPackButton.setEnabled(enabled);
        purchaseExplorerPackButton.setEnabled(enabled);
        purchaseRareHunterPackButton.setEnabled(enabled);
        purchaseAdventurePackButton.setEnabled(enabled);
        purchaseNexusCacheButton.setEnabled(enabled);
        purchaseCollectorPackButton.setEnabled(enabled);
        redeemInitiateFoilPackButton.setEnabled(enabled);
        redeemHeroPackButton.setEnabled(enabled);
        redeemNoblePackButton.setEnabled(enabled);
        redeemLegendPackButton.setEnabled(enabled);
        redeemMythicalPackButton.setEnabled(enabled);
        redeemGodsPackButton.setEnabled(enabled);
        purchaseNoncombatNpcPackButton.setEnabled(enabled);
        purchaseAttackableNpcPackButton.setEnabled(enabled);
        purchaseFoilTestPackButton.setEnabled(enabled);
        purchasePremiumFoilTestPackButton.setEnabled(enabled);
        purchaseTierFoilTestPackButton.setEnabled(enabled);
        purchaseArmourFoilTestPackButton.setEnabled(enabled);
        purchaseBossFoilTestPackButton.setEnabled(enabled);
        purchaseIngredientFoilTestPackButton.setEnabled(enabled);
        purchaseSignatureFoilTestPackButton.setEnabled(enabled);
        purchaseNpcRelationshipFoilTestPackButton.setEnabled(enabled);
    }

    private void updateRevealCard()
    {
        if (displayedPackCard == null)
        {
            revealCardName.setText("PACK REVEAL");
            revealCardMeta.setText("ITEM AND NPC PACKS");
            revealCardOutcome.setText(
                "<html><center>Choose a pack in the Store to begin.</center></html>");
            revealCardOutcome.setForeground(MUTED_TEXT);
            revealCardPanel.setBorder(
                BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(
                        ColorScheme.BRAND_ORANGE, 2),
                    BorderFactory.createEmptyBorder(
                        18, 12, 18, 12)));
            return;
        }

        CardDefinition card =
            catalogue.requireCard(displayedPackCard.getCardId());
        revealCardName.setText(card.getDisplayName());
        revealCardMeta.setText(
            readable(card.getRarity()) + "  |  "
                + readable(card.getCardType())
                + (displayedPackCard.isFoil() ? "  |  FOIL" : ""));
        if (displayedPackCard.isDuplicate())
        {
            String duplicate = "DUPLICATE  |  +"
                + displayedPackCard.getShardsAwarded() + " SHARDS";
            revealCardOutcome.setText(displayedPackCard.isFoil()
                ? foilRevealOutcome(card, duplicate)
                : duplicate);
            revealCardOutcome.setForeground(ColorScheme.BRAND_ORANGE);
        }
        else
        {
            revealCardOutcome.setText(displayedPackCard.isFoil()
                ? foilRevealOutcome(card, "FOIL CARD UNLOCKED")
                : "NEW CARD UNLOCKED");
            revealCardOutcome.setForeground(OWNED_COLOR);
        }
        revealCardPanel.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                    rarityColor(card.getRarity()), 2),
                BorderFactory.createEmptyBorder(
                    18, 12, 18, 12)));
    }

    private String foilRevealOutcome(CardDefinition card, String firstLine)
    {
        int rewards = foilRewardRegistry.getTargetCardIdsForSource(
            card.getCardId()).size();
        String secondLine = rewards == 0
            ? "NO CURATED SECONDARY UNLOCK YET"
            : "UNLOCKS USE OF " + rewards
                + (rewards == 1 ? " RELATED CARD" : " RELATED CARDS");
        return "<html><center>" + firstLine + "<br>"
            + secondLine + "</center></html>";
    }

    private void updatePackSlots(Set<Integer> revealedPositions)
    {
        for (int index = 0; index < packSlotLabels.length; index++)
        {
            JLabel slot = packSlotLabels[index];
            if (slot == null)
            {
                continue;
            }
            boolean revealed = revealedPositions.contains(index);
            slot.setBackground(
                revealed
                    ? ColorScheme.BRAND_ORANGE
                    : ColorScheme.DARKER_GRAY_COLOR);
            slot.setForeground(revealed ? Color.BLACK : MUTED_TEXT);
        }
    }

    private JPanel metricCard(String heading, JLabel value)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setOpaque(true);
        card.setBorder(
            BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel label = new JLabel(heading);
        label.setForeground(MUTED_TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 9f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 17f));
        value.setForeground(ColorScheme.TEXT_COLOR);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(label);
        card.add(value);
        return card;
    }

    private JPanel statusCard(String heading, JLabel value)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setOpaque(true);
        card.setBorder(
            BorderFactory.createEmptyBorder(7, 7, 7, 7));
        JLabel label = new JLabel(heading);
        label.setForeground(MUTED_TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 9f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(label);
        value.setHorizontalAlignment(SwingConstants.LEFT);
        value.setForeground(ColorScheme.TEXT_COLOR);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(value);
        return card;
    }

    private JLabel sectionTitle(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setForeground(new Color(232, 232, 232));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void configureProgressBar(JProgressBar progress)
    {
        progress.setStringPainted(true);
        progress.setMaximumSize(
            new Dimension(Integer.MAX_VALUE, 22));
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void addRow(JPanel grid, String label, JLabel value)
    {
        JLabel key = new JLabel(label);
        key.setForeground(MUTED_TEXT);
        grid.add(key);
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        value.setForeground(ColorScheme.TEXT_COLOR);
        grid.add(value);
    }

    private String categoryText(CardDefinition card)
    {
        return card.getCategories().stream()
            .map(this::readable)
            .sorted()
            .collect(Collectors.joining(", "));
    }

    private String examinePreview(CardDefinition card)
    {
        String text = CatalogueTextQuality.cardDisplayText(card)
            .replace('\n', ' ')
            .replaceAll("\\s+", " ")
            .trim();
        if (text.length() <= 48)
        {
            return text;
        }
        return text.substring(0, 45).trim() + "...";
    }

    private String permissionText(CardDefinition card)
    {
        if (card.getCardType()
            == com.cardrestricted.catalog.CardType.ITEM)
        {
            return "All functional item use";
        }
        if (card.getCardType()
            == com.cardrestricted.catalog.CardType.NPC)
        {
            return "Attack";
        }
        return Stream.concat(
                card.getPermissions().stream(),
                card.getAdditionalPermissionGrants().stream()
                    .flatMap(grant -> grant.getPermissions().stream()))
            .distinct()
            .map(this::permissionName)
            .sorted()
            .collect(Collectors.joining(", "));
    }

    private String permissionName(ActionType action)
    {
        String name = action.name();
        int separator = name.indexOf('_');
        if (separator >= 0)
        {
            name = name.substring(separator + 1);
        }
        return titleCase(name);
    }

    private String readable(Enum<?> value)
    {
        return titleCase(value.name());
    }

    private String titleCase(String value)
    {
        String lower = value.toLowerCase(Locale.ROOT)
            .replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0))
            + lower.substring(1);
    }

    private Color rarityColor(Rarity rarity)
    {
        switch (rarity)
        {
            case COMMON:
                return COMMON_COLOR;
            case UNCOMMON:
                return new Color(120, 180, 110);
            case RARE:
                return new Color(88, 150, 215);
            case EPIC:
                return new Color(165, 105, 205);
            case LEGENDARY:
                return new Color(235, 145, 55);
            case MYTHIC:
                return new Color(215, 75, 85);
            case GODLY:
                return new Color(245, 210, 90);
            default:
                return COMMON_COLOR;
        }
    }

    private String formatNumber(long value)
    {
        return String.format(Locale.UK, "%,d", value);
    }

    private void clearDisplayedPack()
    {
        displayedOpeningId = null;
        displayedPackCard = null;
        displayedRevealNumber = 0;
    }

    private String escape(String value)
    {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private static final class QuestScrollAnchor
    {
        private final String questKey;
        private final int viewportOffset;
        private final int fallbackValue;

        private QuestScrollAnchor(
            String questKey,
            int viewportOffset,
            int fallbackValue)
        {
            this.questKey = questKey;
            this.viewportOffset = viewportOffset;
            this.fallbackValue = fallbackValue;
        }
    }

    private static final class SidebarRowPanel extends JPanel
    {
        private static final long serialVersionUID = 1L;

        private SidebarRowPanel()
        {
            super();
        }

        private SidebarRowPanel(java.awt.LayoutManager layout)
        {
            super(layout);
        }

        @Override
        public void setBounds(int x, int y, int width, int height)
        {
            boolean widthChanged = width != getWidth();
            super.setBounds(x, y, width, height);
            if (widthChanged && getLayout() instanceof java.awt.LayoutManager2)
            {
                ((java.awt.LayoutManager2) getLayout()).invalidateLayout(this);
            }
        }

        @Override
        public Dimension getPreferredSize()
        {
            if (getLayout() instanceof java.awt.LayoutManager2)
            {
                ((java.awt.LayoutManager2) getLayout()).invalidateLayout(this);
            }
            return super.getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize()
        {
            Dimension preferred = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, preferred.height);
        }

        @Override
        public Dimension getMinimumSize()
        {
            Dimension preferred = getPreferredSize();
            return new Dimension(0, preferred.height);
        }
    }

    /** Width-tracking view used by every tab-level scroll pane. */
    private static final class SidebarTabPanel extends JPanel
        implements Scrollable
    {
        private static final long serialVersionUID = 1L;

        private SidebarTabPanel()
        {
            super();
        }

        private SidebarTabPanel(java.awt.LayoutManager layout)
        {
            super(layout);
        }

        @Override
        public void setBounds(int x, int y, int width, int height)
        {
            boolean widthChanged = width != getWidth();
            super.setBounds(x, y, width, height);
            if (widthChanged && getLayout() instanceof java.awt.LayoutManager2)
            {
                ((java.awt.LayoutManager2) getLayout()).invalidateLayout(this);
            }
        }

        @Override
        public Dimension getPreferredSize()
        {
            if (getLayout() instanceof java.awt.LayoutManager2)
            {
                ((java.awt.LayoutManager2) getLayout()).invalidateLayout(this);
            }
            return super.getPreferredSize();
        }

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(
            Rectangle visibleRect,
            int orientation,
            int direction)
        {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(
            Rectangle visibleRect,
            int orientation,
            int direction)
        {
            return Math.max(18, visibleRect.height - 18);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }

        @Override
        public Dimension getMinimumSize()
        {
            Dimension preferred = getPreferredSize();
            return new Dimension(0, preferred.height);
        }
    }

    private static final class SidebarWrappedTextArea extends JTextArea
    {
        private static final long serialVersionUID = 1L;

        private SidebarWrappedTextArea(String value)
        {
            super(value);
            setColumns(1);
        }

        @Override
        public Dimension getPreferredSize()
        {
            int width = availableWidth();
            Insets insets = getInsets();
            int contentWidth = Math.max(
                1,
                width - insets.left - insets.right);
            FontMetrics metrics = getFontMetrics(getFont());
            int lines = wrappedLineCount(getText(), metrics, contentWidth);
            int height = insets.top + insets.bottom
                + Math.max(metrics.getHeight(), lines * metrics.getHeight())
                + 1;
            return new Dimension(width, height);
        }

        @Override
        public Dimension getMaximumSize()
        {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public Dimension getMinimumSize()
        {
            return new Dimension(0, getPreferredSize().height);
        }

        private int availableWidth()
        {
            Container parent = getParent();
            int resolved = availableContentWidth(parent, 0);
            if (resolved > 0)
            {
                return Math.max(48, resolved);
            }
            if (getWidth() > 0)
            {
                return Math.max(48, getWidth());
            }
            // A stable first-pass width prevents login-time rows being laid
            // out as a 48px column before the viewport has received bounds.
            return 88;
        }

        private static int availableContentWidth(
            Container container,
            int depth)
        {
            if (container == null || depth > 12)
            {
                return 0;
            }
            Insets insets = container.getInsets();
            if (container.getWidth() > 0)
            {
                return Math.max(
                    0,
                    container.getWidth() - insets.left - insets.right);
            }
            Container parent = container.getParent();
            int parentWidth = availableContentWidth(parent, depth + 1);
            if (parentWidth <= 0 || parent == null)
            {
                return 0;
            }
            java.awt.LayoutManager layout = parent.getLayout();
            if (layout instanceof BorderLayout)
            {
                BorderLayout border = (BorderLayout) layout;
                Object constraint = border.getConstraints(container);
                if (BorderLayout.CENTER.equals(constraint))
                {
                    Component west = border.getLayoutComponent(
                        parent,
                        BorderLayout.WEST);
                    Component east = border.getLayoutComponent(
                        parent,
                        BorderLayout.EAST);
                    int occupied = visiblePreferredWidth(west)
                        + visiblePreferredWidth(east);
                    int visibleSides = (west != null && west.isVisible() ? 1 : 0)
                        + (east != null && east.isVisible() ? 1 : 0);
                    occupied += visibleSides * border.getHgap();
                    parentWidth = Math.max(0, parentWidth - occupied);
                }
            }
            return Math.max(
                0,
                parentWidth - insets.left - insets.right);
        }

        private static int visiblePreferredWidth(Component component)
        {
            return component == null || !component.isVisible()
                ? 0
                : component.getPreferredSize().width;
        }

        private static int wrappedLineCount(
            String value,
            FontMetrics metrics,
            int maximumWidth)
        {
            if (value == null || value.isEmpty())
            {
                return 1;
            }
            int lines = 0;
            String[] paragraphs = value.replace("\r", "")
                .split("\n", -1);
            for (String paragraph : paragraphs)
            {
                String trimmed = paragraph.trim();
                if (trimmed.isEmpty())
                {
                    lines++;
                    continue;
                }
                int lineWidth = 0;
                for (String word : trimmed.split("\\s+"))
                {
                    int wordWidth = metrics.stringWidth(word);
                    int gap = lineWidth == 0 ? 0 : metrics.charWidth(' ');
                    if (lineWidth > 0
                        && lineWidth + gap + wordWidth <= maximumWidth)
                    {
                        lineWidth += gap + wordWidth;
                        continue;
                    }
                    if (lineWidth > 0)
                    {
                        lines++;
                        lineWidth = 0;
                    }
                    if (wordWidth <= maximumWidth)
                    {
                        lineWidth = wordWidth;
                        continue;
                    }
                    for (int index = 0; index < word.length(); index++)
                    {
                        int characterWidth = Math.max(
                            1,
                            metrics.charWidth(word.charAt(index)));
                        if (lineWidth > 0
                            && lineWidth + characterWidth > maximumWidth)
                        {
                            lines++;
                            lineWidth = 0;
                        }
                        lineWidth += characterWidth;
                    }
                }
                if (lineWidth > 0)
                {
                    lines++;
                }
            }
            return Math.max(1, lines);
        }
    }

    private static final class SidebarScrollBarUI extends BasicScrollBarUI
    {
        private static final Color TRACK = new Color(30, 31, 34);
        private static final Color THUMB = new Color(94, 82, 62);
        private static final Color THUMB_HOVER = new Color(139, 108, 61);

        @Override
        protected void configureScrollBarColors()
        {
            trackColor = TRACK;
            thumbColor = THUMB;
            thumbHighlightColor = THUMB_HOVER;
            thumbLightShadowColor = THUMB;
            thumbDarkShadowColor = THUMB;
        }

        @Override
        protected Dimension getMinimumThumbSize()
        {
            return new Dimension(7, 28);
        }

        @Override
        protected JButton createDecreaseButton(int orientation)
        {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation)
        {
            return zeroButton();
        }

        private JButton zeroButton()
        {
            JButton button = new JButton();
            Dimension zero = new Dimension(0, 0);
            button.setMinimumSize(zero);
            button.setPreferredSize(zero);
            button.setMaximumSize(zero);
            return button;
        }

        @Override
        protected void paintTrack(
            Graphics graphics,
            JComponent component,
            Rectangle bounds)
        {
            graphics.setColor(TRACK);
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        protected void paintThumb(
            Graphics graphics,
            JComponent component,
            Rectangle bounds)
        {
            if (!component.isEnabled() || bounds.isEmpty())
            {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(isThumbRollover() ? THUMB_HOVER : THUMB);
                int width = Math.max(4, bounds.width - 2);
                int height = Math.max(8, bounds.height - 4);
                g.fillRoundRect(
                    bounds.x + 1,
                    bounds.y + 2,
                    width,
                    height,
                    width,
                    width);
            }
            finally
            {
                g.dispose();
            }
        }
    }

    private static final class SidebarTabbedPaneUI extends BasicTabbedPaneUI
    {
        @Override
        protected void installDefaults()
        {
            super.installDefaults();
            tabInsets = new Insets(1, 0, 1, 0);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabAreaInsets = new Insets(3, 2, 3, 2);
            contentBorderInsets = new Insets(4, 0, 0, 0);
        }

        @Override
        protected boolean shouldRotateTabRuns(int tabPlacement)
        {
            return false;
        }

        @Override
        protected void paintTabBackground(
            Graphics graphics,
            int tabPlacement,
            int tabIndex,
            int x,
            int y,
            int width,
            int height,
            boolean selected)
        {
            // The custom tab header paints the complete tab surface.
        }

        @Override
        protected void paintTabBorder(
            Graphics graphics,
            int tabPlacement,
            int tabIndex,
            int x,
            int y,
            int width,
            int height,
            boolean selected)
        {
            // Avoid the default metal borders behind the rounded tab headers.
        }

        @Override
        protected void paintFocusIndicator(
            Graphics graphics,
            int tabPlacement,
            java.awt.Rectangle[] rectangles,
            int tabIndex,
            java.awt.Rectangle iconRect,
            java.awt.Rectangle textRect,
            boolean selected)
        {
            // The active underline is the focus/selection treatment.
        }

        @Override
        protected void paintContentBorder(
            Graphics graphics,
            int tabPlacement,
            int selectedIndex)
        {
            // The outer readyTabs border supplies the content separation.
        }
    }

    private static final class SidebarTabHeader extends JLabel
    {
        private static final long serialVersionUID = 1L;
        private boolean active;
        private boolean hovered;

        private SidebarTabHeader(String title)
        {
            super(title, SwingConstants.CENTER);
            setFont(new Font(Font.DIALOG, Font.BOLD, 9));
            setOpaque(false);
            setForeground(new Color(205, 205, 205));
            setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        }

        private void setActive(boolean active)
        {
            this.active = active;
            updateForeground();
            repaint();
        }

        private void setHovered(boolean hovered)
        {
            this.hovered = hovered;
            updateForeground();
            repaint();
        }

        private void updateForeground()
        {
            setForeground(active
                ? new Color(255, 237, 198)
                : (hovered
                    ? new Color(236, 222, 195)
                    : new Color(205, 205, 205)));
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(active
                    ? new Color(91, 63, 29)
                    : (hovered
                        ? new Color(50, 49, 46)
                        : new Color(40, 42, 45)));
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 9, 9);
                g.setColor(active
                    ? new Color(222, 166, 65)
                    : (hovered
                        ? new Color(116, 94, 61)
                        : new Color(70, 72, 76)));
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 9, 9);
                if (active)
                {
                    g.setColor(new Color(245, 194, 91));
                    g.fillRoundRect(7, getHeight() - 3,
                        Math.max(0, getWidth() - 14), 2, 2, 2);
                }
            }
            finally
            {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private enum CollectionSort
    {
        OWNED_FIRST("Owned first"),
        NAME("Name"),
        RARITY("Rarity"),
        LOCKED_FIRST("Locked first");

        private final String label;

        CollectionSort(String label)
        {
            this.label = label;
        }

        private Comparator<CardDefinition> comparator(Set<String> owned)
        {
            Comparator<CardDefinition> name = Comparator.comparing(
                CardDefinition::getDisplayName,
                String.CASE_INSENSITIVE_ORDER);
            switch (this)
            {
                case NAME:
                    return name;
                case RARITY:
                    return Comparator.comparing(CardDefinition::getRarity)
                        .reversed()
                        .thenComparing(name);
                case LOCKED_FIRST:
                    return Comparator.comparing(
                        (CardDefinition card) ->
                            owned.contains(card.getCardId()))
                        .thenComparing(name);
                case OWNED_FIRST:
                default:
                    return Comparator.comparing(
                        (CardDefinition card) ->
                            !owned.contains(card.getCardId()))
                        .thenComparing(name);
            }
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    private enum QuestReadinessFilter
    {
        INCOMPLETE("Incomplete"),
        ALL("All quests"),
        COMPLETE("Complete"),
        READY("Card ready"),
        MISSING("Missing cards");

        private final String label;

        QuestReadinessFilter(String label)
        {
            this.label = label;
        }

        private boolean includes(QuestReadinessEntry entry)
        {
            if (this == INCOMPLETE)
            {
                return entry.getStatus() != QuestStatus.COMPLETE;
            }
            if (this == COMPLETE)
            {
                return entry.getStatus() == QuestStatus.COMPLETE;
            }
            if (this == READY)
            {
                return entry.getStatus() == QuestStatus.READY;
            }
            if (this == MISSING)
            {
                return entry.getStatus() == QuestStatus.BLOCKED;
            }
            return true;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

}
