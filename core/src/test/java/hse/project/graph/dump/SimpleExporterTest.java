package hse.project.graph.dump;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleExporterTest {

    @Test
    void writesNodeAndEdgeCountHeader(@TempDir Path dir) throws IOException {
        String out = ExporterTestSupport.exportToString(
                new SimpleExporter(), ExporterTestSupport.sampleGraph(), dir);

        assertTrue(out.startsWith("3 2"), out);
    }

    @Test
    void writesWeightedEdgeLines(@TempDir Path dir) throws IOException {
        String out = ExporterTestSupport.exportToString(
                new SimpleExporter(), ExporterTestSupport.sampleGraph(), dir);

        assertTrue(out.contains("A::run B::work 2"), out);
        assertTrue(out.contains("B::work C::done 1"), out);
    }
}
