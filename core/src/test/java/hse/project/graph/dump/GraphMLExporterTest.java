package hse.project.graph.dump;

import hse.project.graph.struct.CallGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphMLExporterTest {

    @Test
    void writesGraphMLHeaderAndKeys(@TempDir Path dir) throws IOException {
        String out = ExporterTestSupport.exportToString(
                new GraphMLExporter(), ExporterTestSupport.sampleGraph(), dir);

        assertTrue(out.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"), out);
        assertTrue(out.contains("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">"), out);
        assertTrue(out.contains("<key id=\"label\" for=\"node\" attr.name=\"label\" attr.type=\"string\"/>"), out);
        assertTrue(out.contains("<key id=\"weight\" for=\"edge\" attr.name=\"weight\" attr.type=\"int\"/>"), out);
        assertTrue(out.contains("<graph id=\"CallGraph\" edgedefault=\"directed\">"), out);
    }

    @Test
    void emitsNodesWithLabelsAndWeightedEdges(@TempDir Path dir) throws IOException {
        String out = ExporterTestSupport.exportToString(
                new GraphMLExporter(), ExporterTestSupport.sampleGraph(), dir);

        // Three distinct methods become three nodes carrying their original names.
        assertTrue(out.contains("<data key=\"label\">A::run</data>"), out);
        assertTrue(out.contains("<data key=\"label\">B::work</data>"), out);
        assertTrue(out.contains("<data key=\"label\">C::done</data>"), out);

        // A::run -> B::work was recorded twice, so its edge weight is 2.
        assertTrue(out.contains("<data key=\"weight\">2</data>"), out);
        assertTrue(out.contains("<data key=\"weight\">1</data>"), out);
    }

    @Test
    void escapesXmlSpecialCharactersInNames(@TempDir Path dir) throws IOException {
        CallGraph graph = new CallGraph();
        // Constructors/static initializers surface as <init>/<clinit> from the caller frame.
        graph.addEdge("Foo::<init>", "Bar::<clinit>");

        String out = ExporterTestSupport.exportToString(new GraphMLExporter(), graph, dir);

        assertTrue(out.contains("&lt;init&gt;"), out);
        assertTrue(out.contains("&lt;clinit&gt;"), out);
        assertTrue(!out.contains("<init>") && !out.contains("<clinit>"), out);
    }

    @Test
    void producesWellFormedXml(@TempDir Path dir) throws IOException {
        CallGraph graph = new CallGraph();
        graph.addEdge("Foo::<init>", "Bar::run");

        String out = ExporterTestSupport.exportToString(new GraphMLExporter(), graph, dir);

        assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(out.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void emptyGraphIsStillWellFormed(@TempDir Path dir) throws IOException {
        String out = ExporterTestSupport.exportToString(new GraphMLExporter(), new CallGraph(), dir);

        assertTrue(out.contains("<graph id=\"CallGraph\" edgedefault=\"directed\">"), out);
        assertTrue(out.contains("</graphml>"), out);
        assertEquals(-1, out.indexOf("<node"), "empty graph should have no nodes");
        assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(out.getBytes(StandardCharsets.UTF_8))));
    }
}
