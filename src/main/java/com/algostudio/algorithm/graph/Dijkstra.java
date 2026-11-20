package com.algostudio.algorithm.graph;

import com.algostudio.algorithm.*;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.frame.*;
import com.algostudio.model.graph.*;
import com.algostudio.model.input.GraphTraversalInput;
import com.algostudio.model.output.ShortestPathOutput;
import com.algostudio.trace.TraceSink;
import java.util.*;
import java.util.concurrent.CancellationException;

/** 小图使用 O(V²+E) 线性选点；实例无可变运行状态。 */
public final class Dijkstra implements Algorithm<GraphTraversalInput> {
    private static final AlgorithmDescriptor DESCRIPTOR = new AlgorithmDescriptor("dijkstra", "Dijkstra 最短路径", AlgorithmCategory.GRAPH);
    @Override public AlgorithmDescriptor descriptor() { return DESCRIPTOR; }
    @Override public AlgorithmRun execute(GraphTraversalInput input, TraceSink sink) {
        return new Run(Objects.requireNonNull(input), Objects.requireNonNull(sink)).execute();
    }
    @Override public String toString() { return DESCRIPTOR.name(); }

    private record Neighbor(String id, GraphEdge edge) { }
    private static final class Run {
        private final GraphTraversalInput input;
        private final TraceSink sink;
        private final Map<String, Long> distances = new LinkedHashMap<>();
        private final Map<String, String> parents = new LinkedHashMap<>();
        private final Set<String> settled = new LinkedHashSet<>();
        private final List<String> order = new ArrayList<>();
        private final Map<String, List<Neighbor>> adjacency = new HashMap<>();
        private long edgesExamined, relaxations;

        Run(GraphTraversalInput input, TraceSink sink) {
            checkCancelled(); this.input = input; this.sink = sink;
            for (var node : input.graph().nodes()) {
                checkCancelled(); distances.put(node.id(), Long.MAX_VALUE); adjacency.put(node.id(), new ArrayList<>());
            }
            for (var edge : input.graph().edges()) {
                checkCancelled(); adjacency.get(edge.from()).add(new Neighbor(edge.to(), edge));
                adjacency.get(edge.to()).add(new Neighbor(edge.from(), edge));
            }
            // GraphInput 的边已按端点排序；显式排序邻居固定教学顺序。
            for (var neighbors : adjacency.values()) { checkCancelled(); neighbors.sort(Comparator.comparing(Neighbor::id)); }
            distances.put(input.startNode(), 0L);
        }

        AlgorithmRun execute() {
            record(EventKind.INITIAL, null, null, null, 0);
            while (true) {
                String current = null; long best = Long.MAX_VALUE;
                // LinkedHashMap 来自 GraphInput 的 ID 升序；相等时保留先遇到的节点。
                for (var entry : distances.entrySet()) {
                    checkCancelled();
                    if (!settled.contains(entry.getKey()) && entry.getValue() < best) { current = entry.getKey(); best = entry.getValue(); }
                }
                if (current == null) break; // 剩余节点均不可达，不将∞计作已确定。
                settled.add(current); order.add(current); record(EventKind.SETTLE, current, null, null, best);
                for (var neighbor : adjacency.get(current)) {
                    checkCancelled(); edgesExamined++;
                    record(EventKind.EXAMINE_EDGE, current, neighbor.edge(), neighbor.id(), distances.get(neighbor.id()));
                    long candidate = best > Long.MAX_VALUE - neighbor.edge().weight() ? Long.MAX_VALUE : best + neighbor.edge().weight();
                    if (!settled.contains(neighbor.id()) && candidate < distances.get(neighbor.id())) {
                        long before = distances.put(neighbor.id(), candidate); parents.put(neighbor.id(), current); relaxations++;
                        record(EventKind.RELAX, current, neighbor.edge(), neighbor.id(), before);
                    }
                }
            }
            record(EventKind.DONE, null, null, null, 0);
            return new AlgorithmRun(new ShortestPathOutput(input.startNode(), distances, parents), metrics());
        }

        private void record(EventKind kind, String current, GraphEdge edge, String target, long before) {
            checkCancelled();
            // 禁止在此判断前构造帧、集合副本、指标快照和解释。
            if (!sink.enabled()) return;
            String text = switch (kind) {
                case INITIAL -> "初始距离：起点" + input.startNode() + "为0，其余为∞；尚未确定最短距离。";
                case SETTLE -> "选择暂定距离最小的" + current + "，确定最短距离为" + before + "；等距离按ID升序。";
                case EXAMINE_EDGE -> "检查边" + current + "—" + target + "（权重" + edge.weight() + "）；"
                        + (settled.contains(target) ? target + "已确定，不再修改。" : "比较经过" + current + "的候选距离与当前dist=" + display(before) + "。");
                case RELAX -> "松弛" + current + "→" + target + "：dist[" + target + "]从" + display(before) + "更新为" + distances.get(target)
                        + "，prev[" + target + "]=" + current + "；该距离尚待确定。";
                case DONE -> "最短路径计算完成，确定" + settled.size() + "个节点；其余" + (distances.size() - settled.size()) + "个不可达，距离为∞。";
                default -> throw new IllegalArgumentException("非最短路事件。");
            };
            sink.append(new GraphFrame(input.graph(), settled, current, edge, List.of(), FrontierKind.NONE,
                    distances, parents, order, kind, metrics(), text, kind.name()));
        }
        private AlgorithmMetrics metrics() { return new AlgorithmMetrics(0, 0, 0, settled.size(), edgesExamined, relaxations, 0); }
        private static String display(long distance) { return distance == Long.MAX_VALUE ? "∞" : Long.toString(distance); }
        private static void checkCancelled() {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("最短路计算已取消。");
        }
    }
}
