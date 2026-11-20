package com.algostudio.model.frame;

import com.algostudio.model.AlgorithmMetrics;

import java.util.Objects;

/** rangeStart 含、rangeEnd 不含；高亮下标 -1 表示不适用。 */
public record ArrayFrame(int[] values, EventKind eventKind,
                         int firstIndex, int secondIndex, int pivotIndex,
                         int rangeStart, int rangeEnd, int rangeMiddle, AlgorithmMetrics metrics,
                         String explanation, String pseudoLineId) implements VisualFrame {
    public ArrayFrame {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(eventKind, "eventKind");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(explanation, "explanation");
        validateIndex(firstIndex, values.length);
        validateIndex(secondIndex, values.length);
        validateIndex(pivotIndex, values.length);
        if (rangeStart < 0 || rangeEnd < rangeStart || rangeEnd > values.length) {
            throw new IllegalArgumentException("活动区间超出数组边界");
        }
        if (rangeMiddle != -1 && (rangeMiddle <= rangeStart || rangeMiddle >= rangeEnd)) {
            throw new IllegalArgumentException("左右区间分界必须位于活动区间内部");
        }
        values = values.clone();
    }

    /** 兼容没有左右子区间的冒泡/快速排序帧；rangeMiddle=-1 表示不适用。 */
    public ArrayFrame(int[] values, EventKind eventKind, int firstIndex, int secondIndex,
                      int pivotIndex, int rangeStart, int rangeEnd, AlgorithmMetrics metrics,
                      String explanation, String pseudoLineId) {
        this(values, eventKind, firstIndex, secondIndex, pivotIndex, rangeStart, rangeEnd,
                -1, metrics, explanation, pseudoLineId);
    }

    @Override
    public int[] values() {
        return values.clone();
    }

    private static void validateIndex(int index, int length) {
        if (index < -1 || index >= length) {
            throw new IllegalArgumentException("高亮下标超出数组边界：" + index);
        }
    }
}
