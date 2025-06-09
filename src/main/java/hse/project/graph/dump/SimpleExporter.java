package hse.project.graph.dump;

import hse.project.graph.struct.CallGraph;
import hse.project.graph.struct.Edge;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleExporter implements GraphExporter{
    @Override
    public void export(CallGraph graph, String filePath) {
        File file = new File(filePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            Set<String> nodes = ConcurrentHashMap.newKeySet();
            for (Edge edge : graph.getEdges()) {
                nodes.add(edge.source());
                nodes.add(edge.target());
            }

            writer.write(nodes.size() + " " + graph.getEdgeCount());
            writer.newLine();
            for (Edge entry : graph.getEdges()) {
                writer.write(String.format(
                        "%s %s %d",
                        entry.source(),
                        entry.target(),
                        entry.weight()
                ));
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }
}
