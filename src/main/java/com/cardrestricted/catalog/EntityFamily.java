package com.cardrestricted.catalog;

import com.cardrestricted.domain.EntityType;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class EntityFamily
{
    private final String familyId;
    private final EntityType entityType;
    private final int canonicalEntityId;
    private final Set<Integer> variantEntityIds;
    private final int familyVersion;
    private final boolean freeToPlay;

    public EntityFamily(
        String familyId,
        EntityType entityType,
        int canonicalEntityId,
        Set<Integer> variantEntityIds,
        int familyVersion,
        boolean freeToPlay)
    {
        this.familyId = requireText(familyId, "familyId");
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.canonicalEntityId = canonicalEntityId;
        this.variantEntityIds = Collections.unmodifiableSet(
            new HashSet<>(Objects.requireNonNull(
                variantEntityIds, "variantEntityIds")));
        this.familyVersion = familyVersion;
        this.freeToPlay = freeToPlay;

        if (entityType != EntityType.ITEM
            && entityType != EntityType.NPC
            && entityType != EntityType.OBJECT)
        {
            throw new IllegalArgumentException(
                "Catalogue families support items, NPCs and objects only.");
        }
        if (canonicalEntityId < 0)
        {
            throw new IllegalArgumentException(
                "canonicalEntityId cannot be negative.");
        }
        if (this.variantEntityIds.contains(canonicalEntityId))
        {
            throw new IllegalArgumentException(
                "The canonical entity cannot also be a variant.");
        }
        if (this.variantEntityIds.stream().anyMatch(id -> id < 0))
        {
            throw new IllegalArgumentException(
                "Variant entity IDs cannot be negative.");
        }
        if (familyVersion < 1)
        {
            throw new IllegalArgumentException(
                "familyVersion must be positive.");
        }
    }

    public String getFamilyId()
    {
        return familyId;
    }

    public EntityType getEntityType()
    {
        return entityType;
    }

    public int getCanonicalEntityId()
    {
        return canonicalEntityId;
    }

    public Set<Integer> getVariantEntityIds()
    {
        return variantEntityIds;
    }

    public int getFamilyVersion()
    {
        return familyVersion;
    }

    public boolean isFreeToPlay()
    {
        return freeToPlay;
    }

    public Set<Integer> allEntityIds()
    {
        Set<Integer> all = new HashSet<>(variantEntityIds);
        all.add(canonicalEntityId);
        return Collections.unmodifiableSet(all);
    }

    private static String requireText(String value, String field)
    {
        Objects.requireNonNull(value, field);
        if (value.trim().isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value;
    }
}
