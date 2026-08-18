package com.cardrestricted.collection;

import com.cardrestricted.domain.EconomyMode;
import com.cardrestricted.domain.IntegrityMode;
import com.cardrestricted.starter.StarterRewardChoice;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

public final class CollectionCreationRequest
{
    private final long accountHash;
    private final String displayName;
    private final EconomyMode economyMode;
    private final IntegrityMode integrityMode;
    private final StarterRewardChoice starterRewardChoice;
    private final Set<String> profileMarkers;

    public CollectionCreationRequest(
        long accountHash,
        String displayName,
        EconomyMode economyMode,
        IntegrityMode integrityMode,
        StarterRewardChoice starterRewardChoice)
    {
        this(accountHash, displayName, economyMode, integrityMode,
            starterRewardChoice, Collections.emptySet());
    }

    public CollectionCreationRequest(
        long accountHash,
        String displayName,
        EconomyMode economyMode,
        IntegrityMode integrityMode,
        StarterRewardChoice starterRewardChoice,
        Set<String> profileMarkers)
    {
        if (accountHash == 0L || accountHash == -1L)
        {
            throw new IllegalArgumentException(
                "A stable account hash is required.");
        }
        this.accountHash = accountHash;
        this.displayName = requireText(displayName, "displayName");
        this.economyMode =
            Objects.requireNonNull(economyMode, "economyMode");
        this.integrityMode =
            Objects.requireNonNull(integrityMode, "integrityMode");
        this.starterRewardChoice = Objects.requireNonNull(
            starterRewardChoice,
            "starterRewardChoice");
        this.profileMarkers = Collections.unmodifiableSet(
            new HashSet<>(Objects.requireNonNull(profileMarkers, "profileMarkers")));
    }

    public long getAccountHash()
    {
        return accountHash;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public EconomyMode getEconomyMode()
    {
        return economyMode;
    }

    public IntegrityMode getIntegrityMode()
    {
        return integrityMode;
    }

    public StarterRewardChoice getStarterRewardChoice()
    {
        return starterRewardChoice;
    }

    public Set<String> getProfileMarkers()
    {
        return profileMarkers;
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
