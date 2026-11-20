package com.algostudio.model.input;

import java.util.Objects;

/** 核心层允许空数组、负数和任意 int，不混入 UI 输入限制。 */
public record ArrayInput(int[] values) implements AlgorithmInput {
    public ArrayInput {
        values = Objects.requireNonNull(values, "values").clone();
    }

    @Override
    public int[] values() {
        return values.clone();
    }

    /** 无分配的容量查询，服务可在复制工作数组或构帧前拒绝超限输入。 */
    public int size() {
        return values.length;
    }
}
