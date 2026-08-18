package com.cardrestricted.ui;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.EntityFamily;
import com.cardrestricted.catalog.CardType;
import com.cardrestricted.domain.EntityType;
import com.cardrestricted.presentation.CardArtworkProvider;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Artwork provider backed by Card Locked's verified offline Wiki artwork pack
 * and exact RuneLite item sprites. No gameplay-time network requests occur.
 */
public final class RuneliteCardArtworkProvider implements CardArtworkProvider,
    AutoCloseable
{
    static final int MAX_CACHED_ARTWORK = 512;
    static final int MAX_PREFETCH_BATCH = 64;

    private static final String FAMILIES_RESOURCE =
        "com/cardrestricted/catalog/members/families.tsv";

    private final ItemManager itemManager;
    private final WikiArtworkDiskCache wikiArtworkDiskCache;
    private final PersistentItemSpriteCache persistentItemSpriteCache;
    private final Map<String, Integer> itemIdsByFamilyId;
    private final Map<String, WikiArtworkEntry> wikiArtworkByCardId;
    private final Set<String> pendingItemSpriteCallbacks =
        ConcurrentHashMap.newKeySet();
    private final Set<Integer> pendingItemSpriteWrites =
        ConcurrentHashMap.newKeySet();
    private final ExecutorService itemSpritePersistenceExecutor =
        Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(
                runnable,
                "card-locked-item-sprite-persistence");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<String, Artwork> cache = Collections.synchronizedMap(
        new LinkedHashMap<String, Artwork>(MAX_CACHED_ARTWORK, 0.75f, true)
        {
            @Override
            protected boolean removeEldestEntry(
                Map.Entry<String, Artwork> eldest)
            {
                return size() > MAX_CACHED_ARTWORK;
            }
        });
    private volatile Consumer<String> artworkAvailableListener = ignored -> { };

    public RuneliteCardArtworkProvider(ItemManager itemManager)
    {
        this(itemManager, null);
    }

    public RuneliteCardArtworkProvider(
        ItemManager itemManager,
        WikiArtworkDiskCache wikiArtworkDiskCache)
    {
        this(
            itemManager,
            wikiArtworkDiskCache,
            loadItemIdsByFamilyId(),
            WikiArtworkManifest.load(
                RuneliteCardArtworkProvider.class.getClassLoader()));
    }

    public RuneliteCardArtworkProvider(
        ItemManager itemManager,
        WikiArtworkDiskCache wikiArtworkDiskCache,
        CardCatalogue catalogue)
    {
        this(
            itemManager,
            wikiArtworkDiskCache,
            itemIdsByFamilyId(Objects.requireNonNull(catalogue, "catalogue")),
            WikiArtworkManifest.load(
                RuneliteCardArtworkProvider.class.getClassLoader()));
    }

    RuneliteCardArtworkProvider(
        ItemManager itemManager,
        WikiArtworkDiskCache wikiArtworkDiskCache,
        Map<String, Integer> itemIdsByFamilyId,
        Map<String, WikiArtworkEntry> wikiArtworkByCardId)
    {
        this.itemManager = Objects.requireNonNull(itemManager, "itemManager");
        this.wikiArtworkDiskCache = wikiArtworkDiskCache;
        this.persistentItemSpriteCache = wikiArtworkDiskCache == null
            ? null
            : new PersistentItemSpriteCache(
                wikiArtworkDiskCache.getDirectory().resolve("item-sprites"));
        this.itemIdsByFamilyId = Collections.unmodifiableMap(
            new HashMap<>(Objects.requireNonNull(
                itemIdsByFamilyId,
                "itemIdsByFamilyId")));
        this.wikiArtworkByCardId = Collections.unmodifiableMap(
            new HashMap<>(Objects.requireNonNull(
                wikiArtworkByCardId,
                "wikiArtworkByCardId")));
    }

    public void setArtworkAvailableListener(Consumer<String> listener)
    {
        if (closed.get())
        {
            artworkAvailableListener = ignored -> { };
            return;
        }
        artworkAvailableListener = listener == null ? ignored -> { } : listener;
    }

    @Override
    public Artwork getArtwork(CardDefinition card)
    {
        Objects.requireNonNull(card, "card");
        if (closed.get())
        {
            return null;
        }
        Artwork cached = cache.get(card.getCardId());
        if (cached != null)
        {
            if (cached.getSource() != ArtworkSource.OSRS_WIKI)
            {
                Artwork offlineWiki = fetchOfflineWikiArtwork(card);
                if (offlineWiki != null)
                {
                    cache.put(card.getCardId(), offlineWiki);
                    return offlineWiki;
                }
            }
            return cached;
        }
        Artwork loaded = loadArtwork(card);
        if (loaded != null)
        {
            cache.put(card.getCardId(), loaded);
        }
        return loaded;
    }

    @Override
    public Artwork getPackagedArtwork(CardDefinition card)
    {
        Objects.requireNonNull(card, "card");
        if (closed.get())
        {
            return null;
        }
        Artwork cached = cache.get(card.getCardId());
        if (cached != null
            && (cached.getSource() == ArtworkSource.OSRS_WIKI
                || cached.getSource() == ArtworkSource.ITEM_SPRITE
                || cached.getSource() == ArtworkSource.BUILT_IN_FALLBACK))
        {
            return cached;
        }
        Artwork packaged = fetchOfflineWikiArtwork(card);
        if (packaged == null)
        {
            packaged = fetchPersistedItemArtwork(card);
        }
        if (packaged == null && card.getCardType() == CardType.NPC)
        {
            packaged = BuiltInCardArtwork.create(card);
        }
        if (packaged != null)
        {
            cache.put(card.getCardId(), packaged);
        }
        return packaged;
    }

    @Override
    public ArtworkSource getPackagedArtworkSource(CardDefinition card)
    {
        Objects.requireNonNull(card, "card");

        // Availability checks are deliberately metadata-only. Album filtering,
        // counters and navigation must never decode PNG files simply to learn
        // whether local artwork exists.
        WikiArtworkEntry wikiEntry = wikiArtworkByCardId.get(card.getCardId());
        if (wikiEntry != null && wikiArtworkDiskCache != null
            && (wikiArtworkDiskCache.hasLocalPackArtwork(wikiEntry)
                || wikiArtworkDiskCache.contains(wikiEntry)))
        {
            return ArtworkSource.OSRS_WIKI;
        }
        if (persistentItemSpriteCache != null
            && card.getCardType() == CardType.ITEM)
        {
            Integer itemId = itemIdsByFamilyId.get(card.getEntityFamilyId());
            if (itemId != null && persistentItemSpriteCache.contains(itemId.intValue()))
            {
                return ArtworkSource.ITEM_SPRITE;
            }
        }
        if (card.getCardType() == CardType.NPC)
        {
            return ArtworkSource.BUILT_IN_FALLBACK;
        }
        return ArtworkSource.NONE;
    }

    private Artwork loadArtwork(CardDefinition card)
    {
        Artwork wikiArtwork = fetchOfflineWikiArtwork(card);
        if (wikiArtwork != null)
        {
            return wikiArtwork;
        }
        if (card.getCardType() == CardType.ITEM)
        {
            return fetchItemArtwork(card);
        }
        return BuiltInCardArtwork.create(card);
    }

    private Artwork fetchOfflineWikiArtwork(CardDefinition card)
    {
        WikiArtworkEntry entry = wikiArtworkByCardId.get(card.getCardId());
        if (entry == null || wikiArtworkDiskCache == null)
        {
            return null;
        }
        Artwork disk = wikiArtworkDiskCache.load(entry)
            .map(image -> new Artwork(
                image,
                entry.isPixelArt(),
                ArtworkSource.OSRS_WIKI))
            .orElse(null);
        if (disk != null)
        {
            return disk;
        }
        return wikiArtworkDiskCache.loadLocalPackArtwork(entry)
            .map(image -> new Artwork(
                image,
                entry.isPixelArt(),
                ArtworkSource.OSRS_WIKI))
            .orElse(null);
    }

    @Override
    public ArtworkSource getArtworkSource(CardDefinition card)
    {
        Objects.requireNonNull(card, "card");
        WikiArtworkEntry wikiEntry = wikiArtworkByCardId.get(card.getCardId());
        if (wikiEntry != null
            && wikiArtworkDiskCache != null
            && (wikiArtworkDiskCache.hasLocalPackArtwork(wikiEntry)
                || wikiArtworkDiskCache.contains(wikiEntry)))
        {
            return ArtworkSource.OSRS_WIKI;
        }
        if (card.getCardType() == CardType.ITEM
            && itemIdsByFamilyId.containsKey(card.getEntityFamilyId()))
        {
            return ArtworkSource.ITEM_SPRITE;
        }
        if (card.getCardType() == CardType.NPC)
        {
            return ArtworkSource.BUILT_IN_FALLBACK;
        }
        return ArtworkSource.NONE;
    }

    public boolean hasWikiMapping(CardDefinition card)
    {
        Objects.requireNonNull(card, "card");
        return wikiArtworkByCardId.containsKey(card.getCardId());
    }

    @Override
    public int mappedArtworkCount()
    {
        return wikiArtworkByCardId.size();
    }

    @Override
    public void prefetch(Iterable<CardDefinition> cards)
    {
        Objects.requireNonNull(cards, "cards");
        if (closed.get())
        {
            return;
        }
        int queued = 0;
        for (CardDefinition card : cards)
        {
            if (queued >= MAX_PREFETCH_BATCH)
            {
                break;
            }
            if (getPackagedArtwork(card) != null)
            {
                continue;
            }
            if (card.getCardType() == CardType.ITEM)
            {
                fetchItemArtwork(card);
                queued++;
            }
        }
    }

    public int downloadedWikiArtworkCount()
    {
        return wikiArtworkDiskCache == null
            ? 0
            : wikiArtworkDiskCache.localPackEntryCount();
    }

    int cachedArtworkCountForTesting()
    {
        return cache.size();
    }

    public void invalidate(String cardId)
    {
        if (cardId != null)
        {
            cache.remove(cardId);
        }
    }

    public void invalidateForOfflinePackReady()
    {
        cache.clear();
    }

    private Artwork fetchPersistedItemArtwork(CardDefinition card)
    {
        if (persistentItemSpriteCache == null || card.getCardType() != CardType.ITEM)
        {
            return null;
        }
        Integer itemId = itemIdsByFamilyId.get(card.getEntityFamilyId());
        if (itemId == null)
        {
            return null;
        }
        return persistentItemSpriteCache.load(itemId.intValue())
            .map(image -> new Artwork(image, true, ArtworkSource.ITEM_SPRITE))
            .orElse(null);
    }

    private Artwork fetchItemArtwork(CardDefinition card)
    {
        Integer itemId = itemIdsByFamilyId.get(card.getEntityFamilyId());
        if (itemId == null)
        {
            return null;
        }
        Artwork persisted = fetchPersistedItemArtwork(card);
        if (persisted != null)
        {
            return persisted;
        }
        BufferedImage image = itemManager.getImage(itemId.intValue());
        if (image instanceof AsyncBufferedImage)
        {
            AsyncBufferedImage async = (AsyncBufferedImage) image;
            if (pendingItemSpriteCallbacks.add(card.getCardId()))
            {
                async.onLoaded(() -> {
                    pendingItemSpriteCallbacks.remove(card.getCardId());
                    if (closed.get())
                    {
                        return;
                    }

                    // RuneLite completes AsyncBufferedImage callbacks on its
                    // client thread. Keep that callback memory-only: publish
                    // the completed sprite immediately, then persist the PNG
                    // on our dedicated low-priority I/O worker. ImageIO and
                    // filesystem operations must never stall the game loop.
                    cache.put(
                        card.getCardId(),
                        new Artwork(
                            async,
                            true,
                            ArtworkSource.ITEM_SPRITE));
                    queuePersistentItemSpriteSave(itemId.intValue(), async);
                    artworkAvailableListener.accept(card.getCardId());
                });
            }
            return null;
        }
        if (image != null)
        {
            queuePersistentItemSpriteSave(itemId.intValue(), image);
        }
        return image == null
            ? null
            : new Artwork(image, true, ArtworkSource.ITEM_SPRITE);
    }

    private void queuePersistentItemSpriteSave(
        int itemId,
        BufferedImage image)
    {
        if (persistentItemSpriteCache == null
            || image == null
            || closed.get()
            || !pendingItemSpriteWrites.add(itemId))
        {
            return;
        }
        try
        {
            itemSpritePersistenceExecutor.execute(() -> {
                try
                {
                    if (!closed.get())
                    {
                        persistentItemSpriteCache.save(itemId, image);
                    }
                }
                finally
                {
                    pendingItemSpriteWrites.remove(itemId);
                }
            });
        }
        catch (RejectedExecutionException ignored)
        {
            pendingItemSpriteWrites.remove(itemId);
        }
    }

    private static Map<String, Integer> itemIdsByFamilyId(
        CardCatalogue catalogue)
    {
        Map<String, Integer> result = new HashMap<>();
        for (EntityFamily family : catalogue.getFamilies())
        {
            if (family.getEntityType() == EntityType.ITEM)
            {
                result.put(
                    family.getFamilyId(),
                    family.getCanonicalEntityId());
            }
        }
        return result;
    }

    private static Map<String, Integer> loadItemIdsByFamilyId()
    {
        Map<String, Integer> result = new HashMap<>();
        ClassLoader loader = RuneliteCardArtworkProvider.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(FAMILIES_RESOURCE))
        {
            if (stream == null)
            {
                return result;
            }
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)))
            {
                String line = reader.readLine();
                while ((line = reader.readLine()) != null)
                {
                    if (line.trim().isEmpty())
                    {
                        continue;
                    }
                    String[] columns = line.split("\\t", -1);
                    if (columns.length < 3 || !"ITEM".equals(columns[1]))
                    {
                        continue;
                    }
                    try
                    {
                        result.put(columns[0], Integer.parseInt(columns[2]));
                    }
                    catch (NumberFormatException ignored)
                    {
                        // Skip malformed rows.
                    }
                }
            }
        }
        catch (IOException ignored)
        {
            return result;
        }
        return result;
    }

    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        artworkAvailableListener = ignored -> { };
        pendingItemSpriteCallbacks.clear();
        pendingItemSpriteWrites.clear();
        itemSpritePersistenceExecutor.shutdownNow();
        cache.clear();
        if (wikiArtworkDiskCache != null)
        {
            wikiArtworkDiskCache.close();
        }
    }

    boolean isClosedForTesting()
    {
        return closed.get();
    }
}
