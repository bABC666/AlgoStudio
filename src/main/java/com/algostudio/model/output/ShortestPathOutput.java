package com.algostudio.model.output;

import java.util.*;

/** 完整距离表，Long.MAX_VALUE 表示不可达；prev 只在距离严格改善时设置。 */
public record ShortestPathOutput(String startNode, Map<String, Long> distances,
                                 Map<String, String> parents) implements AlgorithmOutput {
    public ShortestPathOutput {
        Objects.requireNonNull(startNode); distances = Map.copyOf(distances); parents = Map.copyOf(parents);
        if (!Long.valueOf(0).equals(distances.get(startNode)) || distances.values().stream().anyMatch(d -> d < 0))
            throw new IllegalArgumentException("起点距离须为0，距离不得为负。");
        if (parents.containsKey(startNode) || !distances.keySet().containsAll(parents.keySet()) || !distances.keySet().containsAll(parents.values()))
            throw new IllegalArgumentException("前驱须引用距离表中的节点，起点没有前驱。");
    }

    /** 从已保存的前驱读取路径，不重新运行最短路；也可用于展示中间帧的暂定路径。 */
    public List<String> pathTo(String target) {
        if (!distances.containsKey(target)) throw new IllegalArgumentException("目标节点不存在：" + target);
        if (distances.get(target) == Long.MAX_VALUE) return List.of();
        List<String> reversed = new ArrayList<>(); Set<String> seen = new HashSet<>();
        String current = target;
        while (true) {
            if (current == null || !seen.add(current)) throw new IllegalStateException("前驱链缺失或存在环。");
            reversed.add(current);
            if (current.equals(startNode)) break;
            current = parents.get(current);
        }
        Collections.reverse(reversed); return List.copyOf(reversed);
    }
}
