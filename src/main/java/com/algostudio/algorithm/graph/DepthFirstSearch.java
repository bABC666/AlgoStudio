package com.algostudio.algorithm.graph;

import com.algostudio.algorithm.*;
import com.algostudio.model.frame.EventKind;
import com.algostudio.model.graph.*;
import java.util.*;

/** 显式调用帧保存邻居游标，等价于递归DFS，避免批量压邻居改变parent。 */
public final class DepthFirstSearch extends GraphTraversalAlgorithm {
    private static final AlgorithmDescriptor DESCRIPTOR = new AlgorithmDescriptor("dfs", "深度优先搜索 DFS", AlgorithmCategory.GRAPH);
    private record Cursor(String node, Iterator<TraversalState.Neighbor> neighbors, GraphEdge incoming) { }
    @Override public AlgorithmDescriptor descriptor() { return DESCRIPTOR; }
    @Override protected FrontierKind frontierKind() { return FrontierKind.STACK; }
    @Override protected void traverse(TraversalState s) {
        var calls = new ArrayDeque<Cursor>(); String start = s.input.startNode();
        s.frontier.addLast(start); s.visit(start, null); calls.addLast(new Cursor(start, s.neighbors(start).iterator(), null));
        while (!calls.isEmpty()) {
            Cursor cursor = calls.getLast();
            if (cursor.neighbors().hasNext()) {
                var neighbor = cursor.neighbors().next(); s.examine(cursor.node(), neighbor);
                if (!s.visited.contains(neighbor.id())) {
                    s.parents.put(neighbor.id(), cursor.node()); s.frontier.addLast(neighbor.id()); s.visit(neighbor.id(), neighbor.edge());
                    calls.addLast(new Cursor(neighbor.id(), s.neighbors(neighbor.id()).iterator(), neighbor.edge()));
                }
            } else {
                calls.removeLast(); s.frontier.removeLast();
                s.record(EventKind.BACKTRACK, s.frontier.peekLast(), cursor.incoming(), cursor.node());
            }
        }
    }
}
