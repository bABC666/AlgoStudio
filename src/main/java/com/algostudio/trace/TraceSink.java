package com.algostudio.trace;

import com.algostudio.model.frame.VisualFrame;

public interface TraceSink {
    boolean enabled();
    void append(VisualFrame frame);
}
