package hse.project;

import javassist.*;
import java.io.*;
import java.util.Enumeration;
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
        if (args.length >= 3) CallLogger.setOutputFilename(args[2]);

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
            instrumentBehavior(method, loggerClass, logMethod);
        }
        for (CtConstructor ctor : ctClass.getDeclaredConstructors()) {
            instrumentBehavior(ctor, loggerClass, logMethod);
        }
    }
    
    private static void instrumentBehavior(CtBehavior behavior, 
                                          CtClass loggerClass, 
                                          CtMethod logMethod) throws Exception {
        if (SKIP_EMPTY_BODIES && behavior.isEmpty()) return;

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

        String beforeBlock = "{" + 
            callerInit +
            logCall +
        "}";
        
        behavior.insertBefore(beforeBlock);
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