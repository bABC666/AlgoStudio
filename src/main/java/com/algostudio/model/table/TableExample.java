package com.algostudio.model.table;

import java.util.*;
import java.util.stream.IntStream;

/** 仅定义T10预览布局，不是可执行的背包/LCS算法。 */
public enum TableExample {
    KNAPSACK_SMALL("背包布局 · 4×6（示意）", "前i件物品", "容量 w", numbers(4), numbers(6)),
    KNAPSACK_MAX("背包上限 · 9×31（示意）", "前i件物品", "容量 w", numbers(9), numbers(31)),
    LCS_SMALL("LCS布局 · 4×3（示意）", "串X前缀 i", "串Y前缀 j", prefixes("ABC"), prefixes("AC")),
    LCS_MAX("LCS上限 · 11×11（示意）", "串X前缀 i", "串Y前缀 j", prefixes("ABCDEFGHIJ"), prefixes("ACEGIKLMNO"));

    private final String label, rowTitle, columnTitle;
    private final List<String> rowLabels, columnLabels;
    TableExample(String label, String rowTitle, String columnTitle, List<String> rows, List<String> columns) {
        this.label=label;this.rowTitle=rowTitle;this.columnTitle=columnTitle;rowLabels=List.copyOf(rows);columnLabels=List.copyOf(columns);
    }
    public String rowTitle(){return rowTitle;} public String columnTitle(){return columnTitle;}
    public List<String> rowLabels(){return rowLabels;} public List<String> columnLabels(){return columnLabels;}
    public int rows(){return rowLabels.size();} public int columns(){return columnLabels.size();}
    @Override public String toString(){return label;}
    private static List<String> numbers(int count){return IntStream.range(0,count).mapToObj(Integer::toString).toList();}
    private static List<String> prefixes(String text){return IntStream.rangeClosed(0,text.length()).mapToObj(i->i==0?"0:∅":i+":"+text.charAt(i-1)).toList();}
}
