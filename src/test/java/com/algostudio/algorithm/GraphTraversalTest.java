package com.algostudio.algorithm;

import com.algostudio.algorithm.graph.*;
import com.algostudio.model.*;
import com.algostudio.model.frame.*;
import com.algostudio.model.graph.*;
import com.algostudio.model.input.*;
import com.algostudio.model.output.TraversalOutput;
import com.algostudio.service.GraphExamples;
import com.algostudio.trace.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class GraphTraversalTest {
    static Stream<GraphTraversalAlgorithm> algorithms() { return AlgorithmRegistry.traversalAlgorithms().stream(); }
    private TraversalOutput output(AlgorithmRun run) { return assertInstanceOf(TraversalOutput.class, run.output()); }
    private GraphTraversalInput input(GraphInput graph, String start) { return new GraphTraversalInput(graph, start); }
    private boolean bfs(GraphTraversalAlgorithm algorithm) { return algorithm.descriptor().id().equals("bfs"); }

    @ParameterizedTest @MethodSource("algorithms")
    void fixedGraphOrderParentsAndDomainCountsMatchHandOracle(GraphTraversalAlgorithm algorithm) {
        var run = algorithm.execute(input(GraphExamples.evaluationGraph(), "A"), NoOpTraceSink.INSTANCE);
        assertEquals(List.of("A", "B", "C", "D", "E"), output(run).visitOrder());
        assertEquals(bfs(algorithm) ? Map.of("B", "A", "C", "A", "D", "B", "E", "D")
                : Map.of("B", "A", "C", "B", "D", "C", "E", "D"), output(run).parents());
        assertEquals(new AlgorithmMetrics(0, 0, 0, 5, 12, 0, 0), run.metrics());
        assertThrows(UnsupportedOperationException.class, () -> output(run).visitOrder().clear());
        assertThrows(UnsupportedOperationException.class, () -> output(run).parents().clear());
    }

    @ParameterizedTest @MethodSource("algorithms")
    void triangleDistinguishesRecursiveDfsParentFromBreadthFirstParent(GraphTraversalAlgorithm algorithm) {
        var graph = graph(3, List.of(new GraphEdge("A", "B", 1), new GraphEdge("A", "C", 99), new GraphEdge("B", "C", 0)));
        var result = output(algorithm.execute(input(graph, "A"), NoOpTraceSink.INSTANCE));
        assertEquals(List.of("A", "B", "C"), result.visitOrder());
        assertEquals(bfs(algorithm) ? "A" : "B", result.parents().get("C"));
    }

    @ParameterizedTest @MethodSource("algorithms")
    void isolatedStartOnlyVisitsItselfAndWeightsDoNotAffectTraversal(GraphTraversalAlgorithm algorithm) {
        var isolated = algorithm.execute(input(GraphExamples.evaluationGraph(), "F"), NoOpTraceSink.INSTANCE);
        assertEquals(List.of("F"), output(isolated).visitOrder()); assertEquals(Map.of(), output(isolated).parents());
        assertEquals(1, isolated.metrics().nodesVisited()); assertEquals(0, isolated.metrics().edgesExamined());
        var original = GraphExamples.evaluationGraph();
        var changed = new GraphInput(original.nodes(), original.edges().stream().map(e -> new GraphEdge(e.from(), e.to(), Integer.MAX_VALUE)).toList());
        assertEquals(output(algorithm.execute(input(original, "A"), NoOpTraceSink.INSTANCE)),
                output(algorithm.execute(input(changed, "A"), NoOpTraceSink.INSTANCE)));
    }

    @ParameterizedTest @MethodSource("algorithms")
    void hundredRandomGraphsMatchIndependentMatrixOracle(GraphTraversalAlgorithm algorithm) {
        Random random = new Random(20261102);
        for (int group = 0; group < 100; group++) {
            int n = 1 + random.nextInt(8), start = random.nextInt(n);
            boolean[][] matrix = new boolean[n][n]; List<GraphEdge> edges = new ArrayList<>();
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) if (random.nextBoolean()) {
                matrix[i][j] = matrix[j][i] = true;
                edges.add(new GraphEdge(id(j), id(i), random.nextInt(10)));
            }
            Collections.shuffle(edges, random);
            List<String> expected = new ArrayList<>(); Map<String, String> parents = new HashMap<>(); boolean[] seen = new boolean[n];
            if (bfs(algorithm)) {
                var queue = new ArrayDeque<Integer>(); queue.add(start); seen[start] = true;
                while (!queue.isEmpty()) {
                    int u = queue.remove(); expected.add(id(u));
                    for (int v = 0; v < n; v++) if (matrix[u][v] && !seen[v]) { seen[v] = true; parents.put(id(v), id(u)); queue.add(v); }
                }
            } else oracleDfs(start, matrix, seen, expected, parents);
            var actual = algorithm.execute(input(graph(n, edges), id(start)), NoOpTraceSink.INSTANCE);
            String context = algorithm.descriptor().id() + " seed20261102 group" + group + " start" + id(start);
            assertEquals(expected, output(actual).visitOrder(), context); assertEquals(parents, output(actual).parents(), context);
            long examined = 0; for (int u = 0; u < n; u++) if (seen[u]) for (boolean adjacent : matrix[u]) if (adjacent) examined++;
            assertEquals(examined, actual.metrics().edgesExamined(), context);
        }
    }

    @ParameterizedTest @MethodSource("algorithms")
    void framesAreCompleteMonotoneAndOldCollectionsRemainSnapshots(GraphTraversalAlgorithm algorithm) {
        var sink = new FrameTraceSink(); var graph = GraphExamples.evaluationGraph();
        var run = algorithm.execute(input(graph, "A"), sink); var frames = sink.frames();
        assertEquals(EventKind.INITIAL, frames.getFirst().eventKind()); assertEquals(EventKind.DONE, frames.getLast().eventKind());
        var initial = (GraphFrame) frames.getFirst(); assertTrue(initial.frontier().isEmpty()); assertTrue(initial.visited().isEmpty());
        assertTrue(initial.parents().isEmpty()); assertTrue(initial.visitOrder().isEmpty());
        long nodes = 0, edges = 0;
        for (var visual : frames) {
            var f = assertInstanceOf(GraphFrame.class, visual);
            assertSame(graph, f.graph()); assertTrue(f.metrics().nodesVisited() >= nodes); assertTrue(f.metrics().edgesExamined() >= edges);
            nodes = f.metrics().nodesVisited(); edges = f.metrics().edgesExamined();
            assertEquals(f.visited().size(), nodes); assertEquals(f.visitOrder().size(), nodes);
            assertEquals(f.frontier().size(), new HashSet<>(f.frontier()).size());
            assertFalse(f.visited().contains("F")); assertFalse(f.parents().containsKey("F"));
            if (!bfs(algorithm) && !f.frontier().isEmpty()) {
                for (int i = 1; i < f.frontier().size(); i++) assertEquals(f.frontier().get(i - 1), f.parents().get(f.frontier().get(i)));
            }
        }
        var done = (GraphFrame) frames.getLast(); assertTrue(done.frontier().isEmpty());
        assertEquals(output(run).parents(), done.parents()); assertEquals(output(run).visitOrder(), done.visitOrder());
        assertEquals(run.metrics(), done.metrics());
        assertThrows(UnsupportedOperationException.class, () -> done.parents().clear());
        if (bfs(algorithm)) assertTrue(frames.stream().map(f -> (GraphFrame) f).anyMatch(f -> f.frontier().equals(List.of("B", "C"))));
        else assertTrue(frames.stream().anyMatch(f -> f.eventKind() == EventKind.BACKTRACK));
    }

    @ParameterizedTest @MethodSource("algorithms")
    void noOpNeverAppendsAndMatchesRecordedRun(GraphTraversalAlgorithm algorithm) {
        TraceSink disabled = new TraceSink() {
            public boolean enabled() { return false; }
            public void append(VisualFrame frame) { fail("NoOp不得append帧"); }
        };
        var data = input(GraphExamples.evaluationGraph(), "A"); var recorded = algorithm.execute(data, new FrameTraceSink());
        assertEquals(recorded, algorithm.execute(data, disabled));
        Thread.currentThread().interrupt();
        try { assertThrows(CancellationException.class, () -> algorithm.execute(data, disabled)); }
        finally { Thread.interrupted(); }
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        TraceSink cancelMidRun = new TraceSink() {
            public boolean enabled() { if (checks.incrementAndGet() == 3) Thread.currentThread().interrupt(); return false; }
            public void append(VisualFrame frame) { fail("不应采样"); }
        };
        try { assertThrows(CancellationException.class, () -> algorithm.execute(data, cancelMidRun)); }
        finally { Thread.interrupted(); }
    }

    @Test void dfsExplicitStackHandlesFiveThousandNodeChainWithoutVmRecursion() {
        List<GraphNode> nodes = new ArrayList<>(); List<GraphEdge> edges = new ArrayList<>();
        for (int i = 0; i < 5000; i++) { nodes.add(new GraphNode("N" + i, .5, .5)); if (i > 0) edges.add(new GraphEdge("N" + (i - 1), "N" + i, 1)); }
        var run = new DepthFirstSearch().execute(input(new GraphInput(nodes, edges), "N0"), NoOpTraceSink.INSTANCE);
        assertEquals(5000, output(run).visitOrder().size()); assertEquals("N4999", output(run).visitOrder().getLast());
        assertEquals(9998, run.metrics().edgesExamined());
    }

    @Test void invalidStartIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> input(GraphExamples.evaluationGraph(), "X"));
        assertThrows(IllegalArgumentException.class, () -> input(new GraphInput(List.of(), List.of()), "A"));
    }
    private static String id(int n) { return Character.toString((char) ('A' + n)); }
    private static GraphInput graph(int n, List<GraphEdge> edges) {
        List<GraphNode> nodes = new ArrayList<>(); for (int i = n - 1; i >= 0; i--) nodes.add(new GraphNode(id(i), .5, .5));
        return new GraphInput(nodes, edges);
    }
    private static void oracleDfs(int u, boolean[][] matrix, boolean[] seen, List<String> order, Map<String, String> parents) {
        seen[u] = true; order.add(id(u));
        for (int v = 0; v < seen.length; v++) if (matrix[u][v] && !seen[v]) { parents.put(id(v), id(u)); oracleDfs(v, matrix, seen, order, parents); }
    }
}
