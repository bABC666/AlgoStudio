package com.algostudio.service;

import com.algostudio.algorithm.AlgorithmRun;
import com.algostudio.algorithm.sort.SortAlgorithm;
import com.algostudio.model.ExecutionTrace;
import com.algostudio.model.input.ArrayInput;
import com.algostudio.trace.FrameTraceSink;

import java.util.Arrays;
import java.util.Objects;

/** 每个请求创建独立采集器；异常直接交给 UI 边界，不返回截断的成功结果。 */
public final class TraceService {
    public static final int MAX_VISUALIZATION_ITEMS = 50;
    private final int maxFrames;

    public TraceService() {
        this(FrameTraceSink.MAX_FRAMES);
    }

    public TraceService(int maxFrames) {
        if (maxFrames < 1 || maxFrames > FrameTraceSink.MAX_FRAMES) {
            throw new IllegalArgumentException("无效的轨迹帧数上限");
        }
        this.maxFrames = maxFrames;
    }

    public ExecutionTrace generate(SortAlgorithm algorithm, ArrayInput input) {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(input, "input");
        if (input.size() > MAX_VISUALIZATION_ITEMS) {
            throw new IllegalArgumentException("可视化轨迹最多支持 " + MAX_VISUALIZATION_ITEMS + " 项，请减少输入规模");
        }
        FrameTraceSink sink = new FrameTraceSink(maxFrames);
        AlgorithmRun run = algorithm.execute(input, sink);
        return new ExecutionTrace(algorithm.descriptor().id(), Arrays.toString(input.values()), sink.frames(), run);
    }
}
