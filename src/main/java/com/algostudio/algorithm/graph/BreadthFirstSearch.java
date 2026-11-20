package com.algostudio.algorithm.graph;

import com.algostudio.algorithm.*;
import com.algostudio.model.frame.EventKind;
import com.algostudio.model.graph.FrontierKind;
import java.util.HashSet;

public final class BreadthFirstSearch extends GraphTraversalAlgorithm {
    private static final AlgorithmDescriptor DESCRIPTOR = new AlgorithmDescriptor("bfs", "广度优先搜索 BFS", AlgorithmCategory.GRAPH);
    @Override public AlgorithmDescriptor descriptor() { return DESCRIPTOR; }
    @Override protected FrontierKind frontierKind() { return FrontierKind.QUEUE; }
    @Override protected void traverse(TraversalState s) {
        var discovered = new HashSet<String>(); String start = s.input.startNode();
        discovered.add(start); s.frontier.addLast(start); s.record(EventKind.DISCOVER, start, null, start);
        while (!s.frontier.isEmpty()) {
            String node = s.frontier.removeFirst(); s.visit(node, null);
            for (var neighbor : s.neighbors(node)) {
                s.examine(node, neighbor);
                if (discovered.add(neighbor.id())) {
                    s.parents.put(neighbor.id(), node); s.frontier.addLast(neighbor.id());
                    s.record(EventKind.DISCOVER, node, neighbor.edge(), neighbor.id());
                }
            }
        }
    }
}
