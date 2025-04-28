package hse.project;

import javassist.*;
import java.io.*;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class JarClassLister {
    private final static String INPUT_JAR = "jar_test_sources/app.jar";
    private final static String OUTPUT_JAR = "modified_app.jar";

    public static void main(String[] args) throws Exception {
        File inputJar = new File(INPUT_JAR);
        if (!inputJar.exists()) {
            System.out.println("Input JAR not found at: " + inputJar.getAbsolutePath());
            return;
        }

        String mainClassName = getMainClassName(inputJar);

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

                    if (mainClassName != null && className.equals(mainClassName)) {
                        try {
                            CtClass stringArrayType = pool.get("[Ljava.lang.String;");
                            CtMethod mainMethod = ctClass.getDeclaredMethod(
                                "main",
                                new CtClass[]{stringArrayType}
                            );
                            
                            mainMethod.insertAfter("{ hse.project.CallLogger.dump(); }");
                        } catch (NotFoundException e) {
                            System.err.println("Main method not found in: " + className);
                        }
                    }
                    
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

    private static String getMainClassName(File inputJar) throws IOException {
        String mainClassName = null;
        try (JarFile jar = new JarFile(inputJar)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                mainClassName = manifest.getMainAttributes()
                    .getValue(Attributes.Name.MAIN_CLASS);
                if (mainClassName != null) {
                    mainClassName = mainClassName.replace('/', '.');
                }
            }
        }
        return mainClassName;
    }

    private static void modifyMethods(CtClass ctClass) throws Exception {
        CtClass loggerClass = ClassPool.getDefault().get("hse.project.CallLogger");
        CtMethod logMethod = loggerClass.getDeclaredMethod("log");

        for (CtMethod method : ctClass.getDeclaredMethods()) {
            if (!method.isEmpty()) {
                String callerInit = "StackTraceElement[] stack = Thread.currentThread().getStackTrace();" +
                    "String caller = (stack.length > 2) ? " +
                        "stack[2].getClassName() + \"::\" + stack[2].getMethodName() : \"<unknown>\";";

                String logCall = String.format(
                    "{ %s.%s(%s, %s);}",
                    loggerClass.getName(),
                    logMethod.getName(),
                    "caller",
                    wrap(ctClass.getName() + "::" + method.getName())
                );

                String beforeBlock = "{" + 
                    callerInit +
                    logCall +
                "}";

                method.insertBefore(beforeBlock);
            }
        }
    }

    private static void addCallLoggerClass(JarOutputStream jos) throws Exception {
        addClassToJar(jos, "/hse/project/CallLogger.class");
        addClassToJar(jos, "/hse/project/CallLogger$Pair.class");
    }
    
    private static void addClassToJar(JarOutputStream jos, String resourcePath) 
            throws Exception {
        JarEntry newEntry = new JarEntry(resourcePath.replaceFirst("^/", ""));
        jos.putNextEntry(newEntry);

        try (InputStream classStream = 
                JarClassLister.class.getResourceAsStream(resourcePath)) {
            if (classStream == null) {
                throw new RuntimeException(resourcePath + " not found in project");
            }
            classStream.transferTo(jos);
        }
    }

    private static String wrap(String s) {
        return '\"' + s + '\"';
    }
}