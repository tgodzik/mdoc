package mdoc.internal.markdown;

import java.util.List;

/**
 * Input parameters for compilation.
 */
public final class CompileInput {
    private final String filename;
    private final String sourceCode;
    private final String className;
    private final List<FileImportData> fileImports;

    public CompileInput(
        String filename,
        String sourceCode,
        String className,
        List<FileImportData> fileImports
    ) {
        this.filename = filename;
        this.sourceCode = sourceCode;
        this.className = className;
        this.fileImports = fileImports;
    }

    public String filename() {
        return filename;
    }

    public String sourceCode() {
        return sourceCode;
    }

    public String className() {
        return className;
    }

    public List<FileImportData> fileImports() {
        return fileImports;
    }
}
