package com.algostudio.algorithm;

import com.algostudio.algorithm.sort.BubbleSort;
import com.algostudio.algorithm.sort.QuickSort;
import com.algostudio.algorithm.sort.MergeSort;
import com.algostudio.algorithm.sort.SortAlgorithm;
import java.util.List;
import com.algostudio.algorithm.graph.*;
import com.algostudio.model.input.GraphTraversalInput;
import com.algostudio.model.input.KnapsackInput;
import com.algostudio.model.input.LcsInput;
import com.algostudio.algorithm.dp.*;

/** 演示与性能对比共享候选算法，新增排序只需在此注册。 */
public final class AlgorithmRegistry {
    private AlgorithmRegistry() { }
    public static List<SortAlgorithm> sortingAlgorithms() {
        return List.of(new BubbleSort(), new QuickSort(), new MergeSort());
    }
    public static List<GraphTraversalAlgorithm> traversalAlgorithms() {
        return List.of(new BreadthFirstSearch(), new DepthFirstSearch());
    }
    public static List<Algorithm<GraphTraversalInput>> graphAlgorithms() {
        return List.of(new BreadthFirstSearch(), new DepthFirstSearch(), new Dijkstra());
    }
    public static Algorithm<KnapsackInput> knapsackAlgorithm() { return new Knapsack01(); }
    public static Algorithm<LcsInput> lcsAlgorithm() { return new LongestCommonSubsequence(); }
}
