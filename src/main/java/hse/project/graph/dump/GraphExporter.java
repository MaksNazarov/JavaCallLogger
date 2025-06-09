package hse.project.graph.dump;

import hse.project.graph.struct.CallGraph;

import java.io.IOException;

public interface GraphExporter {
    void export(CallGraph graph, String filePath) throws IOException;
}
