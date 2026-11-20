package com.algostudio.model.input;

import java.util.Objects;

public record GraphTraversalInput(GraphInput graph, String startNode) implements AlgorithmInput {
    public GraphTraversalInput {
        Objects.requireNonNull(graph);
        if (!graph.containsNode(startNode)) throw new IllegalArgumentException("起点不在图中，请重新选择。");
    }
}
