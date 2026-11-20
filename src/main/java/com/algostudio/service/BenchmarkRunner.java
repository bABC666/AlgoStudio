package com.algostudio.service;

import com.algostudio.model.benchmark.BenchmarkReport;
import com.algostudio.model.benchmark.BenchmarkRequest;

@FunctionalInterface
public interface BenchmarkRunner {
    BenchmarkReport run(BenchmarkRequest request);
}
