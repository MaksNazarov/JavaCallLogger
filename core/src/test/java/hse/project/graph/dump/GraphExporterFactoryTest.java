package hse.project.graph.dump;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphExporterFactoryTest {

    @Test
    void createsDotExporter() {
        assertInstanceOf(DotExporter.class, GraphExporterFactory.createExporter("dot"));
    }

    @Test
    void createsSimpleExporter() {
        assertInstanceOf(SimpleExporter.class, GraphExporterFactory.createExporter("simple"));
    }

    @Test
    void createsGraphMLExporter() {
        assertInstanceOf(GraphMLExporter.class, GraphExporterFactory.createExporter("graphml"));
    }

    @Test
    void formatIsCaseInsensitive() {
        assertInstanceOf(GraphMLExporter.class, GraphExporterFactory.createExporter("GraphML"));
    }

    @Test
    void throwsOnUnsupportedFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> GraphExporterFactory.createExporter("xml"));
    }
}
