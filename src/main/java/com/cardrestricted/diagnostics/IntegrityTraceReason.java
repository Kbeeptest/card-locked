package com.cardrestricted.diagnostics;

/** Fixed, non-sensitive reason codes used in local diagnostic exports. */
public enum IntegrityTraceReason
{
    POLICY_ALLOWED,
    POLICY_BLOCKED,
    AUDIT_ONLY_WOULD_BLOCK,
    BANK_NAVIGATION,
    STORAGE_RECOVERY,
    STATE_PENDING_SAFE,
    STATE_PENDING_BLOCKED,
    RESTRICTION_DISABLED,
    RUNTIME_INACTIVE,
    SERVICE_UNAVAILABLE,
    NOT_LOGGED_IN,
    INTERNAL_FAILURE
}
