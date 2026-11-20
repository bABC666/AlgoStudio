package com.algostudio.ui;

public enum TableRunMode {
    PREVIEW("表格样式预览"), KNAPSACK("0/1 背包"), LCS("最长公共子序列 LCS");
    private final String label;
    TableRunMode(String label) { this.label=label; }
    @Override public String toString() { return label; }
}
