package mdoc.internal.markdown;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A compiler interface for compiling Markdown code blocks.
 * This interface abstracts over Scala 2 and Scala 3 compiler implementations.
 *
 * <p>Implementations are loaded via ServiceLoader. Each Scala version (2.12, 2.13, 3.x)
 * should provide its own implementation that will be cross-published.
 *
 * <p><b>Type Parameters:</b> This interface uses {@code Object} for Scala-specific types
 * to enable cross-version compatibility. The actual types are:
 * <ul>
 *   <li>{@code input} - scala.meta.inputs.Input</li>
 *   <li>{@code reporter} - mdoc.Reporter</li>
 *   <li>{@code edit} - mdoc.internal.pos.TokenEditDistance</li>
 *   <li>{@code sectionPos} - scala.meta.inputs.Position</li>
 *   <li>{@code fileImports} - scala.collection.immutable.List[mdoc.document.FileImport]</li>
 * </ul>
 *
 * <p>Scala code using this interface should use the type-safe wrapper in {@code MarkdownCompilerOps}.
 */
public interface MarkdownCompiler {

    /**
     * Compile input source and return the compiled class.
     *
     * @param input The source input to compile (scala.meta.inputs.Input)
     * @param reporter The reporter for diagnostic messages (mdoc.Reporter)
     * @param edit Token edit distance for position mapping (mdoc.internal.pos.TokenEditDistance)
     * @param className The fully qualified class name to load
     * @param fileImports List of file imports (scala.collection.immutable.List[mdoc.document.FileImport])
     * @return Optional containing the compiled class, or empty if compilation failed
     */
    Optional<Class<?>> compile(
        Object input,
        Object reporter,
        Object edit,
        String className,
        Object fileImports
    );

    /**
     * Compile sources without loading the class.
     * Used for reporting compilation errors.
     *
     * @param input The source input to compile (scala.meta.inputs.Input)
     * @param reporter The reporter for diagnostic messages (mdoc.Reporter)
     * @param edit Token edit distance for position mapping (mdoc.internal.pos.TokenEditDistance)
     * @param fileImports List of file imports (scala.collection.immutable.List[mdoc.document.FileImport])
     */
    void compileSources(
        Object input,
        Object reporter,
        Object edit,
        Object fileImports
    );

    /**
     * Compile and return formatted error messages.
     *
     * @param edit Token edit distance for position mapping (mdoc.internal.pos.TokenEditDistance)
     * @param input The source input to compile (scala.meta.inputs.Input)
     * @param sectionPos The section position for filtering errors (scala.meta.inputs.Position)
     * @return Formatted error messages as a string
     */
    String fail(
        Object edit,
        Object input,
        Object sectionPos
    );

    /**
     * Check if there are compilation errors.
     *
     * @return true if there are errors, false otherwise
     */
    boolean hasErrors();

    /**
     * Check if there are compilation warnings.
     *
     * @return true if there are warnings, false otherwise
     */
    boolean hasWarnings();

    /**
     * Shutdown the compiler and release resources.
     */
    void shutdown();

    /**
     * Get the classpath entries used by this compiler.
     *
     * @return List of classpath paths
     */
    List<Path> classpathEntries();

    /**
     * Get the scalac options used by this compiler.
     *
     * @return The scalac options string
     */
    String scalacOptions();

    /**
     * Create a new instance of the MarkdownCompiler.
     *
     * <p>This factory method is used by the ServiceLoader to create instances
     * with specific compiler configurations.
     *
     * @param classpath The classpath string (platform-specific path separator)
     * @param scalacOptions The scalac options string (space-separated)
     * @return A new MarkdownCompiler instance
     */
    MarkdownCompiler newInstance(String classpath, String scalacOptions);
}
