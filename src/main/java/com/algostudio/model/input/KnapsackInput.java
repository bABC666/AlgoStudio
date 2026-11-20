package com.algostudio.model.input;

import com.algostudio.model.dp.KnapsackItem;
import java.util.*;

public record KnapsackInput(List<KnapsackItem> items, int capacity) implements AlgorithmInput {
    public KnapsackInput {
        Objects.requireNonNull(items);
        if (items.isEmpty() || items.size() > 8) throw new IllegalArgumentException("背包须有1～8件物品。");
        if (capacity < 0 || capacity > 30) throw new IllegalArgumentException("容量须在0～30之间。");
        items = List.copyOf(items);
    }
}
