package com.algostudio.model.frame;

import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.table.TableCell;
import java.util.*;

/** 完整矩形表的深度不可变快照；-1是未计算，0是合法已填值。 */
public record TableFrame(String rowTitle, String columnTitle, List<String> rowLabels, List<String> columnLabels,
                         List<List<Integer>> values, TableCell currentCell, Set<TableCell> dependencies, Set<TableCell> path,
                         EventKind eventKind, AlgorithmMetrics metrics, String explanation, String pseudoLineId) implements VisualFrame {
    public static final int UNCOMPUTED = -1;
    public TableFrame {
        Objects.requireNonNull(rowTitle);Objects.requireNonNull(columnTitle);Objects.requireNonNull(eventKind);
        Objects.requireNonNull(metrics);Objects.requireNonNull(explanation);
        rowLabels=List.copyOf(rowLabels);columnLabels=List.copyOf(columnLabels);
        values=values.stream().map(List::copyOf).toList();dependencies=Set.copyOf(dependencies);path=Set.copyOf(path);
        if(rowTitle.isBlank()||columnTitle.isBlank()||rowLabels.isEmpty()||columnLabels.isEmpty()
                ||rowLabels.stream().anyMatch(String::isBlank)||columnLabels.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("表格须有非空行列标题。");
        int columnCount=columnLabels.size();
        if(values.size()!=rowLabels.size()||values.stream().anyMatch(row->row.size()!=columnCount))
            throw new IllegalArgumentException("表格必须为与行列标题一致的非空矩形。");
        if(values.stream().flatMap(Collection::stream).anyMatch(value->value<UNCOMPUTED))
            throw new IllegalArgumentException("单元格只接受未计算标记-1或非负整数。");
        Set<TableCell> references=new HashSet<>(dependencies);references.addAll(path);if(currentCell!=null)references.add(currentCell);
        for(var cell:references)if(cell.row()>=rowLabels.size()||cell.column()>=columnLabels.size())
            throw new IllegalArgumentException("高亮单元格超出表格范围。");
    }
}
