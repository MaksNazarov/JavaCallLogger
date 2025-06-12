package hse.project.graph.struct;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CallGraph {
    private final ConcurrentHashMap<Pair, Integer> graph = new ConcurrentHashMap<>();

    private static class Pair {
        String caller;
        String callee;
        public Pair(String caller, String callee) {
            this.caller = caller;
            this.callee = callee;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Pair)) return false;
            Pair other = (Pair) obj;
            return caller.equals(other.caller) && callee.equals(other.callee);
        }

        @Override
        public int hashCode() {
            return Objects.hash(caller, callee);
        }
    }

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
                        entry.getKey().caller,
                        entry.getKey().callee,
                        entry.getValue()
                ))
                .collect(Collectors.toList());
    }
}
