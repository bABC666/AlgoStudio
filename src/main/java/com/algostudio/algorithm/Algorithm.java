package com.algostudio.algorithm;

import com.algostudio.model.input.AlgorithmInput;
import com.algostudio.trace.TraceSink;

public interface Algorithm<I extends AlgorithmInput> {
    AlgorithmDescriptor descriptor();
    AlgorithmRun execute(I input, TraceSink sink);
}
