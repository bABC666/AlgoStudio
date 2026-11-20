package com.algostudio.trace;

import com.algostudio.algorithm.AlgorithmRun;
import com.algostudio.algorithm.sort.BubbleSort;
import com.algostudio.algorithm.sort.QuickSort;
import com.algostudio.algorithm.sort.SortAlgorithm;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.ExecutionTrace;
import com.algostudio.model.frame.ArrayFrame;
import com.algostudio.model.frame.EventKind;
import com.algostudio.model.frame.VisualFrame;
import com.algostudio.model.input.ArrayInput;
import com.algostudio.model.output.SortedArrayOutput;
import com.algostudio.service.TraceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SortingTraceTest {
    static Stream<SortAlgorithm> algorithms() {
        return Stream.of(new BubbleSort(), new QuickSort());
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void traceStartsAtUntouchedInputAndEndsAtSortedOutput(SortAlgorithm algorithm) {
        ExecutionTrace trace = new TraceService().generate(algorithm, new ArrayInput(new int[]{5, 2, 4, 1, 3}));
        ArrayFrame initial = assertInstanceOf(ArrayFrame.class, trace.frames().getFirst());
        ArrayFrame done = assertInstanceOf(ArrayFrame.class, trace.frames().getLast());
        assertEquals(algorithm.descriptor().id(), trace.algorithmId());
        assertEquals(EventKind.INITIAL, initial.eventKind());
        assertEquals(AlgorithmMetrics.ZERO, initial.metrics());
        assertArrayEquals(new int[]{5, 2, 4, 1, 3}, initial.values());
        assertEquals(EventKind.DONE, done.eventKind());
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, done.values());
        assertArrayEquals(done.values(), assertInstanceOf(SortedArrayOutput.class, trace.run().output()).sortedValues());
        assertEquals(trace.run().metrics(), done.metrics());
        assertThrows(UnsupportedOperationException.class, () -> trace.frames().clear());
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void evenEmptyAndSingletonInputsHaveBothBoundaryFrames(SortAlgorithm algorithm) {
        for (int[] input : new int[][]{{}, {7}}) {
            ExecutionTrace trace = new TraceService().generate(algorithm, new ArrayInput(input));
            assertEquals(2, trace.frames().size());
            assertEquals(EventKind.INITIAL, trace.frames().getFirst().eventKind());
            assertEquals(EventKind.DONE, trace.frames().getLast().eventKind());
            assertEquals(AlgorithmMetrics.ZERO, trace.run().metrics());
        }
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void framesSurviveLaterWorkMutationsAndAccessorMutations(SortAlgorithm algorithm) {
        int[] work = {3, 1, 2};
        FrameTraceSink sink = new FrameTraceSink();
        algorithm.sortInPlace(work, sink);
        ArrayFrame initial = assertInstanceOf(ArrayFrame.class, sink.frames().getFirst());
        work[0] = 99;
        initial.values()[1] = 88;
        assertArrayEquals(new int[]{3, 1, 2}, initial.values());
        assertArrayEquals(new int[]{1, 2, 3}, assertInstanceOf(ArrayFrame.class, sink.frames().getLast()).values());
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void countersAndEventExplanationsMatchEveryRecordedOperation(SortAlgorithm algorithm) {
        ExecutionTrace trace = new TraceService().generate(algorithm, new ArrayInput(new int[]{3, 1, 2}));
        AlgorithmMetrics previousMetrics = AlgorithmMetrics.ZERO;
        int[] previousValues = {3, 1, 2};
        for (VisualFrame visual : trace.frames()) {
            ArrayFrame frame = assertInstanceOf(ArrayFrame.class, visual);
            AlgorithmMetrics current = frame.metrics();
            assertTrue(current.comparisons() >= previousMetrics.comparisons());
            assertTrue(current.writes() >= previousMetrics.writes());
            assertTrue(current.swaps() >= previousMetrics.swaps());
            assertFalse(frame.explanation().isBlank());
            int[] values = frame.values();
            if (frame.eventKind() == EventKind.COMPARE) {
                assertArrayEquals(previousValues, values);
                assertEquals(previousMetrics.comparisons() + 1, current.comparisons());
                assertEquals(previousMetrics.swaps(), current.swaps());
                assertTrue(frame.explanation().contains(Integer.toString(frame.firstIndex())));
                assertTrue(frame.explanation().contains(Integer.toString(values[frame.firstIndex()])));
                assertTrue(frame.explanation().contains(Integer.toString(values[frame.secondIndex()])));
            } else if (frame.eventKind() == EventKind.SWAP) {
                assertNotEquals(frame.firstIndex(), frame.secondIndex());
                assertEquals(previousValues[frame.secondIndex()], values[frame.firstIndex()]);
                assertEquals(previousValues[frame.firstIndex()], values[frame.secondIndex()]);
                for (int index = 0; index < values.length; index++) {
                    if (index != frame.firstIndex() && index != frame.secondIndex()) {
                        assertEquals(previousValues[index], values[index]);
                    }
                }
                assertEquals(previousMetrics.swaps() + 1, current.swaps());
                assertEquals(previousMetrics.writes() + 2, current.writes());
                assertEquals(previousMetrics.comparisons(), current.comparisons());
            } else {
                assertArrayEquals(previousValues, values);
                assertEquals(previousMetrics, current);
            }
            if (frame.pivotIndex() >= 0) {
                assertTrue(frame.pivotIndex() >= frame.rangeStart());
                assertTrue(frame.pivotIndex() < frame.rangeEnd());
            }
            previousMetrics = current;
            previousValues = values;
        }
    }

    @Test
    void quickSortRecordsActualPivotAndMovesItsHighlightWithTheFinalSwap() {
        ExecutionTrace trace = new TraceService().generate(new QuickSort(), new ArrayInput(new int[]{3, 1, 2}));
        ArrayFrame pivot = trace.frames().stream().map(frame -> (ArrayFrame) frame)
                .filter(frame -> frame.eventKind() == EventKind.PIVOT).findFirst().orElseThrow();
        assertEquals(2, pivot.pivotIndex());
        assertEquals(2, pivot.values()[pivot.pivotIndex()]);
        ArrayFrame finalPivotSwap = trace.frames().stream().map(frame -> (ArrayFrame) frame)
                .filter(frame -> frame.eventKind() == EventKind.SWAP && frame.secondIndex() == 2)
                .findFirst().orElseThrow();
        assertEquals(1, finalPivotSwap.pivotIndex());
        assertEquals(2, finalPivotSwap.values()[finalPivotSwap.pivotIndex()]);
    }

    @Test
    void collectorRejectsFrame10001ClearsPartialFramesAndCannotBeReused() {
        FrameTraceSink sink = new FrameTraceSink();
        ArrayFrame frame = initialFrame();
        for (int count = 0; count < 10_000; count++) {
            sink.append(frame);
        }
        List<VisualFrame> previousSnapshot = sink.frames();
        assertEquals(10_000, previousSnapshot.size());
        TraceLimitExceededException failure = assertThrows(TraceLimitExceededException.class, () -> sink.append(frame));
        assertTrue(failure.getMessage().contains("10000"));
        assertTrue(sink.frames().isEmpty());
        assertEquals(10_000, previousSnapshot.size());
        assertThrows(IllegalStateException.class, () -> sink.append(frame));
    }

    @Test
    void traceServiceThrowsInsteadOfPublishingATruncatedRunAndNextRequestCanSucceed() {
        TraceService service = new TraceService(3);
        assertThrows(TraceLimitExceededException.class,
                () -> service.generate(new BubbleSort(), new ArrayInput(new int[]{3, 2, 1})));
        ExecutionTrace recovered = service.generate(new BubbleSort(), new ArrayInput(new int[]{7}));
        assertEquals(2, recovered.frames().size());
        assertEquals(EventKind.DONE, recovered.frames().getLast().eventKind());
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void disabledSinkNeverReceivesAFrame(SortAlgorithm algorithm) {
        TraceSink disabled = new TraceSink() {
            @Override public boolean enabled() { return false; }
            @Override public void append(VisualFrame frame) { fail("禁用轨迹时不能构帧或调用 append"); }
        };
        int[] work = {5, 2, 4, 1, 3};
        algorithm.sortInPlace(work, disabled);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, work);
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void noOpAllocationsStayConstantAsArraySizeAndOperationCountGrow(SortAlgorithm algorithm) {
        var platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof com.sun.management.ThreadMXBean);
        var bean = (com.sun.management.ThreadMXBean) platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        int[] small = new int[16];
        int[] large = new int[512];
        for (int warmup = 0; warmup < 200; warmup++) {
            algorithm.sortInPlace(small, NoOpTraceSink.INSTANCE);
            algorithm.sortInPlace(large, NoOpTraceSink.INSTANCE);
        }
        long threadId = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(threadId);
        for (int iteration = 0; iteration < 20; iteration++) {
            algorithm.sortInPlace(small, NoOpTraceSink.INSTANCE);
        }
        long smallBytes = bean.getThreadAllocatedBytes(threadId) - before;
        before = bean.getThreadAllocatedBytes(threadId);
        for (int iteration = 0; iteration < 20; iteration++) {
            algorithm.sortInPlace(large, NoOpTraceSink.INSTANCE);
        }
        long largeBytes = bean.getThreadAllocatedBytes(threadId) - before;
        System.out.printf("NoOp allocation %s: small=%d B, large=%d B%n", algorithm.descriptor().id(), smallBytes, largeBytes);
        assertTrue(largeBytes <= smallBytes + 8192,
                () -> "无轨迹模式分配量不应随输入规模增长：small=" + smallBytes + ", large=" + largeBytes);
    }

    @Test
    void executionTraceCopiesItsListAndRejectsMissingBoundaryFramesOrMismatchedMetrics() {
        ArrayFrame initial = initialFrame();
        ArrayFrame done = new ArrayFrame(new int[]{1}, EventKind.DONE, -1, -1, -1, 0, 1,
                AlgorithmMetrics.ZERO, "完成", null);
        AlgorithmRun run = new AlgorithmRun(new SortedArrayOutput(new int[]{1}), AlgorithmMetrics.ZERO);
        List<VisualFrame> source = new ArrayList<>(List.of(initial, done));
        ExecutionTrace trace = new ExecutionTrace("bubble-sort", "[1]", source, run);
        source.clear();
        assertEquals(2, trace.frames().size());
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionTrace("bubble-sort", "[1]", List.of(done), run));
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionTrace("bubble-sort", "[1]", List.of(done, done), run));
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionTrace("bubble-sort", "[1]", List.of(initial, initial), run));
        AlgorithmRun incorrectMetrics = new AlgorithmRun(new SortedArrayOutput(new int[]{1}),
                new AlgorithmMetrics(1, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionTrace("bubble-sort", "[1]", List.of(initial, done), incorrectMetrics));
    }

    @Test
    void rejectsInvalidCapacityAndFrameHighlights() {
        assertThrows(IllegalArgumentException.class, () -> new FrameTraceSink(0));
        assertThrows(IllegalArgumentException.class, () -> new FrameTraceSink(10_001));
        assertThrows(IllegalArgumentException.class, () -> new ArrayFrame(new int[]{1}, EventKind.COMPARE,
                1, -1, -1, 0, 1, AlgorithmMetrics.ZERO, "非法下标", null));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void traceServiceAcceptsFiftyItemsAndRejectsFiftyOneWithoutLimitingTheSortingCore(SortAlgorithm algorithm) {
        TraceService service = new TraceService();
        ExecutionTrace accepted = service.generate(algorithm, new ArrayInput(new int[50]));
        assertEquals(50, assertInstanceOf(ArrayFrame.class, accepted.frames().getFirst()).values().length);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.generate(algorithm, new ArrayInput(new int[51])));
        assertTrue(error.getMessage().contains("50"));
        int[] largerCoreInput = {51, 50, 49, 48, 47, 46, 45, 44, 43, 42, 41, 40, 39,
                38, 37, 36, 35, 34, 33, 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22,
                21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
        algorithm.sortInPlace(largerCoreInput, NoOpTraceSink.INSTANCE);
        assertArrayEquals(java.util.stream.IntStream.rangeClosed(1, 51).toArray(), largerCoreInput);
    }

    private ArrayFrame initialFrame() {
        return new ArrayFrame(new int[]{1}, EventKind.INITIAL, -1, -1, -1, 0, 1,
                AlgorithmMetrics.ZERO, "初始状态", null);
    }
}
