package com.cardrestricted.persistence;

public enum PersistenceCommitStage
{
    AFTER_PENDING_SNAPSHOT_FLUSH,
    AFTER_PENDING_EVENT_FLUSH,
    AFTER_EVENT_COMMIT,
    AFTER_RECOVERY_ROTATION,
    AFTER_SNAPSHOT_PROMOTION
}
