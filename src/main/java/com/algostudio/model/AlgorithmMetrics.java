package com.algostudio.model;

/** 主数组比较、写入、交换与其他类别的独立领域指标。 */
public record AlgorithmMetrics(long comparisons, long writes, long swaps,
                               long nodesVisited, long edgesExamined, long relaxations,
                               long dpCellsUpdated) {
    public static final AlgorithmMetrics ZERO = new AlgorithmMetrics(0, 0, 0, 0, 0, 0, 0);

    public AlgorithmMetrics {
        if (comparisons < 0 || writes < 0 || swaps < 0 || nodesVisited < 0
                || edgesExamined < 0 || relaxations < 0 || dpCellsUpdated < 0) {
            throw new IllegalArgumentException("操作计数不能为负数");
        }
    }
}
