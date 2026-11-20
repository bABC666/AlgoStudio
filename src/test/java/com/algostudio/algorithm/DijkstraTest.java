package com.algostudio.algorithm;

import com.algostudio.algorithm.graph.Dijkstra;
import com.algostudio.model.frame.*;
import com.algostudio.model.graph.*;
import com.algostudio.model.input.*;
import com.algostudio.model.output.ShortestPathOutput;
import com.algostudio.service.GraphExamples;
import com.algostudio.trace.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class DijkstraTest {
    private static final long INF = Long.MAX_VALUE;

    // C/D 的首次发现距离不是最短距离；过早确定或忘记更新 prev 都会失败。
    @Test void knownDistancesParentsPathsAndSuccessfulRelaxationCounts() {
        var sink = new FrameTraceSink();
        var run = new Dijkstra().execute(input(GraphExamples.evaluationGraph(), "A"), sink);
        var output = (ShortestPathOutput) run.output();
        assertEquals(Map.of("A", 0L, "B", 1L, "C", 3L, "D", 4L, "E", 7L, "F", INF), output.distances());
        assertEquals(Map.of("B", "A", "C", "B", "D", "C", "E", "D"), output.parents());
        assertEquals(List.of("A", "B", "C", "D", "E"), output.pathTo("E"));
        assertEquals(List.of("A"), output.pathTo("A")); assertEquals(List.of(), output.pathTo("F"));
        assertThrows(IllegalArgumentException.class, () -> output.pathTo("missing"));
        assertEquals(5, run.metrics().nodesVisited()); assertEquals(12, run.metrics().edgesExamined());
        assertEquals(6, run.metrics().relaxations());
        assertEquals(6, sink.frames().stream().filter(f -> f.eventKind().name().equals("RELAX")).count());
        assertEquals(List.of("A", "B", "C", "D", "E"), ((GraphFrame) sink.frames().getLast()).visitOrder());
    }

    // 非严格改善会把零权环写进 prev；输入排列不应改变平局的确定顺序。
    @Test void zeroWeightCycleAndEqualPathsHaveStableAcyclicPredecessors() {
        var nodes = List.of(node("D"), node("C"), node("B"), node("A"));
        var edges = List.of(edge("C", "D", 1), edge("A", "C", 0), edge("B", "C", 0), edge("A", "B", 0), edge("B", "D", 1));
        var graph = new GraphInput(nodes, edges);
        var sink = new FrameTraceSink();
        var output = (ShortestPathOutput) new Dijkstra().execute(input(graph, "A"), sink).output();
        assertEquals(Map.of("A", 0L, "B", 0L, "C", 0L, "D", 1L), output.distances());
        assertEquals(Map.of("B", "A", "C", "A", "D", "B"), output.parents());
        assertEquals(List.of("A", "B", "D"), output.pathTo("D"));
        assertEquals(List.of("A", "B", "C", "D"), ((GraphFrame) sink.frames().getLast()).visitOrder());
        var reversed = new ArrayList<>(edges); Collections.reverse(reversed);
        assertEquals(output, new Dijkstra().execute(input(new GraphInput(nodes.reversed(), reversed), "A"), NoOpTraceSink.INSTANCE).output());
    }

    @Test void isolatedAndNonAStartsDoNotInventReachability() {
        var algorithm = new Dijkstra(); var graph = GraphExamples.evaluationGraph();
        var run = algorithm.execute(input(graph, "F"), NoOpTraceSink.INSTANCE);
        var output = (ShortestPathOutput) run.output();
        assertEquals(0L, output.distances().get("F")); assertEquals(INF, output.distances().get("A"));
        assertEquals(Map.of(), output.parents()); assertEquals(List.of("F"), output.pathTo("F"));
        assertEquals(1, run.metrics().nodesVisited()); assertEquals(0, run.metrics().edgesExamined());
        assertEquals(0, run.metrics().relaxations());
        output = (ShortestPathOutput) algorithm.execute(input(graph, "E"), NoOpTraceSink.INSTANCE).output();
        assertEquals(7L, output.distances().get("A")); assertEquals(List.of("E", "D", "C", "B", "A"), output.pathTo("A"));
    }

    @Test void twoBillionEdgeWeightsUseLongDistancesAndNegativeWeightsAreRejected() {
        var graph = new GraphInput(List.of(node("A"), node("B"), node("C")), List.of(edge("A", "B", 2_000_000_000), edge("B", "C", 2_000_000_000)));
        var output = (ShortestPathOutput) new Dijkstra().execute(input(graph, "A"), NoOpTraceSink.INSTANCE).output();
        assertEquals(4_000_000_000L, output.distances().get("C")); assertEquals(List.of("A", "B", "C"), output.pathTo("C"));
        assertThrows(IllegalArgumentException.class, () -> edge("A", "B", -1));
    }

    // Oracle 为三重循环全点对动态规划，与被测的单源贪心实现独立。
    @Test void hundredRandomGraphsMatchFloydWarshallAndEveryReturnedPathHasTheReportedCost() {
        Random random = new Random(20261102);
        for (int sample = 0; sample < 100; sample++) {
            int n = 1 + random.nextInt(8); var nodes = new ArrayList<GraphNode>(); var edges = new ArrayList<GraphEdge>();
            long[][] reference = new long[n][n];
            for (int i = 0; i < n; i++) { nodes.add(node("N" + i)); Arrays.fill(reference[i], INF); reference[i][i] = 0; }
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) if (random.nextBoolean()) {
                int weight = random.nextInt(10); edges.add(edge("N" + i, "N" + j, weight)); reference[i][j] = reference[j][i] = weight;
            }
            for (int k = 0; k < n; k++) for (int i = 0; i < n; i++) for (int j = 0; j < n; j++)
                if (reference[i][k] != INF && reference[k][j] != INF) reference[i][j] = Math.min(reference[i][j], reference[i][k] + reference[k][j]);
            Collections.shuffle(nodes, random); Collections.shuffle(edges, random); int start = random.nextInt(n);
            var graph = new GraphInput(nodes, edges);
            var output = (ShortestPathOutput) new Dijkstra().execute(input(graph, "N" + start), NoOpTraceSink.INSTANCE).output();
            for (int j = 0; j < n; j++) {
                String target = "N" + j; assertEquals(reference[start][j], output.distances().get(target), "seed20261102 sample=" + sample + " target=" + target);
                var path = output.pathTo(target);
                if (reference[start][j] == INF) { assertTrue(path.isEmpty()); assertFalse(output.parents().containsKey(target)); }
                else {
                    assertEquals("N" + start, path.getFirst()); assertEquals(target, path.getLast()); assertEquals(path.size(), new HashSet<>(path).size());
                    long cost = 0; for (int p = 1; p < path.size(); p++) cost += graph.edgeBetween(path.get(p - 1), path.get(p)).orElseThrow().weight();
                    assertEquals(reference[start][j], cost);
                }
            }
        }
    }

    @Test void framesAreCompleteImmutableMonotoneAndFinalStateMatchesResult() {
        var sink = new FrameTraceSink(); var run = new Dijkstra().execute(input(GraphExamples.evaluationGraph(), "A"), sink);
        List<VisualFrame> frames = sink.frames(); var initial = (GraphFrame) frames.getFirst(); var last = (GraphFrame) frames.getLast();
        assertEquals(EventKind.INITIAL, initial.eventKind()); assertEquals(0, initial.metrics().nodesVisited()); assertTrue(initial.visited().isEmpty());
        assertEquals(0L, initial.distances().get("A")); assertEquals(INF, initial.distances().get("E")); assertTrue(initial.parents().isEmpty());
        assertEquals(EventKind.DONE, last.eventKind()); assertEquals(run.metrics(), last.metrics());
        assertEquals(((ShortestPathOutput) run.output()).distances(), last.distances());
        assertThrows(UnsupportedOperationException.class, () -> initial.distances().put("A", 5L));
        assertThrows(UnsupportedOperationException.class, () -> last.parents().clear());
        assertThrows(UnsupportedOperationException.class, () -> last.visited().clear());
        long visits = 0, examined = 0, relaxed = 0; Map<String, Long> previous = initial.distances();
        for (var value : frames) {
            var frame = (GraphFrame) value;
            assertTrue(frame.metrics().nodesVisited() >= visits); assertTrue(frame.metrics().edgesExamined() >= examined); assertTrue(frame.metrics().relaxations() >= relaxed);
            assertEquals(frame.visited().size(), frame.metrics().nodesVisited());
            for (var id : previous.keySet()) assertTrue(frame.distances().get(id) <= previous.get(id));
            visits = frame.metrics().nodesVisited(); examined = frame.metrics().edgesExamined(); relaxed = frame.metrics().relaxations(); previous = frame.distances();
        }
        assertEquals(INF, initial.distances().get("E"));
    }

    @Test void outputCopiesMutableMapsAndDefendsMalformedPredecessorChains() {
        Map<String, Long> distances = new HashMap<>(Map.of("A", 0L, "B", 1L)); Map<String, String> parents = new HashMap<>(Map.of("B", "A"));
        var output = new ShortestPathOutput("A", distances, parents); distances.put("B", 9L); parents.clear();
        assertEquals(1L, output.distances().get("B")); assertEquals(List.of("A", "B"), output.pathTo("B"));
        assertThrows(UnsupportedOperationException.class, () -> output.pathTo("B").clear());
        var cyclic = new ShortestPathOutput("A", Map.of("A", 0L, "B", 1L, "C", 1L), Map.of("B", "C", "C", "B"));
        assertThrows(IllegalStateException.class, () -> cyclic.pathTo("B"));
    }

    @Test void disabledSinkDoesNotReceiveFramesAndCancellationStopsExecution() {
        var algorithm = new Dijkstra(); var input = input(GraphExamples.evaluationGraph(), "A");
        var recorded = algorithm.execute(input, new FrameTraceSink());
        TraceSink disabled = new TraceSink() { public boolean enabled() { return false; } public void append(VisualFrame f) { fail("NoOp 不应收到帧"); } };
        assertEquals(recorded, algorithm.execute(input, disabled));
        try { Thread.currentThread().interrupt(); assertThrows(CancellationException.class, () -> algorithm.execute(input, disabled)); }
        finally { Thread.interrupted(); }
        TraceSink interrupting = new TraceSink() {
            int checks;
            public boolean enabled() { if (++checks == 3) Thread.currentThread().interrupt(); return false; }
            public void append(VisualFrame f) { fail("NoOp 不应收到帧"); }
        };
        try { assertThrows(CancellationException.class, () -> algorithm.execute(input, interrupting)); }
        finally { Thread.interrupted(); }
    }

    @Test void overflowClearsPartialFramesAndFreshRequestRecovers() {
        var sink = new FrameTraceSink(4); var algorithm = new Dijkstra(); var input = input(GraphExamples.evaluationGraph(), "A");
        assertThrows(TraceLimitExceededException.class, () -> algorithm.execute(input, sink)); assertTrue(sink.frames().isEmpty());
        assertDoesNotThrow(() -> algorithm.execute(input, new FrameTraceSink()));
    }
    private static GraphNode node(String id) { return new GraphNode(id, .5, .5); }
    private static GraphEdge edge(String from, String to, int weight) { return new GraphEdge(from, to, weight); }
    private static GraphTraversalInput input(GraphInput graph, String start) { return new GraphTraversalInput(graph, start); }
}
