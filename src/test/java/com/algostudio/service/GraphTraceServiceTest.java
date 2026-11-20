package com.algostudio.service;

import com.algostudio.algorithm.AlgorithmRegistry;
import com.algostudio.model.frame.*;
import com.algostudio.model.graph.*;
import com.algostudio.model.input.*;
import com.algostudio.playback.*;
import com.algostudio.trace.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphTraceServiceTest {
    @Test void dijkstraIsRegisteredAndItsDistanceSnapshotsUseTheGenericPlayer() {
        var candidates = AlgorithmRegistry.graphAlgorithms().stream().filter(a -> a.descriptor().id().equals("dijkstra")).toList();
        assertEquals(1, candidates.size(), "图算法菜单须注册真实 Dijkstra");
        var trace = new GraphTraceService().generate(candidates.getFirst(), new GraphTraversalInput(GraphExamples.evaluationGraph(), "A"));
        var player = new PlaybackController(new ManualClock(), ignored -> { });
        try {
            player.load(trace); var initial = player.snapshot().frame();
            for (int i = 0; i < 10; i++) player.stepForward();
            assertEquals(3L, ((GraphFrame) player.snapshot().frame()).distances().get("C"));
            for (int i = 0; i < 10; i++) player.stepBackward();
            assertSame(initial, player.snapshot().frame()); assertEquals(Long.MAX_VALUE, ((GraphFrame) initial).distances().get("C"));
        } finally { player.dispose(); }
    }
    @Test void realTraversalsProduceCompleteTracesForGenericPlaybackAndRollback() {
        for (var algorithm : AlgorithmRegistry.traversalAlgorithms()) {
            var input = new GraphTraversalInput(GraphExamples.evaluationGraph(), "A");
            var trace = new GraphTraceService().generate(algorithm, input);
            assertEquals(algorithm.descriptor().id(), trace.algorithmId());
            assertEquals(EventKind.INITIAL, trace.frames().getFirst().eventKind());
            assertEquals(EventKind.DONE, trace.frames().getLast().eventKind());
            var clock = new ManualClock(); var player = new PlaybackController(clock, ignored -> { });
            try {
                player.load(trace); var initial = player.snapshot().frame();
                for (int i = 0; i < 10; i++) player.stepForward();
                for (int i = 0; i < 10; i++) player.stepBackward();
                assertSame(initial, player.snapshot().frame());
                player.play(); Runnable old = clock.tick;
                player.load(new GraphTraceService().generate(algorithm, new GraphTraversalInput(input.graph(), "F")));
                old.run(); assertEquals(0, player.snapshot().stepIndex());
                assertEquals(0, player.snapshot().frame().metrics().nodesVisited());
            } finally { player.dispose(); }
        }
    }

    @Test void overflowFailsClearsCollectorAndNextIndependentRequestRecovers() {
        var algorithm = AlgorithmRegistry.traversalAlgorithms().getFirst();
        var input = new GraphTraversalInput(GraphExamples.evaluationGraph(), "A");
        var sink = new FrameTraceSink(4);
        assertThrows(TraceLimitExceededException.class, () -> algorithm.execute(input, sink));
        assertTrue(sink.frames().isEmpty());
        assertThrows(TraceLimitExceededException.class, () -> new GraphTraceService(4).generate(algorithm, input));
        assertDoesNotThrow(() -> new GraphTraceService().generate(algorithm, input));
        assertThrows(IllegalArgumentException.class, () -> new GraphTraceService(10001));
    }

    @Test void guiBoundsRejectOversizedGraphWithoutRestrictingCoreAlgorithms() {
        var algorithm = AlgorithmRegistry.traversalAlgorithms().getFirst();
        var nodes = new ArrayList<GraphNode>();
        for (int i = 0; i < 9; i++) nodes.add(new GraphNode("N" + i, .5, .5));
        var large = new GraphTraversalInput(new GraphInput(nodes, List.of()), "N0");
        assertThrows(IllegalArgumentException.class, () -> new GraphTraceService().generate(algorithm, large));
        assertDoesNotThrow(() -> algorithm.execute(large, NoOpTraceSink.INSTANCE));
        List<GraphEdge> edges = new ArrayList<>();
        for (int i = 0; i < 6; i++) for (int j = i + 1; j < 6; j++) edges.add(new GraphEdge("N" + i, "N" + j, 1));
        var dense = new GraphTraversalInput(new GraphInput(nodes.subList(0, 6), edges), "N0");
        assertThrows(IllegalArgumentException.class, () -> new GraphTraceService().generate(algorithm, dense));
    }
    private static class ManualClock implements PlaybackClock {
        Runnable tick;
        public void start(Runnable action) { tick = action; }
        public void stop() { tick = null; }
        public void setSpeed(double speed) { }
        public void dispose() { tick = null; }
    }
}
