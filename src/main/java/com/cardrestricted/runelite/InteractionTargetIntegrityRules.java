package com.cardrestricted.runelite;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reconciles entry/event target text without allowing one rewritten metadata
 * source to erase a tracked NPC or participating item identity.
 */
public final class InteractionTargetIntegrityRules
{
    private InteractionTargetIntegrityRules()
    {
    }

    public static NpcTargetResolution resolveNpcTarget(
        String entryTarget,
        String eventTarget,
        Predicate<String> knownOrAmbiguousNpcName)
    {
        Predicate<String> known = knownOrAmbiguousNpcName == null
            ? ignored -> false
            : knownOrAmbiguousNpcName;
        Set<String> all = new LinkedHashSet<>();
        Set<String> tracked = new LinkedHashSet<>();
        addNpcName(all, tracked, entryTarget, known);
        addNpcName(all, tracked, eventTarget, known);

        boolean conflictingTracked = tracked.size() > 1;
        String selected;
        if (tracked.size() == 1)
        {
            selected = tracked.iterator().next();
        }
        else
        {
            String effective = InteractionIntegrityRules.effectiveTarget(
                entryTarget,
                eventTarget);
            selected = InteractionNameNormalizer.targetEntityName(effective);
            if (selected.isEmpty() && !all.isEmpty())
            {
                selected = all.iterator().next();
            }
        }
        return new NpcTargetResolution(
            selected,
            !tracked.isEmpty(),
            conflictingTracked,
            all,
            tracked);
    }

    public static Set<String> allItemNameCandidates(
        String entryTarget,
        String eventTarget)
    {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(InteractionNameNormalizer.itemNameCandidates(entryTarget));
        result.addAll(InteractionNameNormalizer.itemNameCandidates(eventTarget));
        return immutable(result);
    }

    public static Set<String> sourceItemNameCandidates(
        String entryTarget,
        String eventTarget)
    {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(InteractionNameNormalizer.sourceItemNameCandidates(entryTarget));
        result.addAll(InteractionNameNormalizer.sourceItemNameCandidates(eventTarget));
        return immutable(result);
    }

    public static Set<String> targetItemNameCandidates(
        String entryTarget,
        String eventTarget)
    {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(InteractionNameNormalizer.targetItemNameCandidates(entryTarget));
        result.addAll(InteractionNameNormalizer.targetItemNameCandidates(eventTarget));
        return immutable(result);
    }

    private static void addNpcName(
        Set<String> all,
        Set<String> tracked,
        String target,
        Predicate<String> known)
    {
        String name = InteractionNameNormalizer.targetEntityName(target);
        if (name.isEmpty())
        {
            return;
        }
        all.add(name);
        if (known.test(name))
        {
            tracked.add(name);
        }
    }

    private static Set<String> immutable(Set<String> values)
    {
        if (values.isEmpty())
        {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    public static final class NpcTargetResolution
    {
        private final String selectedName;
        private final boolean knownTrackedTarget;
        private final boolean conflictingTrackedTargets;
        private final Set<String> allNames;
        private final Set<String> trackedNames;

        private NpcTargetResolution(
            String selectedName,
            boolean knownTrackedTarget,
            boolean conflictingTrackedTargets,
            Set<String> allNames,
            Set<String> trackedNames)
        {
            this.selectedName = selectedName == null ? "" : selectedName;
            this.knownTrackedTarget = knownTrackedTarget;
            this.conflictingTrackedTargets = conflictingTrackedTargets;
            this.allNames = immutable(allNames);
            this.trackedNames = immutable(trackedNames);
        }

        public String getSelectedName()
        {
            return selectedName;
        }

        public boolean isKnownTrackedTarget()
        {
            return knownTrackedTarget;
        }

        public boolean hasConflictingTrackedTargets()
        {
            return conflictingTrackedTargets;
        }

        public Set<String> getAllNames()
        {
            return allNames;
        }

        public Set<String> getTrackedNames()
        {
            return trackedNames;
        }
    }
}
