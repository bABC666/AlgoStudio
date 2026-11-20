package com.algostudio.model.pseudocode;

import java.util.*;

/** 教学伪代码；ID只在所属算法内解释，未采样的循环行不会被假装高亮。 */
public final class PseudocodeCatalog {
    private PseudocodeCatalog() { }
    public record Line(String id,String text) {
        public Line {
            Objects.requireNonNull(id);Objects.requireNonNull(text);
            if(id.isBlank()||text.isBlank())throw new IllegalArgumentException("伪代码ID与文本不能为空。");
        }
    }
    public record Program(String algorithmId,String name,List<Line> lines) {
        public Program {
            Objects.requireNonNull(algorithmId);Objects.requireNonNull(name);lines=List.copyOf(lines);
            if(algorithmId.isBlank()||name.isBlank()||lines.isEmpty()
                    ||lines.stream().map(Line::id).distinct().count()!=lines.size())
                throw new IllegalArgumentException("伪代码须有名称、非空行与唯一行ID。");
        }
        public OptionalInt lineNumber(String lineId) {
            for(int i=0;i<lines.size();i++)if(lines.get(i).id().equals(lineId))return OptionalInt.of(i+1);
            return OptionalInt.empty();
        }
    }
    private static Line line(String id,String text){return new Line(id,text);}
    private static Program program(String id,String name,Line...lines){return new Program(id,name,List.of(lines));}
    private static final Map<String,Program> PROGRAMS;
    static {
        var programs=List.of(
            program("bubble-sort","冒泡排序",
                line("INITIAL","开始 BubbleSort(a)"),
                line("LOOP","逐轮缩小右边界；从左到右扫描相邻元素"),
                line("COMPARE","比较 a[i] 与 a[i+1]"),
                line("SWAP","若左值更大，交换这两个元素；标记本轮有交换"),
                line("EARLY_EXIT","若本轮无交换，提前结束"),
                line("DONE","返回有序数组与计数")),
            program("quick-sort","快速排序 · 末元素 Lomuto",
                line("INITIAL","开始 QuickSort(a)"),
                line("PIVOT","pivot ← a[hi]；boundary ← lo"),
                line("COMPARE","比较 a[scan] ≤ pivot"),
                line("SWAP","条件成立时交换 a[boundary] 与 a[scan]，随后 boundary++；扫描后枢轴归位，自交换省略"),
                line("RECURSE","递归较小分区，循环处理较大分区"),
                line("DONE","返回有序数组与计数")),
            program("merge-sort","归并排序 · 左闭右开",
                line("INITIAL","开始 MergeSort(a)；准备一个辅助数组"),
                line("DIVIDE","mid ← lo + (hi-lo)/2；划分左右区间"),
                line("RECURSE","依次递归排序 [lo,mid) 与 [mid,hi)"),
                line("MERGE","开始将两个有序区间归并到 buffer"),
                line("COMPARE","比较 a[left] 与 a[right]；较小者入缓冲，平局取左"),
                line("TAIL","把未耗尽区间的剩余元素加入 buffer"),
                line("WRITE","a[k] ← buffer[k]；主数组写入 +1，交换不变"),
                line("DONE","返回有序数组与计数")),
            program("bfs","广度优先搜索 BFS",
                line("INITIAL","开始 BFS；队列、发现集与访问集为空"),
                line("DISCOVER","发现 v 并入队；若有来源则记录 parent[v]"),
                line("VISIT","u ← 队首出队；访问 u 并追加访问顺序"),
                line("EXAMINE_EDGE","按 ID 升序检查 u 的邻居 v；忽略权重"),
                line("LOOP","未发现的邻居继续入队；直到队列为空"),
                line("DONE","返回起点分量的顺序与父节点")),
            program("dfs","深度优先搜索 DFS",
                line("INITIAL","开始 DFS；显式调用栈模拟递归"),
                line("VISIT","将未访问的 u 入栈并访问；若有来源则记父节点"),
                line("EXAMINE_EDGE","按 ID 升序检查栈顶的下一个邻居"),
                line("DESCEND","邻居未访问时，进入该邻居的调用帧"),
                line("BACKTRACK","邻居处理完，弹出调用帧并返回父节点"),
                line("DONE","返回起点分量的顺序与父节点")),
            program("dijkstra","Dijkstra · 非负权线性选点",
                line("INITIAL","dist[start] ← 0；其他距离 ← ∞"),
                line("SETTLE","选择最小有限暂定距离并确定；同距按 ID"),
                line("EXAMINE_EDGE","检查 u—v；考虑经过 u 的候选距离"),
                line("RELAX","若 v 未确定且严格改善，更新 dist[v] 与 prev[v]"),
                line("LOOP","没有有限距离的未确定节点时停止"),
                line("DONE","返回距离与前驱；不可达保持 ∞")),
            program("knapsack-01","0/1 背包",
                line("INITIAL","创建 dp 表，单元格初始为未计算"),
                line("DP_INITIALIZE","dp[0][w] ← 0；dp[i][0] ← 0"),
                line("LOOP","按物品 i、容量 w 依次填表"),
                line("UPDATE_SKIP","重量超过 w：dp[i][w] ← dp[i-1][w]"),
                line("UPDATE_FIT","dp[i][w] ← max(dp[i-1][w], dp[i-1][w-weight[i]]+value[i])"),
                line("TRACE_TAKE","回溯：价值比上一行大，选择物品；扣重量并到上一行"),
                line("TRACE_SKIP","否则不选当前物品（平局不选）；容量不变，到上一行"),
                line("TRACE_END","到第 0 行，结束回溯"),
                line("DONE","物品编号恢复升序；返回最大价值、编号与总重量")),
            program("lcs","最长公共子序列 LCS",
                line("INITIAL","创建 dp 表，单元格初始为未计算"),
                line("DP_INITIALIZE","dp[0][j] ← 0；dp[i][0] ← 0"),
                line("LOOP","按 X、Y 的前缀长度依次填表"),
                line("UPDATE_MATCH","字符相同：dp[i][j] ← dp[i-1][j-1]+1"),
                line("UPDATE_DIFFERENT","字符不同：dp[i][j] ← max(上方, 左侧)"),
                line("TRACE_MATCH","回溯字符相同：记录该字符，向左上移动"),
                line("TRACE_UP","否则上方 ≥ 左侧时向上（平局向上）"),
                line("TRACE_LEFT","否则向左移动"),
                line("TRACE_END","到空前缀边界，结束回溯"),
                line("DONE","反转已记录字符；返回 LCS 长度与实际子序列"))
        );
        var byId=new LinkedHashMap<String,Program>();programs.forEach(p->byId.put(p.algorithmId(),p));PROGRAMS=Map.copyOf(byId);
    }
    public static Optional<Program> find(String algorithmId){return Optional.ofNullable(algorithmId==null?null:PROGRAMS.get(algorithmId));}
}
