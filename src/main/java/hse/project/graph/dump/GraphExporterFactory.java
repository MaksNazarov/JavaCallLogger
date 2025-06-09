package hse.project.graph.dump;

public class GraphExporterFactory {
    public static GraphExporter createExporter(String format) {
        switch (format.toLowerCase()) {
            case "dot": return new DotExporter();
            case "simple": return new SimpleExporter();
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }
}
