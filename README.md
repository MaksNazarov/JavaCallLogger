# JAR Graph Collector

This is a simple project to get a grip on Javassist capabilities & start the work on Coderank Java extension.

## How to use

Specify the path to your JAR file in JarClassLister config options, then run JarClassLister::main(). After this, copy of your JAR with modified method & constructor calls will be created in base project directory. You can then run it to get a "calls.txt" file which stores a simple call graph in the following format:
```
node_count arc_count
(arc_count times) caller_info callee_info call_count
```