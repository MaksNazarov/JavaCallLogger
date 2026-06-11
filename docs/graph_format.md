# Graph save format

## Node identity

Each node is a method or constructor, identified as:

```
<fully.qualified.Class>::<methodName><JVM-descriptor>
```

e.g. `simpleapp.Main::main([Ljava/lang/String;)V`, `recursiveapp.Main::factorial(I)I`.

The descriptor (from Javassist's `getSignature()`) is the standard JVM method descriptor
`(<paramTypes>)<returnType>`, so overloads such as `foo(int)` and `foo(String)` stay distinct
nodes. Descriptor encoding (JVMS §4.3): `Z` boolean, `B` byte, `C` char, `S` short, `I` int,
`J` long, `F` float, `D` double, `V` void; `L<pkg>/<Class>;` a reference type (note `/` not
`.`); a leading `[` per array dimension. Examples: `[Ljava/lang/String;` = `String[]`,
`[[I` = `int[][]`, `(IJD)I` = `(int,long,double) -> int`. Descriptors contain no whitespace,
so they are safe in the space-delimited format below. Constructors use the class's simple name
as the method name (e.g. `Truck::Truck(Ljava/lang/String;D)V`).

### Optional thread-name prefix

With `-Dcallgraph.includeThreadName=true` (off by default), each node is prefixed with the
executing thread's name: `Thread-0@pkg.Class::m()V`. This splits the same method observed on
different threads into per-thread nodes; it is off by default because it multiplies node
counts on thread-pool/many-thread apps.

## Calling-context tree mode

With `-Dcallgraph.contextSensitive=true` (off by default), a **calling-context tree (CCT)** is
built instead of the flat call graph. Each method is recorded per distinct call *path*, so the
same method reached via different callers stays distinct — the main precision gain over the flat
graph. The cost is a larger structure (recursion / deep stacks grow the tree).

One tree is produced per entry point (e.g. `main`, or a thread's first instrumented method);
trees from different threads are kept separate (not merged). Output is an indented text tree:
two spaces per depth level, then `<node> <count>`, with roots and children sorted by name.

```
recursiveapp.Main::main([Ljava/lang/String;)V 1
  recursiveapp.Main::factorial(I)I 1
    recursiveapp.Main::factorial(I)I 1
      recursiveapp.Main::factorial(I)I 1
```

(The flat graph would collapse this to a single `factorial -> factorial` edge with weight 2.)
In this mode the `callgraph.exporterType` setting is ignored. Node identity (descriptors,
optional thread-name prefix) is the same as above.

**Depth bound:** `-Dcallgraph.maxContextDepth=N` (0 = unlimited, the default) caps tree depth so
recursive/deep programs don't grow the tree without limit. Calls below the cap are folded into
the deepest allowed node and counted there, shown as `(+<n> folded)`:

```
recursiveapp.Main::main([Ljava/lang/String;)V 1
  recursiveapp.Main::factorial(I)I 1 (+2 folded)
```

## Cross-thread causal edges

On by default (disable with `-Dcallgraph.crossThread=false`). Without it, each thread is an
island and the submit→run hand-off is lost — a method that only spawns threads gets no outgoing
edges (e.g. `main` disappears). Each thread's current context is tracked in an
`InheritableThreadLocal`, which the JVM copies parent→child at thread *construction*; when a
child enters its first instrumented method it falls back to the inherited parent context as the
caller.

- **Flat graph:** adds a normal edge `spawningContext -> threadEntryMethod`
  (e.g. `Main::main(...)V -> Main::task1()V`).
- **CCT mode:** the child thread keeps its own tree; its root is annotated
  `<root> <count> <- <spawning context>`.

This captures the thread-creation hand-off (`new Thread(...).start()`, including nested
spawning).

**Thread pools / executors:** submission to `java.util.concurrent` is also handled. Calls to
`execute` / `submit` / `schedule*` / `CompletableFuture.runAsync` / `supplyAsync` are rewritten
at instrumentation time to pass their `Runnable`/`Callable`/`Supplier` through a wrapper that
captures the submitter's context and restores it on the worker thread — so a pooled task is
attributed to its real submitter, not to whoever created the worker.

**Remaining limitations:** tasks submitted as *collections* (`invokeAll` / `invokeAny`) are not
wrapped; nor are tasks handed off through custom (non-`java.util.concurrent`) mechanisms or a
`BlockingQueue` you drain yourself.

## Export format

Applies to the **flat call graph** only. The format is selected with
`-Dcallgraph.exporterType=<name>` (system property), the plugin's `<exporterType>` configuration
option, or programmatically. The value is matched case-insensitively by
[GraphExporterFactory](../core/src/main/java/hse/project/graph/dump/GraphExporterFactory.java); an
unknown value fails fast with an `IllegalArgumentException`. The default is `simple`.

In context-sensitive (CCT) mode this setting is ignored — the calling-context tree is always
written by `CctExporter` in the indented-text format described above.

Three formats are currently implemented:

1. `simple` — basic space-delimited text. A header line with the node and edge (arc) counts,
   then one line per edge:
    ```
    <node_count> <arc_count>
    (<arc_count> lines:) <caller> <callee> <call_count>
    ```

2. `dot` — [Graphviz DOT](https://graphviz.org/doc/info/lang.html). A `digraph` with one
   `"<caller>" -> "<callee>" [weight=<call_count>];` line per edge, ready to render with
   `dot`/`neato`:
    ```
    digraph CallGraph {
        "<caller>" -> "<callee>" [weight=<call_count>];
        ...
    }
    ```

3. `graphml` — [GraphML](http://graphml.graphdrawing.org/) XML, for graph tools such as Gephi,
   yEd, or igraph/NetworkX. Nodes are emitted with synthetic ids (`n0`, `n1`, …) and a `label`
   data key carrying the method identity; edges carry an integer `weight` (the call count). The
   method-identity labels are XML-escaped:
    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <graphml xmlns="http://graphml.graphdrawing.org/xmlns">
      <key id="label" for="node" attr.name="label" attr.type="string"/>
      <key id="weight" for="edge" attr.name="weight" attr.type="int"/>
      <graph id="CallGraph" edgedefault="directed">
        <node id="n0"><data key="label"><caller></data></node>
        <node id="n1"><data key="label"><callee></data></node>
        <edge id="e0" source="n0" target="n1"><data key="weight"><call_count></data></edge>
        ...
      </graph>
    </graphml>
    ```

Further formats can be added by implementing the
[GraphExporter](../core/src/main/java/hse/project/graph/dump/GraphExporter.java) interface in core
and registering the new name in
[GraphExporterFactory](../core/src/main/java/hse/project/graph/dump/GraphExporterFactory.java).
