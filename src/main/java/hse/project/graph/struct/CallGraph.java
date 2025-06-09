package hse.project.graph.struct;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CallGraph {
    private final ConcurrentHashMap<Pair, Integer> graph = new ConcurrentHashMap<>();

    private record Pair(String caller, String callee) {}

    public void addEdge(String caller, String callee) {
        Pair key = new Pair(caller, callee);
        graph.merge(key, 1, Integer::sum);
    }

    public long getEdgeCount() {
        return graph.size();
    }

    public List<Edge> getEdges() {
        return graph.entrySet().stream()
                .map(entry -> new Edge(
                        entry.getKey().caller(),
                        entry.getKey().callee(),
                        entry.getValue()
                ))
                .collect(Collectors.toList());
    }
}
