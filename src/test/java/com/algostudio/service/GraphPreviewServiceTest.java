package com.algostudio.service;

import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.frame.*;
import com.algostudio.model.graph.*;
import com.algostudio.playback.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GraphPreviewServiceTest {
    @Test void previewHasCompleteImmutableStyleFramesWithoutTraversalMetrics() {
        var graph = GraphExamples.evaluationGraph();
        var trace = new GraphPreviewService().generate(graph, "A");
        assertEquals("graph-preview", trace.algorithmId());
        assertEquals(5, trace.frames().size());
        assertEquals(EventKind.INITIAL, trace.frames().getFirst().eventKind());
        assertEquals(EventKind.DONE, trace.frames().getLast().eventKind());
        for (var visual : trace.frames()) {
            var frame = assertInstanceOf(GraphFrame.class, visual);
            assertSame(graph, frame.graph()); assertEquals(AlgorithmMetrics.ZERO, frame.metrics());
            assertTrue(frame.explanation().contains("示意"));
        }
        var selected = (GraphFrame) trace.frames().get(1);
        assertEquals("A", selected.currentNode()); assertEquals(List.of("A"), selected.frontier());
        assertEquals(FrontierKind.QUEUE, selected.frontierKind());
        assertEquals(graph.edgeBetween("A", "B").orElseThrow(), ((GraphFrame) trace.frames().get(2)).activeEdge());
        assertEquals(FrontierKind.STACK, ((GraphFrame) trace.frames().get(3)).frontierKind());
        assertTrue(((GraphFrame) trace.frames().getFirst()).visited().isEmpty());
        assertTrue(((GraphFrame) trace.frames().getLast()).frontier().isEmpty());
    }

    @Test void isolatedStartAndAllExampleStartsAreValidAndUnknownStartRejected() {
        var service = new GraphPreviewService();
        var isolated = service.generate(GraphExamples.evaluationGraph(), "F");
        var edge = (GraphFrame) isolated.frames().get(2);
        assertNull(edge.activeEdge()); assertTrue(edge.explanation().contains("孤立"));
        for (var example : GraphExamples.all()) for (var node : example.graph().nodes())
            assertEquals(5, service.generate(example.graph(), node.id()).frames().size());
        assertThrows(IllegalArgumentException.class, () -> service.generate(GraphExamples.evaluationGraph(), "X"));
    }

    @Test void genericPlayerRestoresExactGraphFrameAndIgnoresOldTicksAfterReload() {
        var service = new GraphPreviewService();
        var trace = service.generate(GraphExamples.evaluationGraph(), "A");
        var clock = new ManualClock();
        var player = new PlaybackController(clock, ignored -> { });
        try {
            player.load(trace);
            for (int round = 0; round < 20; round++) {
                for (int i = 0; i < 4; i++) player.stepForward();
                assertEquals(PlaybackState.FINISHED, player.snapshot().state());
                for (int i = 0; i < 4; i++) player.stepBackward();
                assertSame(trace.frames().getFirst(), player.snapshot().frame());
            }
            player.play(); Runnable oldTick = clock.tick;
            player.load(service.generate(GraphExamples.evaluationGraph(), "F")); oldTick.run();
            assertEquals(0, player.snapshot().stepIndex());
            assertTrue(player.snapshot().frame().explanation().contains("F"));
        } finally { player.dispose(); }
        assertNull(clock.tick);
    }

    private static class ManualClock implements PlaybackClock {
        Runnable tick;
        public void start(Runnable action) { tick = action; }
        public void stop() { tick = null; }
        public void setSpeed(double speed) { }
        public void dispose() { tick = null; }
    }
}
