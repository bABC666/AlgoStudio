package com.algostudio.algorithm.graph;

import com.algostudio.algorithm.AlgorithmRun;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.frame.*;
import com.algostudio.model.graph.*;
import com.algostudio.model.input.GraphTraversalInput;
import com.algostudio.model.output.TraversalOutput;
import com.algostudio.trace.TraceSink;
import java.util.*;
import java.util.concurrent.CancellationException;

final class TraversalState {
    record Neighbor(String id, GraphEdge edge) { }
    final GraphTraversalInput input;
    final Set<String> visited = new LinkedHashSet<>();
    final List<String> order = new ArrayList<>();
    final Map<String, String> parents = new LinkedHashMap<>();
    final Deque<String> frontier = new ArrayDeque<>();
    private final Map<String, List<Neighbor>> adjacency = new HashMap<>();
    private final TraceSink sink;
    private final FrontierKind frontierKind;
    private long edgesExamined;

    TraversalState(GraphTraversalInput input, TraceSink sink, FrontierKind kind) {
        checkCancelled(); this.input = Objects.requireNonNull(input); this.sink = Objects.requireNonNull(sink); frontierKind = kind;
        for (var node : input.graph().nodes()) { checkCancelled(); adjacency.put(node.id(), new ArrayList<>()); }
        for (var edge : input.graph().edges()) {
            checkCancelled(); adjacency.get(edge.from()).add(new Neighbor(edge.to(), edge));
            adjacency.get(edge.to()).add(new Neighbor(edge.from(), edge));
        }
        for (var entry : adjacency.entrySet()) {
            checkCancelled(); entry.getValue().sort(Comparator.comparing(Neighbor::id)); entry.setValue(List.copyOf(entry.getValue()));
        }
    }
    List<Neighbor> neighbors(String node) { return adjacency.get(node); }
    void visit(String node, GraphEdge edge) {
        if (!visited.add(node)) throw new IllegalStateException("不能重复访问节点。");
        order.add(node); record(EventKind.VISIT, node, edge, node);
    }
    void examine(String node, Neighbor neighbor) {
        edgesExamined++; record(EventKind.EXAMINE_EDGE, node, neighbor.edge(), neighbor.id());
    }
    void record(EventKind kind, String current, GraphEdge edge, String subject) {
        checkCancelled();
        // 必须在解释、frontier副本、metrics及GraphFrame构造前判断。
        if (!sink.enabled()) return;
        String text = switch (kind) {
            case INITIAL -> "初始图：起点" + input.startNode() + "，尚未发现或访问节点。";
            case DISCOVER -> "发现" + subject + "并入队；父节点" + parents.getOrDefault(subject, "无（起点）") + "；队列" + frontier + "。";
            case VISIT -> "访问" + current + "；父节点" + parents.getOrDefault(current, "无（起点）") + "；访问顺序" + order + "。";
            case EXAMINE_EDGE -> "检查" + current + "的邻居" + subject + "；边权" + edge.weight() + "不参与遍历决策。";
            case BACKTRACK -> "退出" + subject + "，" + (current == null ? "返回栈已空" : "返回" + current) + "；栈" + frontier + "。";
            case DONE -> "遍历完成，访问" + order.size() + "个节点；不可达" + (input.graph().nodes().size() - order.size()) + "个节点。";
            default -> throw new IllegalArgumentException("非遍历事件。");
        };
        sink.append(new GraphFrame(input.graph(), visited, current, edge, List.copyOf(frontier), frontierKind,
                Map.of(), parents, order, kind, metrics(), text, kind.name()));
    }
    AlgorithmRun finish() {
        record(EventKind.DONE, null, null, null);
        return new AlgorithmRun(new TraversalOutput(input.startNode(), order, parents), metrics());
    }
    private AlgorithmMetrics metrics() { return new AlgorithmMetrics(0, 0, 0, visited.size(), edgesExamined, 0, 0); }
    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("图遍历已取消。");
    }
}
