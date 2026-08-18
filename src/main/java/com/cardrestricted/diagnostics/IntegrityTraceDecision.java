package com.cardrestricted.diagnostics;

/** Outcome recorded for a recent interaction integrity decision. */
public enum IntegrityTraceDecision
{
    ALLOWED,
    BLOCKED,
    BYPASSED_SAFE,
    NOT_EVALUATED
}
