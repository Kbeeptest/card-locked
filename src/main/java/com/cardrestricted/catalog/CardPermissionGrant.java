package com.cardrestricted.catalog;

import com.cardrestricted.domain.ActionType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class CardPermissionGrant
{
    private final String entityFamilyId;
    private final Set<ActionType> permissions;

    public CardPermissionGrant(
        String entityFamilyId,
        Set<ActionType> permissions)
    {
        this.entityFamilyId =
            requireText(entityFamilyId, "entityFamilyId");
        Objects.requireNonNull(permissions, "permissions");
        if (permissions.isEmpty())
        {
            throw new IllegalArgumentException(
                "A permission grant must contain at least one action.");
        }
        this.permissions = Collections.unmodifiableSet(
            EnumSet.copyOf(permissions));
    }

    public String getEntityFamilyId()
    {
        return entityFamilyId;
    }

    public Set<ActionType> getPermissions()
    {
        return permissions;
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
