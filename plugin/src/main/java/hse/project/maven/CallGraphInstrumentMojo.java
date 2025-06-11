package hse.project.maven;

import hse.project.Instrumented;
import javassist.*;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import hse.project.utils.ClassInstrumenter;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Mojo(name = "instrument", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class CallGraphInstrumentMojo extends AbstractMojo {
    
    @Component
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File outputDirectory;

    @Parameter(property = "callgraph.exporterType", defaultValue = "simple")
    private String exporterType;

    @Parameter(property = "callgraph.skipEmptyBodies", defaultValue = "false")
    private boolean skipEmptyBodies;

    private ClassPool pool;
    private ClassInstrumenter instrumenter;

    public void execute() throws MojoExecutionException {
        System.setProperty("callgraph.exporterType", exporterType); // TODO: better style? Pass as param to instrumentClass?

        outputDirectory = new File(project.getBuild().getOutputDirectory());
        getLog().info("Instrumenting classes in: " + outputDirectory.getAbsolutePath());
        
        try {
            pool = ClassPool.getDefault();
            pool.appendClassPath(outputDirectory.getAbsolutePath());
            instrumenter = new ClassInstrumenter(pool, skipEmptyBodies);

            Files.walk(outputDirectory.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .forEach(this::instrumentClass);

            copyCoreClasses();
        } catch (Exception e) {
            throw new MojoExecutionException("Instrumentation failed", e);
        }
    }

    private void instrumentClass(Path classPath) {
        try {
            String className = outputDirectory.toPath().relativize(classPath)
                    .toString()
                    .replace(File.separatorChar, '.')
                    .replace(".class", ""); // TODO: to util? duplicate to core.....JarClassLister part

            if (className.startsWith("hse.project")) return;

            CtClass ctClass = pool.get(className);
            if (ctClass.isFrozen()) ctClass.defrost();

            instrumenter.instrumentClass(ctClass);

            ctClass.writeFile(outputDirectory.getAbsolutePath());
            ctClass.detach();
        } catch (Exception e) {
            getLog().warn("Failed to instrument: " + classPath, e);
        }
    }

    private void copyCoreClasses() throws Exception {
        getLog().info("Copying callgraph-core classes to output directory");
        ClassLoader cl = getClass().getClassLoader();
        String packagePath = "hse/project"; // FIXME: remove hardcoded project path
        
        Enumeration<URL> resources = cl.getResources(packagePath);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if (resource.getProtocol().equals("jar")) {
                copyFromJar(resource, packagePath);
            } else if (resource.getProtocol().equals("file")) {
                copyFromFilesystem(new File(resource.toURI()), packagePath);
            }
        }
    }

    private void copyFromJar(URL jarUrl, String packagePath) throws IOException {
        JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
        try (JarFile jar = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(packagePath) && name.endsWith(".class")) {
                    File outFile = new File(outputDirectory, name);
                    outFile.getParentFile().mkdirs();
                    try (InputStream in = jar.getInputStream(entry);
                         OutputStream out = new FileOutputStream(outFile)) {
                        in.transferTo(out);
                    }
                }
            }
        }
    }

    private void copyFromFilesystem(File sourceDir, String packagePath) throws IOException { // TODO: is there a better way?
        Path sourcePath = sourceDir.toPath();
        Files.walk(sourcePath)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".class"))
            .forEach(path -> {
                try {
                    Path relative = sourcePath.getParent().relativize(path);
                    File dest = new File(outputDirectory, relative.toString());
                    dest.getParentFile().mkdirs();
                    Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
    }
}
