package hse.project.graph.dump;

import hse.project.graph.struct.CallGraph;
import hse.project.graph.struct.Edge;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphMLExporter implements GraphExporter {
    @Override
    public void export(CallGraph graph, String filePath) throws IOException {
        List<Edge> edges = graph.getEdges();

        Map<String, String> nodeIds = new LinkedHashMap<>();
        for (Edge edge : edges) {
            nodeIds.computeIfAbsent(edge.source, k -> "n" + nodeIds.size());
            nodeIds.computeIfAbsent(edge.target, k -> "n" + nodeIds.size());
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n");
            writer.write("  <key id=\"label\" for=\"node\" attr.name=\"label\" attr.type=\"string\"/>\n");
            writer.write("  <key id=\"weight\" for=\"edge\" attr.name=\"weight\" attr.type=\"int\"/>\n");
            writer.write("  <graph id=\"CallGraph\" edgedefault=\"directed\">\n");

            for (Map.Entry<String, String> node : nodeIds.entrySet()) {
                writer.write(String.format(
                        "    <node id=\"%s\"><data key=\"label\">%s</data></node>\n",
                        node.getValue(), escape(node.getKey())));
            }

            int edgeId = 0;
            for (Edge edge : edges) {
                writer.write(String.format(
                        "    <edge id=\"e%d\" source=\"%s\" target=\"%s\"><data key=\"weight\">%d</data></edge>\n",
                        edgeId++, nodeIds.get(edge.source), nodeIds.get(edge.target), edge.weight));
            }

            writer.write("  </graph>\n");
            writer.write("</graphml>\n");
        }
    }

    // TODO: is needed really? <init> + args?
    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
