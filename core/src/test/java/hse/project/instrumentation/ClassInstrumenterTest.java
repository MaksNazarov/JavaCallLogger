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

    @Test
    void recordsEdgeBetweenInstrumentedMethods() throws Exception { // TODO: extract pool/loader logic into helper methods?
        String className = "testapp.App"; // TODO: to test args

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

        List<Edge> edges = CallLogger.snapshot().getEdges();
        boolean hasInternalEdge = edges.stream().anyMatch(e ->
                e.source.equals("testapp.App::entry") && e.target.equals("testapp.App::helper"));

        assertTrue(hasInternalEdge,
                "expected edge testapp.App::entry -> testapp.App::helper, got: " + edges);
    }
}
