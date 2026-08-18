package com.cardrestricted.points;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class QuestCompletionTracker
{
    private final Map<String, Boolean> finishedByQuestKey =
        new HashMap<>();
    private final Set<String> pendingQuestKeys =
        new HashSet<>();

    public void establishBaseline(String questKey, boolean finished)
    {
        finishedByQuestKey.put(
            requireQuestKey(questKey),
            finished);
        pendingQuestKeys.remove(questKey);
    }

    public boolean observe(String questKey, boolean finished)
    {
        String key = requireQuestKey(questKey);
        Boolean previous = finishedByQuestKey.get(key);
        if (previous == null)
        {
            establishBaseline(key, finished);
            return false;
        }
        if (!finished)
        {
            finishedByQuestKey.put(key, false);
            return false;
        }
        if (previous || pendingQuestKeys.contains(key))
        {
            return false;
        }
        pendingQuestKeys.add(key);
        return true;
    }

    public void markCommitted(String questKey)
    {
        String key = requireQuestKey(questKey);
        finishedByQuestKey.put(key, true);
        pendingQuestKeys.remove(key);
    }

    public void markFailed(String questKey)
    {
        pendingQuestKeys.remove(requireQuestKey(questKey));
    }

    public void clear()
    {
        finishedByQuestKey.clear();
        pendingQuestKeys.clear();
    }

    private String requireQuestKey(String value)
    {
        Objects.requireNonNull(value, "questKey");
        if (value.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                "questKey cannot be blank.");
        }
        return value;
    }
}
