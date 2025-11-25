package mdoc.internal.markdown;

/**
 * A diagnostic message from the compiler.
 */
public final class CompilerDiagnostic {
    private final int startLine;
    private final int startColumn;
    private final int endLine;
    private final int endColumn;
    private final String message;
    private final Severity severity;

    public enum Severity {
        ERROR, WARNING, INFO
    }

    public CompilerDiagnostic(
        int startLine,
        int startColumn,
        int endLine,
        int endColumn,
        String message,
        Severity severity
    ) {
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.message = message;
        this.severity = severity;
    }

    public int startLine() {
        return startLine;
    }

    public int startColumn() {
        return startColumn;
    }

    public int endLine() {
        return endLine;
    }

    public int endColumn() {
        return endColumn;
    }

    public String message() {
        return message;
    }

    public Severity severity() {
        return severity;
    }
}
