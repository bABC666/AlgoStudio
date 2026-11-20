package com.algostudio.algorithm.graph;

import com.algostudio.algorithm.*;
import com.algostudio.model.graph.FrontierKind;
import com.algostudio.model.input.GraphTraversalInput;
import com.algostudio.trace.TraceSink;

/** 算法实例无运行状态；所有集合、计数和轨迹均由单次调用独占。 */
public abstract class GraphTraversalAlgorithm implements Algorithm<GraphTraversalInput> {
    @Override public final AlgorithmRun execute(GraphTraversalInput input, TraceSink sink) {
        var state = new TraversalState(input, sink, frontierKind());
        state.record(com.algostudio.model.frame.EventKind.INITIAL, null, null, input.startNode());
        traverse(state);
        return state.finish();
    }
    protected abstract FrontierKind frontierKind();
    protected abstract void traverse(TraversalState state);
    @Override public final String toString() { return descriptor().name(); }
}
