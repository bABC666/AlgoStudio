package com.algostudio.model.benchmark;

import com.algostudio.algorithm.AlgorithmDescriptor;
import com.algostudio.model.AlgorithmMetrics;
import java.util.List;
import java.util.Objects;

public record BenchmarkResult(AlgorithmDescriptor algorithm, List<Long> samplesNanos, AlgorithmMetrics metrics) {
    public static final int MEASUREMENTS = 5;
    public BenchmarkResult {
        Objects.requireNonNull(algorithm);
        Objects.requireNonNull(metrics);
        samplesNanos = List.copyOf(samplesNanos);
        if (samplesNanos.size() != MEASUREMENTS || samplesNanos.stream().anyMatch(n -> n < 0))
            throw new IllegalArgumentException("须提供5次非负耗时。");
    }
    public long medianNanos() { return samplesNanos.stream().sorted().toList().get(MEASUREMENTS / 2); }
    public double medianMillis() { return medianNanos() / 1_000_000.0; }
}
