package com.algostudio.algorithm.sort;

import com.algostudio.algorithm.AlgorithmCategory;
import com.algostudio.algorithm.AlgorithmDescriptor;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.trace.TraceSink;

public final class BubbleSort extends SortAlgorithm {
    private static final AlgorithmDescriptor DESCRIPTOR = new AlgorithmDescriptor(
            "bubble-sort", "冒泡排序", AlgorithmCategory.SORTING);

    @Override
    public AlgorithmDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AlgorithmMetrics sortInPlace(int[] work, TraceSink sink) {
        SortOperations operations = new SortOperations(work, sink);
        for (int end = work.length - 1; end > 0; end--) {
            operations.range(0, end + 1, -1);
            boolean changed = false;
            for (int index = 0; index < end; index++) {
                if (operations.compare(index, index + 1) > 0) {
                    operations.swap(index, index + 1);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return operations.finish();
    }
}
