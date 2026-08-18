package com.cardrestricted.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class QuestReadinessSnapshot
{
    private final List<QuestReadinessEntry> entries;
    private final int completeCount;
    private final int readyCount;
    private final int blockedCount;

    QuestReadinessSnapshot(
        List<QuestReadinessEntry> entries,
        int completeCount,
        int readyCount,
        int blockedCount)
    {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.completeCount = completeCount;
        this.readyCount = readyCount;
        this.blockedCount = blockedCount;
    }

    public List<QuestReadinessEntry> getEntries()
    {
        return entries;
    }

    public int getCompleteCount()
    {
        return completeCount;
    }

    /** Card-ready but not complete. */
    public int getReadyCount()
    {
        return readyCount;
    }

    public int getBlockedCount()
    {
        return blockedCount;
    }

    public int getTotalCount()
    {
        return entries.size();
    }
}
