package hse.project.graph.struct;

import hse.project.graph.struct.CallContextTree.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CallContextTreeTest {

    @Test
    void sameContextResolvesToSameNodeAndAccumulatesCount() {
        CallContextTree tree = new CallContextTree(); // TODO: tree construction looks bad

        // A -> B -> C, A -> B, A -> D
        Node a = tree.addRoot("A");
        Node b1 = tree.enterChild(a, "B");
        Node c = tree.enterChild(b1, "C");
        Node b2 = tree.enterChild(a, "B");
        Node d = tree.enterChild(a, "D");

        assertSame(b1, b2, "A->B should resolve to the same context node");
        assertEquals(1, a.count());
        assertEquals(2, b1.count(), "A->B entered twice");
        assertEquals(1, c.count());
        assertEquals(1, d.count());
        assertEquals(2, a.children().size(), "A has children B and D");
        assertEquals(1, b1.children().size(), "B has only child C");
    }

    @Test
    void distinguishesSameMethodReachedViaDifferentContexts() {
        CallContextTree tree = new CallContextTree();

        // A -> C and A -> B -> C
        Node a = tree.addRoot("A");
        Node directC = tree.enterChild(a, "C");
        Node b = tree.enterChild(a, "B");
        Node nestedC = tree.enterChild(b, "C");

        assertEquals("C", directC.method());
        assertEquals("C", nestedC.method());
        org.junit.jupiter.api.Assertions.assertNotSame(directC, nestedC,
                "C reached via A and via A->B must be distinct context nodes");
    }

    @Test
    void separateEntryPointsCreateSeparateRoots() {
        CallContextTree tree = new CallContextTree();
        tree.addRoot("X");
        tree.addRoot("Y");
        assertEquals(2, tree.roots().size());
    }
}
