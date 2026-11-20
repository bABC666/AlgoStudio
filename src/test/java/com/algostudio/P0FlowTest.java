package com.algostudio;

import com.algostudio.algorithm.AlgorithmRegistry;
import com.algostudio.algorithm.sort.SortAlgorithm;
import com.algostudio.model.frame.ArrayFrame;
import com.algostudio.model.input.ArrayInput;
import com.algostudio.model.output.SortedArrayOutput;
import com.algostudio.model.benchmark.BenchmarkRequest;
import com.algostudio.playback.*;
import com.algostudio.service.*;
import com.algostudio.trace.TraceLimitExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Arrays;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** 无桌面集成：真实输入/算法/轨迹/播放器/性能报告；只有播放时钟由测试手动推进。 */
class P0FlowTest {
    static Stream<SortAlgorithm> algorithms() { return AlgorithmRegistry.sortingAlgorithms().stream(); }

    @ParameterizedTest @MethodSource("algorithms")
    void parsedInputTracePlaybackRollbackAndBenchmarkRemainIsolated(SortAlgorithm algorithm) {
        ArrayInput input = new InputValidator().parseArray("5, 2 4, 1, 3");
        int[] original = input.values(), expected = original.clone(); Arrays.sort(expected);
        var trace = new TraceService().generate(algorithm, input);
        var clock = new ManualClock();
        var playback = new PlaybackController(clock, ignored -> { });
        try {
            playback.load(trace);
            var first = playback.snapshot().frame();
            int steps = Math.min(10, trace.frames().size() - 1);
            for (int i = 0; i < steps; i++) playback.stepForward();
            for (int i = 0; i < steps; i++) playback.stepBackward();
            assertSame(first, playback.snapshot().frame());
            assertEquals(0, playback.snapshot().stepIndex());
            playback.play();
            Runnable oldTick = clock.tick;
            for (int i = 1; i < trace.frames().size(); i++) clock.tick.run();
            assertEquals(PlaybackState.FINISHED, playback.snapshot().state());
            assertNull(clock.tick);
            assertArrayEquals(expected, ((ArrayFrame) playback.snapshot().frame()).values());
            assertArrayEquals(expected, ((SortedArrayOutput) trace.run().output()).sortedValues());
            assertEquals(trace.run().metrics(), playback.snapshot().frame().metrics());
            var report = new BenchmarkService().run(new BenchmarkRequest(100, 20261102));
            assertEquals(AlgorithmRegistry.sortingAlgorithms().size(), report.results().size());
            assertArrayEquals(original, input.values());
            assertArrayEquals(original, ((ArrayFrame) first).values());
            playback.load(trace);
            oldTick.run();
            assertEquals(0, playback.snapshot().stepIndex(), "旧回调不能推进重新载入的轨迹");
            assertSame(first, playback.snapshot().frame());
        } finally { playback.dispose(); }
        assertTrue(clock.disposed);
    }

    @Test void invalidInputAndFrameLimitFailCleanlyThenValidInputRecovers() {
        var validator = new InputValidator();
        var playback = new PlaybackController(new ManualClock(), ignored -> { });
        SortAlgorithm algorithm = AlgorithmRegistry.sortingAlgorithms().getFirst();
        try {
            var invalid = assertThrows(IllegalArgumentException.class, () -> validator.parseArray("1,2,a,4,5"));
            playback.fail(invalid.getMessage());
            assertEquals(PlaybackState.ERROR, playback.snapshot().state());
            assertNull(playback.snapshot().frame());
            var input = validator.parseArray("5,2,4,1,3");
            assertThrows(TraceLimitExceededException.class, () -> new TraceService(2).generate(algorithm, input));
            playback.load(new TraceService().generate(algorithm, input));
            assertEquals(PlaybackState.READY, playback.snapshot().state());
            playback.stepForward();
            assertEquals(1, playback.snapshot().stepIndex());
        } finally { playback.dispose(); }
    }

    private static final class ManualClock implements PlaybackClock {
        Runnable tick;
        boolean disposed;
        public void start(Runnable action) { tick = action; }
        public void stop() { tick = null; }
        public void setSpeed(double speed) { }
        public void dispose() { disposed = true; tick = null; }
    }
}
