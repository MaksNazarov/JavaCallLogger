package hse.project.graph.dump;

import hse.project.graph.struct.CallGraph;
import hse.project.graph.struct.Edge;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class DotExporter implements GraphExporter {
    @Override
    public void export(CallGraph graph, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("digraph CallGraph {\n");
            for (Edge edge : graph.getEdges()) {
                writer.write(String.format("    \"%s\" -> \"%s\" [weight=%d];\n",
                        edge.source, edge.target, edge.weight));
            }
            writer.write("}\n");
        }
    }
}
