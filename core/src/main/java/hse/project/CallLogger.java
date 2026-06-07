package hse.project;

import hse.project.graph.dump.GraphExporter;
import hse.project.graph.dump.GraphExporterFactory;
import hse.project.graph.struct.CallGraph;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

public class CallLogger {
    private static final Set<String> EXCLUDED_PACKAGES = Set.of(
            "java.", "javax.", "jdk.", "sun.",
            "com.sun.", "org.xml.", "org.w3c.",
            "hse.project" // TODO: auto skip without adding to Excluded packages
    ); // TODO: make runtime-applied: read from config file, save in instrumented JAR dynamically

    private static final CallGraph callGraph = new CallGraph();

    private static final ThreadLocal<Deque<String>> CALL_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    static {
        // auto-dump on JVM exit; disabled for specific tests
        if (Boolean.parseBoolean(System.getProperty("callgraph.dumpOnShutdown", "true"))) {
            Runtime.getRuntime().addShutdownHook(new Thread(CallLogger::dump));
        }
    }

    public static void enter(String callee) {
        Deque<String> stack = CALL_STACK.get();
        String caller = stack.peek();
        if (caller != null) {
            // null caller means it's an entry point, so no edge
            recordEdge(caller, callee);
        }
        stack.push(callee);
    }

    public static void exit() {
        Deque<String> stack = CALL_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    private static void recordEdge(String caller, String callee) {
        if (isExcluded(caller) || isExcluded(callee)) {
            return;
        }

        // TODO: caller thread info? Thread.currentThread().getName()

        callGraph.addEdge(caller, callee);
    }

    public static boolean isExcluded(String className) {
        for (String prefix : EXCLUDED_PACKAGES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static CallGraph snapshot() {
        return callGraph;
    }

    public static void reset() {
        callGraph.clear();
        CALL_STACK.get().clear();
    }

    public static void dump() {
        String exporterType = System.getProperty("callgraph.exporterType", "simple"); // FIXME: restore to class variables after resolving issue with maven plugin configs
        GraphExporter exporter = GraphExporterFactory.createExporter(exporterType);
        String outputFile = System.getProperty("callgraph.output", "calls.txt");
        try {
            exporter.export(callGraph, outputFile);
        } catch (IOException e) {
            System.err.println("Failed to export graph: " + e.getMessage());
        }
    }

}
