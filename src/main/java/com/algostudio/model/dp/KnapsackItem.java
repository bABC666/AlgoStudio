package com.algostudio.model.dp;

public record KnapsackItem(int weight, int value) {
    public KnapsackItem {
        if (weight < 1 || weight > 20) throw new IllegalArgumentException("物品重量须在1～20之间。");
        if (value < 0 || value > 100) throw new IllegalArgumentException("物品价值须在0～100之间。");
    }
}
