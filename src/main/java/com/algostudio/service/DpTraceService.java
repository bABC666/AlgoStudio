package com.algostudio.service;

import com.algostudio.algorithm.*;
import com.algostudio.model.ExecutionTrace;
import com.algostudio.model.input.*;
import com.algostudio.trace.FrameTraceSink;
import java.util.Objects;

public final class DpTraceService {
    private final int maxFrames;
    public DpTraceService() { this(FrameTraceSink.MAX_FRAMES); }
    public DpTraceService(int maxFrames) {
        if (maxFrames < 1 || maxFrames > FrameTraceSink.MAX_FRAMES) throw new IllegalArgumentException("无效的轨迹帧数上限。");
        this.maxFrames = maxFrames;
    }
    public ExecutionTrace generateKnapsack(KnapsackInput input) {
        Objects.requireNonNull(input); return generate(AlgorithmRegistry.knapsackAlgorithm(),input,input.items().size()+"件物品 · 容量"+input.capacity());
    }
    public ExecutionTrace generateLcs(LcsInput input) {
        Objects.requireNonNull(input);
        if (input.x().isEmpty() || input.y().isEmpty()) throw new IllegalArgumentException("LCS演示的两个字符串均须有1～10个ASCII字母。");
        return generate(AlgorithmRegistry.lcsAlgorithm(),input,"X="+input.x()+" · Y="+input.y());
    }
    private <I extends AlgorithmInput> ExecutionTrace generate(Algorithm<I> algorithm, I input, String summary) {
        var sink = new FrameTraceSink(maxFrames); var run = algorithm.execute(input,sink);
        return new ExecutionTrace(algorithm.descriptor().id(),summary,sink.frames(),run);
    }
}
