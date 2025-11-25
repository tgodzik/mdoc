package mdoc.internal.markdown;

/**
 * Data class representing a file import.
 */
public final class FileImportData {
    private final String path;
    private final String content;

    public FileImportData(String path, String content) {
        this.path = path;
        this.content = content;
    }

    public String path() {
        return path;
    }

    public String content() {
        return content;
    }
}
