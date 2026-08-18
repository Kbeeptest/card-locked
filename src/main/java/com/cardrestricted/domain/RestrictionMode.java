package com.cardrestricted.domain;

/** Controls whether restriction decisions are enforced or only observed. */
public enum RestrictionMode
{
    ENFORCE("Enforce blocked actions"),
    AUDIT_ONLY("Audit only"),
    DISABLED("Disable restrictions");

    private final String displayName;

    RestrictionMode(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
