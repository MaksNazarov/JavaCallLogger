# JAR Graph Collector

Simple, easy-to-install, easy-to-use Java callgraph collector.

# Requirements
* JDK 9+ for plugin/tool compilation
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
    
        ```mvn clean package```
    2.  to instrument your JAR, run

        ```java -jar path/to/tool/jar path/to/your/jar path/to/new/jar```
    3. Now you can use the new JAR file, and each time it's run, the file "calls.txt" will be created, containing the info on all function calls.

    Check out docs for further option/output/tool customisation options.
2. You can use the project as maven plugin in your local project.
    1. to install the project as a plugin to your local Maven repository, run:

        ```mvn clean install```
    2. Move to your Maven-based project and add 
        ```
        <plugin>
            <groupId>hse.project</groupId>
            <artifactId>callgraph-maven-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
            <configuration>
                <exporterType>simple</exporterType>
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
        to your build/plugins section.

    3. Now you can use Maven to build/run your project as usual, and each time you run it, the "calls.txt" is created.

    Check out docs for more info on pom.xml config options and various other features to use in your project. TODO: finish.