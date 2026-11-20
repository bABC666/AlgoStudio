package com.algostudio.algorithm;

import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.output.AlgorithmOutput;

import java.util.Objects;

public record AlgorithmRun(AlgorithmOutput output, AlgorithmMetrics metrics) {
    public AlgorithmRun {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(metrics, "metrics");
    }
}
