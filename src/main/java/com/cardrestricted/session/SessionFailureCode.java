package com.cardrestricted.session;

/** Safe player-facing session failures. Exception messages are never exposed. */
public enum SessionFailureCode
{
    LOAD_FAILED(
        "CL-SESSION-001",
        "Unable to load the local collection. Restrictions remain inactive. Export a local diagnostic report from the Card Locked panel."),
    PROFILE_CREATE_UNAVAILABLE(
        "CL-PROFILE-001",
        "Collection creation is not available in the current session."),
    PROFILE_CREATE_FAILED(
        "CL-PROFILE-002",
        "Unable to create the collection. No incomplete profile will be treated as ready. Export a local diagnostic report from the Card Locked panel."),
    NO_ACTIVE_PROFILE(
        "CL-PROFILE-003",
        "No active profile is available for that operation."),
    INTEGRITY_UPDATE_FAILED(
        "CL-PROFILE-004",
        "Unable to save the integrity change. Export a local diagnostic report from the Card Locked panel."),
    PROFILE_RESET_FAILED(
        "CL-PROFILE-005",
        "Unable to reset the profile. Existing local data has not been intentionally overwritten. Export a local diagnostic report from the Card Locked panel.");

    private final String referenceCode;
    private final String userMessage;

    SessionFailureCode(String referenceCode, String userMessage)
    {
        this.referenceCode = referenceCode;
        this.userMessage = userMessage;
    }

    public String getReferenceCode()
    {
        return referenceCode;
    }

    public String getUserMessage()
    {
        return userMessage + " Reference " + referenceCode + ".";
    }
}
