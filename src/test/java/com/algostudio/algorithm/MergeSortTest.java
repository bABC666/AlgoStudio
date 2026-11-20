package com.algostudio.algorithm;

import com.algostudio.algorithm.sort.MergeSort;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.frame.ArrayFrame;
import com.algostudio.model.frame.EventKind;
import com.algostudio.model.frame.VisualFrame;
import com.algostudio.model.input.ArrayInput;
import com.algostudio.model.output.SortedArrayOutput;
import com.algostudio.service.TraceService;
import com.algostudio.trace.*;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MergeSortTest {
    private final MergeSort algorithm = new MergeSort();

    @Test void equalTopLevelValuesConsumeLeftBeforeComparingTheNextPair(){
        var trace=new TraceService().generate(algorithm,new ArrayInput(new int[]{1,2,1,3}));
        var comparisons=trace.frames().stream().map(f->(ArrayFrame)f)
                .filter(f->f.eventKind()==EventKind.COMPARE&&f.rangeStart()==0&&f.rangeEnd()==4).toList();
        assertEquals(0,comparisons.get(0).firstIndex());assertEquals(2,comparisons.get(0).secondIndex());
        assertEquals(1,comparisons.get(1).firstIndex(),"平局取左后应比较原左侧的下一项");
        assertEquals(2,comparisons.get(1).secondIndex());
    }

    // Catches dropped tails, odd split errors, subtraction overflow, and input mutation.
    @Test void matchesIndependentOracleForBoundariesAndOneHundredSeededArrays() {
        for (int[] values : new int[][]{{}, {7}, {1,2,3,4,5}, {5,4,3,2,1},
                {2,2,2,2,2}, {3,1,3,2,1}, {5,2,4,1,3}, {-5,0,-1,7,-5},
                {Integer.MAX_VALUE,0,Integer.MIN_VALUE,-1,Integer.MAX_VALUE}}) verifySorted(values);
        Random random = new Random(20261102);
        for (int group = 0; group < 100; group++)
            verifySorted(random.ints(random.nextInt(101), -100, 101).toArray());
    }

    private void verifySorted(int[] original) {
        int[] before = original.clone(), expected = original.clone();
        Arrays.sort(expected);
        ArrayInput input = new ArrayInput(original);
        var run = algorithm.execute(input, NoOpTraceSink.INSTANCE);
        String context = "seed=20261102 input=" + Arrays.toString(before);
        assertArrayEquals(expected, assertInstanceOf(SortedArrayOutput.class, run.output()).sortedValues(), context);
        assertArrayEquals(before, original, context);
        assertArrayEquals(before, input.values(), context);
        int[] work = before.clone();
        assertEquals(run.metrics(), algorithm.sortInPlace(work, NoOpTraceSink.INSTANCE));
        assertArrayEquals(expected, work, context);
        assertEquals(0, run.metrics().swaps());
    }

    @Test void countsOnlyDataComparisonsAndEveryMainArrayAssignmentWithoutSwaps() {
        assertCounts(new int[]{2,1}, 1, 2);
        assertCounts(new int[]{1,1}, 1, 2);
        assertCounts(new int[]{3,1,2}, 3, 5);
        assertCounts(new int[]{}, 0, 0);
        assertCounts(new int[]{7}, 0, 0);
    }

    private void assertCounts(int[] input, long comparisons, long writes) {
        assertEquals(new AlgorithmMetrics(comparisons, writes, 0, 0, 0, 0, 0),
                algorithm.execute(new ArrayInput(input), NoOpTraceSink.INSTANCE).metrics());
    }

    @Test void recordsActualSourceComparisonsSplitRangesAndOneWritePerFrame() {
        var trace = new TraceService().generate(algorithm, new ArrayInput(new int[]{3,1,2}));
        assertEquals("merge-sort", trace.algorithmId());
        var frames = trace.frames().stream().map(frame -> assertInstanceOf(ArrayFrame.class, frame)).toList();
        assertEquals(EventKind.INITIAL, frames.getFirst().eventKind());
        assertEquals(-1, frames.getFirst().rangeMiddle());
        assertEquals(AlgorithmMetrics.ZERO, frames.getFirst().metrics());
        assertArrayEquals(new int[]{3,1,2}, frames.getFirst().values());
        assertEquals(EventKind.DIVIDE, frames.get(1).eventKind());
        assertEquals(0, frames.get(1).rangeStart());
        assertEquals(1, frames.get(1).rangeMiddle());
        assertEquals(3, frames.get(1).rangeEnd());
        int[][] expectedWrites = {{3,1,2}, {3,1,2}, {1,1,2}, {1,2,2}, {1,2,3}};
        int[][] expectedComparedIndices = {{1,2}, {0,1}, {0,2}};
        int written = 0, compared = 0;
        for (int i = 1; i < frames.size(); i++) {
            var previous = frames.get(i - 1);
            var frame = frames.get(i);
            assertTrue(frame.metrics().comparisons() >= previous.metrics().comparisons());
            assertTrue(frame.metrics().writes() >= previous.metrics().writes());
            assertEquals(0, frame.metrics().swaps());
            if (frame.eventKind() != EventKind.DONE) {
                assertTrue(frame.rangeStart() < frame.rangeMiddle());
                assertTrue(frame.rangeMiddle() < frame.rangeEnd());
            }
            if (frame.eventKind() == EventKind.COMPARE) {
                assertEquals(expectedComparedIndices[compared][0], frame.firstIndex());
                assertEquals(expectedComparedIndices[compared++][1], frame.secondIndex());
                assertArrayEquals(previous.values(), frame.values());
                assertTrue(frame.explanation().contains("值 " + frame.values()[frame.firstIndex()]));
                assertTrue(frame.explanation().contains("值 " + frame.values()[frame.secondIndex()]));
                assertEquals(previous.metrics().comparisons() + 1, frame.metrics().comparisons());
            } else if (frame.eventKind() == EventKind.WRITE) {
                assertArrayEquals(expectedWrites[written++], frame.values());
                assertEquals(previous.metrics().writes() + 1, frame.metrics().writes());
                assertEquals(previous.metrics().comparisons(), frame.metrics().comparisons());
                assertTrue(frame.firstIndex() >= frame.rangeStart() && frame.firstIndex() < frame.rangeEnd());
                assertTrue(frame.explanation().contains("值 " + frame.values()[frame.firstIndex()]));
                for (int index = 0; index < 3; index++)
                    if (index != frame.firstIndex()) assertEquals(previous.values()[index], frame.values()[index]);
            } else assertArrayEquals(previous.values(), frame.values());
        }
        assertEquals(5, written); assertEquals(3, compared);
        var done = frames.getLast();
        assertEquals(EventKind.DONE, done.eventKind()); assertEquals(-1, done.rangeMiddle());
        assertArrayEquals(new int[]{1,2,3}, done.values());
        assertEquals(trace.run().metrics(), done.metrics());
        frames.getFirst().values()[0] = 99;
        assertArrayEquals(new int[]{3,1,2}, frames.getFirst().values());
        assertThrows(UnsupportedOperationException.class, () -> trace.frames().clear());
    }

    @Test void snapshotsDoNotFollowLaterWorkAndNoOpNeverAppends() {
        int[] work = {5,2,4,1,3};
        var sink = new FrameTraceSink();
        var recorded = algorithm.sortInPlace(work, sink);
        work[0] = 999;
        assertArrayEquals(new int[]{5,2,4,1,3}, ((ArrayFrame)sink.frames().getFirst()).values());
        var disabled = new TraceSink() {
            public boolean enabled() { return false; }
            public void append(VisualFrame frame) { fail("disabled sink must not receive frames"); }
        };
        assertEquals(recorded, algorithm.sortInPlace(new int[]{5,2,4,1,3}, disabled));
    }

    @Test void noOpAllocationGrowsOnlyByTheNecessaryLinearBuffer() {
        var bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var allocation = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(allocation.isThreadAllocatedMemorySupported());
        allocation.setThreadAllocatedMemoryEnabled(true);
        int[] small = new int[10], large = new int[1000];
        for (int i = 0; i < 300; i++) {
            algorithm.sortInPlace(small, NoOpTraceSink.INSTANCE);
            algorithm.sortInPlace(large, NoOpTraceSink.INSTANCE);
        }
        long id = Thread.currentThread().threadId(), before = allocation.getThreadAllocatedBytes(id);
        for (int i = 0; i < 20; i++) algorithm.sortInPlace(small, NoOpTraceSink.INSTANCE);
        long smallBytes = allocation.getThreadAllocatedBytes(id) - before;
        before = allocation.getThreadAllocatedBytes(id);
        for (int i = 0; i < 20; i++) algorithm.sortInPlace(large, NoOpTraceSink.INSTANCE);
        long largeBytes = allocation.getThreadAllocatedBytes(id) - before;
        System.out.printf("Merge NoOp allocation: small=%d B large=%d B; one int[n] buffer/run allowed%n", smallBytes, largeBytes);
        assertTrue(largeBytes - smallBytes <= 20L * 4 * (large.length - small.length) + 32768,
                "NoOp must not allocate per-event frames, snapshots or explanations");
    }

    @Test void observesPreexistingAndMidRunInterruptWithoutClearingIt() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> algorithm.sortInPlace(new int[]{3,2,1}, NoOpTraceSink.INSTANCE));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        var sink = new TraceSink() {
            public boolean enabled() { return true; }
            public void append(VisualFrame frame) {
                if (frame.eventKind() == EventKind.COMPARE) Thread.currentThread().interrupt();
            }
        };
        try {
            assertThrows(CancellationException.class, () -> algorithm.sortInPlace(new int[]{3,2,1}, sink));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test void clearsOverflowedCollectorAndAllowsANewRequestAndFiftyItems() {
        var sink = new FrameTraceSink(3);
        assertThrows(TraceLimitExceededException.class, () -> algorithm.sortInPlace(new int[]{3,2,1}, sink));
        assertTrue(sink.frames().isEmpty());
        assertThrows(IllegalStateException.class, sink::enabled);
        var trace = new TraceService().generate(algorithm, new ArrayInput(new int[50]));
        assertTrue(trace.frames().size() < 10000);
        assertEquals(EventKind.DONE, trace.frames().getLast().eventKind());
        assertThrows(IllegalArgumentException.class,
                () -> new TraceService().generate(algorithm, new ArrayInput(new int[51])));
        for (int[] values : new int[][]{{}, {7}}) {
            var empty = new TraceService().generate(algorithm, new ArrayInput(values));
            assertEquals(2, empty.frames().size());
            assertEquals(AlgorithmMetrics.ZERO, empty.run().metrics());
        }
    }

    @Test void validatesSplitBoundaryAndKeepsLegacyFramesUnsplit() {
        var legacy = new ArrayFrame(new int[]{2,1}, EventKind.COMPARE, 0, 1, -1,
                0, 2, AlgorithmMetrics.ZERO, "legacy", null);
        assertEquals(-1, legacy.rangeMiddle());
        for (int middle : new int[]{-2,0,2,3})
            assertThrows(IllegalArgumentException.class, () -> new ArrayFrame(new int[]{2,1},
                    EventKind.MERGE, -1, -1, -1, 0, 2, middle, AlgorithmMetrics.ZERO, "invalid", null));
    }

    @Test void rejectsNullBeforeMutatingWork() {
        assertThrows(NullPointerException.class, () -> algorithm.sortInPlace(null, NoOpTraceSink.INSTANCE));
        int[] work = {2,1};
        assertThrows(NullPointerException.class, () -> algorithm.sortInPlace(work, null));
        assertArrayEquals(new int[]{2,1}, work);
    }
}
