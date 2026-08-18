package com.cardrestricted.foil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derived foil access for one immutable collection state.
 *
 * <p>Derived cards are use permissions only. They are deliberately separate
 * from owned cards and therefore cannot affect collection completion,
 * duplicate protection, foil ownership, Nexus pricing, or card-count gates.</p>
 */
public final class FoilEntitlementSnapshot
{
    private final Set<String> ownedCardIds;
    private final Set<String> foilCardIds;
    private final Set<String> derivedCardIds;
    private final Set<String> usableCardIds;
    private final Map<String, List<FoilUnlockProvenance>> provenanceByTarget;

    FoilEntitlementSnapshot(
        Set<String> ownedCardIds,
        Set<String> foilCardIds,
        Set<String> derivedCardIds,
        Map<String, List<FoilUnlockProvenance>> provenanceByTarget)
    {
        this.ownedCardIds = immutableSet(ownedCardIds);
        this.foilCardIds = immutableSet(foilCardIds);
        this.derivedCardIds = immutableSet(derivedCardIds);
        LinkedHashSet<String> usable = new LinkedHashSet<>(this.ownedCardIds);
        usable.addAll(this.derivedCardIds);
        this.usableCardIds = Collections.unmodifiableSet(usable);

        Map<String, List<FoilUnlockProvenance>> provenance =
            new LinkedHashMap<>();
        for (Map.Entry<String, List<FoilUnlockProvenance>> entry
            : provenanceByTarget.entrySet())
        {
            provenance.put(
                entry.getKey(),
                Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        this.provenanceByTarget = Collections.unmodifiableMap(provenance);
    }

    public Set<String> getOwnedCardIds()
    {
        return ownedCardIds;
    }

    public Set<String> getFoilCardIds()
    {
        return foilCardIds;
    }

    public Set<String> getDerivedCardIds()
    {
        return derivedCardIds;
    }

    public Set<String> getUsableCardIds()
    {
        return usableCardIds;
    }

    public boolean isDerivedUnlocked(String cardId)
    {
        return derivedCardIds.contains(cardId);
    }

    public List<FoilUnlockProvenance> getProvenance(String targetCardId)
    {
        return provenanceByTarget.getOrDefault(
            targetCardId,
            Collections.emptyList());
    }

    public Map<String, List<FoilUnlockProvenance>> getProvenanceByTarget()
    {
        return provenanceByTarget;
    }

    private static Set<String> immutableSet(Set<String> values)
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
