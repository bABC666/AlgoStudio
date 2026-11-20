package com.algostudio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class InputValidatorTest {
    private final InputValidator validator = new InputValidator();

    static Stream<Arguments> validInputs() {
        return Stream.of(
                Arguments.of("5, 2 4, 1, 3", new int[]{5, 2, 4, 1, 3}),
                Arguments.of("  ,5,,2, \t4\n1\r\n3,,, ", new int[]{5, 2, 4, 1, 3}),
                Arguments.of("1,100,1,100,1", new int[]{1, 100, 1, 100, 1}),
                Arguments.of("+5 +2 +4 +1 +3", new int[]{5, 2, 4, 1, 3}),
                Arguments.of(IntStream.rangeClosed(1, 50).mapToObj(Integer::toString).collect(Collectors.joining(",")),
                        IntStream.rangeClosed(1, 50).toArray()));
    }

    @ParameterizedTest
    @MethodSource("validInputs")
    void acceptsLegalDataAndCollapsesRepeatedSeparators(String text, int[] expected) {
        assertArrayEquals(expected, validator.parseArray(text).values());
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of(null, "输入"), Arguments.of("", "输入"), Arguments.of(" , ,\t\n", "5"),
                Arguments.of("1,2,3,4", "5"),
                Arguments.of(IntStream.rangeClosed(1, 51).mapToObj(Integer::toString).collect(Collectors.joining(",")), "50"),
                Arguments.of("1,2,a,4,5", "整数"), Arguments.of("1,2,3.5,4,5", "整数"),
                Arguments.of("1,2,1e1,4,5", "整数"), Arguments.of("1,2,NaN,4,5", "整数"),
                Arguments.of("1,2,2147483648,4,5", "整数"), Arguments.of("1,2,-2147483649,4,5", "整数"),
                Arguments.of("1,2,0,4,5", "100"), Arguments.of("1,2,101,4,5", "100"),
                Arguments.of("1,2,-1,4,5", "100"), Arguments.of("1,2,Integer.MAX_VALUE,4,5", "整数"),
                Arguments.of("1,2,System.exit(0),4,5", "整数"),
                Arguments.of("1,2,3;calc.exe,4,5", "整数"),
                Arguments.of("1，2，3，4，5", "5"));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void rejectsInvalidDataWithAnActionableMessageAndRecovers(String text, String expectedHint) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> validator.parseArray(text));
        assertTrue(failure.getMessage().contains(expectedHint), failure.getMessage());
        assertArrayEquals(new int[]{5, 2, 4, 1, 3}, validator.parseArray("5,2,4,1,3").values());
    }

    @Test
    void acceptsExactly4096CharactersButChecks4097BeforeParsingTokens() {
        String values = "5,2,4,1,3";
        String boundary = values + " ".repeat(4096 - values.length());
        assertEquals(4096, boundary.length());
        assertArrayEquals(new int[]{5, 2, 4, 1, 3}, validator.parseArray(boundary).values());
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> validator.parseArray("x".repeat(4097)));
        assertTrue(failure.getMessage().contains("4096"));
    }

    @Test
    void veryLongInvalidTokenProducesABoundedErrorMessage() {
        String text = "1,2," + "9".repeat(4000) + ",4,5";
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> validator.parseArray(text));
        assertTrue(failure.getMessage().length() < 120);
        assertTrue(failure.getMessage().contains("3"));
        assertTrue(failure.getMessage().contains("整数"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u30001,2,3,4,5", "1,2,3,4,5\u3000", "1\u30002,3,4,5,6"})
    void nonAsciiWhitespaceIsRejectedConsistentlyAtEdgesAndInside(String text) {
        assertThrows(IllegalArgumentException.class, () -> validator.parseArray(text));
    }

    @ParameterizedTest
    @ValueSource(ints = {13, 11, 12})
    void rawPasteWhitespaceKeepsItsSeparatorMeaningBeforeJavaFxCanRemoveIt(int separator) {
        String raw = "1" + (char) separator + "2,3,4,5,6";
        String normalized = validator.normalizeEdit(raw, 0);
        assertEquals("1 2,3,4,5,6", normalized);
        assertEquals(raw.length(), normalized.length());
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6}, validator.parseArray(normalized).values());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 8, 27, 127, 133, 159})
    void unsupportedRawPasteControlsAreRejectedRatherThanSilentlyDeleted(int control) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.normalizeEdit("1" + (char) control + "2,3,4,5,6", 0));
        assertTrue(error.getMessage().contains("控制字符"));
    }

    @Test
    void selectedReplacementUsesRemainingCapacityAndChecksRawLengthFirst() {
        assertEquals("\t     ", validator.normalizeEdit("\t     ", 4090));
        assertThrows(IllegalArgumentException.class, () -> validator.normalizeEdit(" ".repeat(7), 4090));
        IllegalArgumentException length = assertThrows(IllegalArgumentException.class,
                () -> validator.normalizeEdit(((char) 1 + "x").repeat(50_000), 8));
        assertTrue(length.getMessage().contains("4096"));
        assertThrows(IllegalArgumentException.class, () -> validator.normalizeEdit("", -1));
        assertThrows(IllegalArgumentException.class, () -> validator.normalizeEdit("", 4097));
    }
}
