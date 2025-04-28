package hse.project;

import javassist.*;
import java.io.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class JarClassLister {
    private final static String INPUT_JAR = "jar_test_sources/app.jar";
    private final static String OUTPUT_JAR = "modified_app.jar";

    public static void main(String[] args) throws Exception {
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
            if (!method.isEmpty()) {
                String callerInit = "StackTraceElement[] stack = Thread.currentThread().getStackTrace();" +
                    "String caller = (stack.length > 2) ? " +
                        "stack[2].getClassName() + \"::\" + stack[2].getMethodName() + \"()\" : \"<unknown>\";";

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
        String classResourcePath = "/hse/project/CallLogger.class";
        JarEntry newEntry = new JarEntry("hse/project/CallLogger.class");
        jos.putNextEntry(newEntry);

        try (InputStream classStream = 
                JarClassLister.class.getResourceAsStream(classResourcePath)) {
            if (classStream == null) {
                throw new RuntimeException("CallLogger.class not found in project");
            }
            classStream.transferTo(jos);
        }
    }

    private static String wrap(String s) {
        return '\"' + s + '\"';
    }
}