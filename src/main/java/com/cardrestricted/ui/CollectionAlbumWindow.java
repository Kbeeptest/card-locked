package com.cardrestricted.ui;


import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.CardCategory;
import com.cardrestricted.catalog.Rarity;
import com.cardrestricted.foil.FoilEntitlementResolver;
import com.cardrestricted.foil.FoilEntitlementSnapshot;
import com.cardrestricted.foil.FoilRewardGrant;
import com.cardrestricted.foil.FoilRewardKind;
import com.cardrestricted.foil.FoilRewardRegistry;
import com.cardrestricted.foil.FoilRewardText;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.collection.activity.CollectionActivitySnapshot;
import com.cardrestricted.collection.activity.CardUnlockRecord;
import com.cardrestricted.persistence.CollectionState;
import com.cardrestricted.presentation.CardArtworkProvider;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Path2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;

/**
 * Primary collection album. It uses a bounded painted-grid renderer, live
 * collection state, full filtering and an in-window card detail preview.
 */
final class CollectionAlbumWindow
{
    private static final int PAGE_SIZE = 21;
    private static final int COLUMNS = 7;
    private static final int ROWS = 3;
    private static final int CARD_WIDTH = 132;
    private static final int CARD_HEIGHT = 198;
    private static final int COLUMN_GAP = 18;
    private static final int ROW_GAP = 28;

    private final CardCatalogue catalogue;
    private final FoilEntitlementResolver foilEntitlementResolver;
    private final FoilRewardRegistry foilRewardRegistry;
    private final CardArtworkProvider artworkProvider;
    private final CardArtworkProvider packagedArtworkProvider;
    private final CardDetailMetadata detailMetadata;
    private final List<CardDefinition> cards;
    private final List<AlbumCardRecord> albumCards;
    private final JFrame frame = new JFrame("Card Locked Collection Album");
    private final JTextField searchField = new JTextField(20);
    private final JComboBox<OwnershipFilter> ownershipFilter =
        new JComboBox<>(OwnershipFilter.values());
    private final JComboBox<ArtworkFilter> artworkFilter =
        new JComboBox<>(ArtworkFilter.values());
    private final JComboBox<CardTypeFilter> cardTypeFilter =
        new JComboBox<>(CardTypeFilter.values());
    private final JComboBox<RarityFilter> rarityFilter =
        new JComboBox<>(RarityFilter.values());
    private final JComboBox<CategoryFilter> categoryFilter =
        new JComboBox<>(CategoryFilter.values());
    private final JComboBox<QuestFilter> questFilter =
        new JComboBox<>(QuestFilter.values());
    private final JComboBox<SortMode> sortMode =
        new JComboBox<>(SortMode.values());
    private final JTextField pageField = new JTextField(4);
    private final JLabel overallCountLabel = new JLabel();
    private final JLabel foilCountLabel = new JLabel();
    private final JLabel foilAccessCountLabel = new JLabel();
    private final JLabel pageLabel = new JLabel();
    private final JButton previousButton = new JButton("Previous");
    private final JButton nextButton = new JButton("Next");
    private final JButton newestUnseenButton = new JButton("Newest unseen");
    private final JButton resetViewButton = new JButton("Reset view");
    private final AlbumGridPanel gridPanel;
    private final AlbumPreviewOverlay previewOverlay;
    private static final int FACE_CACHE_LIMIT = 512;
    private static final int PAGE_CACHE_LIMIT = 7;

    private final Timer artworkRefreshTimer;
    private final Timer searchDebounceTimer;
    private final Set<String> pendingArtworkCardIds = new LinkedHashSet<>();
    private final ExecutorService pageRenderer = Executors.newSingleThreadExecutor(
        new AlbumThreadFactory("card-locked-album-page-renderer"));
    private final ExecutorService previewRenderer = Executors.newSingleThreadExecutor(
        new AlbumThreadFactory("card-locked-album-preview-renderer"));
    private final Map<FaceKey, BufferedImage> faceCache =
        Collections.synchronizedMap(new BoundedLruMap<>(FACE_CACHE_LIMIT));
    private final Map<PageKey, List<AlbumSlot>> pageCache =
        Collections.synchronizedMap(new BoundedLruMap<>(PAGE_CACHE_LIMIT));
    private final AtomicInteger modelRevision = new AtomicInteger();
    private boolean pageLoadPending;

    private CollectionState state;
    private CollectionActivitySnapshot activity = CollectionActivitySnapshot.empty();
    private List<CardDefinition> filteredCards = Collections.emptyList();
    private Set<String> ownedCardIds = Collections.emptySet();
    private Set<String> foilCardIds = Collections.emptySet();
    private Set<String> foilAccessCardIds = Collections.emptySet();
    private Set<String> recentUnlockCardIds = Collections.emptySet();
    private final Set<String> inspectedRecentCardIds = new LinkedHashSet<>();
    private int pageIndex;
    private long albumVisibleTotal;
    private long albumOwnedCount;
    private long albumFoilCount;
    private long albumFoilAccessCount;
    private boolean modelInitialized;
    private boolean filterUpdateInProgress;
    private String lastSelectedCardId = "";

    CollectionAlbumWindow(
        CardCatalogue catalogue,
        CardArtworkProvider artworkProvider)
    {
        this(
            catalogue,
            new FoilEntitlementResolver(
                catalogue,
                FoilRewardRegistry.load(
                    CollectionAlbumWindow.class.getClassLoader(),
                    catalogue)),
            artworkProvider);
    }

    CollectionAlbumWindow(
        CardCatalogue catalogue,
        FoilEntitlementResolver foilEntitlementResolver,
        CardArtworkProvider artworkProvider)
    {
        this.catalogue = catalogue;
        this.foilEntitlementResolver = java.util.Objects.requireNonNull(
            foilEntitlementResolver,
            "foilEntitlementResolver");
        this.foilRewardRegistry = foilEntitlementResolver.getRegistry();
        this.artworkProvider = artworkProvider;
        this.detailMetadata = CardDetailMetadata.load(getClass().getClassLoader());
        this.packagedArtworkProvider = new CardArtworkProvider()
        {
            @Override
            public CardArtworkProvider.Artwork getArtwork(CardDefinition card)
            {
                return artworkProvider.getPackagedArtwork(card);
            }

            @Override
            public CardArtworkProvider.ArtworkSource getArtworkSource(CardDefinition card)
            {
                return artworkProvider.getPackagedArtworkSource(card);
            }
        };
        cards = new ArrayList<>(catalogue.getCards());
        cards.sort(Comparator.comparing(
            CardDefinition::getDisplayName,
            String.CASE_INSENSITIVE_ORDER));
        List<AlbumCardRecord> indexedCards = new ArrayList<>();
        for (CardDefinition card : cards)
        {
            if (isSuppressedAlbumVariant(card))
            {
                continue;
            }
            String quest = detailMetadata.detail(card).getQuest();
            indexedCards.add(new AlbumCardRecord(card, quest));
        }
        albumCards = Collections.unmodifiableList(indexedCards);
        albumVisibleTotal = albumCards.size();
        gridPanel = new AlbumGridPanel(this::openPreview, this::changePage);
        previewOverlay = new AlbumPreviewOverlay();
        artworkRefreshTimer = new Timer(120, event -> refreshPendingArtwork());
        artworkRefreshTimer.setRepeats(false);
        searchDebounceTimer = new Timer(180, event -> rebuildModel(true));
        searchDebounceTimer.setRepeats(false);
        buildWindow();
    }

    void close()
    {
        artworkRefreshTimer.stop();
        searchDebounceTimer.stop();
        pendingArtworkCardIds.clear();
        // Do not block RuneLite's Swing EDT during plugin/window shutdown.
        // Rendering tasks are purely cosmetic and safe to cancel immediately.
        pageRenderer.shutdownNow();
        previewRenderer.shutdownNow();
        previewOverlay.hidePreview();
        faceCache.clear();
        pageCache.clear();
        frame.dispose();
    }

    void onArtworkAvailable(String cardId)
    {
        if (cardId == null || cardId.isEmpty())
        {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> onArtworkAvailable(cardId));
            return;
        }
        pendingArtworkCardIds.add(cardId);
        artworkRefreshTimer.restart();
    }

    void onArtworkPackReady()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::onArtworkPackReady);
            return;
        }
        faceCache.clear();
        pageCache.clear();
        pendingArtworkCardIds.clear();
        if (frame.isVisible())
        {
            refreshPage();
        }
    }

    private void refreshPendingArtwork()
    {
        if (pendingArtworkCardIds.isEmpty())
        {
            return;
        }
        Set<String> ready = new LinkedHashSet<>(pendingArtworkCardIds);
        pendingArtworkCardIds.clear();
        List<AlbumSlot> currentSlots = gridPanel.getSlotsSnapshot();
        int revision = modelRevision.get();
        Map<Integer, AlbumSlot> affected = new LinkedHashMap<>();
        for (int index = 0; index < currentSlots.size(); index++)
        {
            AlbumSlot slot = currentSlots.get(index);
            if (ready.contains(slot.card.getCardId()))
            {
                invalidateCard(slot.card.getCardId());
                affected.put(index, slot);
            }
        }
        if (affected.isEmpty())
        {
            return;
        }

        // Artwork-ready callbacks are delivered onto Swing, but decoding and
        // foil/card composition are not EDT work. Reuse the same background
        // renderer used for ordinary Album pages, then install only results
        // that still belong to the current model revision.
        pageRenderer.execute(() -> {
            Map<Integer, AlbumSlot> rendered = new LinkedHashMap<>();
            for (Map.Entry<Integer, AlbumSlot> entry : affected.entrySet())
            {
                if (Thread.currentThread().isInterrupted()
                    || revision != modelRevision.get())
                {
                    return;
                }
                AlbumSlot slot = entry.getValue();
                BufferedImage face = renderFace(
                    slot.card,
                    slot.owned,
                    slot.foil,
                    slot.foilAccess,
                    CARD_WIDTH,
                    CARD_HEIGHT);
                rendered.put(
                    entry.getKey(),
                    new AlbumSlot(
                        slot.card,
                        slot.owned,
                        slot.foil,
                        slot.foilAccess,
                        slot.newUnlock,
                        face));
            }
            SwingUtilities.invokeLater(() -> {
                if (revision != modelRevision.get())
                {
                    return;
                }
                List<AlbumSlot> latestSlots = gridPanel.getSlotsSnapshot();
                for (Map.Entry<Integer, AlbumSlot> entry : rendered.entrySet())
                {
                    int index = entry.getKey();
                    if (index < 0 || index >= latestSlots.size())
                    {
                        continue;
                    }
                    AlbumSlot expected = affected.get(index);
                    AlbumSlot latest = latestSlots.get(index);
                    if (expected == null
                        || !expected.card.getCardId().equals(
                            latest.card.getCardId()))
                    {
                        continue;
                    }
                    gridPanel.replaceSlot(index, entry.getValue());
                }
            });
        });
    }

    void update(CollectionState newState, CollectionActivitySnapshot newActivity)
    {
        if (newState == null)
        {
            return;
        }
        activity = newActivity == null ? CollectionActivitySnapshot.empty() : newActivity;
        Set<String> previousRecent = recentUnlockCardIds;
        refreshRecentUnlockIds();
        boolean recentChanged = !previousRecent.equals(recentUnlockCardIds);
        FoilEntitlementSnapshot nextEntitlements =
            foilEntitlementResolver.resolve(
                newState.getOwnedCardIds(),
                newState.getFoilCardIds());
        Set<String> nextOwned = nextEntitlements.getOwnedCardIds();
        Set<String> nextFoils = nextEntitlements.getFoilCardIds();
        Set<String> nextFoilAccess = nextEntitlements.getDerivedCardIds();
        if (nextOwned.equals(ownedCardIds)
            && nextFoils.equals(foilCardIds)
            && nextFoilAccess.equals(foilAccessCardIds))
        {
            state = newState;
            if (recentChanged && frame.isVisible())
            {
                rebuildModel(false);
            }
            return;
        }
        Set<String> changed = new LinkedHashSet<>(ownedCardIds);
        changed.addAll(nextOwned);
        changed.addAll(foilCardIds);
        changed.addAll(nextFoils);
        changed.addAll(foilAccessCardIds);
        changed.addAll(nextFoilAccess);
        changed.removeIf(cardId ->
            ownedCardIds.contains(cardId) == nextOwned.contains(cardId)
                && foilCardIds.contains(cardId) == nextFoils.contains(cardId)
                && foilAccessCardIds.contains(cardId)
                    == nextFoilAccess.contains(cardId));
        state = newState;
        ownedCardIds = nextOwned;
        foilCardIds = nextFoils;
        foilAccessCardIds = nextFoilAccess;
        for (String cardId : changed)
        {
            invalidateCard(cardId);
        }
        if (frame.isVisible())
        {
            rebuildModel(false);
        }
    }

    void show(CollectionState newState, CollectionActivitySnapshot newActivity)
    {
        if (newState == null)
        {
            return;
        }
        state = newState;
        activity = newActivity == null ? CollectionActivitySnapshot.empty() : newActivity;
        refreshRecentUnlockIds();
        FoilEntitlementSnapshot entitlements =
            foilEntitlementResolver.resolve(
                state.getOwnedCardIds(),
                state.getFoilCardIds());
        ownedCardIds = entitlements.getOwnedCardIds();
        foilCardIds = entitlements.getFoilCardIds();
        foilAccessCardIds = entitlements.getDerivedCardIds();
        rebuildModel(!modelInitialized);
        modelInitialized = true;
        frame.setVisible(true);
        frame.toFront();
    }

    void showCard(
        CollectionState newState,
        CollectionActivitySnapshot newActivity,
        CardDefinition card)
    {
        Objects.requireNonNull(card, "card");
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> showCard(newState, newActivity, card));
            return;
        }
        show(newState, newActivity);
        int index = indexOfFilteredCard(card.getCardId());
        if (index < 0)
        {
            setDefaultFilterState();
            rebuildModel(true);
            index = indexOfFilteredCard(card.getCardId());
        }
        if (index < 0)
        {
            return;
        }
        pageIndex = index / PAGE_SIZE;
        refreshPage();
        previewOverlay.showCard(index);
        updateNavigationState();
    }


    private void refreshRecentUnlockIds()
    {
        LinkedHashSet<String> recent = new LinkedHashSet<>();
        for (com.cardrestricted.collection.activity.CardUnlockRecord unlock : activity.getRecentUnlocks(50))
        {
            recent.add(unlock.getCardId());
        }
        recentUnlockCardIds = Collections.unmodifiableSet(
            catalogue.canonicalizeCardIds(recent));
        inspectedRecentCardIds.retainAll(recentUnlockCardIds);
    }

    private boolean isUninspectedRecent(CardDefinition card)
    {
        return recentUnlockCardIds.contains(card.getCardId())
            && !inspectedRecentCardIds.contains(card.getCardId());
    }

    private void buildWindow()
    {
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setIconImage(CardUiAssets.windowIcon(32));
        frame.setMinimumSize(new Dimension(860, 650));
        frame.setSize(new Dimension(1180, 820));
        frame.setLocationByPlatform(true);
        frame.getContentPane().setBackground(ColorScheme.DARK_GRAY_COLOR);
        frame.setLayout(new BorderLayout(0, 8));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setBackground(ColorScheme.DARK_GRAY_COLOR);
        north.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));

        JPanel titleRow = new JPanel(new BorderLayout(12, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel("COLLECTION ALBUM");
        title.setForeground(new Color(246, 170, 45));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        titleRow.add(title, BorderLayout.WEST);
        CardUiTheme.styleCompactButton(resetViewButton);
        resetViewButton.setForeground(new Color(239, 205, 137));
        resetViewButton.setBackground(new Color(46, 40, 30));
        resetViewButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(151, 111, 47)),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        resetViewButton.setToolTipText(
            "Clear every filter and restore name sorting");
        resetViewButton.addActionListener(event -> resetView());
        titleRow.add(resetViewButton, BorderLayout.EAST);
        north.add(titleRow);
        north.add(javax.swing.Box.createRigidArea(new Dimension(0, 8)));

        JPanel summary = new JPanel(new GridLayout(1, 3, 8, 0));
        summary.setOpaque(false);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.add(albumMetric(
            "UNLOCKED", overallCountLabel, new Color(229, 164, 54)));
        summary.add(albumMetric(
            "FOILS OWNED", foilCountLabel, new Color(204, 135, 231)));
        summary.add(albumMetric(
            "FOIL ACCESS CARDS", foilAccessCountLabel, CardUiTheme.FOIL_ACCESS));
        north.add(summary);
        north.add(javax.swing.Box.createRigidArea(new Dimension(0, 9)));

        JPanel controls = new JPanel(new GridLayout(0, 4, 8, 6));
        controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
        controls.add(albumControl("Search", searchField));
        controls.add(albumControl("Ownership", ownershipFilter));
        controls.add(albumControl("Artwork", artworkFilter));
        controls.add(albumControl("Card type", cardTypeFilter));
        controls.add(albumControl("Rarity", rarityFilter));
        controls.add(albumControl("Category", categoryFilter));
        controls.add(albumControl("Quest relevance", questFilter));
        controls.add(albumControl("Sort", sortMode));
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(controls);
        frame.add(north, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(gridPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.getViewport().setScrollMode(javax.swing.JViewport.SIMPLE_SCROLL_MODE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        frame.add(scrollPane, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        south.setBackground(ColorScheme.DARK_GRAY_COLOR);
        previousButton.addActionListener(event -> changePage(-1));
        nextButton.addActionListener(event -> changePage(1));
        south.add(previousButton);
        south.add(pageLabel);
        south.add(nextButton);
        newestUnseenButton.addActionListener(event -> jumpToNewestUnseen());
        south.add(newestUnseenButton);
        south.add(new JLabel("Go to:"));
        south.add(pageField);
        JButton goButton = new JButton("Go");
        goButton.addActionListener(event -> jumpToPage());
        pageField.addActionListener(event -> jumpToPage());
        south.add(goButton);
        frame.add(south, BorderLayout.SOUTH);

        searchField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent event)
            {
                queueSearchRebuild();
            }

            @Override
            public void removeUpdate(DocumentEvent event)
            {
                queueSearchRebuild();
            }

            @Override
            public void changedUpdate(DocumentEvent event)
            {
                queueSearchRebuild();
            }
        });
        ownershipFilter.addActionListener(event -> rebuildFromFilterChange());
        artworkFilter.addActionListener(event -> rebuildFromFilterChange());
        cardTypeFilter.addActionListener(event -> rebuildFromFilterChange());
        rarityFilter.addActionListener(event -> rebuildFromFilterChange());
        categoryFilter.addActionListener(event -> rebuildFromFilterChange());
        questFilter.addActionListener(event -> rebuildFromFilterChange());
        sortMode.addActionListener(event -> rebuildFromFilterChange());
        registerAlbumKeyActions();
    }

    private JPanel albumMetric(String title, JLabel value, Color accent)
    {
        JPanel metric = new JPanel();
        metric.setLayout(new BoxLayout(metric, BoxLayout.Y_AXIS));
        metric.setBackground(new Color(31, 32, 35));
        metric.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
            BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        JLabel heading = new JLabel(title);
        heading.setForeground(accent);
        heading.setFont(CardUiTheme.META_BOLD);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        metric.add(heading);
        value.setForeground(new Color(238, 238, 238));
        value.setFont(value.getFont().deriveFont(Font.BOLD, 15f));
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        metric.add(value);
        return metric;
    }

    private void queueSearchRebuild()
    {
        if (!filterUpdateInProgress)
        {
            searchDebounceTimer.restart();
        }
    }

    private void rebuildFromFilterChange()
    {
        if (!filterUpdateInProgress)
        {
            rebuildModel(true);
        }
    }

    private void setDefaultFilterState()
    {
        searchDebounceTimer.stop();
        filterUpdateInProgress = true;
        try
        {
            searchField.setText("");
            ownershipFilter.setSelectedItem(OwnershipFilter.ALL);
            artworkFilter.setSelectedItem(ArtworkFilter.ALL);
            cardTypeFilter.setSelectedItem(CardTypeFilter.ALL);
            rarityFilter.setSelectedItem(RarityFilter.ALL);
            categoryFilter.setSelectedItem(CategoryFilter.ALL);
            questFilter.setSelectedItem(QuestFilter.ALL);
            sortMode.setSelectedItem(SortMode.NAME_ASC);
        }
        finally
        {
            filterUpdateInProgress = false;
        }
    }

    private void resetView()
    {
        setDefaultFilterState();
        rebuildModel(true);
        searchField.requestFocusInWindow();
    }

    private JPanel albumControl(String labelText, JComponent control)
    {
        JPanel cell = new JPanel();
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(labelText);
        label.setForeground(new Color(184, 184, 184));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        cell.add(label);
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        cell.add(control);
        return cell;
    }

    private void rebuildModel(boolean resetPage)
    {
        if (state == null)
        {
            return;
        }
        if (resetPage)
        {
            pageIndex = 0;
        }
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        OwnershipFilter selected = (OwnershipFilter) ownershipFilter.getSelectedItem();
        if (selected == null)
        {
            selected = OwnershipFilter.ALL;
        }
        final OwnershipFilter activeFilter = selected;
        ArtworkFilter selectedArtwork = (ArtworkFilter) artworkFilter.getSelectedItem();
        if (selectedArtwork == null)
        {
            selectedArtwork = ArtworkFilter.ALL;
        }
        final ArtworkFilter activeArtworkFilter = selectedArtwork;
        CardTypeFilter selectedCardType =
            (CardTypeFilter) cardTypeFilter.getSelectedItem();
        if (selectedCardType == null)
        {
            selectedCardType = CardTypeFilter.ALL;
        }
        final CardTypeFilter activeCardTypeFilter = selectedCardType;
        RarityFilter selectedRarity = (RarityFilter) rarityFilter.getSelectedItem();
        if (selectedRarity == null)
        {
            selectedRarity = RarityFilter.ALL;
        }
        final RarityFilter activeRarityFilter = selectedRarity;
        CategoryFilter selectedCategory = (CategoryFilter) categoryFilter.getSelectedItem();
        if (selectedCategory == null)
        {
            selectedCategory = CategoryFilter.ALL;
        }
        final CategoryFilter activeCategoryFilter = selectedCategory;
        QuestFilter selectedQuest = (QuestFilter) questFilter.getSelectedItem();
        if (selectedQuest == null)
        {
            selectedQuest = QuestFilter.ALL;
        }
        final QuestFilter activeQuestFilter = selectedQuest;
        SortMode selectedSort = (SortMode) sortMode.getSelectedItem();
        if (selectedSort == null)
        {
            selectedSort = SortMode.NAME_ASC;
        }

        long overallOwned = 0L;
        long overallFoils = 0L;
        long overallFoilAccess = 0L;
        List<CardDefinition> matches = new ArrayList<>();
        for (AlbumCardRecord record : albumCards)
        {
            CardDefinition card = record.card;
            String cardId = card.getCardId();
            boolean owned = ownedCardIds.contains(cardId);
            boolean foil = foilCardIds.contains(cardId);
            boolean foilAccess = foilAccessCardIds.contains(cardId);
            if (owned)
            {
                overallOwned++;
            }
            if (foil)
            {
                overallFoils++;
            }
            if (foilAccess)
            {
                overallFoilAccess++;
            }
            if (!query.isEmpty() && !record.searchText.contains(query))
            {
                continue;
            }
            if (!activeFilter.includes(
                cardId,
                ownedCardIds,
                foilCardIds,
                foilAccessCardIds,
                recentUnlockCardIds)
                || (activeArtworkFilter != ArtworkFilter.ALL
                    && !activeArtworkFilter.includes(
                        artworkProvider.getPackagedArtworkSource(card)))
                || !activeCardTypeFilter.includes(card, detailMetadata)
                || !activeRarityFilter.includes(card.getRarity())
                || !activeCategoryFilter.includes(card.getCategories())
                || !activeQuestFilter.includes(record.quest))
            {
                continue;
            }
            matches.add(card);
        }

        albumOwnedCount = overallOwned;
        albumFoilCount = overallFoils;
        albumFoilAccessCount = overallFoilAccess;
        matches.sort(selectedSort.comparator(
            ownedCardIds,
            foilCardIds,
            activity));
        filteredCards = Collections.unmodifiableList(matches);
        int pageCount = pageCount();
        if (pageIndex >= pageCount)
        {
            pageIndex = Math.max(0, pageCount - 1);
        }
        modelRevision.incrementAndGet();
        pageCache.clear();
        refreshPage();
    }


    private boolean isSuppressedAlbumVariant(CardDefinition card)
    {
        String name = card.getDisplayName();
        return name != null && name.contains("(cr)");
    }

    private void registerAlbumKeyActions()
    {
        JComponent root = frame.getRootPane();
        bindAlbumKey(root, KeyEvent.VK_PAGE_UP, 0, "previous-page", () -> changePage(-1));
        bindAlbumKey(root, KeyEvent.VK_PAGE_DOWN, 0, "next-page", () -> changePage(1));
        bindAlbumKey(root, KeyEvent.VK_HOME, 0, "first-page", () -> goToPageIndex(0));
        bindAlbumKey(root, KeyEvent.VK_END, 0, "last-page", () -> goToPageIndex(pageCount() - 1));
        bindAlbumKey(root, KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK,
            "focus-search", () -> { searchField.requestFocusInWindow(); searchField.selectAll(); });
    }

    private void bindAlbumKey(
        JComponent root,
        int keyCode,
        int modifiers,
        String name,
        Runnable action)
    {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(keyCode, modifiers), name);
        root.getActionMap().put(name, new javax.swing.AbstractAction()
        {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent event)
            {
                action.run();
            }
        });
    }

    private int unseenRecentCount()
    {
        int count = 0;
        for (CardUnlockRecord unlock : activity.getRecentUnlocks(50))
        {
            String cardId = unlock.getCardId();
            if (recentUnlockCardIds.contains(cardId)
                && !inspectedRecentCardIds.contains(cardId))
            {
                count++;
            }
        }
        return count;
    }

    private String newestUnseenCardId()
    {
        for (CardUnlockRecord unlock : activity.getRecentUnlocks(50))
        {
            String cardId = unlock.getCardId();
            if (recentUnlockCardIds.contains(cardId)
                && !inspectedRecentCardIds.contains(cardId))
            {
                return cardId;
            }
        }
        return "";
    }

    private void jumpToNewestUnseen()
    {
        String cardId = newestUnseenCardId();
        if (cardId.isEmpty())
        {
            updateNavigationState();
            return;
        }

        int index = indexOfFilteredCard(cardId);
        if (index < 0)
        {
            searchDebounceTimer.stop();
            filterUpdateInProgress = true;
            try
            {
                searchField.setText("");
                ownershipFilter.setSelectedItem(OwnershipFilter.RECENT);
                artworkFilter.setSelectedItem(ArtworkFilter.ALL);
                cardTypeFilter.setSelectedItem(CardTypeFilter.ALL);
                rarityFilter.setSelectedItem(RarityFilter.ALL);
                categoryFilter.setSelectedItem(CategoryFilter.ALL);
                questFilter.setSelectedItem(QuestFilter.ALL);
                sortMode.setSelectedItem(SortMode.NEWEST_UNLOCKED);
            }
            finally
            {
                filterUpdateInProgress = false;
            }
            rebuildModel(true);
            index = indexOfFilteredCard(cardId);
        }
        if (index < 0)
        {
            return;
        }

        pageIndex = index / PAGE_SIZE;
        refreshPage();
        previewOverlay.showCard(index);
        updateNavigationState();
    }

    private int indexOfFilteredCard(String cardId)
    {
        for (int index = 0; index < filteredCards.size(); index++)
        {
            if (cardId.equals(filteredCards.get(index).getCardId()))
            {
                return index;
            }
        }
        return -1;
    }

    private void jumpToPage()
    {
        String text = pageField.getText().trim();
        if (text.isEmpty())
        {
            return;
        }
        try
        {
            int requested = Integer.parseInt(text);
            if (requested < 1 || requested > pageCount())
            {
                throw new NumberFormatException();
            }
            goToPageIndex(requested - 1);
            pageField.selectAll();
        }
        catch (NumberFormatException exception)
        {
            JOptionPane.showMessageDialog(
                frame,
                "Enter a page number from 1 to " + pageCount() + ".",
                "Invalid page",
                JOptionPane.WARNING_MESSAGE);
            pageField.requestFocusInWindow();
            pageField.selectAll();
        }
    }

    private void goToPageIndex(int requestedPage)
    {
        int bounded = Math.max(0, Math.min(requestedPage, pageCount() - 1));
        if (bounded == pageIndex && !pageLoadPending)
        {
            return;
        }
        pageIndex = bounded;
        refreshPage();
    }

    private void changePage(int delta)
    {
        int next = pageIndex + delta;
        if (next < 0 || next >= pageCount())
        {
            return;
        }
        pageIndex = next;
        refreshPage();
    }

    private void refreshPage()
    {
        final int requestedPage = pageIndex;
        final int revision = modelRevision.get();
        PageKey key = pageKey(requestedPage, revision);
        List<AlbumSlot> cached = pageCache.get(key);
        updateNavigationState();
        if (cached != null)
        {
            installPage(requestedPage, revision, cached);
            prefetchAdjacentPages(requestedPage, revision);
            return;
        }

        pageLoadPending = true;
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
        pageLabel.setText("Preparing page " + (requestedPage + 1) + "...");
        pageRenderer.execute(() -> {
            List<AlbumSlot> rendered = renderPage(requestedPage, revision);
            if (rendered == null)
            {
                return;
            }
            pageCache.put(pageKey(requestedPage, revision), rendered);
            SwingUtilities.invokeLater(() -> {
                if (pageIndex != requestedPage || modelRevision.get() != revision)
                {
                    return;
                }
                installPage(requestedPage, revision, rendered);
                prefetchAdjacentPages(requestedPage, revision);
            });
        });
    }

    private List<AlbumSlot> renderPage(int requestedPage, int revision)
    {
        if (revision != modelRevision.get())
        {
            return null;
        }
        List<CardDefinition> model = filteredCards;
        int start = requestedPage * PAGE_SIZE;
        int end = Math.min(model.size(), start + PAGE_SIZE);
        if (start < 0 || start > end)
        {
            return Collections.emptyList();
        }
        List<CardDefinition> pageCards = new ArrayList<>(model.subList(start, end));
        artworkProvider.prefetch(pageCards);
        List<AlbumSlot> slots = new ArrayList<>(pageCards.size());
        for (CardDefinition card : pageCards)
        {
            if (revision != modelRevision.get())
            {
                return null;
            }
            boolean owned = ownedCardIds.contains(card.getCardId());
            boolean foil = foilCardIds.contains(card.getCardId());
            boolean foilAccess = foilAccessCardIds.contains(card.getCardId());
            BufferedImage face = renderFace(
                card,
                owned,
                foil,
                foilAccess,
                CARD_WIDTH,
                CARD_HEIGHT);
            slots.add(new AlbumSlot(
                card,
                owned,
                foil,
                foilAccess,
                isUninspectedRecent(card),
                face));
        }
        return Collections.unmodifiableList(slots);
    }

    private void installPage(
        int requestedPage,
        int revision,
        List<AlbumSlot> slots)
    {
        if (pageIndex != requestedPage || modelRevision.get() != revision)
        {
            return;
        }
        pageLoadPending = false;
        previewOverlay.hidePreview();
        gridPanel.setSlots(slots);
        updateNavigationState();
    }

    private void updateNavigationState()
    {
        int count = pageCount();
        pageLabel.setText("Page " + (pageIndex + 1) + " of " + count);
        if (!pageField.hasFocus())
        {
            pageField.setText(Integer.toString(pageIndex + 1));
        }
        previousButton.setEnabled(!pageLoadPending && pageIndex > 0);
        nextButton.setEnabled(!pageLoadPending && pageIndex + 1 < count);
        int unseen = unseenRecentCount();
        newestUnseenButton.setEnabled(unseen > 0);
        newestUnseenButton.setText(unseen > 0
            ? "Newest unseen (" + unseen + ")"
            : "No unseen cards");
        overallCountLabel.setText(formatCount(albumOwnedCount)
            + " / " + formatCount(albumVisibleTotal));
        foilCountLabel.setText(formatCount(albumFoilCount));
        foilAccessCountLabel.setText(formatCount(albumFoilAccessCount));
    }

    private static String formatCount(long value)
    {
        return String.format(Locale.ROOT, "%,d", value);
    }




    private void prefetchAdjacentPages(int currentPage, int revision)
    {
        for (int candidate : new int[]{currentPage - 1, currentPage + 1})
        {
            if (candidate < 0 || candidate >= pageCount())
            {
                continue;
            }
            PageKey key = pageKey(candidate, revision);
            if (pageCache.containsKey(key))
            {
                continue;
            }
            pageRenderer.execute(() -> {
                if (revision != modelRevision.get() || pageCache.containsKey(key))
                {
                    return;
                }
                List<AlbumSlot> rendered = renderPage(candidate, revision);
                if (rendered != null)
                {
                    pageCache.put(key, rendered);
                }
            });
        }
    }

    private BufferedImage renderFace(
        CardDefinition card,
        boolean owned,
        boolean foil,
        boolean foilAccess,
        int width,
        int height)
    {
        FaceKey key = new FaceKey(
            card.getCardId(), owned, foil, foilAccess, width, height);
        BufferedImage cached = faceCache.get(key);
        if (cached != null)
        {
            return cached;
        }
        BufferedImage rendered = renderTestThumbnail(
            card,
            owned,
            foil,
            foilAccess,
            width,
            height);
        faceCache.put(key, rendered);
        return rendered;
    }

    private void applyAlbumFoilBase(
        BufferedImage image,
        String cardId)
    {
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
            Composite previous = g.getComposite();
            int width = image.getWidth();
            int height = image.getHeight();
            int seed = cardId.hashCode();
            int band = Math.max(16, width / 5);
            int offset = Math.floorMod(seed, band * 4);

            g.setComposite(AlphaComposite.SrcOver.derive(0.48f));
            for (int x = -band * 3 + offset;
                 x < width + height + band * 3;
                 x += band)
            {
                float hue = Math.floorMod(x / Math.max(1, band), 14) / 14.0f;
                Color spectrum = Color.getHSBColor(hue, 0.54f, 1.0f);
                g.setColor(new Color(
                    spectrum.getRed(),
                    spectrum.getGreen(),
                    spectrum.getBlue(),
                    72));
                Polygon stripe = new Polygon(
                    new int[]{x, x + band, x - height + band, x - height},
                    new int[]{height, height, 0, 0},
                    4);
                g.fillPolygon(stripe);
            }

            g.setComposite(AlphaComposite.SrcOver.derive(0.24f));
            g.setPaint(new GradientPaint(
                0,
                height,
                new Color(120, 255, 235, 66),
                width,
                0,
                new Color(255, 148, 214, 104),
                true));
            g.fillRect(0, 0, width, height);
            g.setComposite(AlphaComposite.SrcOver.derive(0.20f));
            g.setPaint(new GradientPaint(
                width * 0.08f,
                height,
                new Color(255, 255, 255, 0),
                width * 0.88f,
                height * 0.12f,
                new Color(255, 248, 236, 190),
                true));
            g.fillRect(0, 0, width, height);

            Random random = new Random(seed * 31L + width * 17L + height);
            int sparkleCount = Math.max(18, width / 8);
            for (int index = 0; index < sparkleCount; index++)
            {
                int sparkleX = random.nextInt(Math.max(1, width));
                int sparkleY = random.nextInt(Math.max(1, height));
                int arm = Math.max(2, width / 70) + random.nextInt(Math.max(2, width / 55));
                int alpha = 110 + random.nextInt(100);
                g.setColor(new Color(255, 255, 248, alpha));
                g.drawLine(sparkleX - arm, sparkleY, sparkleX + arm, sparkleY);
                g.drawLine(sparkleX, sparkleY - arm, sparkleX, sparkleY + arm);
                int diag = Math.max(1, Math.round(arm * 0.58f));
                g.drawLine(sparkleX - diag, sparkleY - diag, sparkleX + diag, sparkleY + diag);
                g.drawLine(sparkleX - diag, sparkleY + diag, sparkleX + diag, sparkleY - diag);
            }

            int border = Math.max(3, width / 46);
            int badgeWidth = Math.max(28, width / 5);
            int badgeHeight = Math.max(12, height / 17);
            int badgeX = width - badgeWidth - border;
            int badgeY = height - badgeHeight - border;
            g.setComposite(AlphaComposite.SrcOver.derive(1.0f));
            g.setColor(new Color(39, 18, 58, 228));
            g.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, 8, 8);
            g.setColor(new Color(255, 225, 255));
            g.setFont(new Font(
                Font.SANS_SERIF,
                Font.BOLD,
                Math.max(8, badgeHeight * 3 / 5)));
            Font font = g.getFont();
            int textWidth = g.getFontMetrics(font).stringWidth("FOIL");
            int textX = badgeX + (badgeWidth - textWidth) / 2;
            int textY = badgeY + (badgeHeight + g.getFontMetrics(font).getAscent()
                - g.getFontMetrics(font).getDescent()) / 2;
            g.drawString("FOIL", textX, textY);
            g.setComposite(previous);
        }
        finally
        {
            g.dispose();
        }
    }

    private PageKey pageKey(int page, int revision)
    {
        return new PageKey(page, revision);
    }

    private void invalidateCard(String cardId)
    {
        synchronized (faceCache)
        {
            faceCache.keySet().removeIf(key -> key.cardId.equals(cardId));
        }
        synchronized (pageCache)
        {
            pageCache.entrySet().removeIf(entry -> entry.getValue().stream()
                .anyMatch(slot -> slot.card.getCardId().equals(cardId)));
        }
    }

    private BufferedImage renderTestThumbnail(
        CardDefinition card,
        boolean owned,
        boolean foil,
        boolean foilAccess,
        int width,
        int height)
    {
        BufferedImage image = CardUiAssets.cardThumbnail(
            card,
            packagedArtworkProvider,
            owned,
            false,
            foilAccess,
            width,
            height);
        if (foil)
        {
            applyAlbumFoilBase(image, card.getCardId());
        }
        if (artworkProvider.getPackagedArtworkSource(card)
            != CardArtworkProvider.ArtworkSource.NONE)
        {
            return image;
        }
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            int boxWidth = Math.max(74, width - Math.max(18, width / 5));
            int boxHeight = Math.max(24, height / 10);
            int x = (width - boxWidth) / 2;
            int y = Math.max(12, height / 2 - boxHeight / 2);
            boolean expectedAsyncArtwork = card.getCardType() == CardType.ITEM;
            g.setColor(expectedAsyncArtwork
                ? new Color(18, 24, 31, 220)
                : new Color(35, 8, 8, 225));
            g.fillRoundRect(x, y, boxWidth, boxHeight, 10, 10);
            g.setColor(expectedAsyncArtwork
                ? new Color(108, 164, 210)
                : new Color(220, 76, 68));
            g.drawRoundRect(x, y, boxWidth, boxHeight, 10, 10);
            g.setFont(new Font(
                Font.SANS_SERIF,
                Font.BOLD,
                Math.max(9, height / 34)));
            String text = expectedAsyncArtwork
                ? "LOADING ARTWORK"
                : "ARTWORK UNAVAILABLE";
            int textX = x + (boxWidth - g.getFontMetrics().stringWidth(text)) / 2;
            int textY = y + (boxHeight + g.getFontMetrics().getAscent()
                - g.getFontMetrics().getDescent()) / 2;
            g.drawString(text, Math.max(x + 4, textX), textY);
        }
        finally
        {
            g.dispose();
        }
        return image;
    }

    private int pageCount()
    {
        return Math.max(1, (filteredCards.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void openPreview(int slotIndex)
    {
        List<AlbumSlot> slots = gridPanel.getSlotsSnapshot();
        if (slotIndex < 0 || slotIndex >= slots.size())
        {
            return;
        }
        int globalIndex = pageIndex * PAGE_SIZE + slotIndex;
        if (globalIndex >= 0 && globalIndex < filteredCards.size())
        {
            lastSelectedCardId = filteredCards.get(globalIndex).getCardId();
            gridPanel.setSelectedCardId(lastSelectedCardId);
        }
        previewOverlay.showCard(globalIndex);
    }

    /**
     * In-album preview rendered on the frame glass pane. The card is projected
     * as a rigid plane in 3D space and eased towards the cursor position.
     */
    private final class AlbumPreviewOverlay extends JPanel
    {
        private static final long serialVersionUID = 1L;
        private static final int PREVIEW_WIDTH = 320;
        private static final int PREVIEW_HEIGHT = 480;
        private static final int MESH_COLUMNS = 24;
        private static final int MESH_ROWS = 36;
        private final double maxYaw = Math.toRadians(18.0);
        private final double maxPitch = Math.toRadians(14.0);
        private static final double CAMERA_DISTANCE = 1280.0;
        private static final double EASE = 0.18;
        private static final int PREVIEW_GLINT_INTERVAL_MS = 4200;
        private static final int PREVIEW_GLINT_DURATION_MS = 460;
        private static final int PREVIEW_GLINT_FRAME_MS = 24;
        private static final int PREVIEW_AMBIENT_TWINKLE_FRAME_MS = 80;
        private static final int PREVIEW_FLIP_DURATION_MS = 340;
        private static final int PREVIEW_FLIP_FRAME_MS = 16;
        private static final int OVERLAY_MARGIN = 24;
        private static final int CARD_PANEL_GAP = 34;
        private static final int PREVIEW_NAVIGATION_GAP = 12;
        private static final int PREVIEW_NAVIGATION_SIZE = 38;

        private transient BufferedImage front;
        private transient BufferedImage back;
        private boolean loading;
        private boolean frontVisible = true;
        private boolean foilVisible;
        private int filteredIndex = -1;
        private int renderRevision;
        private double targetYaw;
        private double targetPitch;
        private double currentYaw;
        private double currentPitch;
        private Polygon paintedCard = new Polygon();
        private Rectangle informationPanelBounds = new Rectangle();
        private Rectangle wikiButtonBounds = new Rectangle();
        private Rectangle previousPreviewButtonBounds = new Rectangle();
        private Rectangle nextPreviewButtonBounds = new Rectangle();
        private int informationScrollOffset;
        private int informationContentHeight;
        private int informationViewportHeight;
        private transient Shape paintedCardClip = new Path2D.Double();
        private Point2D.Double topLeftCorner = new Point2D.Double();
        private Point2D.Double topRightCorner = new Point2D.Double();
        private Point2D.Double bottomRightCorner = new Point2D.Double();
        private Point2D.Double bottomLeftCorner = new Point2D.Double();
        private final Timer motionTimer = new Timer(16, event -> animateMotion());
        private final Timer previewGlintScheduleTimer =
            new Timer(PREVIEW_GLINT_INTERVAL_MS, event -> startPreviewGlint());
        private final Timer previewGlintAnimationTimer =
            new Timer(PREVIEW_GLINT_FRAME_MS, event -> advancePreviewGlint());
        private final Timer previewAmbientTwinkleTimer =
            new Timer(PREVIEW_AMBIENT_TWINKLE_FRAME_MS, event -> advancePreviewAmbientTwinkle());
        private final Timer flipAnimationTimer =
            new Timer(PREVIEW_FLIP_FRAME_MS, event -> advanceFlipAnimation());
        private long previewGlintStartedAtNanos;
        private long flipAnimationStartedAtNanos;
        private float previewGlintProgress = -1f;
        private float previewAmbientTwinklePhase;
        private float flipProgress = -1f;
        private boolean flipSideSwapped;
        private transient Future<?> previewRenderTask;

        private AlbumPreviewOverlay()
        {
            setOpaque(false);
            setVisible(false);
            setFocusable(true);
            motionTimer.setRepeats(true);
            previewGlintScheduleTimer.setInitialDelay(450);
            previewGlintScheduleTimer.setRepeats(true);
            previewGlintAnimationTimer.setRepeats(true);
            previewAmbientTwinkleTimer.setRepeats(true);
            flipAnimationTimer.setRepeats(true);
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseReleased(MouseEvent event)
                {
                    if (!SwingUtilities.isLeftMouseButton(event))
                    {
                        return;
                    }
                    if (previousPreviewButtonBounds.contains(event.getPoint()))
                    {
                        move(-1);
                    }
                    else if (nextPreviewButtonBounds.contains(event.getPoint()))
                    {
                        move(1);
                    }
                    else if (wikiButtonBounds.contains(event.getPoint()))
                    {
                        openCurrentCardWiki();
                    }
                    else if (paintedCard.contains(event.getPoint()))
                    {
                        startFlipAnimation();
                    }
                    else if (!informationPanelBounds.contains(event.getPoint()))
                    {
                        closePreviewAndSyncAlbum();
                    }
                }

                @Override
                public void mouseExited(MouseEvent event)
                {
                    targetYaw = 0.0;
                    targetPitch = 0.0;
                    ensureMotionTimer();
                }
            });
            addMouseWheelListener(event -> {
                if (!isVisible())
                {
                    return;
                }
                if (informationPanelBounds.contains(event.getPoint()))
                {
                    int maxScroll = Math.max(0, informationContentHeight - informationViewportHeight);
                    if (maxScroll <= 0)
                    {
                        return;
                    }
                    int delta = (int) Math.round(event.getPreciseWheelRotation() * 42.0);
                    informationScrollOffset = Math.max(0,
                        Math.min(maxScroll, informationScrollOffset + delta));
                    repaint(informationPanelBounds);
                    event.consume();
                    return;
                }
                int direction = event.getPreciseWheelRotation() > 0.0 ? 1 : -1;
                move(direction);
                event.consume();
            });
            addMouseMotionListener(new MouseMotionAdapter()
            {
                @Override
                public void mouseMoved(MouseEvent event)
                {
                    Rectangle card = baseCardRectangle();
                    double nx = clamp((event.getX() - card.getCenterX()) / (card.width * 0.72));
                    double ny = clamp((event.getY() - card.getCenterY()) / (card.height * 0.72));
                    double yawDirection = isInteractiveBackFacing() ? -1.0 : 1.0;
                    targetYaw = nx * maxYaw * yawDirection;
                    targetPitch = -ny * maxPitch;
                    ensureMotionTimer();
                }
            });
            registerKeyAction(KeyEvent.VK_ESCAPE, "close-preview", this::closePreviewAndSyncAlbum);
            registerKeyAction(KeyEvent.VK_LEFT, "previous-preview", () -> move(-1));
            registerKeyAction(KeyEvent.VK_RIGHT, "next-preview", () -> move(1));
            registerKeyAction(KeyEvent.VK_A, "previous-preview-a", () -> move(-1));
            registerKeyAction(KeyEvent.VK_D, "next-preview-d", () -> move(1));
            registerKeyAction(KeyEvent.VK_HOME, "first-preview", () -> showCard(0));
            registerKeyAction(KeyEvent.VK_END, "last-preview", () -> showCard(filteredCards.size() - 1));
        }

        private void installOnFrame()
        {
            if (frame.getGlassPane() != this)
            {
                frame.setGlassPane(this);
            }
        }

        private void registerKeyAction(int keyCode, String name, Runnable action)
        {
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(keyCode, 0), name);
            getActionMap().put(name, new javax.swing.AbstractAction()
            {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(java.awt.event.ActionEvent event)
                {
                    action.run();
                }
            });
        }

        private void showCard(int index)
        {
            if (index < 0 || index >= filteredCards.size())
            {
                return;
            }
            installOnFrame();
            filteredIndex = index;
            CardDefinition selectedCard = filteredCards.get(index);
            lastSelectedCardId = selectedCard.getCardId();
            gridPanel.setSelectedCardId(lastSelectedCardId);
            if (recentUnlockCardIds.contains(selectedCard.getCardId()))
            {
                inspectedRecentCardIds.add(selectedCard.getCardId());
                gridPanel.markInspected(selectedCard.getCardId());
                updateNavigationState();
            }
            informationScrollOffset = 0;
            frontVisible = true;
            targetYaw = 0.0;
            targetPitch = 0.0;
            currentYaw = 0.0;
            currentPitch = 0.0;
            foilVisible = false;
            stopPreviewGlint();
            stopFlipAnimation();
            setVisible(true);
            requestFocusInWindow();
            refreshContents();
        }

        private void move(int delta)
        {
            int candidate = filteredIndex + delta;
            if (candidate < 0 || candidate >= filteredCards.size())
            {
                return;
            }
            filteredIndex = candidate;
            CardDefinition selectedCard = filteredCards.get(candidate);
            lastSelectedCardId = selectedCard.getCardId();
            gridPanel.setSelectedCardId(lastSelectedCardId);
            if (recentUnlockCardIds.contains(selectedCard.getCardId()))
            {
                inspectedRecentCardIds.add(selectedCard.getCardId());
                gridPanel.markInspected(selectedCard.getCardId());
                updateNavigationState();
            }
            informationScrollOffset = 0;
            frontVisible = true;
            targetYaw = 0.0;
            targetPitch = 0.0;
            foilVisible = false;
            stopPreviewGlint();
            stopFlipAnimation();
            refreshContents();
        }

        private void refreshContents()
        {
            if (filteredCards.isEmpty() || filteredIndex < 0 || filteredIndex >= filteredCards.size())
            {
                hidePreview();
                return;
            }
            CardDefinition card = filteredCards.get(filteredIndex);
            boolean owned = ownedCardIds.contains(card.getCardId());
            boolean foil = foilCardIds.contains(card.getCardId());
            boolean foilAccess = foilAccessCardIds.contains(card.getCardId());
            foilVisible = foil;
            stopPreviewGlint();
            stopFlipAnimation();
            int requestedRevision = ++renderRevision;
            loading = true;
            front = null;
            back = null;
            foilVisible = false;
            repaint();
            if (previewRenderTask != null)
            {
                previewRenderTask.cancel(true);
            }
            final int requestedIndex = filteredIndex;
            previewRenderTask = previewRenderer.submit(() -> {
                if (Thread.currentThread().isInterrupted())
                {
                    return;
                }
                BufferedImage renderedFront = renderFace(
                    card,
                    owned,
                    foil,
                    foilAccess,
                    PREVIEW_WIDTH,
                    PREVIEW_HEIGHT);
                if (Thread.currentThread().isInterrupted())
                {
                    return;
                }
                BufferedImage renderedBack = CardUiAssets.cardBack(PREVIEW_WIDTH, PREVIEW_HEIGHT);
                SwingUtilities.invokeLater(() -> {
                    if (requestedRevision == renderRevision && isVisible())
                    {
                        loading = false;
                        front = renderedFront;
                        back = renderedBack;
                        foilVisible = foil;
                        resetPreviewGlintSchedule();
                        repaint();
                    }
                });
                if (!Thread.currentThread().isInterrupted())
                {
                    prefetchLargeNeighbour(requestedIndex - 1);
                    prefetchLargeNeighbour(requestedIndex + 1);
                }
            });
        }

        private void prefetchLargeNeighbour(int index)
        {
            if (index < 0 || index >= filteredCards.size())
            {
                return;
            }
            CardDefinition card = filteredCards.get(index);
            renderFace(
                card,
                ownedCardIds.contains(card.getCardId()),
                foilCardIds.contains(card.getCardId()),
                foilAccessCardIds.contains(card.getCardId()),
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT);
        }

        private void closePreviewAndSyncAlbum()
        {
            int selectedIndex = filteredIndex;
            String selectedCardId = selectedIndex >= 0 && selectedIndex < filteredCards.size()
                ? filteredCards.get(selectedIndex).getCardId()
                : lastSelectedCardId;
            int selectedPage = selectedIndex < 0 ? pageIndex : selectedIndex / PAGE_SIZE;
            hidePreview();
            lastSelectedCardId = selectedCardId == null ? "" : selectedCardId;
            gridPanel.setSelectedCardId(lastSelectedCardId);
            if (selectedPage != pageIndex)
            {
                pageIndex = selectedPage;
                refreshPage();
            }
            else
            {
                gridPanel.repaintSelectedCard();
            }
        }

        private void hidePreview()
        {
            renderRevision++;
            if (previewRenderTask != null)
            {
                previewRenderTask.cancel(true);
                previewRenderTask = null;
            }
            motionTimer.stop();
            stopPreviewGlint();
            stopFlipAnimation();
            setVisible(false);
            filteredIndex = -1;
            loading = false;
            front = null;
            back = null;
            paintedCard = new Polygon();
            informationPanelBounds = new Rectangle();
            wikiButtonBounds = new Rectangle();
            previousPreviewButtonBounds = new Rectangle();
            nextPreviewButtonBounds = new Rectangle();
            informationScrollOffset = 0;
            informationContentHeight = 0;
            informationViewportHeight = 0;
            paintedCardClip = new Path2D.Double();
            topLeftCorner = new Point2D.Double();
            topRightCorner = new Point2D.Double();
            bottomRightCorner = new Point2D.Double();
            bottomLeftCorner = new Point2D.Double();
            targetYaw = 0.0;
            targetPitch = 0.0;
            currentYaw = 0.0;
            currentPitch = 0.0;
            previewAmbientTwinklePhase = 0.0f;
            flipProgress = -1f;
            flipSideSwapped = false;
        }


        private void startFlipAnimation()
        {
            if (loading || front == null || back == null || flipAnimationTimer.isRunning())
            {
                return;
            }
            stopPreviewGlint();
            targetYaw = 0.0;
            targetPitch = 0.0;
            currentYaw = 0.0;
            currentPitch = 0.0;
            motionTimer.stop();
            flipAnimationStartedAtNanos = System.nanoTime();
            flipProgress = 0f;
            flipSideSwapped = false;
            flipAnimationTimer.restart();
            repaint();
        }

        private void advanceFlipAnimation()
        {
            long elapsedNanos = System.nanoTime() - flipAnimationStartedAtNanos;
            flipProgress = elapsedNanos / (PREVIEW_FLIP_DURATION_MS * 1_000_000f);
            if (!flipSideSwapped && flipProgress >= 0.5f)
            {
                frontVisible = !frontVisible;
                flipSideSwapped = true;
            }
            if (flipProgress >= 1f)
            {
                stopFlipAnimation();
                if (frontVisible)
                {
                    resetPreviewGlintSchedule();
                }
            }
            repaint();
        }

        private void stopFlipAnimation()
        {
            flipAnimationTimer.stop();
            flipProgress = -1f;
            flipSideSwapped = false;
        }

        private boolean isInteractiveBackFacing()
        {
            return flipProgress < 0f && isBackFacingForProjection();
        }

        private boolean isBackFacingForProjection()
        {
            return Math.cos(currentYaw + flipYawOffset()) < 0.0;
        }

        private void ensureMotionTimer()
        {
            if (!motionTimer.isRunning())
            {
                motionTimer.start();
            }
        }

        private void animateMotion()
        {
            currentYaw += (targetYaw - currentYaw) * EASE;
            currentPitch += (targetPitch - currentPitch) * EASE;
            if (Math.abs(targetYaw - currentYaw) < 0.0004
                && Math.abs(targetPitch - currentPitch) < 0.0004)
            {
                currentYaw = targetYaw;
                currentPitch = targetPitch;
                motionTimer.stop();
            }
            repaint();
        }

        private double clamp(double value)
        {
            return Math.max(-1.0, Math.min(1.0, value));
        }

        private Rectangle baseCardRectangle()
        {
            int panelWidth = informationPanelWidth();
            int navigationSpan = PREVIEW_NAVIGATION_SIZE + PREVIEW_NAVIGATION_GAP;
            int combinedWidth = navigationSpan + PREVIEW_WIDTH + CARD_PANEL_GAP + panelWidth + navigationSpan;
            int layoutLeft = Math.max(OVERLAY_MARGIN, (getWidth() - combinedWidth) / 2);
            int left = layoutLeft + navigationSpan;
            int top = Math.max(OVERLAY_MARGIN, (getHeight() - PREVIEW_HEIGHT) / 2);
            return new Rectangle(left, top, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        }

        private int informationPanelWidth()
        {
            int maxWidth = Math.min(330, Math.max(250, getWidth() / 3));
            int available = Math.max(250,
                getWidth() - (OVERLAY_MARGIN * 2) - PREVIEW_WIDTH - CARD_PANEL_GAP
                    - ((PREVIEW_NAVIGATION_SIZE + PREVIEW_NAVIGATION_GAP) * 2));
            return Math.max(250, Math.min(maxWidth, available));
        }

        private Rectangle calculateInformationPanelBounds()
        {
            Rectangle card = baseCardRectangle();
            int width = informationPanelWidth();
            int rightReserve = OVERLAY_MARGIN + PREVIEW_NAVIGATION_GAP + PREVIEW_NAVIGATION_SIZE;
            int availableHeight = Math.max(320, getHeight() - (OVERLAY_MARGIN * 2));
            int height = Math.min(PREVIEW_HEIGHT, availableHeight);
            int idealX = card.x + card.width + CARD_PANEL_GAP;
            int maxX = getWidth() - width - rightReserve;
            int x = Math.max(card.x + card.width + 12, Math.min(maxX, idealX));
            int maxY = Math.max(OVERLAY_MARGIN, getHeight() - OVERLAY_MARGIN - height);
            int y = Math.min(maxY, Math.max(OVERLAY_MARGIN, (getHeight() - height) / 2));
            return new Rectangle(x, y, width, height);
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                g.setColor(new Color(0, 0, 0, 178));
                g.fillRect(0, 0, getWidth(), getHeight());
                BufferedImage image = frontVisible ? front : back;
                if (image == null)
                {
                    if (loading)
                    {
                        g.setColor(new Color(205, 205, 205));
                        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
                        String text = "Preparing preview...";
                        int x = (getWidth() - g.getFontMetrics().stringWidth(text)) / 2;
                        g.drawString(text, x, getHeight() / 2);
                    }
                    return;
                }
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                drawProjectedCard(g, image);
                drawPreviewNavigation(g);
                drawInformationPanel(g);
            }
            finally
            {
                g.dispose();
            }
        }



        private void drawPreviewNavigation(Graphics2D g)
        {
            Rectangle card = baseCardRectangle();
            Rectangle panel = calculateInformationPanelBounds();
            int previousX = Math.max(OVERLAY_MARGIN, card.x - PREVIEW_NAVIGATION_GAP - PREVIEW_NAVIGATION_SIZE);
            int previousY = Math.min(getHeight() - OVERLAY_MARGIN - PREVIEW_NAVIGATION_SIZE,
                card.y + Math.max(0, (card.height - PREVIEW_NAVIGATION_SIZE) / 2));
            previousPreviewButtonBounds = new Rectangle(
                previousX,
                previousY,
                PREVIEW_NAVIGATION_SIZE,
                PREVIEW_NAVIGATION_SIZE);

            int nextX = Math.min(
                getWidth() - OVERLAY_MARGIN - PREVIEW_NAVIGATION_SIZE,
                panel.x + panel.width + PREVIEW_NAVIGATION_GAP);
            int nextY = Math.min(getHeight() - OVERLAY_MARGIN - PREVIEW_NAVIGATION_SIZE,
                panel.y + Math.max(0, (panel.height - PREVIEW_NAVIGATION_SIZE) / 2));
            nextPreviewButtonBounds = new Rectangle(
                nextX,
                nextY,
                PREVIEW_NAVIGATION_SIZE,
                PREVIEW_NAVIGATION_SIZE);

            drawPreviewNavigationButton(g, previousPreviewButtonBounds, false, filteredIndex > 0);
            drawPreviewNavigationButton(g, nextPreviewButtonBounds, true,
                filteredIndex >= 0 && filteredIndex + 1 < filteredCards.size());
        }

        private void drawPreviewNavigationButton(
            Graphics2D g,
            Rectangle bounds,
            boolean pointsRight,
            boolean enabled)
        {
            g.setColor(enabled ? new Color(24, 26, 29, 220) : new Color(24, 26, 29, 90));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12);
            g.setColor(enabled ? new Color(218, 164, 77, 230) : new Color(110, 110, 110, 100));
            g.setStroke(new BasicStroke(1.3f));
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12);
            int centreX = bounds.x + bounds.width / 2;
            int centreY = bounds.y + bounds.height / 2;
            int direction = pointsRight ? 1 : -1;
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(centreX - direction * 5, centreY - 9);
            arrow.lineTo(centreX + direction * 5, centreY);
            arrow.lineTo(centreX - direction * 5, centreY + 9);
            g.setColor(enabled ? new Color(247, 224, 184) : new Color(125, 125, 125));
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(arrow);
        }

        private void drawInformationPanel(Graphics2D g)
        {
            if (filteredIndex < 0 || filteredIndex >= filteredCards.size())
            {
                informationPanelBounds = new Rectangle();
                wikiButtonBounds = new Rectangle();
                return;
            }
            CardDefinition card = filteredCards.get(filteredIndex);
            informationPanelBounds = calculateInformationPanelBounds();
            Rectangle panel = informationPanelBounds;

            Graphics2D panelGraphics = (Graphics2D) g.create();
            try
            {
                panelGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                panelGraphics.setColor(new Color(22, 24, 27, 238));
                panelGraphics.fillRoundRect(panel.x, panel.y, panel.width, panel.height, 16, 16);
                panelGraphics.setColor(new Color(113, 93, 58, 210));
                panelGraphics.setStroke(new BasicStroke(1.2f));
                panelGraphics.drawRoundRect(panel.x, panel.y, panel.width, panel.height, 16, 16);

                int left = panel.x + 14;
                int right = panel.x + panel.width - 14;
                int y = panel.y + 24;

                panelGraphics.setFont(new Font(Font.DIALOG, Font.BOLD, 18));
                panelGraphics.setColor(new Color(241, 184, 74));
                y = drawWrappedText(panelGraphics, card.getDisplayName(), left, y, right - left, 21, 2);

                panelGraphics.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
                panelGraphics.setColor(new Color(174, 174, 174));
                panelGraphics.drawString((filteredIndex + 1) + " / " + filteredCards.size(), left, y + 1);
                y += 18;

                int buttonHeight = 34;
                wikiButtonBounds = new Rectangle(
                    left,
                    panel.y + panel.height - buttonHeight - 12,
                    right - left,
                    buttonHeight);
                int viewportTop = y + 3;
                int viewportBottom = wikiButtonBounds.y - 9;
                informationViewportHeight = Math.max(0, viewportBottom - viewportTop);

                Graphics2D contentGraphics = (Graphics2D) panelGraphics.create();
                try
                {
                    contentGraphics.clipRect(left, viewportTop, right - left, informationViewportHeight);
                    int contentY = viewportTop + 12 - informationScrollOffset;
                    contentY = drawDetailContent(contentGraphics, card, left, right, contentY);
                    informationContentHeight = Math.max(0, contentY - (viewportTop + 12 - informationScrollOffset));
                }
                finally
                {
                    contentGraphics.dispose();
                }

                int maxScroll = Math.max(0, informationContentHeight - informationViewportHeight);
                if (informationScrollOffset > maxScroll)
                {
                    informationScrollOffset = maxScroll;
                }
                drawInformationScrollIndicator(panelGraphics, right, viewportTop,
                    informationViewportHeight, maxScroll);

                panelGraphics.setColor(new Color(92, 68, 35));
                panelGraphics.fillRoundRect(
                    wikiButtonBounds.x,
                    wikiButtonBounds.y,
                    wikiButtonBounds.width,
                    wikiButtonBounds.height,
                    10,
                    10);
                panelGraphics.setColor(new Color(218, 164, 77));
                panelGraphics.drawRoundRect(
                    wikiButtonBounds.x,
                    wikiButtonBounds.y,
                    wikiButtonBounds.width,
                    wikiButtonBounds.height,
                    10,
                    10);
                panelGraphics.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
                panelGraphics.setColor(new Color(247, 224, 184));
                String label = "Open OSRS Wiki";
                int labelX = wikiButtonBounds.x
                    + (wikiButtonBounds.width - panelGraphics.getFontMetrics().stringWidth(label)) / 2;
                int labelY = wikiButtonBounds.y
                    + (wikiButtonBounds.height + panelGraphics.getFontMetrics().getAscent()
                    - panelGraphics.getFontMetrics().getDescent()) / 2;
                panelGraphics.drawString(label, labelX, labelY);
            }
            finally
            {
                panelGraphics.dispose();
            }
        }

        private int drawDetailContent(
            Graphics2D panelGraphics,
            CardDefinition card,
            int left,
            int right,
            int y)
        {
            y = drawInfoRow(panelGraphics, "Acquired", acquiredAt(card), left, right, y);
            boolean foilAccess = foilAccessCardIds.contains(card.getCardId());
            y = drawInfoRow(panelGraphics, "Collection status",
                ownedCardIds.contains(card.getCardId())
                    ? (foilCardIds.contains(card.getCardId()) ? "Owned foil" : "Owned")
                    : foilAccess ? "Usable via foil — not owned" : "Locked",
                left, right, y);
            y = drawInfoRow(panelGraphics, "Foil reward",
                FoilRewardText.potentialSummary(
                    foilRewardRegistry, catalogue, card.getCardId()),
                left, right, y, Integer.MAX_VALUE);
            if (foilAccess && state != null)
            {
                FoilEntitlementSnapshot currentEntitlements =
                    foilEntitlementResolver.resolve(
                        state.getOwnedCardIds(),
                        state.getFoilCardIds());
                y = drawInfoRow(panelGraphics, "Foil access source",
                    FoilRewardText.accessSourceSummary(
                        currentEntitlements, catalogue, card.getCardId(), 3),
                    left, right, y);
            }
            if (ownedCardIds.contains(card.getCardId()))
            {
                String source = acquisitionSource(card);
                if (!source.isEmpty())
                {
                    y = drawInfoRow(panelGraphics, "Source", source, left, right, y);
                }
                int duplicatePulls = activity.getDuplicateCount(card.getCardId());
                if (duplicatePulls > 0)
                {
                    y = drawInfoRow(panelGraphics, "Duplicates",
                        Integer.toString(duplicatePulls), left, right, y);
                }
            }
            CardDetailMetadata.Detail detail = detailMetadata.detail(card);
            if (card.getCardType() == CardType.ITEM)
            {
                String actions = detailMetadata.itemActions(card);
                String requirements = detail.getLevelRequirements();
                if (requirements.isEmpty() && !isWearable(actions))
                {
                    requirements = "Not wearable";
                }
                y = drawInfoRow(panelGraphics, "Requirements",
                    valueOrUnavailable(requirements), left, right, y);
                if (!detail.getEquipmentSlot().isEmpty())
                {
                    y = drawInfoRow(panelGraphics, "Equipment slot",
                        detail.getEquipmentSlot(), left, right, y);
                }
                if (!detail.getWeaponType().isEmpty())
                {
                    y = drawInfoRow(panelGraphics, "Weapon type",
                        detail.getWeaponType(), left, right, y);
                }
                if (!detail.getAttackSpeed().isEmpty())
                {
                    y = drawInfoRow(panelGraphics, "Attack speed",
                        formatAttackSpeed(detail.getAttackSpeed()), left, right, y);
                }
                y = drawInfoRow(panelGraphics, "Quest relevance",
                    questValue(detail.getQuest()), left, right, y);
                if (!actions.isEmpty())
                {
                    y = drawInfoRow(panelGraphics, "Usable actions", actions, left, right, y);
                }
            }
            else
            {
                String combatLevel = detailMetadata.combatLevel(card);
                if (!combatLevel.isEmpty() && !"0".equals(combatLevel))
                {
                    y = drawInfoRow(panelGraphics, "Combat level", combatLevel, left, right, y);
                    y = drawInfoRow(panelGraphics, "Hitpoints",
                        valueOrUnavailable(detail.getHitpoints()), left, right, y);
                    if (!detail.getMaxHit().isEmpty())
                    {
                        y = drawInfoRow(panelGraphics, "Max hit",
                            detail.getMaxHit(), left, right, y);
                    }
                    if (!detail.getAggression().isEmpty())
                    {
                        y = drawInfoRow(panelGraphics, "Aggression",
                            detail.getAggression(), left, right, y);
                    }
                    if (!detail.getHazards().isEmpty())
                    {
                        y = drawInfoRow(panelGraphics, "Hazards",
                            detail.getHazards(), left, right, y);
                    }
                    if (!detail.getSlayerRequirement().isEmpty())
                    {
                        y = drawInfoRow(panelGraphics, "Slayer level",
                            detail.getSlayerRequirement(), left, right, y);
                    }
                    if (!detail.getAttackStyles().isEmpty())
                    {
                        y = drawInfoRow(panelGraphics, "Attack methods",
                            detail.getAttackStyles(), left, right, y);
                    }
                    String uniqueDrops = detail.getUniqueDrops();
                    if (uniqueDrops.isEmpty())
                    {
                        uniqueDrops = sourceUniqueDrops(card);
                    }
                    if (!uniqueDrops.isEmpty())
                    {
                        y = drawInfoRow(panelGraphics, "Unique drops",
                            uniqueDrops, left, right, y);
                    }
                }
                y = drawInfoRow(panelGraphics, "Quest relevance",
                    questValue(detail.getQuest()), left, right, y);
            }
            if (!detail.getNotes().isEmpty())
            {
                y = drawInfoRow(panelGraphics, "Useful note", detail.getNotes(), left, right, y);
            }

            return y + 4;
        }

        private String sourceUniqueDrops(CardDefinition source)
        {
            List<String> names = new ArrayList<>();
            for (FoilRewardGrant grant
                : foilRewardRegistry.getRewardsForSource(source.getCardId()))
            {
                if (grant.getKind() != FoilRewardKind.SOURCE_UNIQUES)
                {
                    continue;
                }
                catalogue.findCard(grant.getTargetCardId())
                    .map(CardDefinition::getDisplayName)
                    .ifPresent(names::add);
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return String.join(", ", names);
        }

        private void drawInformationScrollIndicator(
            Graphics2D g,
            int right,
            int top,
            int viewportHeight,
            int maxScroll)
        {
            if (maxScroll <= 0 || viewportHeight <= 0)
            {
                return;
            }
            int trackX = right - 3;
            int thumbHeight = Math.max(28,
                (int) Math.round(viewportHeight * (viewportHeight
                    / (double) (viewportHeight + maxScroll))));
            int travel = Math.max(1, viewportHeight - thumbHeight);
            int thumbY = top + (int) Math.round(travel
                * (informationScrollOffset / (double) maxScroll));
            g.setColor(new Color(72, 72, 72, 150));
            g.fillRoundRect(trackX, top, 3, viewportHeight, 3, 3);
            g.setColor(new Color(191, 148, 75, 210));
            g.fillRoundRect(trackX, thumbY, 3, thumbHeight, 3, 3);
        }

        private String acquiredAt(CardDefinition card)
        {
            if (!ownedCardIds.contains(card.getCardId()))
            {
                return "Not acquired";
            }
            return activity.findUnlock(card.getCardId())
                .map(CardUnlockRecord::getOccurredAt)
                .map(instant -> DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(instant))
                .orElse("Acquired before history tracking");
        }

        private String acquisitionSource(CardDefinition card)
        {
            return activity.findUnlock(card.getCardId())
                .map(CardUnlockRecord::getSource)
                .map(source -> {
                    switch (source)
                    {
                        case STARTER:
                            return "Starter pack";
                        case PACK:
                            return "Card pack";
                        case NEXUS:
                            return "Nexus unlock";
                        case PROGRESSION_TRACK:
                            return "Progression track";
                        default:
                            return "";
                    }
                })
                .orElse("");
        }

        private String questValue(String quest)
        {
            if (quest == null || quest.trim().isEmpty())
            {
                return "Not yet verified";
            }
            return quest;
        }

        private String formatAttackSpeed(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return "";
        }
        try
        {
            int ticks = Integer.parseInt(value.trim());
            return ticks + " ticks (" + String.format(java.util.Locale.ROOT, "%.1f", ticks * 0.6) + " sec)";
        }
        catch (NumberFormatException ignored)
        {
            return value + " ticks";
        }
    }

    private String valueOrUnavailable(String value)
        {
            return value == null || value.trim().isEmpty()
                ? "Not yet available"
                : value;
        }

        private int drawInfoRow(
            Graphics2D g,
            String label,
            String value,
            int left,
            int right,
            int y)
        {
            return drawInfoRow(g, label, value, left, right, y, 5);
        }

        private int drawInfoRow(
            Graphics2D g,
            String label,
            String value,
            int left,
            int right,
            int y,
            int maxLines)
        {
            final int availableWidth = right - left;
            final Font labelFont = new Font(Font.DIALOG, Font.BOLD, 9);
            g.setFont(labelFont);
            final int measuredLabelWidth = g.getFontMetrics().stringWidth(
                label.toUpperCase(Locale.ROOT));
            final int labelWidth = Math.min(132, Math.max(104, measuredLabelWidth));
            final int gap = 10;
            final int valueX = left + labelWidth + gap;
            final int valueWidth = right - valueX;

            g.setColor(new Color(160, 160, 160));
            g.drawString(label.toUpperCase(Locale.ROOT), left, y);

            if (valueWidth < 104)
            {
                int valueY = y + 13;
                g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
                g.setColor(new Color(224, 224, 224));
                return drawWrappedText(g, value, left, valueY, availableWidth, 14, maxLines) + 4;
            }

            g.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
            g.setColor(new Color(224, 224, 224));
            int endY = drawWrappedText(g, value, valueX, y, valueWidth, 14, maxLines);
            return Math.max(y + 14, endY) + 4;
        }

        private boolean isWearable(String actions)
        {
            String normalized = actions == null
                ? ""
                : actions.toLowerCase(Locale.ROOT);
            return normalized.contains("wear")
                || normalized.contains("wield")
                || normalized.contains("equip");
        }

        private int drawWrappedText(
            Graphics2D g,
            String text,
            int x,
            int y,
            int maxWidth,
            int lineHeight,
            int maxLines)
        {
            String[] words = text.trim().split("\\s+");
            StringBuilder line = new StringBuilder();
            int currentY = y;
            int lines = 0;
            for (String word : words)
            {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (g.getFontMetrics().stringWidth(candidate) <= maxWidth)
                {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (line.length() > 0)
                {
                    g.drawString(line.toString(), x, currentY);
                    currentY += lineHeight;
                    lines++;
                }
                line.setLength(0);
                line.append(word);
                if (lines >= maxLines)
                {
                    break;
                }
            }
            if (line.length() > 0 && lines < maxLines)
            {
                String finalLine = line.toString();
                if (g.getFontMetrics().stringWidth(finalLine) > maxWidth)
                {
                    while (finalLine.length() > 1
                        && g.getFontMetrics().stringWidth(finalLine + "…") > maxWidth)
                    {
                        finalLine = finalLine.substring(0, finalLine.length() - 1);
                    }
                    finalLine += "…";
                }
                g.drawString(finalLine, x, currentY);
                currentY += lineHeight;
            }
            return currentY;
        }

        private String pretty(String value)
        {
            String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }

        private void openCurrentCardWiki()
        {
            if (filteredIndex < 0 || filteredIndex >= filteredCards.size())
            {
                return;
            }
            CardDefinition card = filteredCards.get(filteredIndex);
            try
            {
                String query = URLEncoder.encode(card.getDisplayName(), StandardCharsets.UTF_8.name());
                URI uri = URI.create("https://oldschool.runescape.wiki/w/Special:Search?search=" + query);
                if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                {
                    throw new IllegalStateException("Desktop browsing is unavailable.");
                }
                Desktop.getDesktop().browse(uri);
            }
            catch (Exception exception)
            {
                JOptionPane.showMessageDialog(
                    frame,
                    "Unable to open the OSRS Wiki in your default browser.",
                    "OSRS Wiki",
                    JOptionPane.WARNING_MESSAGE);
            }
        }

        private void drawProjectedCard(Graphics2D g, BufferedImage image)
        {
            Rectangle base = baseCardRectangle();
            double[][][] points = new double[MESH_ROWS + 1][MESH_COLUMNS + 1][2];
            for (int row = 0; row <= MESH_ROWS; row++)
            {
                double v = row / (double) MESH_ROWS;
                for (int column = 0; column <= MESH_COLUMNS; column++)
                {
                    double u = column / (double) MESH_COLUMNS;
                    points[row][column] = projectPoint(
                        (u - 0.5) * PREVIEW_WIDTH,
                        (v - 0.5) * PREVIEW_HEIGHT,
                        base.getCenterX(),
                        base.getCenterY());
                }
            }
            double[] tl = points[0][0];
            double[] tr = points[0][MESH_COLUMNS];
            double[] br = points[MESH_ROWS][MESH_COLUMNS];
            double[] bl = points[MESH_ROWS][0];
            paintedCard = new Polygon(
                new int[]{round(tl[0]), round(tr[0]), round(br[0]), round(bl[0])},
                new int[]{round(tl[1]), round(tr[1]), round(br[1]), round(bl[1])},
                4);
            Path2D.Double clipPath = new Path2D.Double();
            clipPath.moveTo(tl[0], tl[1]);
            clipPath.lineTo(tr[0], tr[1]);
            clipPath.lineTo(br[0], br[1]);
            clipPath.lineTo(bl[0], bl[1]);
            clipPath.closePath();
            paintedCardClip = clipPath;
            topLeftCorner = new Point2D.Double(tl[0], tl[1]);
            topRightCorner = new Point2D.Double(tr[0], tr[1]);
            bottomRightCorner = new Point2D.Double(br[0], br[1]);
            bottomLeftCorner = new Point2D.Double(bl[0], bl[1]);

            if (frontVisible)
            {
                Shape oldClip = g.getClip();
                Composite oldComposite = g.getComposite();
                g.clip(paintedCardClip);
                g.setComposite(AlphaComposite.SrcOver.derive(1.0f));
                g.setColor(sampleProjectedUnderlay(image));
                g.fillRect(base.x - 2, base.y - 2, base.width + 4, base.height + 4);
                g.setClip(oldClip);
                g.setComposite(oldComposite);
            }

            double sourceInsetX = 0.0;
            double sourceInsetY = 0.0;
            double sourceWidth = image.getWidth();
            double sourceHeight = image.getHeight();

            boolean reverseSourceX = isBackFacingForProjection();
            for (int row = 0; row < MESH_ROWS; row++)
            {
                double sy0 = sourceInsetY + row * sourceHeight / (double) MESH_ROWS;
                double sy1 = sourceInsetY + (row + 1) * sourceHeight / (double) MESH_ROWS;
                for (int column = 0; column < MESH_COLUMNS; column++)
                {
                    double sx0 = reverseSourceX
                        ? sourceInsetX + sourceWidth - column * sourceWidth / (double) MESH_COLUMNS
                        : sourceInsetX + column * sourceWidth / (double) MESH_COLUMNS;
                    double sx1 = reverseSourceX
                        ? sourceInsetX + sourceWidth - (column + 1) * sourceWidth / (double) MESH_COLUMNS
                        : sourceInsetX + (column + 1) * sourceWidth / (double) MESH_COLUMNS;
                    double[] p00 = points[row][column];
                    double[] p10 = points[row][column + 1];
                    double[] p11 = points[row + 1][column + 1];
                    double[] p01 = points[row + 1][column];
                    drawTriangle(g, image,
                        sx0, sy0, sx1, sy0, sx1, sy1,
                        p00[0], p00[1], p10[0], p10[1], p11[0], p11[1]);
                    drawTriangle(g, image,
                        sx0, sy0, sx1, sy1, sx0, sy1,
                        p00[0], p00[1], p11[0], p11[1], p01[0], p01[1]);
                }
            }

            Graphics2D edgeGraphics = (Graphics2D) g.create();
            try
            {
                edgeGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                edgeGraphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                edgeGraphics.setClip(null);
                Color edgeBase = sampleProjectedUnderlay(image);
                edgeGraphics.setColor(new Color(edgeBase.getRed(), edgeBase.getGreen(), edgeBase.getBlue(), 92));
                edgeGraphics.setStroke(new java.awt.BasicStroke(2.0f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                edgeGraphics.draw(paintedCardClip);
                edgeGraphics.setColor(new Color(255, 248, 228, 24));
                edgeGraphics.setStroke(new java.awt.BasicStroke(0.75f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                edgeGraphics.draw(paintedCardClip);
            }
            finally
            {
                edgeGraphics.dispose();
            }
            drawProjectedLighting(g);
            if (foilVisible && frontVisible)
            {
                drawProjectedAmbientFoilTwinkles(g, previewAmbientTwinklePhase);
                if (previewGlintProgress >= 0f)
                {
                    drawProjectedFoilGlint(g, previewGlintProgress);
                }
            }
        }

        private void drawProjectedFoilGlint(Graphics2D graphics, float progress)
        {
            float eased = progress * progress * (3f - 2f * progress);
            Rectangle bounds = paintedCard.getBounds();
            if (bounds.isEmpty())
            {
                return;
            }

            Point2D.Double sweepStart = cardPoint(0.10, 0.88);
            Point2D.Double sweepEnd = cardPoint(0.88, 0.12);
            Point2D.Double centre = new Point2D.Double(
                sweepStart.x + (sweepEnd.x - sweepStart.x) * eased,
                sweepStart.y + (sweepEnd.y - sweepStart.y) * eased);
            float orientationX = effectiveOrientationX();
            float orientationY = effectiveOrientationY();
            float sweepRadius = Math.max(bounds.width, bounds.height) * 0.34f;

            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                g.clip(paintedCardClip);

                float refractX = (float) (centre.x - orientationX * bounds.width * 0.06);
                float refractY = (float) (centre.y + orientationY * bounds.height * 0.04);

                g.setComposite(AlphaComposite.SrcOver.derive(0.27f));
                g.setPaint(new RadialGradientPaint(
                    refractX,
                    refractY,
                    sweepRadius,
                    new float[]{0f, 0.20f, 0.52f, 0.78f, 1f},
                    new Color[]{
                        new Color(255, 250, 232, 170),
                        new Color(255, 206, 236, 96),
                        new Color(148, 234, 255, 74),
                        new Color(255, 248, 220, 22),
                        new Color(255, 255, 255, 0)}));
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

                float secondaryX = refractX - sweepRadius * 0.26f;
                float secondaryY = refractY + sweepRadius * 0.18f;
                g.setComposite(AlphaComposite.SrcOver.derive(0.20f));
                g.setPaint(new RadialGradientPaint(
                    secondaryX,
                    secondaryY,
                    sweepRadius * 0.82f,
                    new float[]{0f, 0.28f, 0.62f, 1f},
                    new Color[]{
                        new Color(134, 246, 255, 92),
                        new Color(255, 255, 250, 74),
                        new Color(255, 173, 228, 36),
                        new Color(255, 255, 255, 0)}));
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

                float tertiaryX = refractX + sweepRadius * 0.22f;
                float tertiaryY = refractY - sweepRadius * 0.16f;
                g.setComposite(AlphaComposite.SrcOver.derive(0.18f));
                g.setPaint(new RadialGradientPaint(
                    tertiaryX,
                    tertiaryY,
                    sweepRadius * 0.68f,
                    new float[]{0f, 0.36f, 0.72f, 1f},
                    new Color[]{
                        new Color(255, 255, 245, 86),
                        new Color(255, 196, 232, 42),
                        new Color(142, 238, 255, 26),
                        new Color(255, 255, 255, 0)}));
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

                float lineStartX = refractX - sweepRadius * 0.85f;
                float lineStartY = refractY + sweepRadius * 0.55f;
                float lineEndX = refractX + sweepRadius * 0.80f;
                float lineEndY = refractY - sweepRadius * 0.70f;
                g.setComposite(AlphaComposite.SrcOver.derive(0.11f));
                g.setPaint(new LinearGradientPaint(
                    lineStartX,
                    lineStartY,
                    lineEndX,
                    lineEndY,
                    new float[]{0f, 0.18f, 0.34f, 0.50f, 0.68f, 0.84f, 1f},
                    new Color[]{
                        new Color(255, 255, 255, 0),
                        new Color(120, 245, 255, 0),
                        new Color(120, 245, 255, 52),
                        new Color(255, 250, 236, 128),
                        new Color(255, 180, 232, 50),
                        new Color(255, 255, 255, 0),
                        new Color(255, 255, 255, 0)}));
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            finally
            {
                g.dispose();
            }
        }

        private void drawProjectedAmbientFoilTwinkles(Graphics2D graphics, float phase)
        {
            Rectangle bounds = paintedCard.getBounds();
            if (bounds.isEmpty())
            {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                g.clip(paintedCardClip);
                Point2D.Double[] points = {
                    cardPoint(0.18, 0.22),
                    cardPoint(0.34, 0.36),
                    cardPoint(0.63, 0.28),
                    cardPoint(0.78, 0.46),
                    cardPoint(0.43, 0.58),
                    cardPoint(0.24, 0.74),
                    cardPoint(0.67, 0.78)};
                float[] offsets = {0.0f, 0.13f, 0.27f, 0.41f, 0.56f, 0.71f, 0.84f};
                for (int index = 0; index < points.length; index++)
                {
                    float local = phase + offsets[index];
                    local = local - (float) Math.floor(local);
                    float pulse = (float) Math.pow(Math.max(0f, Math.sin(local * Math.PI * 2.0)), 2.15);
                    if (pulse <= 0.03f)
                    {
                        continue;
                    }
                    Point2D.Double point = points[index];
                    int radius = Math.max(3, Math.round((float) bounds.width * (0.009f + pulse * 0.018f)));
                    g.setComposite(AlphaComposite.SrcOver.derive(0.28f * pulse));
                    g.setColor(new Color(255, 252, 244, Math.min(220, Math.round(190 * pulse))));
                    g.drawLine((int) Math.round(point.x - radius), (int) Math.round(point.y), (int) Math.round(point.x + radius), (int) Math.round(point.y));
                    g.drawLine((int) Math.round(point.x), (int) Math.round(point.y - radius), (int) Math.round(point.x), (int) Math.round(point.y + radius));
                    if (radius >= 4)
                    {
                        int diag = Math.max(2, Math.round(radius * 0.55f));
                        g.drawLine((int) Math.round(point.x - diag), (int) Math.round(point.y - diag), (int) Math.round(point.x + diag), (int) Math.round(point.y + diag));
                        g.drawLine((int) Math.round(point.x - diag), (int) Math.round(point.y + diag), (int) Math.round(point.x + diag), (int) Math.round(point.y - diag));
                    }
                }
            }
            finally
            {
                g.dispose();
            }
        }

        private double projectAlongNormal(Point2D.Double point, double normalX, double normalY)
        {
            return point.x * normalX + point.y * normalY;
        }

        private Point2D.Double cardPoint(double u, double v)
        {
            double topX = topLeftCorner.x + (topRightCorner.x - topLeftCorner.x) * u;
            double topY = topLeftCorner.y + (topRightCorner.y - topLeftCorner.y) * u;
            double bottomX = bottomLeftCorner.x + (bottomRightCorner.x - bottomLeftCorner.x) * u;
            double bottomY = bottomLeftCorner.y + (bottomRightCorner.y - bottomLeftCorner.y) * u;
            return new Point2D.Double(
                topX + (bottomX - topX) * v,
                topY + (bottomY - topY) * v);
        }

        private void resetPreviewGlintSchedule()
        {
            stopPreviewGlint();
            if (isVisible() && foilVisible && frontVisible && front != null)
            {
                previewGlintScheduleTimer.restart();
                previewAmbientTwinkleTimer.start();
            }
        }

        private void advancePreviewAmbientTwinkle()
        {
            previewAmbientTwinklePhase += 0.065f;
            if (previewAmbientTwinklePhase >= 1f)
            {
                previewAmbientTwinklePhase -= 1f;
            }
            repaint(paintedCard.getBounds());
        }

        private void stopPreviewGlint()
        {
            previewGlintScheduleTimer.stop();
            previewGlintAnimationTimer.stop();
            previewAmbientTwinkleTimer.stop();
            previewGlintProgress = -1f;
        }

        private void startPreviewGlint()
        {
            if (!isVisible() || !foilVisible || !frontVisible || front == null)
            {
                return;
            }
            previewGlintStartedAtNanos = System.nanoTime();
            previewGlintProgress = 0f;
            previewGlintAnimationTimer.restart();
        }

        private void advancePreviewGlint()
        {
            long elapsedNanos = System.nanoTime() - previewGlintStartedAtNanos;
            previewGlintProgress = elapsedNanos / (PREVIEW_GLINT_DURATION_MS * 1_000_000f);
            if (previewGlintProgress >= 1f)
            {
                previewGlintAnimationTimer.stop();
                previewGlintProgress = -1f;
            }
            repaint(paintedCard.getBounds());
        }

        private void drawProjectedLighting(Graphics2D g)
        {
            Shape oldClip = g.getClip();
            Composite oldComposite = g.getComposite();
            java.awt.Paint oldPaint = g.getPaint();
            try
            {
                g.clip(paintedCardClip);
                Rectangle bounds = paintedCard.getBounds();
                float orientationX = effectiveOrientationX();
                float orientationY = effectiveOrientationY();

                // Keep the light fixed in screen/world space, but distribute the
                // response across broad low-opacity lobes so no single hotspot
                // obscures the card artwork.
                float responseX = -orientationX;
                float responseY = orientationY;
                float tiltAmount = Math.min(
                    1f,
                    Math.abs(orientationX) * 0.72f + Math.abs(orientationY) * 0.52f);
                float radius = Math.max(bounds.width, bounds.height) * 1.08f;
                float highlightX = bounds.x + bounds.width * (0.40f + responseX * 0.24f);
                float highlightY = bounds.y + bounds.height * (0.34f + responseY * 0.18f);

                g.setComposite(AlphaComposite.SrcOver.derive(0.24f + tiltAmount * 0.08f));
                g.setPaint(new RadialGradientPaint(
                    highlightX,
                    highlightY,
                    radius,
                    new float[]{0.0f, 0.28f, 0.58f, 0.82f, 1.0f},
                    new Color[]{
                        new Color(255, 255, 247, 118),
                        new Color(255, 248, 226, 78),
                        new Color(255, 241, 216, 38),
                        new Color(255, 255, 250, 12),
                        new Color(255, 255, 250, 0)}));
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

                // A wide diagonal sheen retains clear tilt response while remaining
                // translucent enough for text and artwork to stay readable.
                float sheenCentre = 0.50f + responseX * 0.30f + responseY * 0.07f;
                float sheenStart = Math.max(-0.20f, sheenCentre - 0.38f);
                float sheenEnd = Math.min(1.20f, sheenCentre + 0.38f);
                float x1 = bounds.x + bounds.width * sheenStart;
                float x2 = bounds.x + bounds.width * sheenEnd;
                g.setComposite(AlphaComposite.SrcOver.derive(0.11f + tiltAmount * 0.08f));
                g.setPaint(new LinearGradientPaint(
                    x1,
                    bounds.y + bounds.height * 0.98f,
                    x2,
                    bounds.y + bounds.height * 0.02f,
                    new float[]{0f, 0.24f, 0.46f, 0.54f, 0.76f, 1f},
                    new Color[]{
                        new Color(255, 255, 255, 0),
                        new Color(184, 232, 255, 34),
                        new Color(255, 252, 238, 72),
                        new Color(255, 252, 238, 72),
                        new Color(255, 196, 232, 30),
                        new Color(255, 255, 255, 0)}));
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

                if (foilVisible && frontVisible)
                {
                    float cyanX = bounds.x + bounds.width * (0.30f + responseX * 0.24f);
                    float cyanY = bounds.y + bounds.height * (0.64f + responseY * 0.14f);
                    g.setComposite(AlphaComposite.SrcOver.derive(0.14f + tiltAmount * 0.04f));
                    g.setPaint(new RadialGradientPaint(
                        cyanX,
                        cyanY,
                        radius * 0.84f,
                        new float[]{0f, 0.42f, 0.76f, 1f},
                        new Color[]{
                            new Color(120, 237, 255, 112),
                            new Color(174, 229, 255, 62),
                            new Color(222, 247, 255, 18),
                            new Color(255, 255, 255, 0)}));
                    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

                    float magentaX = bounds.x + bounds.width * (0.68f + responseX * 0.18f);
                    float magentaY = bounds.y + bounds.height * (0.30f + responseY * 0.13f);
                    g.setComposite(AlphaComposite.SrcOver.derive(0.12f + tiltAmount * 0.04f));
                    g.setPaint(new RadialGradientPaint(
                        magentaX,
                        magentaY,
                        radius * 0.78f,
                        new float[]{0f, 0.44f, 0.78f, 1f},
                        new Color[]{
                            new Color(255, 170, 230, 104),
                            new Color(255, 210, 244, 54),
                            new Color(255, 237, 250, 16),
                            new Color(255, 255, 255, 0)}));
                    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                }

                float shadeStrength = 0.18f + Math.abs(orientationX) * 0.13f;
                float shadeX = orientationX >= 0f ? bounds.x + bounds.width : bounds.x;
                float shadeEndX = orientationX >= 0f
                    ? bounds.x + bounds.width * 0.38f
                    : bounds.x + bounds.width * 0.62f;
                g.setComposite(AlphaComposite.SrcOver.derive(shadeStrength));
                g.setPaint(new GradientPaint(
                    shadeX,
                    bounds.y + bounds.height * 0.72f,
                    new Color(0, 0, 0, 96),
                    shadeEndX,
                    bounds.y + bounds.height * 0.30f,
                    new Color(0, 0, 0, 0)));
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            finally
            {
                g.setPaint(oldPaint);
                g.setComposite(oldComposite);
                g.setClip(oldClip);
            }
        }


        private float effectiveOrientationX()
        {
            double totalYaw = currentYaw + flipYawOffset();
            double denominator = Math.max(0.001, Math.sin(maxYaw));
            return (float) clamp(Math.sin(totalYaw) / denominator);
        }

        private float effectiveOrientationY()
        {
            return (float) clamp(currentPitch / maxPitch);
        }

        private double flipYawOffset()
        {
            if (flipProgress < 0f)
            {
                return 0.0;
            }
            double phase = Math.max(0.0, Math.min(1.0, flipProgress));
            return Math.PI * phase;
        }

        private Color sampleProjectedUnderlay(BufferedImage image)
        {
            if (image == null)
            {
                return new Color(112, 88, 54);
            }
            int[][] samples = {
                {Math.max(0, image.getWidth() / 2), Math.max(0, image.getHeight() / 20)},
                {Math.max(0, image.getWidth() / 2), Math.max(0, image.getHeight() - image.getHeight() / 20 - 1)},
                {Math.max(0, image.getWidth() / 20), Math.max(0, image.getHeight() / 2)},
                {Math.max(0, image.getWidth() - image.getWidth() / 20 - 1), Math.max(0, image.getHeight() / 2)},
                {Math.max(0, image.getWidth() / 2), Math.max(0, image.getHeight() / 2)}
            };
            int red = 0;
            int green = 0;
            int blue = 0;
            int count = 0;
            for (int[] sample : samples)
            {
                int argb = image.getRGB(sample[0], sample[1]);
                int alpha = (argb >>> 24) & 0xff;
                if (alpha < 16)
                {
                    continue;
                }
                red += (argb >>> 16) & 0xff;
                green += (argb >>> 8) & 0xff;
                blue += argb & 0xff;
                count++;
            }
            if (count == 0)
            {
                return new Color(112, 88, 54);
            }
            return new Color(red / count, green / count, blue / count);
        }

        private double[] projectPoint(double x, double y, double centerX, double centerY)
        {
            double effectiveYaw = currentYaw + flipYawOffset();
            double cosPitch = Math.cos(currentPitch);
            double sinPitch = Math.sin(currentPitch);
            double cosYaw = Math.cos(effectiveYaw);
            double sinYaw = Math.sin(effectiveYaw);

            double y1 = y * cosPitch;
            double z1 = y * sinPitch;
            double x2 = x * cosYaw + z1 * sinYaw;
            double z2 = -x * sinYaw + z1 * cosYaw;
            double scale = CAMERA_DISTANCE / (CAMERA_DISTANCE - z2);
            return new double[]{centerX + x2 * scale, centerY + y1 * scale};
        }

        private void drawTriangle(
            Graphics2D g,
            BufferedImage image,
            double sx0, double sy0,
            double sx1, double sy1,
            double sx2, double sy2,
            double dx0, double dy0,
            double dx1, double dy1,
            double dx2, double dy2)
        {
            double determinant = sx0 * (sy1 - sy2)
                + sx1 * (sy2 - sy0)
                + sx2 * (sy0 - sy1);
            if (Math.abs(determinant) < 0.00001)
            {
                return;
            }
            double m00 = (dx0 * (sy1 - sy2) + dx1 * (sy2 - sy0) + dx2 * (sy0 - sy1)) / determinant;
            double m01 = (dx0 * (sx2 - sx1) + dx1 * (sx0 - sx2) + dx2 * (sx1 - sx0)) / determinant;
            double m02 = (dx0 * (sx1 * sy2 - sx2 * sy1)
                + dx1 * (sx2 * sy0 - sx0 * sy2)
                + dx2 * (sx0 * sy1 - sx1 * sy0)) / determinant;
            double m10 = (dy0 * (sy1 - sy2) + dy1 * (sy2 - sy0) + dy2 * (sy0 - sy1)) / determinant;
            double m11 = (dy0 * (sx2 - sx1) + dy1 * (sx0 - sx2) + dy2 * (sx1 - sx0)) / determinant;
            double m12 = (dy0 * (sx1 * sy2 - sx2 * sy1)
                + dy1 * (sx2 * sy0 - sx0 * sy2)
                + dy2 * (sx0 * sy1 - sx1 * sy0)) / determinant;

            java.awt.Shape oldClip = g.getClip();
            Path2D.Double clip = new Path2D.Double();
            clip.moveTo(dx0, dy0);
            clip.lineTo(dx1, dy1);
            clip.lineTo(dx2, dy2);
            clip.closePath();
            g.clip(clip);
            Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, new AffineTransform(m00, m10, m01, m11, m02, m12), null);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
            g.setClip(oldClip);
        }

        private int round(double value)
        {
            return (int) Math.round(value);
        }
    }

    private static final class FaceKey
    {
        private final String cardId;
        private final boolean owned;
        private final boolean foil;
        private final boolean foilAccess;
        private final int width;
        private final int height;

        private FaceKey(
            String cardId,
            boolean owned,
            boolean foil,
            boolean foilAccess,
            int width,
            int height)
        {
            this.cardId = Objects.requireNonNull(cardId, "cardId");
            this.owned = owned;
            this.foil = foil;
            this.foilAccess = foilAccess;
            this.width = width;
            this.height = height;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }
            if (!(other instanceof FaceKey))
            {
                return false;
            }
            FaceKey key = (FaceKey) other;
            return owned == key.owned
                && foil == key.foil
                && foilAccess == key.foilAccess
                && width == key.width
                && height == key.height
                && cardId.equals(key.cardId);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(
                cardId, owned, foil, foilAccess, width, height);
        }
    }

    private static final class PageKey
    {
        private final int page;
        private final int revision;

        private PageKey(int page, int revision)
        {
            this.page = page;
            this.revision = revision;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }
            if (!(other instanceof PageKey))
            {
                return false;
            }
            PageKey key = (PageKey) other;
            return page == key.page && revision == key.revision;
        }

        @Override
        public int hashCode()
        {
            return 31 * page + revision;
        }
    }

    private static final class BoundedLruMap<K, V> extends LinkedHashMap<K, V>
    {
        private static final long serialVersionUID = 1L;
        private final int limit;

        private BoundedLruMap(int limit)
        {
            super(16, 0.75f, true);
            this.limit = limit;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest)
        {
            return size() > limit;
        }
    }

    private static final class AlbumThreadFactory implements ThreadFactory
    {
        private final String threadName;

        private AlbumThreadFactory(String threadName)
        {
            this.threadName = threadName;
        }

        @Override
        public Thread newThread(Runnable runnable)
        {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        }
    }

    private enum CardTypeFilter
    {
        ALL("All types"),
        ITEM("Items"),
        COMBAT_NPC("Combat NPCs"),
        NON_COMBAT_NPC("Non-combat NPCs");

        private final String label;

        CardTypeFilter(String label)
        {
            this.label = label;
        }

        private boolean includes(
            CardDefinition card,
            CardDetailMetadata metadata)
        {
            switch (this)
            {
                case ITEM:
                    return card.getCardType() == CardType.ITEM;
                case COMBAT_NPC:
                    return card.getCardType() == CardType.NPC
                        && metadata.hasCombatLevel(card);
                case NON_COMBAT_NPC:
                    return card.getCardType() == CardType.NPC
                        && !metadata.hasCombatLevel(card);
                case ALL:
                default:
                    return true;
            }
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    private enum RarityFilter
    {
        ALL("All rarities", null),
        COMMON("Common", Rarity.COMMON),
        UNCOMMON("Uncommon", Rarity.UNCOMMON),
        RARE("Rare", Rarity.RARE),
        EPIC("Epic", Rarity.EPIC),
        LEGENDARY("Legendary", Rarity.LEGENDARY),
        MYTHIC("Mythic", Rarity.MYTHIC),
        GODLY("Godly", Rarity.GODLY);

        private final String label;
        private final Rarity rarity;

        RarityFilter(String label, Rarity rarity)
        {
            this.label = label;
            this.rarity = rarity;
        }

        private boolean includes(Rarity candidate)
        {
            return rarity == null || rarity == candidate;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    private enum CategoryFilter
    {
        ALL("All categories", null),
        COMBAT_METHOD("Combat method", CardCategory.COMBAT_METHOD),
        NPC_TARGET("NPC target (all)", CardCategory.NPC_TARGET),
        SUPPORT("Support", CardCategory.SUPPORT),
        GATHERING_TOOL("Gathering tool", CardCategory.GATHERING_TOOL),
        PROCESSING_INPUT("Processing input", CardCategory.PROCESSING_INPUT),
        UTILITY("Utility", CardCategory.UTILITY);

        private final String label;
        private final CardCategory category;

        CategoryFilter(String label, CardCategory category)
        {
            this.label = label;
            this.category = category;
        }

        private boolean includes(Set<CardCategory> categories)
        {
            return category == null || categories.contains(category);
        }

        @Override
        public String toString()
        {
            return label;
        }
    }


    private static final class AlbumCardRecord
    {
        private final CardDefinition card;
        private final String quest;
        private final String searchText;

        private AlbumCardRecord(CardDefinition card, String quest)
        {
            this.card = card;
            this.quest = quest == null ? "" : quest;
            this.searchText = (card.getDisplayName() + "\n"
                + card.getExamineText() + "\n" + this.quest)
                .toLowerCase(Locale.ROOT);
        }
    }

    private enum QuestFilter
    {
        ALL("All quest relevance"),
        QUEST_RELATED("Quest related"),
        NOT_QUEST_RELATED("No reviewed quest link");

        private final String label;

        QuestFilter(String label)
        {
            this.label = label;
        }

        private boolean includes(String questRelevance)
        {
            boolean reviewed = questRelevance != null && !questRelevance.trim().isEmpty();
            switch (this)
            {
                case QUEST_RELATED:
                    return reviewed;
                case NOT_QUEST_RELATED:
                    return !reviewed;
                case ALL:
                default:
                    return true;
            }
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    private enum SortMode
    {
        NAME_ASC("Name A-Z"),
        NAME_DESC("Name Z-A"),
        RARITY_HIGH("Rarity high-low"),
        RARITY_LOW("Rarity low-high"),
        OWNED_FIRST("Owned first"),
        MISSING_FIRST("Missing first"),
        FOIL_FIRST("Foil first"),
        NEWEST_UNLOCKED("Newest unlock");

        private final String label;

        SortMode(String label)
        {
            this.label = label;
        }

        private Comparator<CardDefinition> comparator(
            Set<String> owned,
            Set<String> foils,
            CollectionActivitySnapshot activity)
        {
            Comparator<CardDefinition> byName = Comparator.comparing(
                CardDefinition::getDisplayName,
                String.CASE_INSENSITIVE_ORDER);
            switch (this)
            {
                case NAME_DESC:
                    return byName.reversed();
                case RARITY_HIGH:
                    return Comparator.comparingInt((CardDefinition card) -> card.getRarity().ordinal())
                        .reversed().thenComparing(byName);
                case RARITY_LOW:
                    return Comparator.comparingInt((CardDefinition card) -> card.getRarity().ordinal())
                        .thenComparing(byName);
                case OWNED_FIRST:
                    return Comparator.comparing((CardDefinition card) -> !owned.contains(card.getCardId()))
                        .thenComparing(byName);
                case MISSING_FIRST:
                    return Comparator.comparing((CardDefinition card) -> owned.contains(card.getCardId()))
                        .thenComparing(byName);
                case FOIL_FIRST:
                    return Comparator.comparing((CardDefinition card) -> !foils.contains(card.getCardId()))
                        .thenComparing(byName);
                case NEWEST_UNLOCKED:
                    return Comparator.comparing(
                        (CardDefinition card) -> activity.findUnlock(card.getCardId())
                            .map(com.cardrestricted.collection.activity.CardUnlockRecord::getOccurredAt)
                            .orElse(java.time.Instant.EPOCH),
                        Comparator.reverseOrder())
                        .thenComparing(byName);
                case NAME_ASC:
                default:
                    return byName;
            }
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    private enum ArtworkFilter
    {
        ALL("All artwork"),
        PACKAGED("Packaged artwork"),
        MISSING("Missing artwork");

        private final String label;

        ArtworkFilter(String label)
        {
            this.label = label;
        }

        private boolean includes(CardArtworkProvider.ArtworkSource source)
        {
            switch (this)
            {
                case PACKAGED:
                    return source != CardArtworkProvider.ArtworkSource.NONE;
                case MISSING:
                    return source == CardArtworkProvider.ArtworkSource.NONE;
                case ALL:
                default:
                    return true;
            }
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    private enum OwnershipFilter
    {
        ALL("All cards"),
        OWNED("Owned only"),
        MISSING("Missing only"),
        FOIL("Foil only"),
        FOIL_ACCESS("Foil access"),
        RECENT("Recent unlocks");

        private final String label;

        OwnershipFilter(String label)
        {
            this.label = label;
        }

        private boolean includes(
            String cardId,
            Set<String> owned,
            Set<String> foils,
            Set<String> foilAccess,
            Set<String> recent)
        {
            switch (this)
            {
                case OWNED:
                    return owned.contains(cardId);
                case MISSING:
                    return !owned.contains(cardId);
                case FOIL:
                    return foils.contains(cardId);
                case FOIL_ACCESS:
                    return foilAccess.contains(cardId);
                case RECENT:
                    return recent.contains(cardId);
                case ALL:
                default:
                    return true;
            }
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    private static final class AlbumSlot
    {
        private final CardDefinition card;
        private final boolean owned;
        private final boolean foil;
        private final boolean foilAccess;
        private final boolean newUnlock;
        private final BufferedImage face;

        private AlbumSlot(
            CardDefinition card,
            boolean owned,
            boolean foil,
            boolean foilAccess,
            BufferedImage face)
        {
            this(card, owned, foil, foilAccess, false, face);
        }

        private AlbumSlot(
            CardDefinition card,
            boolean owned,
            boolean foil,
            boolean foilAccess,
            boolean newUnlock,
            BufferedImage face)
        {
            this.card = card;
            this.owned = owned;
            this.foil = foil;
            this.foilAccess = foilAccess;
            this.newUnlock = newUnlock;
            this.face = face;
        }
    }

    /** One painted surface, one listener set, and exact card-face hitboxes. */
    private static final class AlbumGridPanel extends JPanel implements Scrollable
    {
        private static final long serialVersionUID = 1L;
        private static final Color BACKGROUND = new Color(30, 30, 30);
        private static final Color HOVER_BORDER = CardUiTheme.GOLD_HOVER;
        private static final int OUTER_MARGIN = 20;
        private static final int FOIL_AURA_FRAME_MS = 90;
        private static final float FOIL_AURA_PHASE_STEP = 0.006f;
        private static final int FOIL_AURA_REPAINT_PADDING = 8;

        private final transient java.util.function.IntConsumer openPreview;
        private final transient java.util.function.IntConsumer navigatePage;
        private final Timer foilAuraAnimationTimer;
        private transient List<AlbumSlot> slots = Collections.emptyList();
        private transient List<Rectangle> cardBounds = Collections.emptyList();
        private int hoverIndex = -1;
        private int pressedIndex = -1;
        private String selectedCardId = "";
        private float foilAuraPhase;

        private AlbumGridPanel(
            java.util.function.IntConsumer openPreview,
            java.util.function.IntConsumer navigatePage)
        {
            this.openPreview = openPreview;
            this.navigatePage = navigatePage;
            setOpaque(true);
            setBackground(BACKGROUND);
            setDoubleBuffered(true);
            setPreferredSize(preferredGridSize());
            installMouseHandling();
            addComponentListener(new ComponentAdapter()
            {
                @Override
                public void componentResized(ComponentEvent event)
                {
                    updateCardBounds();
                }
            });

            foilAuraAnimationTimer = new Timer(FOIL_AURA_FRAME_MS, event -> advanceFoilAura());
            foilAuraAnimationTimer.setRepeats(true);
        }

        @Override
        public void addNotify()
        {
            super.addNotify();
            foilAuraAnimationTimer.start();
        }

        @Override
        public void removeNotify()
        {
            foilAuraAnimationTimer.stop();
            super.removeNotify();
        }

        private void setSlots(List<AlbumSlot> newSlots)
        {
            slots = Collections.unmodifiableList(new ArrayList<>(newSlots));
            hoverIndex = -1;
            pressedIndex = -1;
            updateCardBounds();
            revalidate();
            repaint();
        }

        private List<AlbumSlot> getSlotsSnapshot()
        {
            return new ArrayList<>(slots);
        }

        private void setSelectedCardId(String cardId)
        {
            String next = cardId == null ? "" : cardId;
            if (Objects.equals(selectedCardId, next))
            {
                return;
            }
            String previous = selectedCardId;
            selectedCardId = next;
            repaintCardId(previous);
            repaintCardId(selectedCardId);
        }

        private void repaintSelectedCard()
        {
            repaintCardId(selectedCardId);
        }

        private void repaintCardId(String cardId)
        {
            if (cardId == null || cardId.isEmpty())
            {
                return;
            }
            for (int index = 0; index < slots.size(); index++)
            {
                if (cardId.equals(slots.get(index).card.getCardId()))
                {
                    repaintIndex(index);
                    return;
                }
            }
        }

        private void replaceSlot(int index, AlbumSlot replacement)
        {
            if (index < 0 || index >= slots.size() || replacement == null)
            {
                return;
            }
            List<AlbumSlot> updated = new ArrayList<>(slots);
            updated.set(index, replacement);
            slots = Collections.unmodifiableList(updated);
            repaintIndex(index);
        }

        private void markInspected(String cardId)
        {
            if (cardId == null || cardId.isEmpty())
            {
                return;
            }
            List<AlbumSlot> updated = new ArrayList<>(slots.size());
            boolean changed = false;
            for (AlbumSlot slot : slots)
            {
                if (slot.newUnlock && cardId.equals(slot.card.getCardId()))
                {
                    updated.add(new AlbumSlot(
                        slot.card,
                        slot.owned,
                        slot.foil,
                        slot.foilAccess,
                        false,
                        slot.face));
                    changed = true;
                }
                else
                {
                    updated.add(slot);
                }
            }
            if (changed)
            {
                slots = Collections.unmodifiableList(updated);
                repaint();
            }
        }

        private void installMouseHandling()
        {
            addMouseMotionListener(new MouseMotionAdapter()
            {
                @Override
                public void mouseMoved(MouseEvent event)
                {
                    updateHover(hitTest(event.getPoint()));
                }
            });
            addMouseWheelListener(event -> {
                double rotation = event.getPreciseWheelRotation();
                if (Math.abs(rotation) < 0.01)
                {
                    return;
                }
                navigatePage.accept(rotation > 0.0 ? 1 : -1);
                event.consume();
            });
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseExited(MouseEvent event)
                {
                    updateHover(-1);
                }

                @Override
                public void mousePressed(MouseEvent event)
                {
                    if (SwingUtilities.isLeftMouseButton(event))
                    {
                        pressedIndex = hitTest(event.getPoint());
                        repaintIndex(pressedIndex);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event)
                {
                    if (!SwingUtilities.isLeftMouseButton(event))
                    {
                        return;
                    }
                    int releasedIndex = hitTest(event.getPoint());
                    int originalPressed = pressedIndex;
                    pressedIndex = -1;
                    repaintIndex(originalPressed);
                    if (releasedIndex >= 0
                        && releasedIndex == originalPressed
                        && releasedIndex < slots.size())
                    {
                        openPreview.accept(releasedIndex);
                    }
                }
            });
        }

        private void updateHover(int nextIndex)
        {
            if (nextIndex == hoverIndex)
            {
                return;
            }
            int previous = hoverIndex;
            hoverIndex = nextIndex;
            repaintIndex(previous);
            repaintIndex(nextIndex);
        }

        private int hitTest(java.awt.Point point)
        {
            for (int index = 0; index < cardBounds.size(); index++)
            {
                if (cardBounds.get(index).contains(point))
                {
                    return index;
                }
            }
            return -1;
        }

        private void repaintIndex(int index)
        {
            if (index < 0 || index >= cardBounds.size())
            {
                return;
            }
            Rectangle bounds = new Rectangle(cardBounds.get(index));
            bounds.grow(FOIL_AURA_REPAINT_PADDING, FOIL_AURA_REPAINT_PADDING);
            repaint(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        private void advanceFoilAura()
        {
            if (!isShowing())
            {
                return;
            }
            foilAuraPhase += FOIL_AURA_PHASE_STEP;
            if (foilAuraPhase >= 1f)
            {
                foilAuraPhase -= 1f;
            }
            repaintFoilCards();
        }

        private void repaintFoilCards()
        {
            for (int index = 0; index < slots.size() && index < cardBounds.size(); index++)
            {
                if (slots.get(index).foil)
                {
                    repaintIndex(index);
                }
            }
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                Rectangle clip = g.getClipBounds();
                if (clip == null)
                {
                    clip = new Rectangle(0, 0, getWidth(), getHeight());
                }
                g.setColor(BACKGROUND);
                g.fillRect(clip.x, clip.y, clip.width, clip.height);
                g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

                for (int index = 0; index < slots.size(); index++)
                {
                    Rectangle bounds = cardBounds.get(index);
                    if (!clip.intersects(bounds))
                    {
                        continue;
                    }
                    AlbumSlot slot = slots.get(index);
                    if (slot.foil)
                    {
                        paintFoilAura(g, bounds, slot.card.getCardId(), foilAuraPhase);
                    }
                    g.drawImage(
                        slot.face,
                        bounds.x,
                        bounds.y,
                        bounds.width,
                        bounds.height,
                        null);
                    if (slot.newUnlock)
                    {
                        paintNewUnlockBadge(g, bounds);
                    }
                    if (slot.card.getCardId().equals(selectedCardId))
                    {
                        g.setColor(new Color(235, 191, 92, 225));
                        g.setStroke(new BasicStroke(2.0f));
                        g.drawRoundRect(
                            bounds.x - 2,
                            bounds.y - 2,
                            bounds.width + 3,
                            bounds.height + 3,
                            8,
                            8);
                    }
                    if (index == hoverIndex || index == pressedIndex)
                    {
                        g.setColor(HOVER_BORDER);
                        g.setStroke(new BasicStroke(1.0f));
                        g.drawRect(
                            bounds.x - 1,
                            bounds.y - 1,
                            bounds.width + 1,
                            bounds.height + 1);
                    }
                }
            }
            finally
            {
                g.dispose();
            }
        }

        private void paintNewUnlockBadge(Graphics2D graphics, Rectangle bounds)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                String label = "NEW";
                g.setFont(new Font(Font.DIALOG, Font.BOLD, 10));
                FontMetrics metrics = g.getFontMetrics();
                int width = metrics.stringWidth(label) + 12;
                int height = 18;
                int x = bounds.x + bounds.width - width - 5;
                int y = bounds.y + 5;
                g.setColor(new Color(20, 20, 20, 220));
                g.fillRoundRect(x, y, width, height, 8, 8);
                g.setColor(new Color(231, 179, 72));
                g.drawRoundRect(x, y, width, height, 8, 8);
                g.setColor(new Color(255, 230, 170));
                g.drawString(label, x + 6, y + 13);
            }
            finally
            {
                g.dispose();
            }
        }

        private void paintFoilAura(
            Graphics2D graphics,
            Rectangle bounds,
            String cardId,
            float phase)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            java.awt.Stroke oldStroke = g.getStroke();
            Composite oldComposite = g.getComposite();
            java.awt.Paint oldPaint = g.getPaint();
            try
            {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                long seed = cardId == null ? 0L : cardId.hashCode() * 0x9E3779B97F4A7C15L;
                float cardOffset = ((seed >>> 17) & 0xFFFFL) / 65535f;
                float localPhase = phase + cardOffset;
                localPhase -= (float) Math.floor(localPhase);
                float breath = 0.5f + 0.5f * (float) Math.sin(localPhase * Math.PI * 2.0);
                float secondary = 0.5f + 0.5f * (float) Math.sin(
                    (localPhase * 0.63f + cardOffset * 0.37f) * Math.PI * 2.0);

                float angle = (localPhase * 0.72f + cardOffset * 0.28f) * (float) Math.PI * 2.0f;
                float centreX = (float) bounds.getCenterX();
                float centreY = (float) bounds.getCenterY();
                float gradientReach = Math.max(bounds.width, bounds.height) * 0.70f;
                float directionX = (float) Math.cos(angle) * gradientReach;
                float directionY = (float) Math.sin(angle) * gradientReach;
                LinearGradientPaint auraPaint = new LinearGradientPaint(
                    centreX - directionX,
                    centreY - directionY,
                    centreX + directionX,
                    centreY + directionY,
                    new float[]{0f, 0.24f, 0.50f, 0.76f, 1f},
                    new Color[]{
                        new Color(244, 248, 255),
                        new Color(151, 226, 255),
                        new Color(255, 246, 226),
                        new Color(255, 174, 230),
                        new Color(244, 248, 255)});

                int arc = Math.max(8, Math.round(bounds.width * 0.08f));
                int outerExpansion = 2;
                int outerWidth = Math.max(6, Math.round(bounds.width * (0.048f + breath * 0.008f)));
                g.setPaint(auraPaint);
                g.setComposite(AlphaComposite.SrcOver.derive(0.075f + breath * 0.040f));
                g.setStroke(new BasicStroke(
                    outerWidth,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
                g.drawRoundRect(
                    bounds.x - outerExpansion,
                    bounds.y - outerExpansion,
                    bounds.width + outerExpansion * 2 - 1,
                    bounds.height + outerExpansion * 2 - 1,
                    arc + outerExpansion * 2,
                    arc + outerExpansion * 2);

                int middleWidth = Math.max(4, Math.round(bounds.width * (0.030f + secondary * 0.006f)));
                g.setComposite(AlphaComposite.SrcOver.derive(0.12f + secondary * 0.045f));
                g.setStroke(new BasicStroke(
                    middleWidth,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
                g.drawRoundRect(
                    bounds.x - 1,
                    bounds.y - 1,
                    bounds.width + 1,
                    bounds.height + 1,
                    arc + 2,
                    arc + 2);

                g.setComposite(AlphaComposite.SrcOver.derive(0.18f + breath * 0.040f));
                g.setStroke(new BasicStroke(
                    1.5f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
                g.drawRoundRect(
                    bounds.x - 1,
                    bounds.y - 1,
                    bounds.width + 1,
                    bounds.height + 1,
                    arc + 2,
                    arc + 2);
            }
            finally
            {
                g.setPaint(oldPaint);
                g.setComposite(oldComposite);
                g.setStroke(oldStroke);
                g.dispose();
            }
        }

        private void updateCardBounds()
        {
            cardBounds = calculateBounds(getWidth(), getHeight());
        }

        private List<Rectangle> calculateBounds(int availableWidth, int availableHeight)
        {
            List<Rectangle> result = new ArrayList<>();
            int usableWidth = Math.max(1, availableWidth - OUTER_MARGIN * 2);
            int usableHeight = Math.max(1, availableHeight - OUTER_MARGIN * 2);
            int cardWidth = fittingCardWidth(usableWidth, usableHeight);
            int columnGap = columnGap(cardWidth);
            int cardHeight = Math.max(
                1,
                (int) Math.round(cardWidth * (CARD_HEIGHT / (double) CARD_WIDTH)));
            int rowGap = rowGap(cardWidth);
            int gridWidth = COLUMNS * cardWidth + (COLUMNS - 1) * columnGap;
            int gridHeight = ROWS * cardHeight + (ROWS - 1) * rowGap;
            int startX = Math.max(OUTER_MARGIN, (availableWidth - gridWidth) / 2);
            int startY = Math.max(OUTER_MARGIN, (availableHeight - gridHeight) / 2);

            for (int index = 0; index < slots.size(); index++)
            {
                int column = index % COLUMNS;
                int row = index / COLUMNS;
                result.add(new Rectangle(
                    startX + column * (cardWidth + columnGap),
                    startY + row * (cardHeight + rowGap),
                    cardWidth,
                    cardHeight));
            }
            return Collections.unmodifiableList(result);
        }

        private static Dimension preferredGridSize()
        {
            int width = COLUMNS * CARD_WIDTH
                + (COLUMNS - 1) * COLUMN_GAP
                + OUTER_MARGIN * 2;
            return new Dimension(width, preferredGridHeight(width));
        }

        private static int preferredGridHeight(int availableWidth)
        {
            int usableWidth = Math.max(1, availableWidth - OUTER_MARGIN * 2);
            int gap = columnGap(CARD_WIDTH);
            int cardWidth = Math.max(
                1,
                (usableWidth - (COLUMNS - 1) * gap) / COLUMNS);
            int cardHeight = Math.max(
                1,
                (int) Math.round(cardWidth * (CARD_HEIGHT / (double) CARD_WIDTH)));
            return ROWS * cardHeight
                + (ROWS - 1) * rowGap(cardWidth)
                + OUTER_MARGIN * 2;
        }

        private static int fittingCardWidth(int usableWidth, int usableHeight)
        {
            int low = 1;
            int high = Math.max(1, usableWidth / COLUMNS);
            int best = 1;
            while (low <= high)
            {
                int candidate = low + (high - low) / 2;
                int width = COLUMNS * candidate
                    + (COLUMNS - 1) * columnGap(candidate);
                int height = ROWS * Math.max(1, (int) Math.round(
                    candidate * (CARD_HEIGHT / (double) CARD_WIDTH)))
                    + (ROWS - 1) * rowGap(candidate);
                if (width <= usableWidth && height <= usableHeight)
                {
                    best = candidate;
                    low = candidate + 1;
                }
                else
                {
                    high = candidate - 1;
                }
            }
            return best;
        }

        private static int columnGap(int cardWidth)
        {
            double scale = Math.min(1.0, cardWidth / (double) CARD_WIDTH);
            return Math.max(6, (int) Math.round(COLUMN_GAP * scale));
        }

        private static int rowGap(int cardWidth)
        {
            double scale = Math.min(1.0, cardWidth / (double) CARD_WIDTH);
            return Math.max(8, (int) Math.round(ROW_GAP * scale));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return preferredGridSize();
        }

        @Override
        public int getScrollableUnitIncrement(
            Rectangle visibleRect,
            int orientation,
            int direction)
        {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(
            Rectangle visibleRect,
            int orientation,
            int direction)
        {
            return orientation == SwingConstants.VERTICAL
                ? Math.max(24, visibleRect.height - 24)
                : Math.max(24, visibleRect.width - 24);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return true;
        }
    }

}
