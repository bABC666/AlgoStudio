package com.algostudio.service;

import com.algostudio.model.dp.KnapsackItem;
import com.algostudio.model.input.*;
import java.util.*;

public final class DpExamples {
    private DpExamples() { }
    public record KnapsackExample(String name, KnapsackInput input) { @Override public String toString() { return name; } }
    public record LcsExample(String name, LcsInput input) { @Override public String toString() { return name; } }
    private static final List<KnapsackExample> KNAPSACKS = List.of(
            new KnapsackExample("标准例 · 容量5", new KnapsackInput(List.of(new KnapsackItem(2,3),new KnapsackItem(3,4),new KnapsackItem(4,5)),5)),
            new KnapsackExample("零容量 · 容量0", new KnapsackInput(List.of(new KnapsackItem(2,3),new KnapsackItem(3,4),new KnapsackItem(4,5)),0)),
            new KnapsackExample("平局例 · 容量1", new KnapsackInput(List.of(new KnapsackItem(1,1),new KnapsackItem(1,1)),1)),
            new KnapsackExample("上限例 · 8件/容量30", new KnapsackInput(Collections.nCopies(8,new KnapsackItem(1,100)),30)));
    private static final List<LcsExample> LCS = List.of(new LcsExample("标准例 · ABC / AC",new LcsInput("ABC","AC")),
            new LcsExample("无公共 · ABC / DEF",new LcsInput("ABC","DEF")),new LcsExample("重复 · AAAA / AA",new LcsInput("AAAA","AA")),
            new LcsExample("平局 · AB / BA",new LcsInput("AB","BA")),new LcsExample("上限例 · 10 / 10",new LcsInput("ABCDEFGHIJ","ABCDEFGHIJ")));
    public static List<KnapsackExample> knapsacks() { return KNAPSACKS; }
    public static List<LcsExample> lcs() { return LCS; }
}
