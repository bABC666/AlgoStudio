package com.algostudio.service;

import com.algostudio.algorithm.AlgorithmCategory;
import com.algostudio.algorithm.AlgorithmDescriptor;
import com.algostudio.algorithm.sort.SortAlgorithm;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.benchmark.BenchmarkRequest;
import com.algostudio.model.benchmark.BenchmarkResult;
import com.algostudio.trace.NoOpTraceSink;
import com.algostudio.trace.TraceSink;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkServiceTest {
    private final AlgorithmDescriptor descriptor = new AlgorithmDescriptor("probe", "Probe", AlgorithmCategory.SORTING);

    @Test void registeredThirdCandidateUsesTheSameSeededBaseAndRealMergeCounts() {
        var report = new BenchmarkService().run(new BenchmarkRequest(100, 20261102));
        assertEquals(List.of("bubble-sort", "quick-sort", "merge-sort"),
                report.results().stream().map(result -> result.algorithm().id()).toList());
        int[] base = new Random(20261102).ints(100, 1, 10_001).toArray();
        var expected = new com.algostudio.algorithm.sort.MergeSort()
                .sortInPlace(base, NoOpTraceSink.INSTANCE);
        assertEquals(expected, report.results().get(2).metrics());
        assertEquals(0, expected.swaps());
        assertEquals(672, expected.writes(), "100项标准分治各层主数组写入总数，独立手算");
    }

    @Test void usesFreshIdenticalCopiesAndOnlyTimesCoreWithNoOp() {
        List<int[]> seen = new ArrayList<>();
        List<String> events = new ArrayList<>();
        SortAlgorithm probe = new SortAlgorithm() {
            public AlgorithmDescriptor descriptor() { return descriptor; }
            public AlgorithmMetrics sortInPlace(int[] work, TraceSink sink) {
                assertSame(NoOpTraceSink.INSTANCE, sink);
                assertFalse(sink.enabled());
                for (int[] previous : seen) assertNotSame(previous, work);
                seen.add(work.clone()); // compare content before sorting
                events.add("core");
                Arrays.sort(work);
                return AlgorithmMetrics.ZERO;
            }
        };
        AtomicInteger clock = new AtomicInteger();
        var report = new BenchmarkService(List.of(probe), () -> {
            events.add("clock"); return clock.getAndAdd(10);
        }).run(new BenchmarkRequest(100, 42));
        assertEquals(7, seen.size());
        for (int[] input : seen) assertArrayEquals(seen.getFirst(), input);
        assertTrue(Arrays.stream(seen.getFirst()).allMatch(n -> n >= 1 && n <= 10_000));
        assertEquals(List.of("core", "core", "clock", "core", "clock", "clock", "core", "clock",
                "clock", "core", "clock", "clock", "core", "clock", "clock", "core", "clock"), events);
        assertEquals(List.of(10L, 10L, 10L, 10L, 10L), report.results().getFirst().samplesNanos());
        assertEquals(10, report.results().getFirst().medianNanos());
    }

    @Test void allAlgorithmsReceiveSameSeededInputAndIndependentWorkArrays() {
        List<int[]> references = new ArrayList<>();
        List<int[]> originals = new ArrayList<>();
        SortAlgorithm probe = new SortAlgorithm() {
            public AlgorithmDescriptor descriptor() { return descriptor; }
            public AlgorithmMetrics sortInPlace(int[] work, TraceSink sink) {
                references.forEach(previous -> assertNotSame(previous, work));
                references.add(work);
                originals.add(work.clone());
                Arrays.sort(work);
                return AlgorithmMetrics.ZERO;
            }
        };
        new BenchmarkService(List.of(probe, probe), System::nanoTime).run(new BenchmarkRequest(500, 42));
        assertEquals(14, originals.size());
        originals.forEach(input -> assertArrayEquals(originals.getFirst(), input));
    }

    @Test void resultMedianAndSamplesAreImmutable() {
        List<Long> values = new ArrayList<>(List.of(9L, 1L, 5L, 3L, 7L));
        var result = new BenchmarkResult(descriptor, values, AlgorithmMetrics.ZERO);
        values.set(0, 99L);
        assertEquals(5, result.medianNanos());
        assertThrows(UnsupportedOperationException.class, () -> result.samplesNanos().set(0, 0L));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkResult(descriptor, List.of(1L), AlgorithmMetrics.ZERO));
    }

    @Test void sameSeedReproducesOriginalInputAcrossReportsAndDifferentSeedChangesIt() {
        List<int[]> originals = new ArrayList<>();
        SortAlgorithm probe = new SortAlgorithm() {
            public AlgorithmDescriptor descriptor() { return descriptor; }
            public AlgorithmMetrics sortInPlace(int[] work, TraceSink sink) {
                originals.add(work.clone());
                Arrays.sort(work);
                return AlgorithmMetrics.ZERO;
            }
        };
        var service = new BenchmarkService(List.of(probe), System::nanoTime);
        service.run(new BenchmarkRequest(100, 42));
        service.run(new BenchmarkRequest(100, 42));
        service.run(new BenchmarkRequest(100, 43));
        assertEquals(21, originals.size());
        assertArrayEquals(originals.get(0), originals.get(7), "同seed跨报告必须复现输入");
        assertFalse(Arrays.equals(originals.get(0), originals.get(14)), "不同seed应产生不同输入");
    }

    @Test void invalidWarmupAndMeasuredOutputsFailInsteadOfReturningPartialReport() {
        for (int failureAt : List.of(1, 4)) {
            AtomicInteger calls = new AtomicInteger();
            SortAlgorithm broken = new SortAlgorithm() {
                public AlgorithmDescriptor descriptor() { return descriptor; }
                public AlgorithmMetrics sortInPlace(int[] work, TraceSink sink) {
                    Arrays.sort(work);
                    if (calls.incrementAndGet() == failureAt) work[0] = -1;
                    return AlgorithmMetrics.ZERO;
                }
            };
            assertThrows(IllegalStateException.class, () -> new BenchmarkService(List.of(broken), System::nanoTime)
                    .run(new BenchmarkRequest(100, 42)));
            assertEquals(failureAt, calls.get());
        }
    }

    @Test void interruptedWorkStopsBeforeAnySort() {
        Thread.currentThread().interrupt();
        try { assertThrows(CancellationException.class, () -> new BenchmarkService().run(new BenchmarkRequest(3000, 42))); }
        finally { Thread.interrupted(); }
    }

    @Test void requestBoundsAndRealAlgorithmsAtMaximum() {
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkRequest(0, 42));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkRequest(3001, 42));
        var report = new BenchmarkService().run(new BenchmarkRequest(3000, 42));
        assertEquals(3, report.results().size());
        report.results().forEach(result -> {
            assertEquals(5, result.samplesNanos().size());
            assertTrue(result.medianNanos() >= 0);
            assertTrue(result.metrics().comparisons() > 0);
            if (result.algorithm().id().equals("merge-sort")) {
                assertEquals(0, result.metrics().swaps());
                assertTrue(result.metrics().writes() > 0);
            } else assertEquals(result.metrics().swaps() * 2, result.metrics().writes());
        });
    }
}
