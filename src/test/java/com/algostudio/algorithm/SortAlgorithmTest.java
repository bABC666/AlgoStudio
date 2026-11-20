package com.algostudio.algorithm;

import com.algostudio.algorithm.sort.BubbleSort;
import com.algostudio.algorithm.sort.QuickSort;
import com.algostudio.algorithm.sort.SortAlgorithm;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.input.ArrayInput;
import com.algostudio.model.output.SortedArrayOutput;
import com.algostudio.trace.NoOpTraceSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SortAlgorithmTest {
    private static final long SEED = 20261102;

    static Stream<SortAlgorithm> algorithms() {
        return Stream.of(new BubbleSort(), new QuickSort());
    }

    static Stream<Arguments> fixedInputs() {
        int[][] fixtures = {
                {}, {7}, {1, 2, 3, 4, 5}, {5, 4, 3, 2, 1}, {2, 2, 2, 2, 2},
                {3, 1, 3, 2, 1}, {5, 2, 4, 1, 3}, {-5, 0, -1, 7, -5},
                {Integer.MAX_VALUE, 0, Integer.MIN_VALUE, -1, Integer.MAX_VALUE}
        };
        return algorithms().flatMap(algorithm -> Arrays.stream(fixtures)
                .map(values -> Arguments.of(algorithm, values)));
    }

    @ParameterizedTest
    @MethodSource("fixedInputs")
    void sortsBoundaryCasesWithoutChangingReadOnlyInput(SortAlgorithm algorithm, int[] original) {
        int[] before = original.clone();
        int[] expected = original.clone();
        Arrays.sort(expected);
        ArrayInput input = new ArrayInput(original);
        AlgorithmRun run = algorithm.execute(input, NoOpTraceSink.INSTANCE);
        assertArrayEquals(expected, assertInstanceOf(SortedArrayOutput.class, run.output()).sortedValues());
        assertArrayEquals(before, original);
        assertArrayEquals(before, input.values());
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void matchesIndependentOracleForOneHundredSeededInputs(SortAlgorithm algorithm) {
        Random random = new Random(SEED);
        for (int group = 0; group < 100; group++) {
            int[] values = random.ints(random.nextInt(101), -100, 101).toArray();
            int[] before = values.clone();
            int[] expected = values.clone();
            Arrays.sort(expected);
            AlgorithmRun run = algorithm.execute(new ArrayInput(values), NoOpTraceSink.INSTANCE);
            String context = "seed=" + SEED + ", group=" + group + ", input=" + Arrays.toString(before);
            assertArrayEquals(expected, assertInstanceOf(SortedArrayOutput.class, run.output()).sortedValues(), context);
            assertArrayEquals(before, values, context);
            assertTrue(run.metrics().comparisons() >= 0, context);
            assertEquals(run.metrics().swaps() * 2, run.metrics().writes(), context);
        }
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void countsOneRealSwapAsTwoMainArrayWrites(SortAlgorithm algorithm) {
        AlgorithmRun run = algorithm.execute(new ArrayInput(new int[]{2, 1}), NoOpTraceSink.INSTANCE);
        assertEquals(new AlgorithmMetrics(1, 2, 1, 0, 0, 0, 0), run.metrics());
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void equalPairDoesNotCountSelfSwapsOrWrites(SortAlgorithm algorithm) {
        AlgorithmRun run = algorithm.execute(new ArrayInput(new int[]{1, 1}), NoOpTraceSink.INSTANCE);
        assertEquals(new AlgorithmMetrics(1, 0, 0, 0, 0, 0, 0), run.metrics());
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void countersAreIndependentAcrossInvocations(SortAlgorithm algorithm) {
        algorithm.execute(new ArrayInput(new int[]{3, 2, 1}), NoOpTraceSink.INSTANCE);
        AlgorithmRun run = algorithm.execute(new ArrayInput(new int[0]), NoOpTraceSink.INSTANCE);
        assertEquals(AlgorithmMetrics.ZERO, run.metrics());
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void inPlaceEntrySortsTheCallersWorkArray(SortAlgorithm algorithm) {
        int[] work = {5, 2, 4, 1, 3};
        AlgorithmMetrics metrics = algorithm.sortInPlace(work, NoOpTraceSink.INSTANCE);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, work);
        assertTrue(metrics.comparisons() > 0);
    }

    @Test
    void inputAndOutputArraysAreDefensivelyCopied() {
        int[] values = {3, 1, 2};
        ArrayInput input = new ArrayInput(values);
        values[0] = 99;
        input.values()[1] = 88;
        assertArrayEquals(new int[]{3, 1, 2}, input.values());
        SortedArrayOutput output = new SortedArrayOutput(values);
        values[2] = 77;
        output.sortedValues()[0] = 66;
        assertArrayEquals(new int[]{99, 1, 2}, output.sortedValues());
    }

    @Test
    void lomutoQuickSortHandlesThreeThousandDegenerateElementsWithoutStackOverflow() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            for (int[] work : new int[][]{
                    IntStream.range(0, 3000).toArray(),
                    IntStream.iterate(2999, value -> value - 1).limit(3000).toArray(),
                    new int[3000]}) {
                int[] expected = work.clone();
                Arrays.sort(expected);
                new QuickSort().sortInPlace(work, NoOpTraceSink.INSTANCE);
                assertArrayEquals(expected, work);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void rejectsNullBeforeMutatingWork(SortAlgorithm algorithm) {
        assertThrows(NullPointerException.class, () -> algorithm.execute(null, NoOpTraceSink.INSTANCE));
        assertThrows(NullPointerException.class, () -> algorithm.sortInPlace(null, NoOpTraceSink.INSTANCE));
        int[] work = {2, 1};
        assertThrows(NullPointerException.class, () -> algorithm.sortInPlace(work, null));
        assertArrayEquals(new int[]{2, 1}, work);
        assertThrows(NullPointerException.class, () -> new ArrayInput(null));
        assertThrows(NullPointerException.class, () -> new SortedArrayOutput(null));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void respectsCancellationWithoutClearingTheInterruptFlag(SortAlgorithm algorithm) {
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class,
                    () -> algorithm.sortInPlace(new int[]{3, 2, 1}, NoOpTraceSink.INSTANCE));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
