package hse.project.graph.dump;

import hse.project.graph.struct.CallGraph;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ExporterTestSupport {

    private ExporterTestSupport() {
    }

    static String exportToString(GraphExporter exporter, CallGraph graph, Path dir) throws IOException {
        Path out = dir.resolve("graph.out");
        exporter.export(graph, out.toString());
        return new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
    }

    // TODO: is enough? Add init?
    static CallGraph sampleGraph() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A::run", "B::work");
        graph.addEdge("A::run", "B::work");
        graph.addEdge("B::work", "C::done");
        return graph;
    }
}
