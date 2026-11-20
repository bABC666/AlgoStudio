package com.algostudio.algorithm.sort;

import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.frame.ArrayFrame;
import com.algostudio.model.frame.EventKind;
import com.algostudio.trace.TraceSink;

import java.util.Objects;
import java.util.concurrent.CancellationException;

/** 每次运行独占的计数与数组操作，算法对象自身不保存运行状态。 */
final class SortOperations {
    private final int[] values;
    private final TraceSink sink;
    private int pivotIndex = -1;
    private int rangeStart;
    private int rangeEnd;
    private int rangeMiddle = -1;
    private long comparisons;
    private long writes;
    private long swaps;

    SortOperations(int[] values, TraceSink sink) {
        this.values = Objects.requireNonNull(values, "work");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.rangeEnd = values.length;
        checkCancelled();
        if (sink.enabled()) {
            record(EventKind.INITIAL, -1, -1, "初始数组，尚未执行比较或主数组写入");
        }
    }

    void range(int start, int end, int pivot) {
        rangeStart = start;
        rangeEnd = end;
        pivotIndex = pivot;
        rangeMiddle = -1;
        if (pivot >= 0 && sink.enabled()) {
            record(EventKind.PIVOT, -1, -1,
                    "选择下标 " + pivot + "（值 " + values[pivot] + "）为区间 [" + start + ", " + end + ") 的枢轴");
        }
    }

    void mergeRange(int start, int middle, int end, boolean splitting) {
        checkCancelled();
        rangeStart = start;
        rangeMiddle = middle;
        rangeEnd = end;
        pivotIndex = -1;
        if (sink.enabled()) {
            record(splitting ? EventKind.DIVIDE : EventKind.MERGE, -1, -1,
                    (splitting ? "分治" : "合并已排序的") + "左区间 [" + start + ", " + middle
                            + ") 与右区间 [" + middle + ", " + end + ")"
                            + (splitting ? "，先处理左侧，再处理右侧" : "；先归并到辅助缓冲，再逐项写回主数组"));
        }
    }

    void write(int index, int value) {
        checkCancelled();
        values[index] = value;
        writes++;
        if (sink.enabled()) {
            record(EventKind.WRITE, index, -1,
                    "将归并缓冲中的值 " + value + " 写回主数组下标 " + index
                            + "，合并区间 [" + rangeStart + ", " + rangeEnd + ")；主数组写入 +1，交换不变");
        }
    }

    int compare(int left, int right) {
        checkCancelled();
        comparisons++;
        if (sink.enabled()) {
            record(EventKind.COMPARE, left, right,
                    "比较下标 " + left + "（值 " + values[left] + "）与下标 " + right + "（值 " + values[right] + "）");
        }
        return Integer.compare(values[left], values[right]);
    }

    void swap(int left, int right) {
        checkCancelled();
        if (left == right) {
            return;
        }
        int previous = values[left];
        values[left] = values[right];
        values[right] = previous;
        swaps++;
        writes += 2;
        if (pivotIndex == left) {
            pivotIndex = right;
        } else if (pivotIndex == right) {
            pivotIndex = left;
        }
        if (sink.enabled()) {
            record(EventKind.SWAP, left, right,
                    "交换下标 " + left + " 与 " + right + "，当前值分别为 " + values[left] + " 和 " + values[right]);
        }
    }

    AlgorithmMetrics finish() {
        checkCancelled();
        pivotIndex = -1;
        rangeStart = 0;
        rangeEnd = values.length;
        rangeMiddle = -1;
        if (sink.enabled()) {
            record(EventKind.DONE, -1, -1, "排序完成，数组已按升序排列");
        }
        return metrics();
    }

    private AlgorithmMetrics metrics() {
        return new AlgorithmMetrics(comparisons, writes, swaps, 0, 0, 0, 0);
    }

    private void record(EventKind kind, int first, int second, String explanation) {
        // 所有调用点都先判断 enabled；复制仅发生在 ArrayFrame 构造器内部。
        sink.append(new ArrayFrame(values, kind, first, second, pivotIndex,
                rangeStart, rangeEnd, rangeMiddle, metrics(), explanation, kind.name()));
    }

    void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("排序已取消");
        }
    }
}
