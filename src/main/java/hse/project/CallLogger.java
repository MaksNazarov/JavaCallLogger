package hse.project;

import hse.project.graph.dump.GraphExporter;
import hse.project.graph.dump.GraphExporterFactory;
import hse.project.graph.struct.CallGraph;

import java.io.IOException;
import java.util.Set;

public class CallLogger {
    private static final String exporterType = "simple";
    private static final Set<String> EXCLUDED_PACKAGES = Set.of(
            "java.", "javax.", "jdk.", "sun.",
            "com.sun.", "org.xml.", "org.w3c.",
            "hse.project" // TODO: auto skip without adding to Excluded packages
    ); // TODO: make runtime-applied: read from config file, save in instrumented JAR dynamically

    private static final CallGraph callGraph = new CallGraph();
    private static final GraphExporter exporter;

    private static String OUTPUT_FILENAME = "calls.txt";

    static {
        exporter = GraphExporterFactory.createExporter(exporterType);
        Runtime.getRuntime().addShutdownHook(new Thread(CallLogger::dump));
    }

    public static void log(String caller, String callee) {
        if (isExcluded(caller) || isExcluded(callee)) {
            return;
        }

        // map lambda methods to the enclosing method for better graph readability
        if (caller.contains("lambda$")) {
            caller = lambdaNameToEnclosingName(caller);
        }

        // TODO: caller thread info? Thread.currentThread().getName()

        callGraph.addEdge(caller, callee);
    }

    private static String lambdaNameToEnclosingName(String name) {
        String[] parts = name.split("\\$");
        if (parts.length == 1) {
            return parts[0]; // no $ encountered, a normal method name
        } else {
            return parts[0].substring(0, parts[0].lastIndexOf("lambda")) + parts[1];
        }
    }

    public static boolean isExcluded(String className) {
        for (String prefix : EXCLUDED_PACKAGES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static void setOutputFilename(String filename) {
        OUTPUT_FILENAME = filename;
    }

    public static void dump() {
        try {
            exporter.export(callGraph, OUTPUT_FILENAME);
        } catch (IOException e) {
            System.err.println("Failed to export graph: " + e.getMessage());
        }
    }

}
