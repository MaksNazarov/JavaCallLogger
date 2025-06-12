package hse.project.utils;

import hse.project.CallLogger;
import hse.project.Instrumented;
import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;

public class ClassInstrumenter {
    private final ClassPool pool;
    private final boolean skipEmptyBodies;

    public ClassInstrumenter(ClassPool pool, boolean skipEmptyBodies) {
        this.pool = pool;
        this.skipEmptyBodies = skipEmptyBodies;
    }

    public void instrumentClass(CtClass ctClass) throws Exception {
        if (ctClass.isInterface()) {
            return;
        }

        CtClass loggerClass = pool.get(CallLogger.class.getName());
        CtMethod logMethod = loggerClass.getDeclaredMethod("log");

        for (CtMethod method : ctClass.getDeclaredMethods()) {
            if (canInstrument(method)) {
                instrumentBehavior(method, loggerClass, logMethod);
                markAsInstrumented(method);
            }
        }
        
        if (!Modifier.isAbstract(ctClass.getModifiers())) {
            for (CtConstructor ctor : ctClass.getDeclaredConstructors()) {
                if (canInstrument(ctor)) {
                    instrumentBehavior(ctor, loggerClass, logMethod);
                    markAsInstrumented(ctor);
                }
            }
        }
    }

    private boolean canInstrument(CtBehavior behavior) {
        try {
            return !behavior.hasAnnotation(Instrumented.class) &&
                !Modifier.isAbstract(behavior.getModifiers()) &&
                (!skipEmptyBodies || !behavior.isEmpty());
        } catch (Exception e) {
            return false; // TODO: refactor, bad style
        }
    }

    public void markAsInstrumented(CtBehavior behavior) throws CannotCompileException {
        ConstPool constPool = behavior.getMethodInfo().getConstPool();
        AnnotationsAttribute attr = new AnnotationsAttribute(
            constPool, 
            AnnotationsAttribute.visibleTag
        );
        Annotation ann = new Annotation(Instrumented.class.getName(), constPool);
        attr.addAnnotation(ann);
        behavior.getMethodInfo().addAttribute(attr);
    }

    private void instrumentBehavior(CtBehavior behavior, 
                                   CtClass loggerClass, 
                                   CtMethod logMethod) throws CannotCompileException {
        if (skipEmptyBodies && behavior.isEmpty()) return;
        
        String callerInit = "StackTraceElement[] stack = Thread.currentThread().getStackTrace();" +
                           "String caller = (stack.length > 2) ? " +
                           "stack[2].getClassName() + \"::\" + stack[2].getMethodName() : \"<unknown>\";";
        
        String callee = String.format(
            "\"%s::%s\"", 
            behavior.getDeclaringClass().getName(), 
            behavior.getName()
        );
        
        String logCall = String.format(
            "{%s %s.%s(caller, %s);}",
            callerInit,
            loggerClass.getName(),
            logMethod.getName(),
            callee
        );
        
        behavior.insertBefore(logCall);
    }
}
