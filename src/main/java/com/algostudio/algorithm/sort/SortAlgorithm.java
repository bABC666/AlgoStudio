package com.algostudio.algorithm.sort;

import com.algostudio.algorithm.Algorithm;
import com.algostudio.algorithm.AlgorithmRun;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.input.ArrayInput;
import com.algostudio.model.output.SortedArrayOutput;
import com.algostudio.trace.TraceSink;

import java.util.Objects;

public abstract class SortAlgorithm implements Algorithm<ArrayInput> {
    @Override
    public final AlgorithmRun execute(ArrayInput input, TraceSink sink) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(sink, "sink");
        int[] work = input.values();
        AlgorithmMetrics metrics = sortInPlace(work, sink);
        return new AlgorithmRun(new SortedArrayOutput(work), metrics);
    }

    /** 性能入口：work 由调用方独占；不复制输入，也不封装带防御性复制的输出。 */
    public abstract AlgorithmMetrics sortInPlace(int[] work, TraceSink sink);

    @Override
    public final String toString() {
        return descriptor().name();
    }
}
