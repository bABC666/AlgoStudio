package com.algostudio.model.output;

import java.util.*;

public record TraversalOutput(String startNode, List<String> visitOrder, Map<String, String> parents) implements AlgorithmOutput {
    public TraversalOutput {
        Objects.requireNonNull(startNode); visitOrder = List.copyOf(visitOrder); parents = Map.copyOf(parents);
    }
}
