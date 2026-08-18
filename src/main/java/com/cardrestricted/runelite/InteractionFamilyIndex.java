package com.cardrestricted.runelite;

import com.cardrestricted.catalog.CardCatalogue;
import com.cardrestricted.catalog.CardDefinition;
import com.cardrestricted.catalog.EntityFamily;
import com.cardrestricted.domain.EntityRef;
import com.cardrestricted.catalog.CardType;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fast family and card lookup for data-driven interaction rules. */
public final class InteractionFamilyIndex
{
    private final Map<EntityRef, String> familyByEntity;
    private final Map<String, Set<String>> cardsByFamily;
    private final Map<String, String> uniqueItemFamilyByName;
    private final Set<String> ambiguousItemNames;
    private final Map<String, String> uniqueNpcFamilyByName;
    private final Set<String> ambiguousNpcNames;

    public InteractionFamilyIndex(CardCatalogue catalogue)
    {
        Objects.requireNonNull(catalogue, "catalogue");
        Map<EntityRef, String> entityIndex = new HashMap<>();
        for (EntityFamily family : catalogue.getFamilies())
        {
            for (int entityId : family.allEntityIds())
            {
                EntityRef key = new EntityRef(
                    family.getEntityType(),
                    entityId);
                String previous = entityIndex.put(key, family.getFamilyId());
                if (previous != null && !previous.equals(family.getFamilyId()))
                {
                    throw new IllegalArgumentException(
                        "Entity " + key.getType() + ":" + key.getId()
                            + " belongs to multiple interaction families.");
                }
            }
        }

        Map<String, Set<String>> familyCards = new HashMap<>();
        for (CardDefinition card : catalogue.getCards())
        {
            familyCards.computeIfAbsent(
                card.getEntityFamilyId(),
                ignored -> new LinkedHashSet<>()).add(
                    catalogue.resolveCardId(card.getCardId()));
        }
        Map<String, Set<String>> immutableCards = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : familyCards.entrySet())
        {
            immutableCards.put(
                entry.getKey(),
                Collections.unmodifiableSet(
                    new LinkedHashSet<>(entry.getValue())));
        }
        Map<String, Set<String>> itemFamiliesByName = new HashMap<>();
        Map<String, Set<String>> npcFamiliesByName = new HashMap<>();
        for (CardDefinition card : catalogue.getCards())
        {
            if (card.getCardType() == CardType.ITEM)
            {
                addFamilyByName(
                    itemFamiliesByName,
                    InteractionNameNormalizer.normaliseItemName(
                        card.getDisplayName()),
                    card.getEntityFamilyId());
            }
            else if (card.getCardType() == CardType.NPC)
            {
                addFamilyByName(
                    npcFamiliesByName,
                    InteractionNameNormalizer.normaliseEntityName(
                        card.getDisplayName()),
                    card.getEntityFamilyId());
            }
        }
        NameIndex itemNames = buildNameIndex(itemFamiliesByName);
        NameIndex npcNames = buildNameIndex(npcFamiliesByName);

        this.familyByEntity = Collections.unmodifiableMap(entityIndex);
        this.cardsByFamily = Collections.unmodifiableMap(immutableCards);
        this.uniqueItemFamilyByName = itemNames.unique;
        this.ambiguousItemNames = itemNames.ambiguous;
        this.uniqueNpcFamilyByName = npcNames.unique;
        this.ambiguousNpcNames = npcNames.ambiguous;
    }


    private static void addFamilyByName(
        Map<String, Set<String>> destination,
        String name,
        String familyId)
    {
        if (name == null || name.isEmpty())
        {
            return;
        }
        destination.computeIfAbsent(
            name,
            ignored -> new LinkedHashSet<>()).add(familyId);
    }

    private static NameIndex buildNameIndex(
        Map<String, Set<String>> familiesByName)
    {
        Map<String, String> unique = new HashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : familiesByName.entrySet())
        {
            if (entry.getValue().size() == 1)
            {
                unique.put(entry.getKey(), entry.getValue().iterator().next());
            }
            else
            {
                ambiguous.add(entry.getKey());
            }
        }
        return new NameIndex(
            Collections.unmodifiableMap(unique),
            Collections.unmodifiableSet(ambiguous));
    }

    public String familyId(EntityRef entity)
    {
        if (entity == null || !entity.isKnown())
        {
            return null;
        }
        return familyByEntity.get(entity);
    }


    public String familyIdForNpcId(int npcId)
    {
        if (npcId < 0)
        {
            return null;
        }
        return familyByEntity.get(
            new EntityRef(
                com.cardrestricted.domain.EntityType.NPC,
                npcId));
    }

    public String familyIdForUniqueNpcName(String npcName)
    {
        String normalisedName = InteractionNameNormalizer
            .normaliseEntityName(npcName);
        if (normalisedName.isEmpty())
        {
            return null;
        }
        return uniqueNpcFamilyByName.get(normalisedName);
    }

    public boolean hasConflictingNpcIdentity(int npcId, String npcName)
    {
        String idFamily = familyIdForNpcId(npcId);
        String nameFamily = familyIdForUniqueNpcName(npcName);
        return idFamily != null && nameFamily != null
            && !idFamily.equals(nameFamily);
    }

    public String familyIdForNpc(int npcId, String npcName)
    {
        String familyId = familyIdForNpcId(npcId);
        if (familyId != null)
        {
            return familyId;
        }
        String normalisedName = InteractionNameNormalizer
            .normaliseEntityName(npcName);
        if (normalisedName.isEmpty())
        {
            return null;
        }
        return uniqueNpcFamilyByName.get(normalisedName);
    }

    public boolean isAmbiguousNpcName(String npcName)
    {
        String normalisedName = InteractionNameNormalizer
            .normaliseEntityName(npcName);
        return !normalisedName.isEmpty()
            && ambiguousNpcNames.contains(normalisedName);
    }

    public int uniqueNpcNameCount()
    {
        return uniqueNpcFamilyByName.size();
    }

    public int ambiguousNpcNameCount()
    {
        return ambiguousNpcNames.size();
    }

    public String familyIdForItem(int itemId)
    {
        if (itemId < 0)
        {
            return null;
        }
        return familyByEntity.get(
            new EntityRef(com.cardrestricted.domain.EntityType.ITEM, itemId));
    }

    public String familyIdForUniqueItemName(String normalisedItemName)
    {
        if (normalisedItemName == null || normalisedItemName.isEmpty())
        {
            return null;
        }
        return uniqueItemFamilyByName.get(normalisedItemName);
    }

    public boolean isAmbiguousItemName(String normalisedItemName)
    {
        return normalisedItemName != null
            && ambiguousItemNames.contains(normalisedItemName);
    }

    public int uniqueItemNameCount()
    {
        return uniqueItemFamilyByName.size();
    }

    public int ambiguousItemNameCount()
    {
        return ambiguousItemNames.size();
    }

    public Set<String> cardIdsForFamily(String familyId)
    {
        if (familyId == null)
        {
            return Collections.emptySet();
        }
        return cardsByFamily.getOrDefault(
            familyId,
            Collections.emptySet());
    }

    private static final class NameIndex
    {
        private final Map<String, String> unique;
        private final Set<String> ambiguous;

        private NameIndex(
            Map<String, String> unique,
            Set<String> ambiguous)
        {
            this.unique = unique;
            this.ambiguous = ambiguous;
        }
    }

}
