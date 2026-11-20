package com.algostudio.service;

import com.algostudio.model.input.ArrayInput;

import java.util.Random;

/** 固定初始seed便于复现；同一会话每次生成继续推进随机序列。 */
public final class RandomArrayGenerator {
    public static final int DEFAULT_SIZE = 12;
    public static final long DEFAULT_SEED = 20261102L;
    private final Random random;

    public RandomArrayGenerator() {
        this(DEFAULT_SEED);
    }

    public RandomArrayGenerator(long seed) {
        random = new Random(seed);
    }

    public ArrayInput generate(int size) {
        InputValidator.validateSize(size);
        int[] values = new int[size];
        for (int index = 0; index < size; index++) {
            values[index] = random.nextInt(InputValidator.MIN_VALUE, InputValidator.MAX_VALUE + 1);
        }
        return new ArrayInput(values);
    }
}
