package com.cardrestricted.session;

import com.cardrestricted.collection.activity.CollectionActivitySnapshot;
import com.cardrestricted.persistence.CollectionState;
import java.util.Objects;
import java.util.Optional;

public final class SessionSnapshot
{
    private final SessionStatus status;
    private final String displayName;
    private final String message;
    private final CollectionState collectionState;
    private final CollectionActivitySnapshot activitySnapshot;
    private final SessionFailureCode failureCode;
    private final String failureType;

    private SessionSnapshot(
        SessionStatus status,
        String displayName,
        String message,
        CollectionState collectionState,
        CollectionActivitySnapshot activitySnapshot,
        SessionFailureCode failureCode,
        String failureType)
    {
        this.status = Objects.requireNonNull(status, "status");
        this.displayName = displayName == null ? "" : displayName;
        this.message = message == null ? "" : message;
        this.collectionState = collectionState;
        this.activitySnapshot = Objects.requireNonNull(
            activitySnapshot,
            "activitySnapshot");
        this.failureCode = failureCode;
        this.failureType = safeFailureType(failureType);
    }

    public static SessionSnapshot loggedOut()
    {
        return new SessionSnapshot(
            SessionStatus.LOGGED_OUT,
            "",
            "Log in to load or create a collection.",
            null,
            CollectionActivitySnapshot.empty(),
            null,
            "");
    }

    public static SessionSnapshot identityUnavailable(String displayName)
    {
        return new SessionSnapshot(
            SessionStatus.IDENTITY_UNAVAILABLE,
            displayName,
            "A stable account identity is not available yet.",
            null,
            CollectionActivitySnapshot.empty(),
            null,
            "");
    }

    public static SessionSnapshot needsSetup(String displayName)
    {
        return new SessionSnapshot(
            SessionStatus.NEEDS_SETUP,
            displayName,
            "No collection exists for this character.",
            null,
            CollectionActivitySnapshot.empty(),
            null,
            "");
    }

    public static SessionSnapshot ready(CollectionState state)
    {
        return ready(state, CollectionActivitySnapshot.empty());
    }

    public static SessionSnapshot ready(
        CollectionState state,
        CollectionActivitySnapshot activitySnapshot)
    {
        return ready(state, activitySnapshot, "Collection loaded.");
    }

    public static SessionSnapshot ready(
        CollectionState state,
        CollectionActivitySnapshot activitySnapshot,
        String message)
    {
        return new SessionSnapshot(
            SessionStatus.READY,
            state.getDisplayName(),
            message == null || message.trim().isEmpty()
                ? "Collection loaded."
                : message,
            state,
            activitySnapshot,
            null,
            "");
    }

    public static SessionSnapshot error(
        String displayName,
        SessionFailureCode failureCode)
    {
        return error(displayName, failureCode, null);
    }

    public static SessionSnapshot error(
        String displayName,
        SessionFailureCode failureCode,
        Throwable failure)
    {
        Objects.requireNonNull(failureCode, "failureCode");
        return new SessionSnapshot(
            SessionStatus.ERROR,
            displayName,
            failureCode.getUserMessage(),
            null,
            CollectionActivitySnapshot.empty(),
            failureCode,
            failure == null ? "" : failure.getClass().getSimpleName());
    }

    public SessionStatus getStatus()
    {
        return status;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public String getMessage()
    {
        return message;
    }

    public Optional<CollectionState> getCollectionState()
    {
        return Optional.ofNullable(collectionState);
    }

    public CollectionActivitySnapshot getActivitySnapshot()
    {
        return activitySnapshot;
    }

    public Optional<SessionFailureCode> getFailureCode()
    {
        return Optional.ofNullable(failureCode);
    }

    public String getFailureType()
    {
        return failureType;
    }

    private static String safeFailureType(String value)
    {
        if (value == null || value.isEmpty())
        {
            return "";
        }
        String safe = value.replaceAll("[^A-Za-z0-9_$.-]", "_");
        return safe.length() <= 80 ? safe : safe.substring(0, 80);
    }
}
