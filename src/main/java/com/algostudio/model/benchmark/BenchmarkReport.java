package com.algostudio.model.benchmark;

import java.util.List;
import java.util.Objects;

public record BenchmarkReport(BenchmarkRequest request, List<BenchmarkResult> results, BenchmarkEnvironment environment) {
    public BenchmarkReport {
        Objects.requireNonNull(request);
        Objects.requireNonNull(environment);
        results = List.copyOf(results);
        if (results.isEmpty()) throw new IllegalArgumentException("至少需要一种排序算法。");
    }
}
