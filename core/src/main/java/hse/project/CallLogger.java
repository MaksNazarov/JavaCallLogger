package hse.project;

import hse.project.graph.dump.CctExporter;
import hse.project.graph.dump.GraphExporter;
import hse.project.graph.dump.GraphExporterFactory;
import hse.project.graph.struct.CallContextTree;
import hse.project.graph.struct.CallGraph;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class CallLogger {
    private static final Set<String> EXCLUDED_PACKAGES = Set.of(
            "java.", "javax.", "jdk.", "sun.",
            "com.sun.", "org.xml.", "org.w3c.",
            "hse.project" // TODO: auto skip without adding to Excluded packages
    ); // TODO: make runtime-applied: read from config file, save in instrumented JAR dynamically

    private static final CallGraph callGraph = new CallGraph();

    private static final ThreadLocal<Deque<String>> CALL_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private static final boolean INCLUDE_THREAD_NAME =
            Boolean.parseBoolean(System.getProperty("callgraph.includeThreadName", "false"));

    private static final boolean CONTEXT_SENSITIVE =
            Boolean.parseBoolean(System.getProperty("callgraph.contextSensitive", "false"));

    private static final CallContextTree contextTree = new CallContextTree();

    private static final ThreadLocal<CallContextTree.Node> currentContext =
            ThreadLocal.withInitial(() -> null);

    // TODO: settings-static members grouping? Config subclasses?
    private static final boolean CROSS_THREAD =
            Boolean.parseBoolean(System.getProperty("callgraph.crossThread", "true"));

    private static final InheritableThreadLocal<String> spawnerContext =
            new InheritableThreadLocal<>();

    // TODO: System.getProperty consistency: read into flags vs in-place
    static {
        // auto-dump on JVM exit; disabled for specific tests
        if (Boolean.parseBoolean(System.getProperty("callgraph.dumpOnShutdown", "true"))) {
            Runtime.getRuntime().addShutdownHook(new Thread(CallLogger::dump));
        }
    }

    public static void enter(String callee) {
        if (INCLUDE_THREAD_NAME) {
            callee = Thread.currentThread().getName().replace(' ', '_') + "@" + callee;
        }
        if (CONTEXT_SENSITIVE) {
            enterContext(callee);
        } else {
            enterFlat(callee);
        }
    }

    private static void enterFlat(String callee) {
        Deque<String> stack = CALL_STACK.get();
        String caller = stack.peek();
        if (caller == null && CROSS_THREAD) {
            // entry point on this thread: fall back to the spawning thread's context, if any
            caller = spawnerContext.get();
        }
        if (caller != null) {
            // null caller means it's an entry point, so no edge
            recordEdge(caller, callee);
        }
        stack.push(callee);
        if (CROSS_THREAD) {
            spawnerContext.set(callee);
        }
    }

    private static void enterContext(String callee) {
        CallContextTree.Node cur = currentContext.get();
        CallContextTree.Node next;
        if (cur == null) {
            // entry point: attach the spawning thread's context as a causal link, if any
            String causalCaller = CROSS_THREAD ? spawnerContext.get() : null;
            next = contextTree.addRoot(callee, causalCaller);
        } else {
            next = contextTree.enterChild(cur, callee);
        }
        currentContext.set(next);
        if (CROSS_THREAD) {
            spawnerContext.set(callee);
        }
    }

    public static void exit() {
        if (CONTEXT_SENSITIVE) {
            CallContextTree.Node cur = currentContext.get();
            if (cur != null) {
                CallContextTree.Node parent = cur.parent();
                currentContext.set(parent);
                if (CROSS_THREAD) {
                    spawnerContext.set(parent == null ? null : parent.method());
                }
            }
        } else {
            Deque<String> stack = CALL_STACK.get();
            if (!stack.isEmpty()) {
                stack.pop();
                if (CROSS_THREAD) {
                    spawnerContext.set(stack.peek());
                }
            }
        }
    }

    public static Runnable wrapRunnable(Runnable task) {
        if (!CROSS_THREAD || task == null) {
            return task;
        }
        final String submitter = spawnerContext.get();
        return () -> {
            String previous = spawnerContext.get();
            spawnerContext.set(submitter);
            try {
                task.run();
            } finally {
                spawnerContext.set(previous);
            }
        };
    }

    public static <T> Callable<T> wrapCallable(Callable<T> task) {
        if (!CROSS_THREAD || task == null) {
            return task;
        }
        final String submitter = spawnerContext.get();
        return () -> {
            String previous = spawnerContext.get();
            spawnerContext.set(submitter);
            try {
                return task.call();
            } finally {
                spawnerContext.set(previous);
            }
        };
    }

    public static <T> Supplier<T> wrapSupplier(Supplier<T> task) {
        if (!CROSS_THREAD || task == null) {
            return task;
        }
        final String submitter = spawnerContext.get();
        return () -> {
            String previous = spawnerContext.get();
            spawnerContext.set(submitter);
            try {
                return task.get();
            } finally {
                spawnerContext.set(previous);
            }
        };
    }

    private static void recordEdge(String caller, String callee) {
        if (isExcluded(caller) || isExcluded(callee)) {
            return;
        }

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
        contextTree.clear();
        currentContext.remove();
    }

    public static void dump() {
        String outputFile = System.getProperty("callgraph.output", "calls.txt");
        try {
            if (CONTEXT_SENSITIVE) {
                new CctExporter().export(contextTree, outputFile); // TODO: need different exporters?
            } else {
                String exporterType = System.getProperty("callgraph.exporterType", "simple"); // FIXME: restore to class variables after resolving issue with maven plugin configs
                GraphExporter exporter = GraphExporterFactory.createExporter(exporterType);
                exporter.export(callGraph, outputFile);
            }
        } catch (IOException e) {
            System.err.println("Failed to export graph: " + e.getMessage());
        }
    }

}
