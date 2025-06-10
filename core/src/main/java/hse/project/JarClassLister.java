package hse.project;

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
        if (args.length >= 3) {
            CallLogger.setOutputFilename(args[2]); // FIXME: ofc doesn't work, should edit the CallLogger entry directly and/or use config file
        }

        File inputJar = new File(INPUT_JAR);
        if (!inputJar.exists()) {
            System.out.println("Input JAR not found at: " + inputJar.getAbsolutePath());
            return;
        }

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(inputJar.getAbsolutePath());

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
                    modifyMethods(ctClass);

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

    private static void modifyMethods(CtClass ctClass) throws Exception {
        CtClass loggerClass = ClassPool.getDefault().get("hse.project.CallLogger");
        CtMethod logMethod = loggerClass.getDeclaredMethod("log");

        for (CtMethod method : ctClass.getDeclaredMethods()) {
            if (notInstrumented(method)) {
                instrumentBehavior(method, loggerClass, logMethod);
                markAsInstrumented(method);
            }
        }
        for (CtConstructor ctor : ctClass.getDeclaredConstructors()) {
            if (notInstrumented(ctor)) {
                instrumentBehavior(ctor, loggerClass, logMethod);
                markAsInstrumented(ctor);
            }
        }
        // TODO: destructors
    }

    private static boolean notInstrumented(CtBehavior behavior) {
        return !behavior.hasAnnotation(Instrumented.class);
    }

    private static void markAsInstrumented(CtBehavior behavior) {
//        System.out.println("Marking as instrumented: " + behavior.getLongName());
        javassist.bytecode.ConstPool constPool = behavior.getMethodInfo().getConstPool();
        javassist.bytecode.AnnotationsAttribute attr =
                new javassist.bytecode.AnnotationsAttribute(constPool, javassist.bytecode.AnnotationsAttribute.visibleTag);

        javassist.bytecode.annotation.Annotation ann =
                new javassist.bytecode.annotation.Annotation(Instrumented.class.getName(), constPool);
//        System.out.println("Annotation created: " + ann);

        attr.addAnnotation(ann);
        behavior.getMethodInfo().addAttribute(attr);
    }

    private static void instrumentBehavior(CtBehavior behavior,
                                           CtClass loggerClass,
                                           CtMethod logMethod) throws Exception {
        if (SKIP_EMPTY_BODIES && behavior.isEmpty()) return;

        String beforeBlock = getBeforeBlock(behavior, loggerClass, logMethod);
        behavior.insertBefore(beforeBlock);
    }

    private static String getBeforeBlock(CtBehavior behavior, CtClass loggerClass, CtMethod logMethod) {
        String callerInit = "StackTraceElement[] stack = Thread.currentThread().getStackTrace();" +
                "String caller = (stack.length > 2) ? " +
                "stack[2].getClassName() + \"::\" + stack[2].getMethodName() : \"<unknown>\";";

        String callee = String.format(
                "%s::%s",
                behavior.getDeclaringClass().getName(),
                behavior.getName()
        );

        String logCall = String.format(
                "{ %s.%s(%s, %s);}",
                loggerClass.getName(),
                logMethod.getName(),
                "caller",
                wrap(callee)
        );

        return "{" + callerInit + logCall + "}";
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