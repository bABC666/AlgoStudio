package com.algostudio.algorithm.sort;

import com.algostudio.algorithm.AlgorithmCategory;
import com.algostudio.algorithm.AlgorithmDescriptor;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.trace.TraceSink;

/** 左闭右开分治；相等取左。单个 O(n) 辅助数组不计入主数组写入。 */
public final class MergeSort extends SortAlgorithm {
    private static final AlgorithmDescriptor DESCRIPTOR = new AlgorithmDescriptor(
            "merge-sort", "归并排序", AlgorithmCategory.SORTING);

    @Override public AlgorithmDescriptor descriptor() { return DESCRIPTOR; }

    @Override public AlgorithmMetrics sortInPlace(int[] work, TraceSink sink) {
        SortOperations operations = new SortOperations(work, sink);
        if (work.length > 1) sort(work, new int[work.length], 0, work.length, operations);
        return operations.finish();
    }

    private void sort(int[] work, int[] buffer, int start, int end, SortOperations operations) {
        operations.checkCancelled();
        if (end - start < 2) return;
        int middle = start + (end - start) / 2;
        operations.mergeRange(start, middle, end, true);
        sort(work, buffer, start, middle, operations);
        sort(work, buffer, middle, end, operations);
        operations.mergeRange(start, middle, end, false);
        int left = start, right = middle, target = start;
        // 比较阶段不覆盖主数组，帧中下标与解释始终引用同一份源值。
        while (left < middle && right < end) {
            buffer[target++] = operations.compare(left, right) <= 0 ? work[left++] : work[right++];
        }
        while (left < middle) {
            operations.checkCancelled();
            buffer[target++] = work[left++];
        }
        while (right < end) {
            operations.checkCancelled();
            buffer[target++] = work[right++];
        }
        for (int index = start; index < end; index++) operations.write(index, buffer[index]);
    }
}
