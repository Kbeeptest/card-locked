package com.cardrestricted.runelite;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.IntegerNode;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.api.Node;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

/**
 * Resolves the rune item families required by a spellbook action.
 *
 * <p>The live cache-backed spell definition is inspected first. A named
 * fallback covers the stable spellbook actions when a client revision does not
 * expose rune item ids through the spell definition params. Unknown spellbook
 * actions fail closed instead of silently bypassing Card Locked rules.</p>
 */
public final class SpellRuneRequirementResolver
{
    private static final Set<Integer> RUNE_ITEM_IDS = Collections.unmodifiableSet(
        new LinkedHashSet<>(Arrays.asList(
            ItemID.AIR_RUNE,
            ItemID.WATER_RUNE,
            ItemID.EARTH_RUNE,
            ItemID.FIRE_RUNE,
            ItemID.MIND_RUNE,
            ItemID.BODY_RUNE,
            ItemID.DEATH_RUNE,
            ItemID.NATURE_RUNE,
            ItemID.CHAOS_RUNE,
            ItemID.LAW_RUNE,
            ItemID.COSMIC_RUNE,
            ItemID.BLOOD_RUNE,
            ItemID.SOUL_RUNE,
            ItemID.ASTRAL_RUNE,
            ItemID.WRATH_RUNE,
            ItemID.SUNFIRE_RUNE,
            ItemID.MIST_RUNE,
            ItemID.DUST_RUNE,
            ItemID.MUD_RUNE,
            ItemID.SMOKE_RUNE,
            ItemID.STEAM_RUNE,
            ItemID.LAVA_RUNE)));

    private static final Set<String> RUNE_FREE_SPELLS = Set.of(
        "lumbridge home teleport",
        "edgeville home teleport",
        "lunar home teleport",
        "arceuus home teleport",
        "kourend home teleport",
        "home teleport");

    private static final Map<String, Set<Integer>> NAMED_REQUIREMENTS =
        buildNamedRequirements();

    private final ItemManager itemManager;
    private final Map<Integer, Set<Integer>> cacheRequirements =
        new ConcurrentHashMap<>();

    public SpellRuneRequirementResolver(ItemManager itemManager)
    {
        this.itemManager = itemManager;
    }

    /** Recognises a literal spell name when a menu rewriter removes Cast. */
    public static boolean isRecognisedSpellTarget(String menuTarget)
    {
        String spellName = InteractionNameNormalizer.spellName(menuTarget);
        return !spellName.isEmpty()
            && (RUNE_FREE_SPELLS.contains(spellName)
                || !namedRequirements(spellName).isEmpty());
    }

    public static boolean isRecognisedSpellOption(String menuOption)
    {
        if (menuOption == null)
        {
            return false;
        }
        String value = menuOption.replaceAll("<[^>]*>", "")
            .replace('\u00a0', ' ')
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ");
        if (value.isEmpty()
            || SpellbookWidgetRules.isSpellCastingOption(value))
        {
            return false;
        }
        return RUNE_FREE_SPELLS.contains(value)
            || !namedRequirements(value).isEmpty();
    }

    public Resolution resolve(String menuTarget, Widget... widgets)
    {
        return resolve("", menuTarget, widgets);
    }

    public Resolution resolveReconciled(
        String menuOption,
        String entryTarget,
        String eventTarget,
        Widget... widgets)
    {
        java.util.LinkedHashSet<String> targets = new java.util.LinkedHashSet<>();
        if (entryTarget != null && !entryTarget.trim().isEmpty())
        {
            targets.add(entryTarget);
        }
        if (eventTarget != null && !eventTarget.trim().isEmpty())
        {
            targets.add(eventTarget);
        }
        if (targets.isEmpty())
        {
            targets.add("");
        }

        Resolution selected = null;
        for (String target : targets)
        {
            Resolution candidate = resolve(menuOption, target, widgets);
            if (!candidate.isSpellbookAction())
            {
                continue;
            }
            if (selected == null)
            {
                selected = candidate;
                continue;
            }
            if (selected.isResolved() && candidate.isResolved())
            {
                if (selected.isRuneFree() != candidate.isRuneFree()
                    || !selected.getRequiredRuneIds().equals(
                        candidate.getRequiredRuneIds()))
                {
                    return Resolution.unresolved(
                        selected.getSpellName().isEmpty()
                            ? candidate.getSpellName()
                            : selected.getSpellName());
                }
                if (!selected.isCacheBacked() && candidate.isCacheBacked())
                {
                    selected = candidate;
                }
            }
            else if (!selected.isResolved() && candidate.isResolved())
            {
                selected = candidate;
            }
        }
        return selected == null ? Resolution.notSpellbookAction() : selected;
    }

    /**
     * Resolves both ordinary spellbook clicks and menu entries whose widget
     * metadata has been stripped or rewritten by another plugin. A literal
     * Cast/Autocast option is itself sufficient evidence that this is a spell
     * action; unknown spell identities then fail closed.
     */
    public Resolution resolve(
        String menuOption,
        String menuTarget,
        Widget... widgets)
    {
        boolean spellbookAction = false;
        Set<Integer> fromCache = new LinkedHashSet<>();
        if (widgets != null)
        {
            for (Widget widget : widgets)
            {
                if (widget == null
                    || !SpellbookWidgetRules.isSpellbookPackedId(widget.getId()))
                {
                    continue;
                }
                spellbookAction = true;
                int definitionItemId = widget.getItemId();
                if (definitionItemId >= 0)
                {
                    fromCache.addAll(cacheRequirements.computeIfAbsent(
                        definitionItemId,
                        this::readRuneParams));
                }
            }
        }
        boolean explicitCastingOption =
            SpellbookWidgetRules.isSpellCastingOption(menuOption);
        boolean literalSpellOption = isRecognisedSpellOption(menuOption);
        if (!spellbookAction && !explicitCastingOption
            && !literalSpellOption)
        {
            return Resolution.notSpellbookAction();
        }

        String optionSpellName = literalSpellOption
            ? menuOption.replaceAll("<[^>]*>", "")
                .replace('\u00a0', ' ')
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
            : spellNameFromOption(menuOption);
        String targetSpellName = InteractionNameNormalizer.spellName(menuTarget);
        String spellName = optionSpellName.isEmpty()
            ? targetSpellName
            : optionSpellName;
        if (RUNE_FREE_SPELLS.contains(spellName)
            || RUNE_FREE_SPELLS.contains(targetSpellName))
        {
            return Resolution.runeFree(spellName.isEmpty()
                ? targetSpellName
                : spellName);
        }
        if (!fromCache.isEmpty())
        {
            return Resolution.resolved(spellName, fromCache, true);
        }
        Set<Integer> named = namedRequirements(spellName);
        if (named.isEmpty() && !targetSpellName.equals(spellName))
        {
            named = namedRequirements(targetSpellName);
            if (!named.isEmpty())
            {
                spellName = targetSpellName;
            }
        }
        if (!named.isEmpty())
        {
            return Resolution.resolved(spellName, named, false);
        }
        return Resolution.unresolved(spellName);
    }

    static String spellNameFromOption(String menuOption)
    {
        if (menuOption == null)
        {
            return "";
        }
        String value = menuOption.replaceAll("<[^>]*>", "")
            .replace('\u00a0', ' ')
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ");
        for (String prefix : new String[]{
            "defensive autocast", "autocast", "cast"})
        {
            if (value.equals(prefix))
            {
                return "";
            }
            if (value.startsWith(prefix + " "))
            {
                return value.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    static Set<Integer> namedRequirements(String spellName)
    {
        if (spellName == null)
        {
            return Collections.emptySet();
        }
        String name = spellName.trim().toLowerCase(Locale.ROOT);
        Set<Integer> exact = NAMED_REQUIREMENTS.get(name);
        if (exact != null)
        {
            return exact;
        }

        Set<Integer> elemental = elementalCombatRequirements(name);
        if (!elemental.isEmpty())
        {
            return elemental;
        }
        Set<Integer> ancient = ancientCombatRequirements(name);
        if (!ancient.isEmpty())
        {
            return ancient;
        }
        return Collections.emptySet();
    }

    private Set<Integer> readRuneParams(int spellDefinitionItemId)
    {
        if (itemManager == null)
        {
            return Collections.emptySet();
        }
        ItemComposition composition = itemManager.getItemComposition(
            spellDefinitionItemId);
        if (composition == null || composition.getParams() == null)
        {
            return Collections.emptySet();
        }
        Set<Integer> runes = new LinkedHashSet<>();
        for (Node node : composition.getParams())
        {
            if (!(node instanceof IntegerNode))
            {
                continue;
            }
            int value = ((IntegerNode) node).getValue();
            if (RUNE_ITEM_IDS.contains(value))
            {
                runes.add(value);
            }
        }
        return Collections.unmodifiableSet(runes);
    }

    private static Set<Integer> elementalCombatRequirements(String name)
    {
        int elemental;
        if (name.startsWith("wind "))
        {
            elemental = ItemID.AIR_RUNE;
        }
        else if (name.startsWith("water "))
        {
            elemental = ItemID.WATER_RUNE;
        }
        else if (name.startsWith("earth "))
        {
            elemental = ItemID.EARTH_RUNE;
        }
        else if (name.startsWith("fire "))
        {
            elemental = ItemID.FIRE_RUNE;
        }
        else
        {
            return Collections.emptySet();
        }

        int tier;
        if (name.endsWith(" strike"))
        {
            tier = ItemID.MIND_RUNE;
        }
        else if (name.endsWith(" bolt"))
        {
            tier = ItemID.CHAOS_RUNE;
        }
        else if (name.endsWith(" blast"))
        {
            tier = ItemID.DEATH_RUNE;
        }
        else if (name.endsWith(" wave"))
        {
            tier = ItemID.BLOOD_RUNE;
        }
        else if (name.endsWith(" surge"))
        {
            tier = ItemID.WRATH_RUNE;
        }
        else
        {
            return Collections.emptySet();
        }

        Set<Integer> result = new LinkedHashSet<>();
        result.add(ItemID.AIR_RUNE);
        result.add(elemental);
        result.add(tier);
        return Collections.unmodifiableSet(result);
    }

    private static Set<Integer> ancientCombatRequirements(String name)
    {
        String element;
        if (name.startsWith("smoke "))
        {
            element = "smoke";
        }
        else if (name.startsWith("shadow "))
        {
            element = "shadow";
        }
        else if (name.startsWith("blood "))
        {
            element = "blood";
        }
        else if (name.startsWith("ice "))
        {
            element = "ice";
        }
        else
        {
            return Collections.emptySet();
        }

        boolean highTier = name.endsWith(" blitz")
            || name.endsWith(" barrage");
        boolean validTier = highTier
            || name.endsWith(" rush")
            || name.endsWith(" burst");
        if (!validTier)
        {
            return Collections.emptySet();
        }
        Set<Integer> result = new LinkedHashSet<>();
        result.add(ItemID.DEATH_RUNE);
        result.add(highTier ? ItemID.BLOOD_RUNE : ItemID.CHAOS_RUNE);
        switch (element)
        {
            case "smoke":
                result.add(ItemID.AIR_RUNE);
                result.add(ItemID.FIRE_RUNE);
                break;
            case "shadow":
                result.add(ItemID.AIR_RUNE);
                result.add(ItemID.SOUL_RUNE);
                break;
            case "blood":
                result.add(ItemID.BLOOD_RUNE);
                break;
            case "ice":
                result.add(ItemID.WATER_RUNE);
                break;
            default:
                break;
        }
        return Collections.unmodifiableSet(result);
    }

    private static Map<String, Set<Integer>> buildNamedRequirements()
    {
        Map<String, Set<Integer>> map = new HashMap<>();
        put(map, "confuse", ItemID.WATER_RUNE, ItemID.EARTH_RUNE, ItemID.BODY_RUNE);
        put(map, "weaken", ItemID.WATER_RUNE, ItemID.EARTH_RUNE, ItemID.BODY_RUNE);
        put(map, "curse", ItemID.WATER_RUNE, ItemID.EARTH_RUNE, ItemID.BODY_RUNE);
        put(map, "vulnerability", ItemID.WATER_RUNE, ItemID.EARTH_RUNE, ItemID.SOUL_RUNE);
        put(map, "enfeeble", ItemID.WATER_RUNE, ItemID.EARTH_RUNE, ItemID.SOUL_RUNE);
        put(map, "stun", ItemID.WATER_RUNE, ItemID.EARTH_RUNE, ItemID.SOUL_RUNE);
        put(map, "bind", ItemID.WATER_RUNE, ItemID.EARTH_RUNE, ItemID.NATURE_RUNE);
        put(map, "snare", ItemID.WATER_RUNE, ItemID.EARTH_RUNE, ItemID.NATURE_RUNE);
        put(map, "entangle", ItemID.WATER_RUNE, ItemID.EARTH_RUNE, ItemID.NATURE_RUNE);
        put(map, "low level alchemy", ItemID.FIRE_RUNE, ItemID.NATURE_RUNE);
        put(map, "high level alchemy", ItemID.FIRE_RUNE, ItemID.NATURE_RUNE);
        put(map, "superheat item", ItemID.FIRE_RUNE, ItemID.NATURE_RUNE);
        put(map, "telekinetic grab", ItemID.AIR_RUNE, ItemID.LAW_RUNE);
        put(map, "bones to bananas", ItemID.WATER_RUNE, ItemID.EARTH_RUNE, ItemID.NATURE_RUNE);
        put(map, "bones to peaches", ItemID.WATER_RUNE, ItemID.EARTH_RUNE, ItemID.NATURE_RUNE);
        put(map, "crumble undead", ItemID.AIR_RUNE, ItemID.EARTH_RUNE, ItemID.CHAOS_RUNE);
        put(map, "iban blast", ItemID.FIRE_RUNE, ItemID.DEATH_RUNE);
        put(map, "magic dart", ItemID.MIND_RUNE, ItemID.DEATH_RUNE);
        put(map, "tele block", ItemID.CHAOS_RUNE, ItemID.DEATH_RUNE, ItemID.LAW_RUNE);
        put(map, "charge", ItemID.AIR_RUNE, ItemID.FIRE_RUNE, ItemID.BLOOD_RUNE);
        put(map, "charge air orb", ItemID.AIR_RUNE, ItemID.COSMIC_RUNE);
        put(map, "charge water orb", ItemID.WATER_RUNE, ItemID.COSMIC_RUNE);
        put(map, "charge earth orb", ItemID.EARTH_RUNE, ItemID.COSMIC_RUNE);
        put(map, "charge fire orb", ItemID.FIRE_RUNE, ItemID.COSMIC_RUNE);

        put(map, "varrock teleport", ItemID.AIR_RUNE, ItemID.FIRE_RUNE, ItemID.LAW_RUNE);
        put(map, "lumbridge teleport", ItemID.AIR_RUNE, ItemID.EARTH_RUNE, ItemID.LAW_RUNE);
        put(map, "falador teleport", ItemID.AIR_RUNE, ItemID.WATER_RUNE, ItemID.LAW_RUNE);
        put(map, "camelot teleport", ItemID.AIR_RUNE, ItemID.LAW_RUNE);
        put(map, "ardougne teleport", ItemID.WATER_RUNE, ItemID.LAW_RUNE);
        put(map, "watchtower teleport", ItemID.EARTH_RUNE, ItemID.LAW_RUNE);
        put(map, "trollheim teleport", ItemID.FIRE_RUNE, ItemID.LAW_RUNE);
        put(map, "teleport to house", ItemID.AIR_RUNE, ItemID.EARTH_RUNE, ItemID.LAW_RUNE);
        put(map, "ape atoll teleport", ItemID.WATER_RUNE, ItemID.FIRE_RUNE, ItemID.LAW_RUNE);
        put(map, "civitas illa fortis teleport", ItemID.EARTH_RUNE, ItemID.LAW_RUNE, ItemID.SUNFIRE_RUNE);

        put(map, "paddewwa teleport", ItemID.AIR_RUNE, ItemID.FIRE_RUNE, ItemID.LAW_RUNE);
        put(map, "senntisten teleport", ItemID.SOUL_RUNE, ItemID.LAW_RUNE);
        put(map, "kharyrll teleport", ItemID.BLOOD_RUNE, ItemID.LAW_RUNE);
        put(map, "lassar teleport", ItemID.WATER_RUNE, ItemID.LAW_RUNE);
        put(map, "dareeyak teleport", ItemID.AIR_RUNE, ItemID.FIRE_RUNE, ItemID.LAW_RUNE);
        put(map, "carrallanger teleport", ItemID.SOUL_RUNE, ItemID.LAW_RUNE);
        put(map, "annakarl teleport", ItemID.BLOOD_RUNE, ItemID.LAW_RUNE);
        put(map, "ghorrock teleport", ItemID.WATER_RUNE, ItemID.LAW_RUNE);

        put(map, "moonclan teleport", ItemID.ASTRAL_RUNE, ItemID.EARTH_RUNE, ItemID.LAW_RUNE);
        put(map, "waterbirth teleport", ItemID.ASTRAL_RUNE, ItemID.WATER_RUNE, ItemID.LAW_RUNE);
        put(map, "barbarian teleport", ItemID.ASTRAL_RUNE, ItemID.FIRE_RUNE, ItemID.LAW_RUNE);
        put(map, "khazard teleport", ItemID.ASTRAL_RUNE, ItemID.WATER_RUNE, ItemID.LAW_RUNE);
        put(map, "fishing guild teleport", ItemID.ASTRAL_RUNE, ItemID.WATER_RUNE, ItemID.LAW_RUNE);
        put(map, "catherby teleport", ItemID.ASTRAL_RUNE, ItemID.WATER_RUNE, ItemID.LAW_RUNE);
        put(map, "ice plateau teleport", ItemID.ASTRAL_RUNE, ItemID.WATER_RUNE, ItemID.LAW_RUNE);
        put(map, "ourania teleport", ItemID.ASTRAL_RUNE, ItemID.EARTH_RUNE, ItemID.LAW_RUNE);
        put(map, "npc contact", ItemID.ASTRAL_RUNE, ItemID.AIR_RUNE, ItemID.COSMIC_RUNE);
        put(map, "humidify", ItemID.ASTRAL_RUNE, ItemID.WATER_RUNE, ItemID.FIRE_RUNE);
        put(map, "hunter kit", ItemID.ASTRAL_RUNE, ItemID.EARTH_RUNE);
        put(map, "spellbook swap", ItemID.ASTRAL_RUNE, ItemID.COSMIC_RUNE, ItemID.LAW_RUNE);
        put(map, "vengeance", ItemID.ASTRAL_RUNE, ItemID.EARTH_RUNE, ItemID.DEATH_RUNE);
        put(map, "vengeance other", ItemID.ASTRAL_RUNE, ItemID.EARTH_RUNE, ItemID.DEATH_RUNE);

        return Collections.unmodifiableMap(map);
    }

    private static void put(
        Map<String, Set<Integer>> map,
        String name,
        Integer... runeIds)
    {
        map.put(name, Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(runeIds))));
    }

    public static final class Resolution
    {
        private static final Resolution NOT_SPELL = new Resolution(
            false, true, false, "", Collections.emptySet(), false);

        private final boolean spellbookAction;
        private final boolean resolved;
        private final boolean runeFree;
        private final String spellName;
        private final Set<Integer> requiredRuneIds;
        private final boolean cacheBacked;

        private Resolution(
            boolean spellbookAction,
            boolean resolved,
            boolean runeFree,
            String spellName,
            Set<Integer> requiredRuneIds,
            boolean cacheBacked)
        {
            this.spellbookAction = spellbookAction;
            this.resolved = resolved;
            this.runeFree = runeFree;
            this.spellName = spellName == null ? "" : spellName;
            this.requiredRuneIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(requiredRuneIds));
            this.cacheBacked = cacheBacked;
        }

        private static Resolution notSpellbookAction()
        {
            return NOT_SPELL;
        }

        private static Resolution runeFree(String spellName)
        {
            return new Resolution(
                true, true, true, spellName, Collections.emptySet(), false);
        }

        private static Resolution resolved(
            String spellName,
            Set<Integer> runeIds,
            boolean cacheBacked)
        {
            return new Resolution(
                true, true, false, spellName, runeIds, cacheBacked);
        }

        private static Resolution unresolved(String spellName)
        {
            return new Resolution(
                true, false, false, spellName, Collections.emptySet(), false);
        }

        public boolean isSpellbookAction()
        {
            return spellbookAction;
        }

        public boolean isResolved()
        {
            return resolved;
        }

        public boolean isRuneFree()
        {
            return runeFree;
        }

        public String getSpellName()
        {
            return spellName;
        }

        public Set<Integer> getRequiredRuneIds()
        {
            return requiredRuneIds;
        }

        public boolean isCacheBacked()
        {
            return cacheBacked;
        }
    }
}
