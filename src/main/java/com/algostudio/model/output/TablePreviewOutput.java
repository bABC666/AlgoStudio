package com.algostudio.model.output;

import java.util.Objects;

/** 仅标识表格布局，不是DP最优值或解。 */
public record TablePreviewOutput(String exampleId) implements AlgorithmOutput {
    public TablePreviewOutput { Objects.requireNonNull(exampleId); }
}
