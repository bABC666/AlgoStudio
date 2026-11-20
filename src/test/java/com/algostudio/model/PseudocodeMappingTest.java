package com.algostudio.model;

import com.algostudio.algorithm.*;
import com.algostudio.model.dp.KnapsackItem;
import com.algostudio.model.frame.*;
import com.algostudio.model.input.*;
import com.algostudio.model.pseudocode.PseudocodeCatalog;
import com.algostudio.model.table.TableExample;
import com.algostudio.service.*;
import com.algostudio.trace.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PseudocodeMappingTest {
    private <I extends AlgorithmInput> ExecutionTrace trace(Algorithm<I> algorithm,I input){
        var sink=new FrameTraceSink();var run=algorithm.execute(input,sink);
        var disabled=new TraceSink(){public boolean enabled(){return false;}public void append(VisualFrame frame){fail("NoOp cannot append");}};
        assertEquals(run.metrics(),algorithm.execute(input,disabled).metrics());
        return new ExecutionTrace(algorithm.descriptor().id(),"fixture",sink.frames(),run);
    }
    private List<ExecutionTrace> allTraces(){
        var traces=new ArrayList<ExecutionTrace>();
        for(var algorithm:AlgorithmRegistry.sortingAlgorithms())traces.add(trace(algorithm,new ArrayInput(new int[]{5,2,4,1,3})));
        for(var algorithm:AlgorithmRegistry.graphAlgorithms())traces.add(trace(algorithm,new GraphTraversalInput(GraphExamples.evaluationGraph(),"A")));
        traces.add(trace(AlgorithmRegistry.knapsackAlgorithm(),DpExamples.knapsacks().getFirst().input()));
        traces.add(trace(AlgorithmRegistry.lcsAlgorithm(),new LcsInput("AB","BA")));
        return traces;
    }
    // Catches missing programs, stale IDs and mappings attached to only one of a category's algorithms.
    @Test void everyRealFrameResolvesInsideItsOwnImmutableProgram(){
        var traces=allTraces();assertEquals(8,traces.size());
        for(var trace:traces){
            var program=PseudocodeCatalog.find(trace.algorithmId()).orElseThrow();
            assertEquals(trace.algorithmId(),program.algorithmId());
            assertFalse(program.name().isBlank());
            assertThrows(UnsupportedOperationException.class,()->program.lines().clear());
            for(var frame:trace.frames()){
                assertNotNull(frame.pseudoLineId(),trace.algorithmId()+" "+frame.eventKind());
                assertTrue(program.lineNumber(frame.pseudoLineId()).isPresent(),trace.algorithmId()+" "+frame.pseudoLineId());
            }
            assertEquals(1,program.lineNumber(trace.frames().getFirst().pseudoLineId()).orElseThrow());
            assertEquals(program.lines().size(),program.lineNumber(trace.frames().getLast().pseudoLineId()).orElseThrow());
        }
    }
    @Test void sortAndGraphHighlightsFollowTheActualRecordedOperation(){
        for(var trace:allTraces())if(!Set.of("knapsack-01","lcs").contains(trace.algorithmId()))
            for(var frame:trace.frames())assertEquals(frame.eventKind().name(),frame.pseudoLineId(),trace.algorithmId());
        var merge=allTraces().get(2);
        assertEquals(30,merge.frames().size());
        assertEquals(new AlgorithmMetrics(8,12,0,0,0,0,0),merge.run().metrics());
    }
    @Test void knapsackMarksTooHeavyFitTakeSkipAndBoundaryFromActualState(){
        var input=new KnapsackInput(List.of(new KnapsackItem(2,3)),2);
        var frames=trace(AlgorithmRegistry.knapsackAlgorithm(),input).frames();
        assertEquals("UPDATE_SKIP",cell(frames,EventKind.DP_UPDATE,1,1).pseudoLineId());
        assertEquals("UPDATE_FIT",cell(frames,EventKind.DP_UPDATE,1,2).pseudoLineId());
        assertEquals("TRACE_TAKE",cell(frames,EventKind.BACKTRACK,1,2).pseudoLineId());
        assertEquals("TRACE_END",cell(frames,EventKind.BACKTRACK,0,0).pseudoLineId());
        var skip=trace(AlgorithmRegistry.knapsackAlgorithm(),new KnapsackInput(input.items(),1)).frames();
        assertEquals("TRACE_SKIP",cell(skip,EventKind.BACKTRACK,1,1).pseudoLineId());
    }
    @Test void lcsMarksMatchingDifferentUpTieLeftAndBoundaryWithoutUsingFutureResult(){
        var frames=trace(AlgorithmRegistry.lcsAlgorithm(),new LcsInput("AB","BA")).frames();
        assertEquals("UPDATE_DIFFERENT",cell(frames,EventKind.DP_UPDATE,1,1).pseudoLineId());
        assertEquals("UPDATE_MATCH",cell(frames,EventKind.DP_UPDATE,1,2).pseudoLineId());
        assertEquals("TRACE_UP",cell(frames,EventKind.BACKTRACK,2,2).pseudoLineId());
        assertEquals("TRACE_MATCH",cell(frames,EventKind.BACKTRACK,1,2).pseudoLineId());
        assertEquals("TRACE_END",cell(frames,EventKind.BACKTRACK,0,1).pseudoLineId());
        var left=trace(AlgorithmRegistry.lcsAlgorithm(),new LcsInput("A","AB")).frames();
        assertEquals("TRACE_LEFT",cell(left,EventKind.BACKTRACK,1,2).pseudoLineId());
    }
    private TableFrame cell(List<VisualFrame> frames,EventKind event,int row,int column){
        return frames.stream().filter(f->f.eventKind()==event).map(f->(TableFrame)f)
                .filter(f->f.currentCell()!=null&&f.currentCell().row()==row&&f.currentCell().column()==column).findFirst().orElseThrow();
    }
    @Test void previewsIncludingDoneRemainUnmappedAndUnknownIdsNeverPickAnArbitraryLine(){
        var graph=new GraphPreviewService().generate(GraphExamples.evaluationGraph(),"A");
        var table=new TablePreviewService().generate(TableExample.KNAPSACK_SMALL);
        for(var preview:List.of(graph,table)){
            assertTrue(PseudocodeCatalog.find(preview.algorithmId()).isEmpty());
            preview.frames().forEach(frame->assertNull(frame.pseudoLineId()));
        }
        assertTrue(PseudocodeCatalog.find(null).isEmpty());assertTrue(PseudocodeCatalog.find("unknown").isEmpty());
        var bubble=PseudocodeCatalog.find("bubble-sort").orElseThrow();
        assertTrue(bubble.lineNumber(null).isEmpty());assertTrue(bubble.lineNumber("WRITE").isEmpty());
    }
    @Test void programCopiesLinesAndRejectsAmbiguousLineIdentifiers(){
        var lines=new ArrayList<>(List.of(new PseudocodeCatalog.Line("A","first")));
        var program=new PseudocodeCatalog.Program("sample","sample",lines);lines.clear();
        assertEquals(1,program.lineNumber("A").orElseThrow());
        assertThrows(IllegalArgumentException.class,()->new PseudocodeCatalog.Program("sample","sample",List.of(
                new PseudocodeCatalog.Line("A","one"),new PseudocodeCatalog.Line("A","two"))));
        assertThrows(IllegalArgumentException.class,()->new PseudocodeCatalog.Line(" ","text"));
    }
}
