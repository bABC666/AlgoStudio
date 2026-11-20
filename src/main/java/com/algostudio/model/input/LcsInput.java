package com.algostudio.model.input;

import java.util.Objects;

/** 核心允许空串；UI轨迹入口另外要求两串非空。 */
public record LcsInput(String x, String y) implements AlgorithmInput {
    public LcsInput {
        Objects.requireNonNull(x); Objects.requireNonNull(y);
        if (x.length() > 10 || y.length() > 10) throw new IllegalArgumentException("LCS字符串长度最多10。");
        if (!x.matches("[A-Za-z]*") || !y.matches("[A-Za-z]*")) throw new IllegalArgumentException("LCS只接受ASCII字母，区分大小写。");
    }
}
