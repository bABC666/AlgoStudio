package com.algostudio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RandomArrayGeneratorTest {
    @ParameterizedTest
    @ValueSource(ints = {5, 12, 20, 30, 50})
    void generatesTheRequestedLegalSizeWithValuesBetweenOneAndOneHundred(int size) {
        int[] values = new RandomArrayGenerator(20261102).generate(size).values();
        assertEquals(size, values.length);
        for (int value : values) assertTrue(value >= 1 && value <= 100);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 4, 51, Integer.MAX_VALUE})
    void rejectsIllegalSizesBeforeAllocation(int size) {
        assertThrows(IllegalArgumentException.class, () -> new RandomArrayGenerator().generate(size));
    }

    @Test
    void seededSequenceIsRepeatableButSuccessiveClicksProduceNewValues() {
        RandomArrayGenerator first = new RandomArrayGenerator(20261102);
        RandomArrayGenerator second = new RandomArrayGenerator(20261102);
        int[] firstValues = first.generate(12).values();
        assertArrayEquals(firstValues, second.generate(12).values());
        int[] nextValues = first.generate(12).values();
        assertFalse(Arrays.equals(firstValues, nextValues));
        assertArrayEquals(nextValues, second.generate(12).values());
        assertFalse(Arrays.equals(firstValues, new RandomArrayGenerator(17).generate(12).values()));
    }

    @Test
    void everySizeFromFiveToFiftyStaysWithinBounds() {
        RandomArrayGenerator generator = new RandomArrayGenerator(20261102);
        for (int size = 5; size <= 50; size++) {
            int[] values = generator.generate(size).values();
            assertEquals(size, values.length);
            assertTrue(Arrays.stream(values).allMatch(value -> value >= 1 && value <= 100));
        }
    }
}
