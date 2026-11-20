package com.algostudio.model;

import com.algostudio.model.frame.*;
import com.algostudio.model.graph.FrontierKind;
import com.algostudio.service.GraphExamples;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphFrameTest {
    @Test void allMutableStateBecomesAnIndependentImmutableSnapshot() {
        var graph = GraphExamples.evaluationGraph();
        Set<String> visited = new HashSet<>(Set.of("A"));
        List<String> frontier = new ArrayList<>(List.of("B", "C")), order = new ArrayList<>(List.of("A"));
        Map<String, Long> distances = new HashMap<>(Map.of("A", 0L, "B", 4_000_000_000L));
        Map<String, String> parents = new HashMap<>(Map.of("B", "A"));
        var frame = new GraphFrame(graph, visited, "B", graph.edges().getFirst(), frontier, FrontierKind.QUEUE,
                distances, parents, order, EventKind.INITIAL, AlgorithmMetrics.ZERO, "snapshot", null);
        visited.clear(); frontier.clear(); distances.clear(); parents.clear(); order.clear();
        assertEquals(Set.of("A"), frame.visited()); assertEquals(List.of("B", "C"), frame.frontier());
        assertEquals(4_000_000_000L, frame.distances().get("B")); assertEquals(Map.of("B", "A"), frame.parents());
        assertEquals(List.of("A"), frame.visitOrder());
        assertThrows(UnsupportedOperationException.class, () -> frame.visited().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.frontier().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.distances().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.parents().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.visitOrder().clear());
    }

    @Test void frameRejectsReferencesOutsideItsGraphAndInvalidDistances() {
        var graph = GraphExamples.evaluationGraph();
        assertThrows(IllegalArgumentException.class, () -> new GraphFrame(graph, Set.of("X"), null, null,
                List.of(), FrontierKind.NONE, Map.of(), Map.of(), List.of(), EventKind.INITIAL, AlgorithmMetrics.ZERO, "", null));
        assertThrows(IllegalArgumentException.class, () -> new GraphFrame(graph, Set.of(), "X", null,
                List.of(), FrontierKind.NONE, Map.of(), Map.of(), List.of(), EventKind.INITIAL, AlgorithmMetrics.ZERO, "", null));
        assertThrows(IllegalArgumentException.class, () -> new GraphFrame(graph, Set.of(), null, null,
                List.of("X"), FrontierKind.QUEUE, Map.of(), Map.of(), List.of(), EventKind.INITIAL, AlgorithmMetrics.ZERO, "", null));
        assertThrows(IllegalArgumentException.class, () -> new GraphFrame(graph, Set.of(), null, null,
                List.of(), FrontierKind.NONE, Map.of("A", -1L), Map.of(), List.of(), EventKind.INITIAL, AlgorithmMetrics.ZERO, "", null));
        assertThrows(IllegalArgumentException.class, () -> new GraphFrame(graph, Set.of(), null, null,
                List.of(), FrontierKind.NONE, Map.of(), Map.of("A", "X"), List.of(), EventKind.INITIAL, AlgorithmMetrics.ZERO, "", null));
    }

    @Test void nonexistentEdgeOrderAndUnlabelledWorkListAreRejected() {
        var graph = GraphExamples.evaluationGraph();
        assertThrows(IllegalArgumentException.class, () -> new GraphFrame(graph, Set.of(), null,
                new com.algostudio.model.graph.GraphEdge("A", "F", 1), List.of(), FrontierKind.NONE,
                Map.of(), Map.of(), List.of(), EventKind.INITIAL, AlgorithmMetrics.ZERO, "", null));
        assertThrows(IllegalArgumentException.class, () -> new GraphFrame(graph, Set.of(), null, null,
                List.of(), FrontierKind.NONE, Map.of(), Map.of(), List.of("X"), EventKind.INITIAL, AlgorithmMetrics.ZERO, "", null));
        assertThrows(IllegalArgumentException.class, () -> new GraphFrame(graph, Set.of(), null, null,
                List.of("A"), FrontierKind.NONE, Map.of(), Map.of(), List.of(), EventKind.INITIAL, AlgorithmMetrics.ZERO, "", null));
    }
}
