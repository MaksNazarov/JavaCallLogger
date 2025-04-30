package hse.project;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class CallLogger {
    private static HashMap<Pair, Integer> graph = new HashMap<>();

    private static String OUTPUT_FILENAME = "calls.txt";

    static { // TODO: decide on approach: shutdown hook for any case vs insertAfter(Main::main) for cleanness
        Runtime.getRuntime().addShutdownHook(new Thread(CallLogger::dump));
    }

    public static void log(String caller, String callee) {
        Pair key = new Pair(caller, callee);
        graph.put(key, graph.getOrDefault(key, 0) + 1);
    }

    public static void setOutputFilename(String filename) {
        OUTPUT_FILENAME = filename;
    }

    public static void dump() {
        File file = new File(OUTPUT_FILENAME);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            Set<String> nodes = new HashSet<>();
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

    private record Pair(String caller, String callee) {}
}
