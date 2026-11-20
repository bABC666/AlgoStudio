package com.algostudio.service;

import com.algostudio.algorithm.AlgorithmRegistry;
import com.algostudio.algorithm.sort.SortAlgorithm;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.benchmark.*;
import com.algostudio.trace.NoOpTraceSink;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.LongSupplier;

/** 同一排序核心的教学测量；无JavaFX依赖，不生成轨迹、动画或输入输出包装。 */
public final class BenchmarkService implements BenchmarkRunner {
    public static final int WARMUPS = 2;
    private final List<SortAlgorithm> algorithms;
    private final LongSupplier clock;

    public BenchmarkService() { this(AlgorithmRegistry.sortingAlgorithms(), System::nanoTime); }

    BenchmarkService(List<SortAlgorithm> algorithms, LongSupplier clock) {
        this.algorithms = List.copyOf(algorithms);
        if (this.algorithms.isEmpty()) throw new IllegalArgumentException("缺少排序算法。");
        this.clock = Objects.requireNonNull(clock);
    }

    @Override public BenchmarkReport run(BenchmarkRequest request) {
        Objects.requireNonNull(request);
        checkInterrupted();
        int[] base = new Random(request.seed()).ints(request.size(), 1, 10_001).toArray();
        int[] expected = base.clone();
        Arrays.sort(expected);
        List<BenchmarkResult> results = new ArrayList<>();
        for (SortAlgorithm algorithm : algorithms) {
            for (int i = 0; i < WARMUPS; i++) {
                checkInterrupted();
                int[] work = base.clone();
                algorithm.sortInPlace(work, NoOpTraceSink.INSTANCE);
                validate(work, expected);
            }
            List<Long> samples = new ArrayList<>();
            AlgorithmMetrics metrics = null;
            for (int i = 0; i < BenchmarkResult.MEASUREMENTS; i++) {
                checkInterrupted();
                int[] work = base.clone();
                long start = clock.getAsLong();
                AlgorithmMetrics measured = algorithm.sortInPlace(work, NoOpTraceSink.INSTANCE);
                long elapsed = clock.getAsLong() - start;
                validate(work, expected);
                if (metrics != null && !metrics.equals(measured))
                    throw new IllegalStateException("相同输入的操作计数不一致。");
                metrics = Objects.requireNonNull(measured);
                samples.add(elapsed);
            }
            results.add(new BenchmarkResult(algorithm.descriptor(), samples, metrics));
        }
        checkInterrupted();
        return new BenchmarkReport(request, results, BenchmarkEnvironment.current());
    }

    private static void validate(int[] actual, int[] expected) {
        checkInterrupted();
        if (!Arrays.equals(actual, expected)) throw new IllegalStateException("排序结果校验失败，未生成性能报告。");
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("性能对比已取消。");
    }
}
