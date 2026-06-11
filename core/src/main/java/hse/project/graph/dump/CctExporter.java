package hse.project.graph.dump;

import hse.project.graph.struct.CallContextTree;
import hse.project.graph.struct.CallContextTree.Node;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TODO: output fmt description // TODO: better fmt?
 */
public class CctExporter {

    public void export(CallContextTree tree, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            List<Node> roots = sortedByMethod(tree.roots());
            for (Node root : roots) {
                writeNode(writer, root, 0);
            }
        }
    }

    private void writeNode(BufferedWriter writer, Node node, int depth) throws IOException {
        for (int i = 0; i < depth; i++) {
            writer.write("  ");
        }
        writer.write(node.method() + " " + node.count());
        if (node.causalCaller() != null) {
            // non-main thread: print thread starter too
            writer.write(" <- " + node.causalCaller());
        }
        if (node.foldedCount() > 0) {
            writer.write(" (+" + node.foldedCount() + " folded)");
        }
        writer.newLine();

        for (Node child : sortedByMethod(node.children().values())) {
            writeNode(writer, child, depth + 1);
        }
    }

    private List<Node> sortedByMethod(java.util.Collection<Node> nodes) {
        return nodes.stream()
                .sorted(Comparator.comparing(Node::method))
                .collect(Collectors.toList());
    }
}
