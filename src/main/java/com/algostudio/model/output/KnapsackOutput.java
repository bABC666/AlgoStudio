package com.algostudio.model.output;

import java.util.*;

public record KnapsackOutput(int maxValue, List<Integer> chosenItemIds, int totalWeight) implements AlgorithmOutput {
    public KnapsackOutput {
        chosenItemIds = List.copyOf(chosenItemIds);
        if (maxValue < 0 || totalWeight < 0 || chosenItemIds.stream().anyMatch(id -> id < 1)
                || new HashSet<>(chosenItemIds).size() != chosenItemIds.size()) throw new IllegalArgumentException("背包结果含非法数值或重复物品。");
    }
}
