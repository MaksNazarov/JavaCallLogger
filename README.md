# JAR Graph Collector

Simple, easy-to-install, easy-to-use Java callgraph collector. It rewrites compiled bytecode (via
[Javassist](https://www.javassist.org/)) to log every method/constructor entry and exit, so running
your program records which methods actually call which - with call counts, optional calling-context
trees, cross-thread call tracking and several export formats.

Unlike static analysis, it captures only the paths exercised at runtime. The graph is written to a
file (`calls.txt` by default) when the instrumented program's JVM exits.

# Requirements
* JDK 21+ for plugin/tool compilation (the core engine itself targets Java 9)
* Maven
* Your project must be Java 8+

# How to use:

Clone the project to your machine:
```
git clone <project-url>
```
Step into the project directory, which contains .gitignore and README.md files.

Now you have 2 usage options.
1. You can use the project as a standalone tool:
    1. to download all dependencies and compile the tool, run:

        ```mvn -pl core -am package```
    2. to instrument your JAR, run

        ```java -jar core/target/callgraph-core-1.0-SNAPSHOT.jar path/to/your.jar path/to/new.jar```
    3. Now you can run the new JAR file, and each time it's run the file "calls.txt" is created (on
       JVM exit), containing the info on all function calls. The instrumented JAR bundles the
       runtime, so it needs no extra classpath.

    Check out the [docs](docs/graph_format.md) for further output/configuration options.
2. You can use the project as a maven plugin in your local project.
    1. to install the project as a plugin to your local Maven repository, run:

        ```mvn clean install```
    2. Move to your Maven-based project and add
        ```xml
        <plugin>
            <groupId>hse.project</groupId>
            <artifactId>callgraph-maven-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
            <configuration>
                <exporterType>simple</exporterType>     <!-- simple | dot | graphml -->
                <skipEmptyBodies>false</skipEmptyBodies> <!-- skip empty methods/ctors -->
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>instrument</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
        ```
        to your build/plugins section. The `instrument` goal runs in the `process-classes` phase.

    3. Now you can use Maven to build/run your project as usual, and each time you run it the
       "calls.txt" is created.

    Check out the [docs](docs/graph_format.md) for more info on config options and various other
    features to use in your project.

# Output

Only your application's classes are recorded; the JDK (`java.*`, `javax.*`, `jdk.*`, `sun.*`,
`com.sun.*`, `org.xml.*`, `org.w3c.*`) and the collector itself are skipped.

Nodes are methods/constructors identified as `fully.qualified.Class::name<JVM-descriptor>` (so
overloads stay distinct). The default `simple` format is one edge per line — `<caller> <callee>
<count>` after a `<nodes> <edges>` header; `dot` and `graphml` are also available. With
context-sensitive mode the output is instead an indented **calling-context tree**.

See **[docs/graph_format.md](docs/graph_format.md)** for the full specification of node identity,
export formats, calling-context-tree mode, and cross-thread handling.

# Configuration

Runtime behaviour is controlled by `-D` system properties on the **instrumented program**:

| Property | Default | Meaning |
| --- | --- | --- |
| `callgraph.output` | `calls.txt` | Output file path. |
| `callgraph.exporterType` | `simple` | Flat-graph format: `simple`, `dot`, or `graphml`. |
| `callgraph.contextSensitive` | `false` | Build a calling-context tree instead of the flat graph. |
| `callgraph.maxContextDepth` | `0` | CCT depth cap (`0` = unlimited); deeper calls are folded. |
| `callgraph.includeThreadName` | `false` | Prefix each node with the executing thread's name. |
| `callgraph.crossThread` | `true` | Track submit→run hand-offs across threads/executors. |
| `callgraph.dumpOnShutdown` | `true` | Write the graph from a JVM shutdown hook. |

Instrumentation-time options (`exporterType`, `skipEmptyBodies`) are set via the plugin
`<configuration>` above, or — for the standalone tool — are currently compile-time constants in
`JarClassLister`.
