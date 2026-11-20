package com.algostudio.algorithm;

import java.util.Objects;

public record AlgorithmDescriptor(String id, String name, AlgorithmCategory category) {
    public AlgorithmDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
    }
}
