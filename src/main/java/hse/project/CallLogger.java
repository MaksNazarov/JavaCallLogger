package hse.project;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class CallLogger {
    private static HashMap<Pair, Integer> graph = new HashMap<>();

    public static void log(String caller, String callee) {
        Pair key = new Pair(caller, callee);
        graph.put(key, graph.getOrDefault(key, 0) + 1);
    }

    public static void dump() {
        Set<String> nodes = new HashSet<>();
        for (Pair pair : graph.keySet()) {
            nodes.add(pair.caller);
            nodes.add(pair.callee);
        }

        System.out.println(nodes.size() + " " + graph.size()); // node count, arc count
        for (var entry : graph.entrySet()) {
            Pair pair = entry.getKey();
            System.out.println(pair.caller + " " + pair.callee + " " + entry.getValue());
        }
    }

    private static record Pair(String caller, String callee) {}
}
