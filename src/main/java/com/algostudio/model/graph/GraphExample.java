package com.algostudio.model.graph;

import com.algostudio.model.input.GraphInput;
import java.util.Objects;

public record GraphExample(String name, GraphInput graph) {
    public GraphExample { Objects.requireNonNull(name); Objects.requireNonNull(graph); }
    @Override public String toString() { return name; }
}
