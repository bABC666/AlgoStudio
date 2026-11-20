package com.algostudio.service;

import com.algostudio.algorithm.AlgorithmRun;
import com.algostudio.model.*;
import com.algostudio.model.frame.*;
import com.algostudio.model.output.TablePreviewOutput;
import com.algostudio.model.table.*;
import com.algostudio.trace.FrameTraceSink;
import java.util.*;

/** 固定样式夹具：不求解背包/LCS，不冒充算法轨迹或更新计数。 */
public final class TablePreviewService {
    private final int maxFrames;
    public TablePreviewService(){this(FrameTraceSink.MAX_FRAMES);}
    public TablePreviewService(int maxFrames){
        if(maxFrames<1||maxFrames>FrameTraceSink.MAX_FRAMES)throw new IllegalArgumentException("无效的轨迹帧数上限。");
        this.maxFrames=maxFrames;
    }
    public ExecutionTrace generate(TableExample example){
        Objects.requireNonNull(example);var sink=new FrameTraceSink(maxFrames);List<List<Integer>> values=new ArrayList<>();
        for(int row=0;row<example.rows();row++)values.add(new ArrayList<>(Collections.nCopies(example.columns(),TableFrame.UNCOMPUTED)));
        append(sink,example,values,null,Set.of(),Set.of(),EventKind.INITIAL,"表格样式示意：所有格尚未计算（—），不执行DP算法。");
        for(int row=0;row<example.rows();row++)values.get(row).set(0,0);
        Collections.fill(values.getFirst(),0);
        append(sink,example,values,null,Set.of(),Set.of(),EventKind.TABLE_PREVIEW,"边界样式示意：第0行与第0列填0；0与未计算的—不同。");
        int r=example.rows()-1,c=example.columns()-1;var current=new TableCell(r,c);
        Set<TableCell> dependencies=Set.of(new TableCell(r-1,c),new TableCell(r-1,c-1));
        append(sink,example,values,current,dependencies,Set.of(),EventKind.TABLE_PREVIEW,"当前格与依赖格颜色示意：dp["+r+"]["+c+"]；不代表实际状态转移。");
        values.get(r).set(c,1);
        append(sink,example,values,current,dependencies,Set.of(),EventKind.TABLE_PREVIEW,"已填样式示意：为检查显示而填入1，不是最优值或算法计算结果。");
        Set<TableCell> path=Set.of(current,new TableCell(r-1,c));
        append(sink,example,values,null,Set.of(),path,EventKind.TABLE_PREVIEW,"回溯路径颜色示意：绿色格仅展示高亮样式，尚未求解或回溯。");
        append(sink,example,values,null,Set.of(),path,EventKind.DONE,"表格样式示意完成；右下角示意值1不是算法结果，这里不计算背包最优值或LCS结果。");
        return new ExecutionTrace("table-preview",example.toString(),sink.frames(),new AlgorithmRun(new TablePreviewOutput(example.name()),AlgorithmMetrics.ZERO));
    }
    private void append(FrameTraceSink sink,TableExample example,List<List<Integer>> values,TableCell current,Set<TableCell> dependencies,Set<TableCell> path,EventKind event,String text){
        if(sink.enabled())sink.append(new TableFrame(example.rowTitle(),example.columnTitle(),example.rowLabels(),example.columnLabels(),values,current,dependencies,path,event,AlgorithmMetrics.ZERO,text,null));
    }
}
