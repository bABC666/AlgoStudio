package com.algostudio.service;

import com.algostudio.algorithm.AlgorithmRegistry;
import com.algostudio.model.frame.*;
import com.algostudio.model.input.LcsInput;
import com.algostudio.model.output.*;
import com.algostudio.playback.*;
import com.algostudio.trace.TraceLimitExceededException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DpTraceServiceTest {
    @Test void registeredTypedAlgorithmsProduceCompleteRealTraces() {
        assertEquals("knapsack-01",AlgorithmRegistry.knapsackAlgorithm().descriptor().id());assertEquals("lcs",AlgorithmRegistry.lcsAlgorithm().descriptor().id());
        var service=new DpTraceService();var knapsack=service.generateKnapsack(DpExamples.knapsacks().getFirst().input());var lcs=service.generateLcs(new LcsInput("ABC","AC"));
        assertEquals(new KnapsackOutput(7,java.util.List.of(1,2),5),knapsack.run().output());assertEquals(new LcsOutput(2,"AC"),lcs.run().output());
        for(var trace:java.util.List.of(knapsack,lcs)){assertEquals(EventKind.INITIAL,trace.frames().getFirst().eventKind());assertEquals(EventKind.DONE,trace.frames().getLast().eventKind());assertEquals(trace.run().metrics(),trace.frames().getLast().metrics());}
        assertEquals(3,AlgorithmRegistry.sortingAlgorithms().size());
    }
    @Test void emptyUiStringsAndInvalidLimitsAreRejectedWhileOverflowCanRecover() {
        var service=new DpTraceService();assertThrows(IllegalArgumentException.class,()->service.generateLcs(new LcsInput("","A")));
        assertThrows(IllegalArgumentException.class,()->service.generateLcs(new LcsInput("A","")));
        assertThrows(IllegalArgumentException.class,()->new DpTraceService(10001));assertThrows(IllegalArgumentException.class,()->new DpTraceService(0));
        assertThrows(TraceLimitExceededException.class,()->new DpTraceService(4).generateKnapsack(DpExamples.knapsacks().getFirst().input()));
        assertThrows(TraceLimitExceededException.class,()->new DpTraceService(4).generateLcs(new LcsInput("ABC","AC")));
        assertDoesNotThrow(()->service.generateKnapsack(DpExamples.knapsacks().getFirst().input()));
    }
    @Test void genericPlaybackRestoresTenFramesAndRejectsOldTicksAcrossDpAlgorithms() {
        var service=new DpTraceService();var clock=new Clock();var player=new PlaybackController(clock,ignored->{});
        try{var trace=service.generateKnapsack(DpExamples.knapsacks().getFirst().input());player.load(trace);var initial=player.snapshot().frame();for(int i=0;i<10;i++)player.stepForward();for(int i=0;i<10;i++)player.stepBackward();assertSame(initial,player.snapshot().frame());player.play();var old=clock.tick;player.load(service.generateLcs(new LcsInput("ABC","AC")));old.run();assertEquals(0,player.snapshot().stepIndex());assertEquals(0,player.snapshot().frame().metrics().dpCellsUpdated());}finally{player.dispose();}
    }
    private static class Clock implements PlaybackClock{Runnable tick;public void start(Runnable action){tick=action;}public void stop(){tick=null;}public void setSpeed(double speed){}public void dispose(){tick=null;}}
}
