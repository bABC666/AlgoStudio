package com.algostudio.model.graph;

import java.util.Objects;

/** 简单无向边，端点按ID规范化；权重保持int，累计距离另用long。 */
public record GraphEdge(String from, String to, int weight) {
    public GraphEdge {
        Objects.requireNonNull(from); Objects.requireNonNull(to);
        if (from.isBlank() || to.isBlank() || from.equals(to)) throw new IllegalArgumentException("边须连接两个不同的有效节点。");
        if (weight < 0) throw new IllegalArgumentException("不支持负边权。");
        if (from.compareTo(to) > 0) { String swap = from; from = to; to = swap; }
    }
    public boolean connects(String a, String b) {
        return (from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a));
    }
}
