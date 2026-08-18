package com.cardrestricted.diagnostics;

public enum DiagnosticEventCode
{
    STARTUP_BEGIN(DiagnosticSeverity.INFO),
    STARTUP_COMPLETE(DiagnosticSeverity.INFO),
    STARTUP_FAILED(DiagnosticSeverity.ERROR),
    SHUTDOWN_BEGIN(DiagnosticSeverity.INFO),
    SHUTDOWN_COMPLETE(DiagnosticSeverity.INFO),
    CLEANUP_FAILED(DiagnosticSeverity.ERROR),
    TASK_REJECTED(DiagnosticSeverity.ERROR),
    TASK_FAILED(DiagnosticSeverity.ERROR),
    TASK_SHUTDOWN_TIMEOUT(DiagnosticSeverity.WARNING),
    SESSION_FAILURE(DiagnosticSeverity.ERROR),
    RECOVERY_APPLIED(DiagnosticSeverity.WARNING),
    DIAGNOSTIC_EXPORT_COMPLETE(DiagnosticSeverity.INFO),
    DIAGNOSTIC_EXPORT_FAILED(DiagnosticSeverity.ERROR);

    private final DiagnosticSeverity severity;

    DiagnosticEventCode(DiagnosticSeverity severity)
    {
        this.severity = severity;
    }

    public DiagnosticSeverity getSeverity()
    {
        return severity;
    }
}
