package com.cardrestricted.diagnostics;

/** Fixed, non-sensitive operation identifiers used in local diagnostics. */
public enum DiagnosticOperation
{
    STARTUP("CL-START-001", "Card Locked could not start."),
    STORAGE_PREPARE("CL-STORAGE-001", "Card Locked could not prepare its local storage."),
    ARTWORK_WARMUP("CL-ART-001", "Card Locked could not prepare offline artwork."),
    SESSION_OPEN("CL-SESSION-001", "The local collection could not be loaded."),
    PROFILE_CREATE("CL-PROFILE-001", "The collection could not be created."),
    INTEGRITY_UPDATE("CL-PROFILE-002", "The integrity setting could not be saved."),
    PROFILE_RESET("CL-PROFILE-003", "The profile could not be reset."),
    BACKUP_EXPORT("CL-RECOVERY-001", "The save backup could not be exported."),
    BACKUP_IMPORT("CL-RECOVERY-002", "The selected save backup could not be imported."),
    BACKUP_RESTORE("CL-RECOVERY-003", "The automatic backup could not be restored."),
    TEST_BALANCE("CL-TEST-001", "The temporary testing balance could not be saved."),
    PACK_PURCHASE("CL-PACK-001", "The pack operation could not be completed."),
    PACK_REVEAL("CL-PACK-002", "The card reveal could not be completed."),
    NEXUS_EXCHANGE("CL-NEXUS-001", "The Nexus exchange could not be completed."),
    NPC_REWARD("CL-REWARD-001", "The NPC reward could not be saved."),
    XP_REWARD("CL-REWARD-002", "The experience reward could not be saved."),
    LEVEL_REWARD("CL-REWARD-003", "The level reward could not be saved."),
    QUEST_REWARD("CL-REWARD-004", "The quest reward could not be saved."),
    CLUE_REWARD("CL-REWARD-005", "The clue reward could not be saved."),
    DIAGNOSTIC_EXPORT("CL-DIAG-001", "The diagnostic report could not be exported."),
    CLEANUP("CL-LIFE-001", "A plugin resource did not shut down cleanly."),
    SHUTDOWN("CL-SHUT-001", "Card Locked did not finish shutting down cleanly.");

    private final String referenceCode;
    private final String userMessage;

    DiagnosticOperation(String referenceCode, String userMessage)
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
        return userMessage;
    }

    public String getUserMessageWithAdvice()
    {
        return userMessage
            + " Export a local diagnostic report from the Card Locked panel. "
            + "Reference " + referenceCode + ".";
    }
}
