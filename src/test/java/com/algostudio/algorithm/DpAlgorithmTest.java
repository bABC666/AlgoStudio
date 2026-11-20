package com.algostudio.algorithm;

import com.algostudio.algorithm.dp.*;
import com.algostudio.model.dp.KnapsackItem;
import com.algostudio.model.frame.*;
import com.algostudio.model.input.*;
import com.algostudio.model.output.*;
import com.algostudio.model.table.TableCell;
import com.algostudio.trace.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class DpAlgorithmTest {
    @Test void standardKnapsackReturnsOptimalValueAndEachItemOnceWithoutMutatingInput() {
        var items=new ArrayList<>(List.of(new KnapsackItem(2,3),new KnapsackItem(3,4),new KnapsackItem(4,5)));
        var input=new KnapsackInput(items,5);items.clear();
        var run=new Knapsack01().execute(input,NoOpTraceSink.INSTANCE);var result=(KnapsackOutput)run.output();
        assertEquals(new KnapsackOutput(7,List.of(1,2),5),result);assertEquals(15,run.metrics().dpCellsUpdated());
        assertEquals(3,input.items().size());assertThrows(UnsupportedOperationException.class,()->input.items().clear());
        assertThrows(UnsupportedOperationException.class,()->result.chosenItemIds().clear());
    }
    @Test void knapsackCapacityZeroZeroValuesAndEqualChoicesFollowSkipRule() {
        var algorithm=new Knapsack01();
        assertEquals(new KnapsackOutput(0,List.of(),0),algorithm.execute(knapsack(0,new int[]{2,3},new int[]{3,4}),NoOpTraceSink.INSTANCE).output());
        assertEquals(new KnapsackOutput(0,List.of(),0),algorithm.execute(knapsack(5,new int[]{1,2},new int[]{0,0}),NoOpTraceSink.INSTANCE).output());
        assertEquals(new KnapsackOutput(1,List.of(1),1),algorithm.execute(knapsack(1,new int[]{1,1},new int[]{1,1}),NoOpTraceSink.INSTANCE).output());
        var run=algorithm.execute(knapsack(0,new int[]{1},new int[]{100}),new FrameTraceSink());assertEquals(0,run.metrics().dpCellsUpdated());
    }
    // 子集枚举独立于二维DP；返回物品还须分别复核重量、价值和唯一性。
    @Test void hundredKnapsacksMatchExhaustiveSubsetOracle() {
        Random random=new Random(20261102);
        for(int sample=0;sample<100;sample++){
            int n=1+random.nextInt(8),capacity=random.nextInt(31);int[] weights=new int[n],values=new int[n];
            for(int i=0;i<n;i++){weights[i]=1+random.nextInt(20);values[i]=random.nextInt(101);}
            int best=0;
            for(int mask=0;mask<(1<<n);mask++){int weight=0,value=0;for(int i=0;i<n;i++)if((mask&(1<<i))!=0){weight+=weights[i];value+=values[i];}if(weight<=capacity)best=Math.max(best,value);}
            var result=(KnapsackOutput)new Knapsack01().execute(knapsack(capacity,weights,values),NoOpTraceSink.INSTANCE).output();
            assertEquals(best,result.maxValue(),"seed20261102 sample="+sample);
            assertEquals(result.chosenItemIds().size(),new HashSet<>(result.chosenItemIds()).size());
            int weight=0,value=0;for(int id:result.chosenItemIds()){assertTrue(id>=1&&id<=n);weight+=weights[id-1];value+=values[id-1];}
            assertEquals(best,value);assertEquals(weight,result.totalWeight());assertTrue(weight<=capacity);
            assertEquals(result.chosenItemIds().stream().sorted().toList(),result.chosenItemIds());
        }
    }
    @Test void fixedLcsCasesReturnRealSubsequenceAndStableUpwardTie() {
        var algorithm=new LongestCommonSubsequence();
        assertEquals(new LcsOutput(2,"AC"),algorithm.execute(new LcsInput("ABC","AC"),NoOpTraceSink.INSTANCE).output());
        assertEquals(new LcsOutput(0,""),algorithm.execute(new LcsInput("ABC","DEF"),NoOpTraceSink.INSTANCE).output());
        assertEquals(new LcsOutput(2,"AA"),algorithm.execute(new LcsInput("AAAA","AA"),NoOpTraceSink.INSTANCE).output());
        assertEquals(new LcsOutput(1,"A"),algorithm.execute(new LcsInput("AB","BA"),NoOpTraceSink.INSTANCE).output());
        assertEquals(new LcsOutput(0,""),algorithm.execute(new LcsInput("","A"),NoOpTraceSink.INSTANCE).output());
        assertEquals(new LcsOutput(0,""),algorithm.execute(new LcsInput("A",""),new FrameTraceSink()).output());
        assertEquals(new LcsOutput(0,""),algorithm.execute(new LcsInput("a","A"),NoOpTraceSink.INSTANCE).output());
    }
    // 枚举较短串的所有子序列，只校验最优长度与两串合法性，不复制被测转移。
    @Test void hundredLcsInputsMatchSubsequenceEnumeration() {
        Random random=new Random(20261102);
        for(int sample=0;sample<100;sample++){
            String x=randomString(random),y=randomString(random);String shortText=x.length()<=y.length()?x:y,other=shortText==x?y:x;int best=0;
            for(int mask=0;mask<(1<<shortText.length());mask++){var candidate=new StringBuilder();for(int i=0;i<shortText.length();i++)if((mask&(1<<i))!=0)candidate.append(shortText.charAt(i));if(isSubsequence(candidate.toString(),other))best=Math.max(best,candidate.length());}
            var output=(LcsOutput)new LongestCommonSubsequence().execute(new LcsInput(x,y),NoOpTraceSink.INSTANCE).output();
            assertEquals(best,output.length(),"seed20261102 sample="+sample+" x="+x+" y="+y);
            assertEquals(output.length(),output.subsequence().length());assertTrue(isSubsequence(output.subsequence(),x));assertTrue(isSubsequence(output.subsequence(),y));
        }
    }
    @Test void invalidInputsAreRejectedBeforeAllocatingTables() {
        assertThrows(IllegalArgumentException.class,()->new KnapsackItem(0,1));assertThrows(IllegalArgumentException.class,()->new KnapsackItem(21,1));
        assertThrows(IllegalArgumentException.class,()->new KnapsackItem(1,-1));assertThrows(IllegalArgumentException.class,()->new KnapsackItem(1,101));
        assertThrows(IllegalArgumentException.class,()->new KnapsackInput(List.of(),1));
        assertThrows(IllegalArgumentException.class,()->new KnapsackInput(Collections.nCopies(9,new KnapsackItem(1,1)),1));
        assertThrows(IllegalArgumentException.class,()->new KnapsackInput(List.of(new KnapsackItem(1,1)),-1));
        assertThrows(IllegalArgumentException.class,()->new KnapsackInput(List.of(new KnapsackItem(1,1)),31));
        assertThrows(IllegalArgumentException.class,()->new LcsInput("ABCDEFGHIJK","A"));
        for(String invalid:List.of("中","A1","A B","é","A\n"))assertThrows(IllegalArgumentException.class,()->new LcsInput(invalid,"A"));
        assertThrows(IllegalArgumentException.class,()->new LcsOutput(2,"A"));
    }
    @Test void knapsackFramesShowTrueCellsDependenciesAndImmutableBacktrackPath() {
        var sink=new FrameTraceSink();var run=new Knapsack01().execute(knapsack(5,new int[]{2,3,4},new int[]{3,4,5}),sink);
        assertFrames(sink,run,15);var initial=(TableFrame)sink.frames().getFirst();var last=(TableFrame)sink.frames().getLast();
        assertEquals(List.of(List.of(0,0,0,0,0,0),List.of(0,0,3,3,3,3),List.of(0,0,3,4,4,7),List.of(0,0,3,4,5,7)),last.values());
        assertEquals(Set.of(new TableCell(3,5),new TableCell(2,5),new TableCell(1,2),new TableCell(0,0)),last.path());
        var target=sink.frames().stream().map(TableFrame.class::cast).filter(f->f.eventKind().name().equals("DP_UPDATE")&&new TableCell(2,5).equals(f.currentCell())).findFirst().orElseThrow();
        assertEquals(Set.of(new TableCell(1,5),new TableCell(1,2)),target.dependencies());assertEquals(7,target.values().get(2).get(5));
        assertEquals(-1,initial.values().get(2).get(5));assertThrows(UnsupportedOperationException.class,()->last.values().getFirst().set(0,3));
    }
    @Test void lcsFramesShowMatchAndMismatchDependenciesAndRealBacktracking() {
        var sink=new FrameTraceSink();var run=new LongestCommonSubsequence().execute(new LcsInput("ABC","AC"),sink);assertFrames(sink,run,6);
        var frames=sink.frames().stream().map(TableFrame.class::cast).toList();var last=frames.getLast();
        assertEquals(List.of(List.of(0,0,0),List.of(0,1,1),List.of(0,1,1),List.of(0,1,2)),last.values());
        assertEquals(Set.of(new TableCell(3,2),new TableCell(2,1),new TableCell(1,1),new TableCell(0,0)),last.path());
        var match=frames.stream().filter(f->f.eventKind().name().equals("DP_UPDATE")&&new TableCell(3,2).equals(f.currentCell())).findFirst().orElseThrow();
        assertEquals(Set.of(new TableCell(2,1)),match.dependencies());
        var mismatch=frames.stream().filter(f->f.eventKind().name().equals("DP_UPDATE")&&new TableCell(2,1).equals(f.currentCell())).findFirst().orElseThrow();
        assertEquals(Set.of(new TableCell(1,1),new TableCell(2,0)),mismatch.dependencies());
    }
    @Test void maximumInputsRemainBoundedAndProduceValidMaximumResults() {
        var sink=new FrameTraceSink();var run=new Knapsack01().execute(new KnapsackInput(Collections.nCopies(8,new KnapsackItem(1,100)),30),sink);
        assertEquals(new KnapsackOutput(800,List.of(1,2,3,4,5,6,7,8),8),run.output());assertEquals(240,run.metrics().dpCellsUpdated());
        assertEquals(9,((TableFrame)sink.frames().getLast()).values().size());assertEquals(31,((TableFrame)sink.frames().getLast()).columnLabels().size());
        sink=new FrameTraceSink();run=new LongestCommonSubsequence().execute(new LcsInput("ABCDEFGHIJ","ABCDEFGHIJ"),sink);
        assertEquals(new LcsOutput(10,"ABCDEFGHIJ"),run.output());assertEquals(100,run.metrics().dpCellsUpdated());assertEquals(11,((TableFrame)sink.frames().getLast()).values().size());
    }
    @Test void noOpMatchesRecordedResultsAndBothAlgorithmsStopOnInterruptOrOverflow() {
        var knapsack=new Knapsack01();var input=knapsack(5,new int[]{2,3,4},new int[]{3,4,5});var lcs=new LongestCommonSubsequence();var strings=new LcsInput("ABC","AC");
        TraceSink disabled=new TraceSink(){public boolean enabled(){return false;}public void append(VisualFrame f){fail("NoOp不得收到帧");}};
        assertEquals(knapsack.execute(input,new FrameTraceSink()),knapsack.execute(input,disabled));assertEquals(lcs.execute(strings,new FrameTraceSink()),lcs.execute(strings,disabled));
        try{Thread.currentThread().interrupt();assertThrows(CancellationException.class,()->knapsack.execute(input,disabled));assertThrows(CancellationException.class,()->lcs.execute(strings,disabled));}finally{Thread.interrupted();}
        for(boolean first:List.of(true,false)){
            TraceSink interrupting=new TraceSink(){int checks;public boolean enabled(){if(++checks==3)Thread.currentThread().interrupt();return false;}public void append(VisualFrame f){fail("disabled");}};
            try{assertThrows(CancellationException.class,()->{if(first)knapsack.execute(input,interrupting);else lcs.execute(strings,interrupting);});}finally{Thread.interrupted();}
            var sink=new FrameTraceSink(4);assertThrows(TraceLimitExceededException.class,()->{if(first)knapsack.execute(input,sink);else lcs.execute(strings,sink);});assertTrue(sink.frames().isEmpty());
        }
        assertDoesNotThrow(()->knapsack.execute(input,new FrameTraceSink()));assertDoesNotThrow(()->lcs.execute(strings,new FrameTraceSink()));
    }
    private static void assertFrames(FrameTraceSink sink,AlgorithmRun run,int updates){
        var frames=sink.frames();assertEquals(EventKind.INITIAL,frames.getFirst().eventKind());assertEquals(EventKind.DONE,frames.getLast().eventKind());assertEquals(run.metrics(),frames.getLast().metrics());
        assertTrue(((TableFrame)frames.getFirst()).values().stream().flatMap(Collection::stream).allMatch(v->v==-1));long count=0;
        for(var frame:frames){if(frame.eventKind().name().equals("DP_UPDATE"))count++;assertEquals(count,frame.metrics().dpCellsUpdated());assertFalse(frame.explanation().contains("示意"));}
        assertEquals(updates,count);assertTrue(frames.stream().anyMatch(f->f.eventKind()==EventKind.BACKTRACK));
    }
    private static KnapsackInput knapsack(int capacity,int[] weights,int[] values){var items=new ArrayList<KnapsackItem>();for(int i=0;i<weights.length;i++)items.add(new KnapsackItem(weights[i],values[i]));return new KnapsackInput(items,capacity);}
    private static String randomString(Random random){var text=new StringBuilder();for(int n=1+random.nextInt(8);n>0;n--)text.append("ABC".charAt(random.nextInt(3)));return text.toString();}
    private static boolean isSubsequence(String small,String text){int i=0;for(int j=0;j<text.length()&&i<small.length();j++)if(text.charAt(j)==small.charAt(i))i++;return i==small.length();}
}
