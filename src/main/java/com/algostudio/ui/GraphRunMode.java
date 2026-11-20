package com.algostudio.ui;

public enum GraphRunMode {
    PREVIEW("", "状态预览"), BFS("bfs", "广度优先 BFS"), DFS("dfs", "深度优先 DFS"), DIJKSTRA("dijkstra", "Dijkstra 最短路径");
    private final String algorithmId, label;
    GraphRunMode(String algorithmId, String label) { this.algorithmId = algorithmId; this.label = label; }
    public String algorithmId() { return algorithmId; }
    @Override public String toString() { return label; }
}
