package com.algostudio.model.benchmark;

public record BenchmarkRequest(int size, long seed) {
    public static final int MAX_SIZE = 3000;
    public static final long DEFAULT_SEED = 20261102L;
    public BenchmarkRequest {
        if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("性能规模须为1～3000。");
    }
}
