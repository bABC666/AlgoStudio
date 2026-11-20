package com.algostudio.model.output;

import java.util.Objects;

/** 预览选择，不是图遍历或最短路径的计算结果。 */
public record GraphPreviewOutput(String startNode) implements AlgorithmOutput {
    public GraphPreviewOutput { Objects.requireNonNull(startNode); }
}
