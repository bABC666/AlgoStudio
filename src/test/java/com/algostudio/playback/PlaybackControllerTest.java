package com.algostudio.playback;

import com.algostudio.algorithm.sort.BubbleSort;
import com.algostudio.algorithm.sort.QuickSort;
import com.algostudio.model.ExecutionTrace;
import com.algostudio.model.frame.ArrayFrame;
import com.algostudio.model.input.ArrayInput;
import com.algostudio.service.TraceService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackControllerTest {
    private final TestClock clock = new TestClock();
    private final List<PlaybackSnapshot> observations = new ArrayList<>();
    private final PlaybackController player = new PlaybackController(clock, observations::add);

    private ExecutionTrace bubble() {
        return new TraceService().generate(new BubbleSort(), new ArrayInput(new int[]{5, 2, 4, 1, 3}));
    }

    @Test
    void emptyCommandsRemainSafeWithoutStartingAClock() {
        player.play();
        player.pause();
        player.stepForward();
        player.stepBackward();
        player.reset();
        assertEquals(PlaybackState.EMPTY, player.snapshot().state());
        assertEquals(-1, player.snapshot().stepIndex());
        assertEquals(0, player.snapshot().frameCount());
        assertNull(player.snapshot().frame());
        assertEquals(0, clock.starts);
    }

    @Test
    void loadStartsAtTheExactInitialFrameAndPublishesTheSameSnapshot() {
        ExecutionTrace trace = bubble();
        player.load(trace);
        assertEquals(PlaybackState.READY, player.snapshot().state());
        assertEquals(0, player.snapshot().stepIndex());
        assertEquals(trace.frames().size(), player.snapshot().frameCount());
        assertSame(trace.frames().getFirst(), player.snapshot().frame());
        assertEquals(player.snapshot(), observations.getLast());
    }

    @Test
    void tenStepsForwardAndBackRestoreTheWholeHistoricalFrame() {
        player.load(bubble());
        var initial = player.snapshot().frame();
        for (int index = 0; index < 10; index++) player.stepForward();
        assertEquals(10, player.snapshot().stepIndex());
        for (int index = 0; index < 10; index++) player.stepBackward();
        assertEquals(0, player.snapshot().stepIndex());
        assertSame(initial, player.snapshot().frame());
        assertArrayEquals(new int[]{5, 2, 4, 1, 3}, ((ArrayFrame) player.snapshot().frame()).values());
        assertEquals(initial.explanation(), player.snapshot().frame().explanation());
        assertEquals(initial.metrics(), player.snapshot().frame().metrics());
    }

    @Test
    void stepsPausePlaybackAndBothBoundariesRemainInRange() {
        ExecutionTrace trace = bubble();
        player.load(trace);
        player.stepBackward();
        assertEquals(0, player.snapshot().stepIndex());
        player.play();
        Runnable oldTick = clock.callbacks.getLast();
        player.stepForward();
        assertEquals(PlaybackState.PAUSED, player.snapshot().state());
        assertFalse(clock.running);
        oldTick.run();
        assertEquals(1, player.snapshot().stepIndex());
        for (int index = 0; index < 100; index++) player.stepForward();
        assertEquals(trace.frames().size() - 1, player.snapshot().stepIndex());
        assertEquals(PlaybackState.FINISHED, player.snapshot().state());
        player.stepBackward();
        assertEquals(PlaybackState.PAUSED, player.snapshot().state());
        player.reset();
        assertEquals(PlaybackState.READY, player.snapshot().state());
        assertEquals(0, player.snapshot().stepIndex());
    }

    @Test
    void repeatedPlayStartsOnlyOnceAndPauseRejectsQueuedCallbacks() {
        player.load(bubble());
        player.play();
        player.play();
        player.play();
        assertEquals(1, clock.starts);
        Runnable firstRun = clock.callbacks.getFirst();
        firstRun.run();
        assertEquals(1, player.snapshot().stepIndex());
        player.pause();
        firstRun.run();
        assertEquals(PlaybackState.PAUSED, player.snapshot().state());
        assertEquals(1, player.snapshot().stepIndex());
        player.play();
        assertEquals(2, clock.starts);
        firstRun.run();
        assertEquals(1, player.snapshot().stepIndex());
        clock.callbacks.getLast().run();
        assertEquals(2, player.snapshot().stepIndex());
    }

    @Test
    void finalTickStopsAutomaticallyAndPlayAtTheEndRestartsAtZero() {
        ExecutionTrace trace = bubble();
        player.load(trace);
        player.play();
        Runnable tick = clock.callbacks.getLast();
        for (int index = 0; index < trace.frames().size() + 10; index++) tick.run();
        assertEquals(PlaybackState.FINISHED, player.snapshot().state());
        assertEquals(trace.frames().size() - 1, player.snapshot().stepIndex());
        assertFalse(clock.running);
        player.play();
        assertEquals(PlaybackState.PLAYING, player.snapshot().state());
        assertEquals(0, player.snapshot().stepIndex());
        tick.run();
        assertEquals(0, player.snapshot().stepIndex());
        clock.callbacks.getLast().run();
        assertEquals(1, player.snapshot().stepIndex());
    }

    @Test
    void loadingAnotherAlgorithmStopsAndInvalidatesThePreviousClock() {
        player.load(bubble());
        player.play();
        Runnable previous = clock.callbacks.getLast();
        previous.run();
        ExecutionTrace replacement = new TraceService().generate(new QuickSort(), new ArrayInput(new int[]{3, 1, 2}));
        player.load(replacement);
        previous.run();
        assertFalse(clock.running);
        assertEquals(PlaybackState.READY, player.snapshot().state());
        assertSame(replacement.frames().getFirst(), player.snapshot().frame());
        assertEquals(0, player.snapshot().stepIndex());
    }

    @Test
    void resetClearAndErrorInvalidateOldCallbacksAndReleaseTheTrace() {
        player.load(bubble());
        player.play();
        Runnable first = clock.callbacks.getLast();
        player.reset();
        first.run();
        assertEquals(0, player.snapshot().stepIndex());
        player.play();
        Runnable second = clock.callbacks.getLast();
        player.clear();
        second.run();
        assertNull(player.snapshot().frame());
        assertEquals(PlaybackState.EMPTY, player.snapshot().state());
        player.load(bubble());
        player.play();
        Runnable third = clock.callbacks.getLast();
        player.fail("轨迹生成失败");
        third.run();
        assertEquals(PlaybackState.ERROR, player.snapshot().state());
        assertEquals("轨迹生成失败", player.snapshot().error());
        assertNull(player.snapshot().frame());
        player.load(bubble());
        assertEquals(PlaybackState.READY, player.snapshot().state());
        assertEquals("", player.snapshot().error());
    }

    @Test
    void speedChangesDoNotRestartPlaybackOrMoveTheIndex() {
        player.load(bubble());
        player.play();
        clock.callbacks.getLast().run();
        for (double rate : new double[]{1, 2, 4, 8}) {
            player.setSpeed(rate);
            assertEquals(rate, clock.speed);
            assertEquals(rate, player.snapshot().speed());
            assertEquals(PlaybackState.PLAYING, player.snapshot().state());
            assertEquals(1, player.snapshot().stepIndex());
            assertEquals(1, clock.starts);
        }
        for (double invalid : new double[]{0, -1, 3, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> player.setSpeed(invalid));
        }
        assertEquals(8, player.snapshot().speed());
    }

    @Test
    void loadingNullClearsTheOldTraceAndCanRecoverFromError() {
        player.load(bubble());
        player.play();
        Runnable tick = clock.callbacks.getLast();
        player.load(null);
        tick.run();
        assertEquals(PlaybackState.ERROR, player.snapshot().state());
        assertNull(player.snapshot().frame());
        assertFalse(clock.running);
        player.load(bubble());
        assertEquals(PlaybackState.READY, player.snapshot().state());
    }

    @Test
    void disposeIsIdempotentStopsAndPermanentlyRejectsOldTicks() {
        player.load(bubble());
        player.play();
        Runnable tick = clock.callbacks.getLast();
        player.dispose();
        player.dispose();
        tick.run();
        player.play();
        player.reset();
        assertEquals(1, clock.disposals);
        assertFalse(clock.running);
        assertEquals(PlaybackState.EMPTY, player.snapshot().state());
        assertNull(player.snapshot().frame());
        assertThrows(IllegalStateException.class, () -> player.load(bubble()));
    }

    private static final class TestClock implements PlaybackClock {
        private final List<Runnable> callbacks = new ArrayList<>();
        private int starts;
        private int disposals;
        private boolean running;
        private double speed = 1;

        @Override public void start(Runnable callback) { starts++; running = true; callbacks.add(callback); }
        @Override public void stop() { running = false; }
        @Override public void setSpeed(double multiplier) { speed = multiplier; }
        @Override public void dispose() { running = false; disposals++; }
    }
}
