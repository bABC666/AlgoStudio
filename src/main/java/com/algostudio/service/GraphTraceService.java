package com.algostudio.service;

import com.algostudio.algorithm.Algorithm;
import com.algostudio.model.ExecutionTrace;
import com.algostudio.model.input.GraphTraversalInput;
import com.algostudio.trace.FrameTraceSink;
import java.util.Objects;

/** UI图请求的完整有界轨迹，核心算法不受该演示规模限制。 */
public final class GraphTraceService {
    public static final int MAX_NODES = 8, MAX_EDGES = 12;
    private final int maxFrames;
    public GraphTraceService() { this(FrameTraceSink.MAX_FRAMES); }
    public GraphTraceService(int maxFrames) {
        if (maxFrames < 1 || maxFrames > FrameTraceSink.MAX_FRAMES) throw new IllegalArgumentException("无效的轨迹帧数上限。");
        this.maxFrames = maxFrames;
    }
    public ExecutionTrace generate(Algorithm<GraphTraversalInput> algorithm, GraphTraversalInput input) {
        Objects.requireNonNull(algorithm); Objects.requireNonNull(input);
        if (input.graph().nodes().size() > MAX_NODES || input.graph().edges().size() > MAX_EDGES)
            throw new IllegalArgumentException("图演示最多支持8顶点、12条边。");
        var sink = new FrameTraceSink(maxFrames); var run = algorithm.execute(input, sink);
        return new ExecutionTrace(algorithm.descriptor().id(), input.graph().nodes().size() + "顶点/"
                + input.graph().edges().size() + "边 · 起点" + input.startNode(), sink.frames(), run);
    }
}
