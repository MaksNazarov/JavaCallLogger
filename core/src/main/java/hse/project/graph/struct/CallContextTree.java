package hse.project.graph.struct;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


public class CallContextTree {

    public static final class Node {
        private final String method;
        private final Node parent;
        private final Map<String, Node> children = new LinkedHashMap<>(); // a node is thread-local by design
        private long count;
        private final String causalCaller; // thread starter for non-main threads

        private Node(String method, Node parent, String causalCaller) {
            this.method = method;
            this.parent = parent;
            this.causalCaller = causalCaller;
        }

        public String causalCaller() {
            return causalCaller;
        }

        public String method() {
            return method;
        }

        public Node parent() {
            return parent;
        }

        public long count() {
            return count;
        }

        public Map<String, Node> children() {
            return children;
        }
    }

    private final Queue<Node> roots = new ConcurrentLinkedQueue<>(); // TODO: need merging func?

    public Node addRoot(String method) {
        return addRoot(method, null);
    }

    public Node addRoot(String method, String causalCaller) {
        Node root = new Node(method, null, causalCaller);
        root.count++;
        roots.add(root);
        return root;
    }

    public Node enterChild(Node parent, String method) {
        Node child = parent.children.computeIfAbsent(method, m -> new Node(m, parent, null)); // TODO: second constructor better?
        child.count++;
        return child;
    }

    public List<Node> roots() {
        return new ArrayList<>(roots);
    }

    public void clear() {
        roots.clear();
    }
}
