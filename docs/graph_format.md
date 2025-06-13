# Graph save format

The format of the graph saved can be controlled in plugin's options / by setting callgraph.exportertype system variable / in main utility code (WIP).

Currently 2 graph formats are supported/implemented:
1. Basic graph format:
    ```
    <node_count> <arc_count>
    (<arc_count> lines:) <caller_info> <callee_info> <call_count>
    ```

2. DOT graph format for easy Graphviz compatibility/future integration.


Further format implementation can be done by implementing [GraphExporter](../core/src/main/java/hse/project/graph/dump/GraphExporter.java) interface in core, then updating [GraphExporterFactory](../core/src/main/java/hse/project/graph/dump/GraphExporterFactory.java) accordingly.