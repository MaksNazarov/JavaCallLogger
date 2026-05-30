package hse.project.graph.dump;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DotExporterTest {

    @Test
    void wrapsEdgesInDigraph(@TempDir Path dir) throws IOException {
        String out = ExporterTestSupport.exportToString(
                new DotExporter(), ExporterTestSupport.sampleGraph(), dir);

        assertTrue(out.startsWith("digraph CallGraph {"), out);
        assertTrue(out.trim().endsWith("}"), out);
    }

    @Test
    void writesQuotedWeightedEdges(@TempDir Path dir) throws IOException {
        String out = ExporterTestSupport.exportToString(
                new DotExporter(), ExporterTestSupport.sampleGraph(), dir);

        assertTrue(out.contains("\"A::run\" -> \"B::work\" [weight=2];"), out);
        assertTrue(out.contains("\"B::work\" -> \"C::done\" [weight=1];"), out);
    }
}
