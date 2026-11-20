package com.algostudio.service;

import com.algostudio.algorithm.AlgorithmRun;
import com.algostudio.model.*;
import com.algostudio.model.frame.*;
import com.algostudio.model.graph.*;
import com.algostudio.model.input.GraphInput;
import com.algostudio.model.output.GraphPreviewOutput;
import java.util.*;

/** 仅展示快照样式与播放能力，不运行或冒充BFS/DFS/Dijkstra。 */
public final class GraphPreviewService {
    public ExecutionTrace generate(GraphInput graph, String start) {
        Objects.requireNonNull(graph);
        if (!graph.containsNode(start)) throw new IllegalArgumentException("请选择图中的起点。");
        List<String> neighbors = graph.neighbors(start);
        GraphEdge edge = neighbors.isEmpty() ? null : graph.edgeBetween(start, neighbors.getFirst()).orElseThrow();
        List<GraphFrame> frames = List.of(
                frame(graph, EventKind.INITIAL, null, null, Set.of(), List.of(), FrontierKind.NONE,
                        "图状态示意：起点" + start + "；尚未执行遍历。"),
                frame(graph, EventKind.GRAPH_PREVIEW, start, null, Set.of(), List.of(start), FrontierKind.QUEUE,
                        "队列样式示意：选中" + start + "，队首在左；未执行遍历。"),
                frame(graph, EventKind.GRAPH_PREVIEW, start, edge, Set.of(), List.of(start), FrontierKind.QUEUE,
                        edge == null ? "邻边样式示意：" + start + "是孤立点，没有邻边。"
                                : "邻边样式示意：高亮" + edge.from() + "—" + edge.to() + "，权重" + edge.weight() + "；未执行遍历。"),
                frame(graph, EventKind.GRAPH_PREVIEW, null, null, Set.of(start), List.of(start), FrontierKind.STACK,
                        "已访问颜色与栈样式示意：" + start + "显示绿色，栈顶在右；不是遍历结果。"),
                frame(graph, EventKind.DONE, null, null, Set.of(), List.of(), FrontierKind.NONE,
                        "图状态示意结束，可回退查看快照；未执行图算法。"));
        return new ExecutionTrace("graph-preview", graph.nodes().size() + "顶点/" + graph.edges().size() + "边 · 起点" + start,
                new ArrayList<>(frames), new AlgorithmRun(new GraphPreviewOutput(start), AlgorithmMetrics.ZERO));
    }
    private GraphFrame frame(GraphInput graph, EventKind kind, String current, GraphEdge edge,
                             Set<String> visited, List<String> frontier, FrontierKind frontierKind, String text) {
        return new GraphFrame(graph, visited, current, edge, frontier, frontierKind, Map.of(), Map.of(),
                List.of(), kind, AlgorithmMetrics.ZERO, text, null);
    }
}
