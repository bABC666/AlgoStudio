package com.algostudio.algorithm.sort;

import com.algostudio.algorithm.AlgorithmCategory;
import com.algostudio.algorithm.AlgorithmDescriptor;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.trace.TraceSink;

/** 固定末元素 Lomuto 分区。只递归较小分区，递归栈深度为 O(log n)。 */
public final class QuickSort extends SortAlgorithm {
    private static final AlgorithmDescriptor DESCRIPTOR = new AlgorithmDescriptor(
            "quick-sort", "快速排序", AlgorithmCategory.SORTING);

    @Override
    public AlgorithmDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AlgorithmMetrics sortInPlace(int[] work, TraceSink sink) {
        SortOperations operations = new SortOperations(work, sink);
        sort(0, work.length - 1, operations);
        return operations.finish();
    }

    private void sort(int low, int high, SortOperations operations) {
        while (low < high) {
            int pivot = partition(low, high, operations);
            if (pivot - low < high - pivot) {
                sort(low, pivot - 1, operations);
                low = pivot + 1;
            } else {
                sort(pivot + 1, high, operations);
                high = pivot - 1;
            }
        }
    }

    private int partition(int low, int high, SortOperations operations) {
        operations.range(low, high + 1, high);
        int boundary = low;
        for (int scan = low; scan < high; scan++) {
            if (operations.compare(scan, high) <= 0) {
                operations.swap(boundary, scan);
                boundary++;
            }
        }
        operations.swap(boundary, high);
        return boundary;
    }
}
