package com.algostudio.algorithm.dp;

import com.algostudio.algorithm.*;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.frame.*;
import com.algostudio.model.input.KnapsackInput;
import com.algostudio.model.output.KnapsackOutput;
import com.algostudio.model.table.TableCell;
import com.algostudio.trace.TraceSink;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.IntStream;

/** 每件物品最多一次；take与skip同值时不选当前物品。 */
public final class Knapsack01 implements Algorithm<KnapsackInput> {
    private static final AlgorithmDescriptor DESCRIPTOR = new AlgorithmDescriptor("knapsack-01", "0/1 背包", AlgorithmCategory.DYNAMIC_PROGRAMMING);
    @Override public AlgorithmDescriptor descriptor() { return DESCRIPTOR; }
    @Override public AlgorithmRun execute(KnapsackInput input, TraceSink sink) { return new Run(Objects.requireNonNull(input), Objects.requireNonNull(sink)).execute(); }

    private static final class Run {
        private final KnapsackInput input;
        private final TraceSink sink;
        private final int[][] table;
        private final List<Integer> chosen = new ArrayList<>();
        private List<String> rows, columns;
        private Set<TableCell> path;
        private int i, w, nextW, skip, take, totalWeight;
        private long updates;
        private boolean selected;

        Run(KnapsackInput input, TraceSink sink) {
            check(); this.input = input; this.sink = sink;
            table = new int[input.items().size() + 1][input.capacity() + 1];
            for (var row : table) { check(); Arrays.fill(row, TableFrame.UNCOMPUTED); }
        }
        AlgorithmRun execute() {
            emit(EventKind.INITIAL);
            for (var row : table) { check(); row[0] = 0; } Arrays.fill(table[0], 0);
            emit(EventKind.DP_INITIALIZE);
            for (i = 1; i < table.length; i++) {
                var item = input.items().get(i - 1);
                for (w = 1; w <= input.capacity(); w++) {
                    check(); skip = table[i - 1][w];
                    take = item.weight() <= w ? table[i - 1][w - item.weight()] + item.value() : -1;
                    table[i][w] = Math.max(skip, take); updates++; emit(EventKind.DP_UPDATE);
                }
            }
            i = input.items().size(); w = input.capacity();
            while (i > 0) {
                check(); var item = input.items().get(i - 1);
                selected = table[i][w] > table[i - 1][w]; nextW = selected ? w - item.weight() : w;
                if (selected) { chosen.add(i); totalWeight += item.weight(); }
                emit(EventKind.BACKTRACK); i--; w = nextW;
            }
            emit(EventKind.BACKTRACK); Collections.reverse(chosen); emit(EventKind.DONE); check();
            return new AlgorithmRun(new KnapsackOutput(table[table.length - 1][input.capacity()], chosen, totalWeight), metrics());
        }
        private void emit(EventKind event) {
            check();
            // NoOp必须在标题、解释、单元格集合和完整表副本之前退出。
            if (!sink.enabled()) return;
            String lineId = switch(event) {
                case DP_UPDATE -> take < 0 ? "UPDATE_SKIP" : "UPDATE_FIT";
                case BACKTRACK -> i == 0 ? "TRACE_END" : selected ? "TRACE_TAKE" : "TRACE_SKIP";
                default -> event.name();
            };
            if (rows == null) {
                rows = IntStream.range(0, table.length).mapToObj(Integer::toString).toList();
                columns = IntStream.rangeClosed(0, input.capacity()).mapToObj(Integer::toString).toList(); path = new LinkedHashSet<>();
            }
            TableCell current = event == EventKind.DP_UPDATE || event == EventKind.BACKTRACK ? new TableCell(i, w) : null;
            Set<TableCell> dependencies = new LinkedHashSet<>();
            if (event == EventKind.DP_UPDATE) {
                dependencies.add(new TableCell(i - 1, w));
                if (take >= 0) dependencies.add(new TableCell(i - 1, w - input.items().get(i - 1).weight()));
            } else if (event == EventKind.BACKTRACK) {
                path.add(current); if (i > 0) dependencies.add(new TableCell(i - 1, nextW));
            }
            String text = switch (event) {
                case INITIAL -> "初始背包表，容量" + input.capacity() + "；尚未初始化或更新单元格。";
                case DP_INITIALIZE -> "初始化边界：没有物品或容量为0时价值为0；边界不计转移更新。";
                case DP_UPDATE -> "填写dp[" + i + "][" + w + "]=" + table[i][w] + "：不选物品" + i + "得" + skip
                        + (take < 0 ? "；重量超过容量，不能选。" : "，选得" + take + "；相等时不选当前物品。");
                case BACKTRACK -> i == 0 ? "到达第0行，背包回溯结束。" : "回溯dp[" + i + "][" + w + "]=" + table[i][w]
                        + (selected ? "，选择物品" + i + "；剩余容量" + nextW + "。" : "，不选物品" + i + "（上一行已达到相同价值），容量保持" + w + "。");
                case DONE -> "背包完成：最大价值" + table[table.length - 1][input.capacity()] + "，选择物品" + chosen + "，总重量" + totalWeight + "。";
                default -> throw new IllegalArgumentException("非背包事件。");
            };
            var values = Arrays.stream(table).map(row -> Arrays.stream(row).boxed().toList()).toList();
            sink.append(new TableFrame("前i件物品", "容量 w", rows, columns, values, current, dependencies, path, event, metrics(), text, lineId));
        }
        private AlgorithmMetrics metrics() { return new AlgorithmMetrics(0, 0, 0, 0, 0, 0, updates); }
        private static void check() { if (Thread.currentThread().isInterrupted()) throw new CancellationException("背包计算已取消。"); }
    }
}
