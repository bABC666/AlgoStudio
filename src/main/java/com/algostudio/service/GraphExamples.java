package com.algostudio.service;

import com.algostudio.model.graph.*;
import com.algostudio.model.input.GraphInput;
import java.util.List;

public final class GraphExamples {
    private GraphExamples() { }
    private static final GraphInput EVALUATION = new GraphInput(List.of(
            new GraphNode("A", .12, .45), new GraphNode("B", .36, .17), new GraphNode("C", .36, .75),
            new GraphNode("D", .62, .45), new GraphNode("E", .85, .20), new GraphNode("F", .85, .80)),
            List.of(new GraphEdge("A", "B", 1), new GraphEdge("A", "C", 4), new GraphEdge("B", "C", 2),
                    new GraphEdge("B", "D", 5), new GraphEdge("C", "D", 1), new GraphEdge("D", "E", 3)));
    private static final GraphInput TEACHING = new GraphInput(List.of(
            new GraphNode("A", .10, .48), new GraphNode("B", .32, .12), new GraphNode("C", .32, .85),
            new GraphNode("D", .55, .22), new GraphNode("E", .55, .78), new GraphNode("F", .82, .46),
            new GraphNode("G", .86, .94)), List.of(
            new GraphEdge("A", "B", 2), new GraphEdge("A", "C", 4), new GraphEdge("B", "C", 1),
            new GraphEdge("B", "D", 3), new GraphEdge("C", "D", 4), new GraphEdge("C", "E", 2),
            new GraphEdge("D", "E", 1), new GraphEdge("D", "F", 5), new GraphEdge("E", "F", 2)));
    private static final List<GraphExample> ALL = List.of(new GraphExample("课程图 · 7顶点9边", TEACHING),
            new GraphExample("评测图 · 6顶点6边 · F孤立", EVALUATION));
    public static GraphInput evaluationGraph() { return EVALUATION; }
    public static List<GraphExample> all() { return ALL; }
}
