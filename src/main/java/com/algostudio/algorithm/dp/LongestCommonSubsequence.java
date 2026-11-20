package com.algostudio.algorithm.dp;

import com.algostudio.algorithm.*;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.frame.*;
import com.algostudio.model.input.LcsInput;
import com.algostudio.model.output.LcsOutput;
import com.algostudio.model.table.TableCell;
import com.algostudio.trace.TraceSink;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.IntStream;

/** 大小写敏感的LCS；不匹配且上/左相等时回溯向上。 */
public final class LongestCommonSubsequence implements Algorithm<LcsInput> {
    private static final AlgorithmDescriptor DESCRIPTOR = new AlgorithmDescriptor("lcs", "最长公共子序列 LCS", AlgorithmCategory.DYNAMIC_PROGRAMMING);
    @Override public AlgorithmDescriptor descriptor() { return DESCRIPTOR; }
    @Override public AlgorithmRun execute(LcsInput input, TraceSink sink) { return new Run(Objects.requireNonNull(input), Objects.requireNonNull(sink)).execute(); }

    private static final class Run {
        private final LcsInput input;
        private final TraceSink sink;
        private final int[][] table;
        private final StringBuilder reversed = new StringBuilder();
        private List<String> rows, columns;
        private Set<TableCell> path;
        private int i, j, nextI, nextJ;
        private long updates;
        private boolean match;
        private String result;

        Run(LcsInput input, TraceSink sink) {
            check(); this.input = input; this.sink = sink; table = new int[input.x().length() + 1][input.y().length() + 1];
            for (var row : table) { check(); Arrays.fill(row, TableFrame.UNCOMPUTED); }
        }
        AlgorithmRun execute() {
            emit(EventKind.INITIAL);
            for (var row : table) { check(); row[0] = 0; } Arrays.fill(table[0], 0); emit(EventKind.DP_INITIALIZE);
            for (i = 1; i < table.length; i++) for (j = 1; j < table[i].length; j++) {
                check(); match = input.x().charAt(i - 1) == input.y().charAt(j - 1);
                table[i][j] = match ? table[i - 1][j - 1] + 1 : Math.max(table[i - 1][j], table[i][j - 1]);
                updates++; emit(EventKind.DP_UPDATE);
            }
            i = input.x().length(); j = input.y().length();
            while (i > 0 && j > 0) {
                check(); match = input.x().charAt(i - 1) == input.y().charAt(j - 1);
                if (match) { reversed.append(input.x().charAt(i - 1)); nextI = i - 1; nextJ = j - 1; }
                else if (table[i - 1][j] >= table[i][j - 1]) { nextI = i - 1; nextJ = j; }
                else { nextI = i; nextJ = j - 1; }
                emit(EventKind.BACKTRACK); i = nextI; j = nextJ;
            }
            emit(EventKind.BACKTRACK); result = reversed.reverse().toString(); emit(EventKind.DONE); check();
            return new AlgorithmRun(new LcsOutput(table[table.length - 1][input.y().length()], result), metrics());
        }
        private void emit(EventKind event) {
            check();
            if (!sink.enabled()) return;
            String lineId = switch(event) {
                case DP_UPDATE -> match ? "UPDATE_MATCH" : "UPDATE_DIFFERENT";
                case BACKTRACK -> i == 0 || j == 0 ? "TRACE_END" : match ? "TRACE_MATCH" : nextI < i ? "TRACE_UP" : "TRACE_LEFT";
                default -> event.name();
            };
            if (rows == null) { rows = labels(input.x()); columns = labels(input.y()); path = new LinkedHashSet<>(); }
            TableCell current = event == EventKind.DP_UPDATE || event == EventKind.BACKTRACK ? new TableCell(i, j) : null;
            Set<TableCell> dependencies = new LinkedHashSet<>();
            if (event == EventKind.DP_UPDATE) {
                if (match) dependencies.add(new TableCell(i - 1, j - 1));
                else { dependencies.add(new TableCell(i - 1, j)); dependencies.add(new TableCell(i, j - 1)); }
            } else if (event == EventKind.BACKTRACK) {
                path.add(current); if (i > 0 && j > 0) dependencies.add(new TableCell(nextI, nextJ));
            }
            String text = switch (event) {
                case INITIAL -> "初始LCS表，X=" + input.x() + "，Y=" + input.y() + "；尚未更新单元格。";
                case DP_INITIALIZE -> "初始化第0行/列为0：空前缀的LCS长度为0；边界不计转移更新。";
                case DP_UPDATE -> "填写dp[" + i + "][" + j + "]=" + table[i][j] + "：X[" + (i - 1) + "]=" + input.x().charAt(i - 1)
                        + "，Y[" + (j - 1) + "]=" + input.y().charAt(j - 1) + (match ? "相同，取左上+1。" : "不同，取上/左较大值。");
                case BACKTRACK -> i == 0 || j == 0 ? "到达空前缀边界，LCS回溯结束。" : "回溯dp[" + i + "][" + j + "]=" + table[i][j]
                        + (match ? "，匹配字符" + input.x().charAt(i - 1) + "，向左上。" : nextI < i ? "，向上；上/左相等时固定向上。" : "，向左，左侧长度更大。");
                case DONE -> "LCS完成：长度" + result.length() + "，公共子序列" + (result.isEmpty() ? "为空串（∅）" : "为“" + result + "”") + "。";
                default -> throw new IllegalArgumentException("非LCS事件。");
            };
            var values = Arrays.stream(table).map(row -> Arrays.stream(row).boxed().toList()).toList();
            sink.append(new TableFrame("串X前缀 i", "串Y前缀 j", rows, columns, values, current, dependencies, path, event, metrics(), text, lineId));
        }
        private static List<String> labels(String text) { return IntStream.rangeClosed(0, text.length()).mapToObj(n -> n == 0 ? "0:∅" : n + ":" + text.charAt(n - 1)).toList(); }
        private AlgorithmMetrics metrics() { return new AlgorithmMetrics(0, 0, 0, 0, 0, 0, updates); }
        private static void check() { if (Thread.currentThread().isInterrupted()) throw new CancellationException("LCS计算已取消。"); }
    }
}
