package hse.project.graph.struct;

import java.util.Objects;

public class Edge {
    public String source;
    public String target;
    public int weight;

    public Edge(String source, String target, int weight) {
        this.source = source;
        this.target = target;
        this.weight = weight;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Edge)) return false;
        Edge other = (Edge) obj;
        return source.equals(other.source) && target.equals(other.target) && weight == other.weight;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, weight);
    }
}
