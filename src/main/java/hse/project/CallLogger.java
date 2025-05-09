package hse.project;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public class CallLogger {
    private static final ConcurrentHashMap<Pair, Integer> graph = new ConcurrentHashMap<>();
    private static final Set<String> EXCLUDED_PACKAGES = Set.of(
            "java.", "javax.", "jdk.", "sun.",
            "com.sun.", "org.xml.", "org.w3c.",
            "hse.project" // TODO: auto skip without adding to Excluded packages
    ); // TODO: make runtime-applied: read from config file, save in instrumented JAR dynamically

    private static String OUTPUT_FILENAME = "calls.txt";

    static { // TODO: decide on approach: shutdown hook for any case vs insertAfter(Main::main) for cleanness
        Runtime.getRuntime().addShutdownHook(new Thread(CallLogger::dump));
    }

    public static void log(String caller, String callee) {
        if (isExcluded(caller) || isExcluded(callee) || caller.contains("lambda$")) {
            return;
        }

        // TODO: caller thread info? Thread.currentThread().getName()

        Pair key = new Pair(caller, callee);
        graph.merge(key, 1, Integer::sum);
    }

    private static boolean isExcluded(String className) {
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
        File file = new File(OUTPUT_FILENAME);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            Set<String> nodes = ConcurrentHashMap.newKeySet();
            for (Pair pair : graph.keySet()) {
                nodes.add(pair.caller);
                nodes.add(pair.callee);
            }

            writer.write(nodes.size() + " " + graph.size());
            writer.newLine();
            for (var entry : graph.entrySet()) {
                Pair pair = entry.getKey();
                writer.write(String.format(
                        "%s %s %d",
                        pair.caller,
                        pair.callee,
                        entry.getValue()
                ));
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    private record Pair(String caller, String callee) {
    }
}
