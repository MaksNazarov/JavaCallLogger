package hse.project.instrumentation;

import hse.project.JarClassLister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E test (compile -> package -> instrument -> run -> check output) for each bundled
 * test app under test/resources/test-apps
 */
class CallGraphEndToEndTest {

    @Test
    void simpleApp(@TempDir Path work) throws Exception {
        assertGraphMatches("simple-app", "simpleapp.Main", work);
    }

    @Test
    void lambdaApp(@TempDir Path work) throws Exception {
        assertGraphMatches("lambda-app", "lambdaapp.Main", work);
    }

    @Test
    void recursiveApp(@TempDir Path work) throws Exception {
        assertGraphMatches("recursive-app", "recursiveapp.Main", work);
    }

    @Test
    void multithreadApp(@TempDir Path work) throws Exception {
        assertGraphMatches("multithread-app", "multithreadapp.Main", work);
    }

    @Test
    void inheritanceApp(@TempDir Path work) throws Exception {
        assertGraphMatches("inheritance-app", "inheritanceapp.Main", work);
    }

    @Test
    void java8App(@TempDir Path work) throws Exception {
        assertGraphMatches("java8-app", "java8app.Main", work);
    }

    @Test
    void java17App(@TempDir Path work) throws Exception {
        assertGraphMatches("java17-app", "java17app.Main", work);
    }

    @Test
    void threadNameFlagPrefixesNodes(@TempDir Path work) throws Exception {
        Path appDir = locateApp("simple-app");
        Set<String> edges = runPipeline(appDir, "simpleapp.Main", work,
                "-Dcallgraph.includeThreadName=true");

        assertFalse(edges.isEmpty(), "expected some edges for simple-app");
        for (String edge : edges) {
            String[] parts = edge.split("\\s+");
            assertTrue(parts[0].startsWith("main@"),
                    "caller node missing thread prefix: " + edge);
            assertTrue(parts[1].startsWith("main@"),
                    "callee node missing thread prefix: " + edge);
        }
    }

    // TODO: add instrumenter test w/ thread names enabled for multithreaded app

    @Test
    void contextSensitiveModeProducesCallingContextTree(@TempDir Path work) throws Exception {
        Path appDir = locateApp("recursive-app");
        Path calls = runPipelineToFile(appDir, "recursiveapp.Main", work,
                "-Dcallgraph.contextSensitive=true");

        List<String> expected = List.of(
                "recursiveapp.Main::main([Ljava/lang/String;)V 1",
                "  recursiveapp.Main::factorial(I)I 1",
                "    recursiveapp.Main::factorial(I)I 1",
                "      recursiveapp.Main::factorial(I)I 1");

        assertEquals(expected, Files.readAllLines(calls),
                "calling-context tree differs for recursive-app");
    }

    private void assertGraphMatches(String appName, String mainClass, Path work) throws Exception {
        Path appDir = locateApp(appName);
        Set<String> actual = runPipeline(appDir, mainClass, work);
        Set<String> expected = parseEdges(appDir.resolve("calls.txt.expected"));

        assertEquals(expected, actual, "recorded edge set differs for " + appName);
    }

    // returns resulting edge set
    private Set<String> runPipeline(Path appDir, String mainClass, Path work, String... extraProps) throws Exception {
        return parseEdges(runPipelineToFile(appDir, mainClass, work, extraProps));
    }

    // runs the full pipeline and returns the path to the produced output file
    private Path runPipelineToFile(Path appDir, String mainClass, Path work, String... extraProps) throws Exception {
        Path classes = work.resolve("classes");
        Path appJar = work.resolve("app.jar");
        Path modifiedJar = work.resolve("modified_app.jar");
        Path calls = work.resolve("calls.txt");

        compile(appDir.resolve("src"), classes);
        buildJar(classes, mainClass, appJar);
        JarClassLister.instrument(appJar.toFile(), modifiedJar.toFile());
        runJar(modifiedJar, calls, work, extraProps);

        assertTrue(Files.exists(calls), "instrumented run did not produce " + calls);
        return calls;
    }

    private void compile(Path srcDir, Path outDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        Files.createDirectories(outDir);

        List<Path> sources;
        try (Stream<Path> walk = Files.walk(srcDir)) {
            sources = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        assertTrue(!sources.isEmpty(), "no .java sources under " + srcDir);

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outDir.toFile()));
            boolean ok = compiler.getTask(null, fm, null, null, null,
                    fm.getJavaFileObjectsFromPaths(sources)).call();
            assertTrue(ok, "compilation failed for " + srcDir);
        }
    }

    private void buildJar(Path classesDir, String mainClass, Path jarPath) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath), manifest);
             Stream<Path> walk = Files.walk(classesDir)) {
            List<Path> files = walk.filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path file : files) {
                String entryName = classesDir.relativize(file).toString().replace('\\', '/');
                jos.putNextEntry(new java.util.jar.JarEntry(entryName));
                Files.copy(file, jos);
                jos.closeEntry();
            }
        }
    }

    private void runJar(Path jar, Path callsOutput, Path workDir, String... extraProps) throws IOException, InterruptedException {
        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(
                javaBin,
                "-Dcallgraph.output=" + callsOutput.toAbsolutePath(),
                "-Dcallgraph.exporterType=simple"));
        command.addAll(List.of(extraProps));
        command.add("-jar");
        command.add(jar.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exit = process.waitFor();
        assertEquals(0, exit, "instrumented app exited non-zero. Output:\n" + output);
    }

    // TODO: support for diff formats? Or enforce + write somewhere abt .expected using my fmt
    private Set<String> parseEdges(Path file) throws IOException {
        Set<String> edges = new HashSet<>();
        for (String line : Files.readAllLines(file)) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 3) {
                edges.add(parts[0] + " " + parts[1] + " " + parts[2]);
            }
        }
        return edges;
    }

    // TODO: drop once test refactoring finished
    private Path locateApp(String name) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get("test", "resources", "test-apps", name));
        candidates.add(Paths.get("..", "test", "resources", "test-apps", name));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("could not locate test app '" + name + "' from " +
                Paths.get("").toAbsolutePath());
    }
}
