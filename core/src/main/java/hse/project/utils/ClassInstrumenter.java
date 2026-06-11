package hse.project.utils;

import hse.project.CallLogger;
import hse.project.Instrumented;
import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.util.Set;

public class ClassInstrumenter {

    // java.util.concurrent methods that hand a task off to another thread; their Runnable/
    // Callable/Supplier argument is wrapped so the task carries the submitter's context
    private static final Set<String> ASYNC_SINK_METHODS = Set.of(
            "execute", "submit", "schedule", "scheduleAtFixedRate", "scheduleWithFixedDelay",
            "runAsync", "supplyAsync");

    private static final String LOGGER = CallLogger.class.getName();

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

        for (CtMethod method : ctClass.getDeclaredMethods()) {
            if (canInstrument(method)) {
                instrumentBehavior(method, loggerClass);
                markAsInstrumented(method);
            }
        }

        if (!Modifier.isAbstract(ctClass.getModifiers())) {
            for (CtConstructor ctor : ctClass.getDeclaredConstructors()) {
                if (canInstrument(ctor)) {
                    instrumentBehavior(ctor, loggerClass);
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
                                   CtClass loggerClass) throws CannotCompileException {
        if (skipEmptyBodies && behavior.isEmpty()) return;

        // rewrite executor/CompletableFuture submissions in the original body first, so the
        // editor never sees the injected enter/exit calls
        wrapAsyncSubmissions(behavior);

        String callee = String.format(
            "\"%s::%s%s\"",
            behavior.getDeclaringClass().getName(),
            behavior.getName(),
            behavior.getSignature()
        );

        behavior.insertBefore(loggerClass.getName() + ".enter(" + callee + ");");
        behavior.insertAfter(loggerClass.getName() + ".exit();", true);
    }

    private void wrapAsyncSubmissions(CtBehavior behavior) throws CannotCompileException {
        behavior.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (!m.getClassName().startsWith("java.util.concurrent.")
                        || !ASYNC_SINK_METHODS.contains(m.getMethodName())) {
                    return;
                }
                try {
                    String replacement = buildWrappedCall(m);
                    if (replacement != null) {
                        m.replace(replacement);
                    }
                } catch (NotFoundException e) {
                    // callee signature not resolvable, leave the call untouched
                }
            }
        });
    }

    private String buildWrappedCall(MethodCall m) throws NotFoundException {
        CtClass[] params = m.getMethod().getParameterTypes();
        StringBuilder args = new StringBuilder();
        boolean wrappedAny = false;

        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                args.append(", ");
            }
            String arg = "$" + (i + 1);
            switch (params[i].getName()) {
                case "java.lang.Runnable":
                    args.append(LOGGER).append(".wrapRunnable(").append(arg).append(")");
                    wrappedAny = true;
                    break;
                case "java.util.concurrent.Callable":
                    args.append(LOGGER).append(".wrapCallable(").append(arg).append(")");
                    wrappedAny = true;
                    break;
                case "java.util.function.Supplier":
                    args.append(LOGGER).append(".wrapSupplier(").append(arg).append(")");
                    wrappedAny = true;
                    break;
                default:
                    args.append(arg);
            }
        }

        if (!wrappedAny) {
            return null;
        }
        String assign = m.getMethod().getReturnType() == CtClass.voidType ? "" : "$_ = ";
        return "{ " + assign + "$proceed(" + args + "); }";
    }
}
