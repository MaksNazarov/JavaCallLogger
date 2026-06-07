package hse.project.instrumentation;

import hse.project.CallLogger;
import hse.project.graph.struct.Edge;
import hse.project.utils.ClassInstrumenter;
import javassist.ClassPool;
import javassist.CtClass;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Check instrumentation correctness only // TODO: full description
 */
class ClassInstrumenterTest {

    private static final class InstrumentedLoader extends ClassLoader {
        private final String name;
        private final byte[] code;

        InstrumentedLoader(String name, byte[] code, ClassLoader parent) {
            super(parent);
            this.name = name;
            this.code = code;
        }

        @Override
        protected Class<?> loadClass(String n, boolean resolve) throws ClassNotFoundException {
            if (n.equals(name)) {
                Class<?> c = findLoadedClass(n);
                if (c == null) {
                    c = defineClass(n, code, 0, code.length);
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
            return super.loadClass(n, resolve);
        }
    }

    private List<Edge> instrumentAndRunEntry(String className) throws Exception {
        ClassPool pool = new ClassPool(true);
        CtClass ctClass = pool.get(className);
        new ClassInstrumenter(pool, false).instrumentClass(ctClass);
        byte[] instrumented = ctClass.toBytecode();

        CallLogger.reset();

        InstrumentedLoader loader =
                new InstrumentedLoader(className, instrumented, getClass().getClassLoader());
        Class<?> loaded = loader.loadClass(className);
        Object instance = loaded.getDeclaredConstructor().newInstance();
        loaded.getMethod("entry").invoke(instance);

        return CallLogger.snapshot().getEdges();
    }

    @Test
    void recordsEdgeBetweenInstrumentedMethods() throws Exception {
        List<Edge> edges = instrumentAndRunEntry("testapp.App");

        boolean callsNoArgHelper = edges.stream().anyMatch(e ->
                e.source.equals("testapp.App::entry()V") && e.target.equals("testapp.App::helper()V"));
        boolean callsIntHelper = edges.stream().anyMatch(e ->
                e.source.equals("testapp.App::entry()V") && e.target.equals("testapp.App::helper(I)V"));

        assertTrue(callsNoArgHelper,
                "expected edge testapp.App::entry()V -> testapp.App::helper()V, got: " + edges);
        assertTrue(callsIntHelper,
                "expected edge testapp.App::entry()V -> testapp.App::helper(I)V, got: " + edges);
    }
}
