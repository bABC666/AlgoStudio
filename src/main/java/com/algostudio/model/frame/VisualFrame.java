package com.algostudio.model.frame;

import com.algostudio.model.AlgorithmMetrics;

/** 所有实现必须是深度不可变快照；新增类别时显式扩展 permits。 */
public sealed interface VisualFrame permits ArrayFrame, GraphFrame, TableFrame {
    EventKind eventKind();
    AlgorithmMetrics metrics();
    String explanation();
    String pseudoLineId();
}
