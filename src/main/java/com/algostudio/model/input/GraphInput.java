package com.algostudio.model.input;

import com.algostudio.model.graph.*;
import java.util.*;

public record GraphInput(List<GraphNode> nodes, List<GraphEdge> edges) implements AlgorithmInput {
    public GraphInput {
        nodes = List.copyOf(nodes).stream().sorted(Comparator.comparing(GraphNode::id)).toList();
        edges = List.copyOf(edges).stream().sorted(Comparator.comparing(GraphEdge::from).thenComparing(GraphEdge::to)).toList();
        Set<String> ids = new HashSet<>();
        for (GraphNode node : nodes) if (!ids.add(node.id())) throw new IllegalArgumentException("节点ID重复：" + node.id());
        Set<List<String>> pairs = new HashSet<>();
        for (GraphEdge edge : edges) {
            if (!ids.contains(edge.from()) || !ids.contains(edge.to())) throw new IllegalArgumentException("边引用不存在的节点。");
            if (!pairs.add(List.of(edge.from(), edge.to()))) throw new IllegalArgumentException("无向边重复。");
        }
    }
    public boolean containsNode(String id) { return nodes.stream().anyMatch(n -> n.id().equals(id)); }
    public List<String> neighbors(String id) {
        if (!containsNode(id)) throw new IllegalArgumentException("节点不存在：" + id);
        return edges.stream().filter(e -> e.from().equals(id) || e.to().equals(id))
                .map(e -> e.from().equals(id) ? e.to() : e.from()).sorted().toList();
    }
    public Optional<GraphEdge> edgeBetween(String a, String b) { return edges.stream().filter(e -> e.connects(a, b)).findFirst(); }
}
