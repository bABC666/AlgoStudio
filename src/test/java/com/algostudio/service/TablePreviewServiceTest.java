package com.algostudio.service;

import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.frame.*;
import com.algostudio.model.table.*;
import com.algostudio.model.output.TablePreviewOutput;
import com.algostudio.playback.*;
import com.algostudio.trace.TraceLimitExceededException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TablePreviewServiceTest {
    @Test void everyLayoutHasFullImmutableFramesAndHonestPreviewMetadata() {
        for (var example : TableExample.values()) {
            var trace = new TablePreviewService().generate(example);
            assertEquals(6, trace.frames().size()); assertEquals("table-preview", trace.algorithmId());
            assertInstanceOf(TablePreviewOutput.class, trace.run().output()); assertEquals(AlgorithmMetrics.ZERO, trace.run().metrics());
            var initial = (TableFrame) trace.frames().getFirst(); var last = (TableFrame) trace.frames().getLast();
            assertEquals(EventKind.INITIAL, initial.eventKind()); assertEquals(EventKind.DONE, last.eventKind());
            assertEquals(example.rows(), last.values().size()); assertEquals(example.columns(), last.values().getFirst().size());
            for (var value : trace.frames()) { var frame = (TableFrame) value; assertTrue(frame.explanation().contains("示意")); assertEquals(AlgorithmMetrics.ZERO, frame.metrics()); }
            assertTrue(initial.values().stream().flatMap(java.util.Collection::stream).allMatch(n -> n == -1));
            assertEquals(0, ((TableFrame) trace.frames().get(1)).values().getFirst().getLast());
            assertEquals(1, last.values().getLast().getLast()); assertEquals(-1, initial.values().getLast().getLast());
            assertFalse(((TableFrame) trace.frames().get(2)).dependencies().isEmpty()); assertFalse(last.path().isEmpty());
        }
    }
    @Test void maximumLayoutsIncludeBoundaryRowsAndColumns() {
        var service = new TablePreviewService();
        var knapsack = (TableFrame) service.generate(TableExample.KNAPSACK_MAX).frames().getLast();
        assertEquals(9, knapsack.rowLabels().size()); assertEquals(31, knapsack.columnLabels().size()); assertEquals("30", knapsack.columnLabels().getLast());
        var lcs = (TableFrame) service.generate(TableExample.LCS_MAX).frames().getLast();
        assertEquals(11, lcs.rowLabels().size()); assertEquals(11, lcs.columnLabels().size()); assertTrue(lcs.rowLabels().getLast().startsWith("10:"));
    }
    @Test void genericPlayerRestoresEveryFrameAndInvalidatesOldTicksOnNewLayout() {
        var clock = new ManualClock(); var player = new PlaybackController(clock, ignored -> { }); var service = new TablePreviewService();
        try {
            var trace = service.generate(TableExample.KNAPSACK_MAX); player.load(trace);
            for (int i = 1; i < trace.frames().size(); i++) { player.stepForward(); assertSame(trace.frames().get(i), player.snapshot().frame()); }
            for (int i = trace.frames().size() - 2; i >= 0; i--) { player.stepBackward(); assertSame(trace.frames().get(i), player.snapshot().frame()); }
            player.play(); var old = clock.tick; player.load(service.generate(TableExample.LCS_MAX)); old.run();
            assertEquals(0, player.snapshot().stepIndex()); assertEquals(11, ((TableFrame) player.snapshot().frame()).values().size());
        } finally { player.dispose(); }
    }
    @Test void overflowFailsWithoutPartialSuccessAndNextRequestRecovers() {
        assertThrows(TraceLimitExceededException.class, () -> new TablePreviewService(5).generate(TableExample.KNAPSACK_MAX));
        assertDoesNotThrow(() -> new TablePreviewService().generate(TableExample.KNAPSACK_MAX));
        assertThrows(IllegalArgumentException.class, () -> new TablePreviewService(10001));
        assertThrows(IllegalArgumentException.class, () -> new TablePreviewService(0));
    }
    private static class ManualClock implements PlaybackClock {
        Runnable tick;
        public void start(Runnable action) { tick=action; } public void stop() { tick=null; } public void setSpeed(double speed) { } public void dispose() { tick=null; }
    }
}
