package com.algostudio.model.output;

import java.util.Objects;

public record SortedArrayOutput(int[] sortedValues) implements AlgorithmOutput {
    public SortedArrayOutput {
        sortedValues = Objects.requireNonNull(sortedValues, "sortedValues").clone();
    }

    @Override
    public int[] sortedValues() {
        return sortedValues.clone();
    }
}
