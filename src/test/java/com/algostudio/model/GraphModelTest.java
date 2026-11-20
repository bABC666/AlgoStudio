package com.algostudio.model;

import com.algostudio.model.graph.*;
import com.algostudio.model.input.GraphInput;
import com.algostudio.service.GraphExamples;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphModelTest {
    private GraphNode node(String id) { return new GraphNode(id, .5, .5); }

    @Test void graphCopiesAndSortsDefinitionsAndUndirectedNeighbors() {
        var nodes = new ArrayList<>(List.of(node("C"), node("A"), node("B")));
        var edges = new ArrayList<>(List.of(new GraphEdge("C", "A", 0), new GraphEdge("B", "A", Integer.MAX_VALUE)));
        var graph = new GraphInput(nodes, edges);
        nodes.clear(); edges.clear();
        assertEquals(List.of("A", "B", "C"), graph.nodes().stream().map(GraphNode::id).toList());
        assertEquals(List.of("B", "C"), graph.neighbors("A"));
        assertEquals(List.of("A"), graph.neighbors("B"));
        assertEquals(graph.edgeBetween("A", "B"), graph.edgeBetween("B", "A"));
        assertEquals(Integer.MAX_VALUE, graph.edgeBetween("A", "B").orElseThrow().weight());
        assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.edges().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.neighbors("A").clear());
        assertThrows(IllegalArgumentException.class, () -> graph.neighbors("missing"));
    }

    @Test void malformedTopologyAndNegativeWeightsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GraphInput(List.of(node("A"), node("A")), List.of()));
        var nodes = List.of(node("A"), node("B"));
        assertThrows(IllegalArgumentException.class, () -> new GraphEdge("A", "B", -1));
        assertThrows(IllegalArgumentException.class, () -> new GraphEdge("A", "A", 1));
        assertThrows(IllegalArgumentException.class, () -> new GraphInput(nodes, List.of(new GraphEdge("A", "C", 1))));
        assertThrows(IllegalArgumentException.class, () -> new GraphInput(nodes,
                List.of(new GraphEdge("A", "B", 1), new GraphEdge("B", "A", 2))));
    }

    @Test void invalidCoordinatesAndBlankIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> node(" "));
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -.1, 1.1}) {
            assertThrows(IllegalArgumentException.class, () -> new GraphNode("A", value, .5));
            assertThrows(IllegalArgumentException.class, () -> new GraphNode("A", .5, value));
        }
        assertDoesNotThrow(() -> new GraphNode("A", 0, 1));
    }

    @Test void canonicalEvaluationGraphMatchesIndependentFixtureIncludingIsolatedF() {
        GraphInput graph = GraphExamples.evaluationGraph();
        assertEquals(List.of("A", "B", "C", "D", "E", "F"), graph.nodes().stream().map(GraphNode::id).toList());
        assertEquals(6, graph.edges().size());
        assertEquals(List.of("B", "C"), graph.neighbors("A"));
        assertEquals(List.of("A", "C", "D"), graph.neighbors("B"));
        assertEquals(List.of(), graph.neighbors("F"));
        assertEquals(1, graph.edgeBetween("A", "B").orElseThrow().weight());
        assertEquals(4, graph.edgeBetween("A", "C").orElseThrow().weight());
        assertEquals(2, graph.edgeBetween("B", "C").orElseThrow().weight());
        assertEquals(5, graph.edgeBetween("B", "D").orElseThrow().weight());
        assertEquals(1, graph.edgeBetween("C", "D").orElseThrow().weight());
        assertEquals(3, graph.edgeBetween("D", "E").orElseThrow().weight());
    }

    @Test void teachingExampleMeetsPrdSizeAndDefinitionsAreStable() {
        GraphInput graph = GraphExamples.all().getFirst().graph();
        assertEquals(7, graph.nodes().size()); assertEquals(9, graph.edges().size());
        assertEquals(graph, GraphExamples.all().getFirst().graph());
        assertTrue(graph.edges().stream().allMatch(e -> e.weight() >= 0));
        assertThrows(UnsupportedOperationException.class, () -> GraphExamples.all().clear());
        assertDoesNotThrow(() -> new GraphInput(List.of(), List.of()));
    }
}
