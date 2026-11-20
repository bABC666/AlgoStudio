package com.algostudio.trace;

import com.algostudio.model.frame.VisualFrame;

/** 不持有任何帧；算法必须先判断 enabled，禁止先创建快照再丢弃。 */
public enum NoOpTraceSink implements TraceSink {
    INSTANCE;

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void append(VisualFrame frame) {
        // 接口允许忽略事件；调用方应跳过整个构帧路径。
    }
}
