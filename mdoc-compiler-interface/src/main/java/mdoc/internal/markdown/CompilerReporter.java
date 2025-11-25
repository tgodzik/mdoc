package mdoc.internal.markdown;

/**
 * Callback interface for reporting compilation diagnostics.
 */
public interface CompilerReporter {
    /**
     * Report a diagnostic message.
     *
     * @param diagnostic The diagnostic to report
     */
    void report(CompilerDiagnostic diagnostic);

    /**
     * Report an error message without position information.
     *
     * @param message The error message
     */
    void error(String message);

    /**
     * Report a warning message without position information.
     *
     * @param message The warning message
     */
    void warning(String message);

    /**
     * Report an info message without position information.
     *
     * @param message The info message
     */
    void info(String message);
}
