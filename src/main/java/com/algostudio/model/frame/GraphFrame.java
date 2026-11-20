package com.algostudio.model.frame;

import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.graph.*;
import com.algostudio.model.input.GraphInput;
import java.util.*;

/** 所有元素也是不可变值；队列从左到右、栈底到栈顶，顺序保存在List中。 */
public record GraphFrame(GraphInput graph, Set<String> visited, String currentNode, GraphEdge activeEdge,
                         List<String> frontier, FrontierKind frontierKind, Map<String, Long> distances,
                         Map<String, String> parents, List<String> visitOrder, EventKind eventKind,
                         AlgorithmMetrics metrics, String explanation, String pseudoLineId) implements VisualFrame {
    public GraphFrame {
        Objects.requireNonNull(graph); Objects.requireNonNull(frontierKind); Objects.requireNonNull(eventKind);
        Objects.requireNonNull(metrics); Objects.requireNonNull(explanation);
        visited = Set.copyOf(visited); frontier = List.copyOf(frontier); distances = Map.copyOf(distances);
        parents = Map.copyOf(parents); visitOrder = List.copyOf(visitOrder);
        Set<String> references = new HashSet<>(visited);
        references.addAll(frontier); references.addAll(distances.keySet()); references.addAll(parents.keySet());
        references.addAll(parents.values()); references.addAll(visitOrder);
        if (currentNode != null) references.add(currentNode);
        for (String id : references) if (!graph.containsNode(id)) throw new IllegalArgumentException("图快照引用不存在的节点：" + id);
        if (activeEdge != null && !graph.edges().contains(activeEdge)) throw new IllegalArgumentException("图快照引用不存在的边。");
        if (distances.values().stream().anyMatch(n -> n < 0)) throw new IllegalArgumentException("距离不能为负数。");
        if (frontierKind == FrontierKind.NONE && !frontier.isEmpty()) throw new IllegalArgumentException("非空工作列表须注明队列或栈。");
    }
}
