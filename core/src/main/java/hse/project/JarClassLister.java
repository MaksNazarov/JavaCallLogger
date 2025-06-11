package hse.project;

import hse.project.utils.ClassInstrumenter;
import javassist.*;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class JarClassLister {
    private static String INPUT_JAR = "jar_test_sources/app.jar";
    private static String OUTPUT_JAR = "modified_app.jar";
    private final static Boolean SKIP_EMPTY_BODIES = false; // prevents logging if callee is empty method/constructor

    public static void main(String[] args) throws Exception {
        if (args.length >= 1) INPUT_JAR = args[0];
        if (args.length >= 2) OUTPUT_JAR = args[1];

        File inputJar = new File(INPUT_JAR);
        if (!inputJar.exists()) {
            System.out.println("Input JAR not found at: " + inputJar.getAbsolutePath());
            return;
        }

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(inputJar.getAbsolutePath());

        ClassInstrumenter instrumenter = new ClassInstrumenter(pool, SKIP_EMPTY_BODIES);
        try (JarFile jarFile = new JarFile(inputJar);
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(OUTPUT_JAR))) {

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.endsWith(".class")) {
                    String className = entryName
                            .replace('/', '.')
                            .replace(".class", "");

                    CtClass ctClass = pool.get(className);
                    instrumenter.instrumentClass(ctClass);

                    JarEntry newEntry = new JarEntry(entryName);
                    jos.putNextEntry(newEntry);
                    jos.write(ctClass.toBytecode());
                    ctClass.detach();
                } else { // copy non-class entries as is
                    jos.putNextEntry(entry);
                    if (!entry.isDirectory()) {
                        try (InputStream is = jarFile.getInputStream(entry)) {
                            is.transferTo(jos);
                        }
                    }
                }
                jos.closeEntry();
            }

            addCallLoggerClass(jos);
        }
    }

    private static void addCallLoggerClass(JarOutputStream jos) throws Exception {
        addAllClassesFromPackage(jos, "hse.project");
    }

    private static void addAllClassesFromPackage(JarOutputStream jos, String packageName) throws IOException, URISyntaxException {
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = JarClassLister.class.getClassLoader();
        Enumeration<URL> resources = classLoader.getResources(path);

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();

            if (resource.getProtocol().equals("jar")) {
                JarFile jarFile = ((JarURLConnection) resource.openConnection()).getJarFile();
                Enumeration<JarEntry> entries = jarFile.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith(path) && entry.getName().endsWith(".class")) {
                        addClassFromJar(jos, jarFile, entry);
                    }
                }
            } else {
                File directory = new File(resource.toURI());
                if (directory.exists()) {
                    collectAndAddClasses(jos, directory, packageName);
                }
            }
        }
    }

    private static void collectAndAddClasses(JarOutputStream jos, File dir, String packageName) throws IOException {
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.isDirectory()) {
                collectAndAddClasses(jos, file, packageName + "." + file.getName());
            } else if (file.getName().endsWith(".class")) {
                String entryName = packageName.replace('.', '/') + "/" + file.getName();
                JarEntry entry = new JarEntry(entryName);
                jos.putNextEntry(entry);
                try (InputStream is = new FileInputStream(file)) {
                    is.transferTo(jos);
                }
                jos.closeEntry();
            }
        }
    }

    private static void addClassFromJar(JarOutputStream jos, JarFile sourceJar, JarEntry entry) throws IOException {
        jos.putNextEntry(new JarEntry(entry.getName()));
        try (InputStream is = sourceJar.getInputStream(entry)) {
            is.transferTo(jos);
        }
        jos.closeEntry();
    }

    private static String wrap(String s) {
        return '\"' + s + '\"';
    }
}
