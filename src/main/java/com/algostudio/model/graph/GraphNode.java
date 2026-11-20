package com.algostudio.model.graph;

import java.util.Objects;

/** 固定布局的归一化坐标，不依赖窗口或JavaFX。 */
public record GraphNode(String id, double x, double y) {
    public GraphNode {
        Objects.requireNonNull(id);
        if (id.isBlank()) throw new IllegalArgumentException("节点ID不能为空。");
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1)
            throw new IllegalArgumentException("节点坐标须为[0,1]内的有限值。");
    }
}
