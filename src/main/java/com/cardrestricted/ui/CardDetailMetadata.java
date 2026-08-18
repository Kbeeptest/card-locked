package com.cardrestricted.ui;

import com.cardrestricted.catalog.CardDefinition;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class CardDetailMetadata
{
    private static final String NPC_RESOURCE =
        "/com/cardrestricted/catalog/runtime/npc-combat-levels.tsv";
    private static final String ITEM_RESOURCE =
        "/com/cardrestricted/catalog/runtime/item-actions.tsv";
    private static final String DETAIL_RESOURCE =
        "/com/cardrestricted/catalog/card-details.tsv";
    private static final String QUEST_REVIEW_RESOURCE =
        "/com/cardrestricted/catalog/quest-review.tsv";

    private final Map<String, String> combatLevels;
    private final Map<String, String> itemActions;
    private final Map<String, Detail> details;

    private CardDetailMetadata(
        Map<String, String> combatLevels,
        Map<String, String> itemActions,
        Map<String, Detail> details)
    {
        this.combatLevels = Collections.unmodifiableMap(combatLevels);
        this.itemActions = Collections.unmodifiableMap(itemActions);
        this.details = Collections.unmodifiableMap(details);
    }

    static CardDetailMetadata load(ClassLoader loader)
    {
        Map<String, String> combat = new LinkedHashMap<>();
        Map<String, String> actions = new LinkedHashMap<>();
        Map<String, Detail> details = new LinkedHashMap<>();
        readColumnsWithAliases(loader, NPC_RESOURCE, 0, 1, combat);
        readColumnsWithAliases(loader, ITEM_RESOURCE, 0, 1, actions);
        readDetails(loader, details);
        readQuestReviews(loader, details);
        return new CardDetailMetadata(combat, actions, details);
    }

    String combatLevel(CardDefinition card)
    {
        return lookup(combatLevels, card).replace('|', ',');
    }

    boolean hasCombatLevel(CardDefinition card)
    {
        String level = combatLevel(card);
        return !level.isEmpty() && !"0".equals(level);
    }

    String itemActions(CardDefinition card)
    {
        return lookup(itemActions, card).replace('|', ',');
    }

    private static String lookup(Map<String, String> values, CardDefinition card)
    {
        String direct = clean(values.get(card.getCardId()));
        if (!direct.isEmpty())
        {
            return direct;
        }
        String family = clean(values.get(card.getEntityFamilyId()));
        if (!family.isEmpty())
        {
            return family;
        }
        return clean(values.get(stripNumericSuffix(card.getCardId())));
    }

    Detail detail(CardDefinition card)
    {
        Detail direct = details.get(card.getCardId());
        if (direct != null)
        {
            return direct;
        }
        Detail family = details.get(card.getEntityFamilyId());
        if (family != null)
        {
            return family;
        }
        return details.getOrDefault(stripNumericSuffix(card.getCardId()), Detail.EMPTY);
    }

    private static void readColumnsWithAliases(
        ClassLoader loader,
        String resource,
        int keyIndex,
        int valueIndex,
        Map<String, String> target)
    {
        try (BufferedReader reader = reader(loader, resource))
        {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null)
            {
                if (header)
                {
                    header = false;
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length > Math.max(keyIndex, valueIndex))
                {
                    String key = clean(fields[keyIndex]);
                    String value = clean(fields[valueIndex]);
                    target.put(key, value);
                    mergeNumericValue(target, stripNumericSuffix(key), value);
                    if (fields.length > 0)
                    {
                        mergeNumericValue(target, clean(fields[0]), value);
                    }
                }
            }
        }
        catch (IOException ignored)
        {
            // Optional enrichment must never prevent the album opening.
        }
    }

    private static void readDetails(
        ClassLoader loader,
        Map<String, Detail> target)
    {
        try (BufferedReader reader = reader(loader, DETAIL_RESOURCE))
        {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null)
            {
                if (header)
                {
                    header = false;
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length >= 6 && !fields[0].trim().isEmpty())
                {
                    target.put(fields[0], new Detail(
                        field(fields, 1), field(fields, 2), field(fields, 3),
                        field(fields, 4), field(fields, 5), field(fields, 6),
                        field(fields, 7), field(fields, 8), field(fields, 9),
                        field(fields, 10), field(fields, 11), field(fields, 12),
                        field(fields, 13)));
                }
            }
        }
        catch (IOException ignored)
        {
            // The generated cache data remains usable without this table.
        }
    }

    private static void readQuestReviews(
        ClassLoader loader,
        Map<String, Detail> target)
    {
        try (BufferedReader reader = reader(loader, QUEST_REVIEW_RESOURCE))
        {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null)
            {
                if (header)
                {
                    header = false;
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length < 3 || !"approved".equalsIgnoreCase(clean(fields[2])))
                {
                    continue;
                }
                String cardId = clean(fields[0]);
                String relevance = clean(fields[1]);
                if (cardId.isEmpty() || relevance.isEmpty())
                {
                    continue;
                }
                Detail current = target.getOrDefault(cardId, Detail.EMPTY);
                target.put(cardId, current.withQuest(relevance));
            }
        }
        catch (IOException ignored)
        {
            // Reviewed quest enrichment is optional at runtime.
        }
    }

    private static void mergeNumericValue(Map<String, String> target, String key, String value)
    {
        if (key.isEmpty() || value.isEmpty())
        {
            return;
        }
        String current = clean(target.get(key));
        if (current.isEmpty())
        {
            target.put(key, value);
            return;
        }
        try
        {
            int next = Integer.parseInt(value);
            int min = next;
            int max = next;
            for (String part : current.split("[–-]"))
            {
                int parsed = Integer.parseInt(part.trim());
                min = Math.min(min, parsed);
                max = Math.max(max, parsed);
            }
            target.put(key, min == max ? Integer.toString(min) : min + "–" + max);
        }
        catch (NumberFormatException ignored)
        {
            target.putIfAbsent(key, value);
        }
    }

    private static BufferedReader reader(ClassLoader loader, String resource)
        throws IOException
    {
        InputStream stream = loader.getResourceAsStream(
            resource.startsWith("/") ? resource.substring(1) : resource);
        if (stream == null)
        {
            throw new IOException("Missing resource " + resource);
        }
        return new BufferedReader(new InputStreamReader(
            stream,
            StandardCharsets.UTF_8));
    }


    private static String field(String[] fields, int index)
    {
        return index < fields.length ? fields[index] : "";
    }

    private static String stripNumericSuffix(String value)
    {
        return value == null ? "" : value.replaceFirst("\\.\\d+$", "");
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.trim();
    }

    static final class Detail
    {
        private static final Detail EMPTY = new Detail("", "", "", "", "", "", "", "", "", "", "", "", "");
        private final String levelRequirements;
        private final String quest;
        private final String hitpoints;
        private final String uniqueDrops;
        private final String notes;
        private final String slayerRequirement;
        private final String attackStyles;
        private final String equipmentSlot;
        private final String weaponType;
        private final String attackSpeed;
        private final String maxHit;
        private final String aggression;
        private final String hazards;

        private Detail(
            String levelRequirements,
            String quest,
            String hitpoints,
            String uniqueDrops,
            String notes,
            String slayerRequirement,
            String attackStyles,
            String equipmentSlot,
            String weaponType,
            String attackSpeed,
            String maxHit,
            String aggression,
            String hazards)
        {
            this.levelRequirements = clean(levelRequirements);
            this.quest = clean(quest);
            this.hitpoints = clean(hitpoints);
            this.uniqueDrops = clean(uniqueDrops);
            this.notes = clean(notes);
            this.slayerRequirement = clean(slayerRequirement);
            this.attackStyles = clean(attackStyles);
            this.equipmentSlot = clean(equipmentSlot);
            this.weaponType = clean(weaponType);
            this.attackSpeed = clean(attackSpeed);
            this.maxHit = clean(maxHit);
            this.aggression = clean(aggression);
            this.hazards = clean(hazards);
        }

        private Detail withQuest(String reviewedQuest)
        {
            return new Detail(
                levelRequirements,
                reviewedQuest,
                hitpoints,
                uniqueDrops,
                notes,
                slayerRequirement,
                attackStyles,
                equipmentSlot,
                weaponType,
                attackSpeed,
                maxHit,
                aggression,
                hazards);
        }

        String getLevelRequirements() { return levelRequirements; }
        String getQuest() { return quest; }
        String getHitpoints() { return hitpoints; }
        String getUniqueDrops() { return uniqueDrops; }
        String getNotes() { return notes; }
        String getSlayerRequirement() { return slayerRequirement; }
        String getAttackStyles() { return attackStyles; }
        String getEquipmentSlot() { return equipmentSlot; }
        String getWeaponType() { return weaponType; }
        String getAttackSpeed() { return attackSpeed; }
        String getMaxHit() { return maxHit; }
        String getAggression() { return aggression; }
        String getHazards() { return hazards; }
    }
}
