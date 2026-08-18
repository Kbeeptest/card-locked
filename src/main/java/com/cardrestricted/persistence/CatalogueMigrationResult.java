package com.cardrestricted.persistence;

import java.util.Objects;

public final class CatalogueMigrationResult
{
    private final CollectionState state;
    private final boolean migrated;
    private final int aliasesResolved;
    private final int ownershipCollisions;
    private final int foilAliasesResolved;
    private final int pendingAliasesResolved;

    public CatalogueMigrationResult(
        CollectionState state,
        boolean migrated,
        int aliasesResolved,
        int ownershipCollisions,
        int foilAliasesResolved,
        int pendingAliasesResolved)
    {
        this.state = Objects.requireNonNull(state, "state");
        this.migrated = migrated;
        this.aliasesResolved = aliasesResolved;
        this.ownershipCollisions = ownershipCollisions;
        this.foilAliasesResolved = foilAliasesResolved;
        this.pendingAliasesResolved = pendingAliasesResolved;
    }

    public CollectionState getState()
    {
        return state;
    }

    public boolean isMigrated()
    {
        return migrated;
    }

    public int getAliasesResolved()
    {
        return aliasesResolved;
    }

    public int getOwnershipCollisions()
    {
        return ownershipCollisions;
    }

    public int getFoilAliasesResolved()
    {
        return foilAliasesResolved;
    }

    public int getPendingAliasesResolved()
    {
        return pendingAliasesResolved;
    }
}
