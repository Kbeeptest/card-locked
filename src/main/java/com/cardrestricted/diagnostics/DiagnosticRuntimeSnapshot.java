package com.cardrestricted.diagnostics;

import java.util.Objects;

/** Non-sensitive runtime flags captured at diagnostic export time. */
public final class DiagnosticRuntimeSnapshot
{
    private final String gameState;
    private final String sessionStatus;
    private final boolean startupComplete;
    private final boolean sessionSuspended;
    private final boolean collectionRuntimeActive;
    private final boolean restrictionRuntimeActive;
    private final boolean panelPresent;
    private final boolean overlaysPresent;
    private final boolean artworkExecutorActive;
    private final boolean taskScopeAccepting;
    private final int trackedTasks;
    private final boolean restrictionStatePending;
    private final boolean autocastVerified;
    private final boolean shopOpen;
    private final boolean shopAuthorized;
    private final boolean storageOpen;
    private final boolean storageAuthorized;
    private final boolean exchangeOpen;
    private final boolean exchangeAuthorized;
    private final boolean serviceOpen;
    private final boolean serviceAuthorized;
    private final int integrityTraceEvents;

    public DiagnosticRuntimeSnapshot(
        String gameState,
        String sessionStatus,
        boolean startupComplete,
        boolean sessionSuspended,
        boolean collectionRuntimeActive,
        boolean restrictionRuntimeActive,
        boolean panelPresent,
        boolean overlaysPresent,
        boolean artworkExecutorActive,
        boolean taskScopeAccepting,
        int trackedTasks)
    {
        this(
            gameState,
            sessionStatus,
            startupComplete,
            sessionSuspended,
            collectionRuntimeActive,
            restrictionRuntimeActive,
            panelPresent,
            overlaysPresent,
            artworkExecutorActive,
            taskScopeAccepting,
            trackedTasks,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            0);
    }

    public DiagnosticRuntimeSnapshot(
        String gameState,
        String sessionStatus,
        boolean startupComplete,
        boolean sessionSuspended,
        boolean collectionRuntimeActive,
        boolean restrictionRuntimeActive,
        boolean panelPresent,
        boolean overlaysPresent,
        boolean artworkExecutorActive,
        boolean taskScopeAccepting,
        int trackedTasks,
        boolean restrictionStatePending,
        boolean autocastVerified,
        boolean shopOpen,
        boolean shopAuthorized,
        boolean storageOpen,
        boolean storageAuthorized,
        boolean exchangeOpen,
        boolean exchangeAuthorized,
        boolean serviceOpen,
        boolean serviceAuthorized,
        int integrityTraceEvents)
    {
        this.gameState = safeEnumLike(gameState);
        this.sessionStatus = safeEnumLike(sessionStatus);
        this.startupComplete = startupComplete;
        this.sessionSuspended = sessionSuspended;
        this.collectionRuntimeActive = collectionRuntimeActive;
        this.restrictionRuntimeActive = restrictionRuntimeActive;
        this.panelPresent = panelPresent;
        this.overlaysPresent = overlaysPresent;
        this.artworkExecutorActive = artworkExecutorActive;
        this.taskScopeAccepting = taskScopeAccepting;
        this.trackedTasks = Math.max(0, trackedTasks);
        this.restrictionStatePending = restrictionStatePending;
        this.autocastVerified = autocastVerified;
        this.shopOpen = shopOpen;
        this.shopAuthorized = shopAuthorized;
        this.storageOpen = storageOpen;
        this.storageAuthorized = storageAuthorized;
        this.exchangeOpen = exchangeOpen;
        this.exchangeAuthorized = exchangeAuthorized;
        this.serviceOpen = serviceOpen;
        this.serviceAuthorized = serviceAuthorized;
        this.integrityTraceEvents = Math.max(0, integrityTraceEvents);
    }

    public String getGameState()
    {
        return gameState;
    }

    public String getSessionStatus()
    {
        return sessionStatus;
    }

    public boolean isStartupComplete()
    {
        return startupComplete;
    }

    public boolean isSessionSuspended()
    {
        return sessionSuspended;
    }

    public boolean isCollectionRuntimeActive()
    {
        return collectionRuntimeActive;
    }

    public boolean isRestrictionRuntimeActive()
    {
        return restrictionRuntimeActive;
    }

    public boolean isPanelPresent()
    {
        return panelPresent;
    }

    public boolean isOverlaysPresent()
    {
        return overlaysPresent;
    }

    public boolean isArtworkExecutorActive()
    {
        return artworkExecutorActive;
    }

    public boolean isTaskScopeAccepting()
    {
        return taskScopeAccepting;
    }

    public int getTrackedTasks()
    {
        return trackedTasks;
    }

    public boolean isRestrictionStatePending()
    {
        return restrictionStatePending;
    }

    public boolean isAutocastVerified()
    {
        return autocastVerified;
    }

    public boolean isShopOpen()
    {
        return shopOpen;
    }

    public boolean isShopAuthorized()
    {
        return shopAuthorized;
    }

    public boolean isStorageOpen()
    {
        return storageOpen;
    }

    public boolean isStorageAuthorized()
    {
        return storageAuthorized;
    }

    public boolean isExchangeOpen()
    {
        return exchangeOpen;
    }

    public boolean isExchangeAuthorized()
    {
        return exchangeAuthorized;
    }

    public boolean isServiceOpen()
    {
        return serviceOpen;
    }

    public boolean isServiceAuthorized()
    {
        return serviceAuthorized;
    }

    public int getIntegrityTraceEvents()
    {
        return integrityTraceEvents;
    }

    private static String safeEnumLike(String value)
    {
        Objects.requireNonNull(value, "value");
        String safe = value.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.length() <= 64 ? safe : safe.substring(0, 64);
    }
}
