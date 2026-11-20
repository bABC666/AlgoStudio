package com.algostudio.model.output;

import java.util.Objects;

public record LcsOutput(int length, String subsequence) implements AlgorithmOutput {
    public LcsOutput {
        Objects.requireNonNull(subsequence);
        if (length < 0 || length != subsequence.length()) throw new IllegalArgumentException("LCS长度与子序列不一致。");
    }
}
