package com.algostudio.service;

import com.algostudio.model.input.ArrayInput;

import java.util.Arrays;
import java.util.regex.Pattern;

/** 只解析有界数据；UI限制留在服务边界，不修改算法核心的合法输入范围。 */
public final class InputValidator {
    public static final int MAX_TEXT_LENGTH = 4096;
    public static final int MIN_ITEMS = 5;
    public static final int MAX_ITEMS = TraceService.MAX_VISUALIZATION_ITEMS;
    public static final int MIN_VALUE = 1;
    public static final int MAX_VALUE = 100;
    private static final Pattern SEPARATORS = Pattern.compile("[,\\s]+");

    public ArrayInput parseArray(String text) {
        if (text != null && text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("输入文本最多 " + MAX_TEXT_LENGTH + " 个字符，请缩短后重试。");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("请输入 " + MIN_ITEMS + "～" + MAX_ITEMS + " 个整数。");
        }
        String[] tokens = Arrays.stream(SEPARATORS.split(text))
                .filter(token -> !token.isEmpty()).toArray(String[]::new);
        validateSize(tokens.length);
        int[] values = new int[tokens.length];
        for (int index = 0; index < tokens.length; index++) {
            try {
                values[index] = Integer.parseInt(tokens[index]);
            } catch (NumberFormatException invalid) {
                String preview = tokens[index].length() > 12 ? tokens[index].substring(0, 12) + "…" : tokens[index];
                throw new IllegalArgumentException("第 " + (index + 1) + " 项「" + preview + "」不是有效的 32 位整数。");
            }
            if (values[index] < MIN_VALUE || values[index] > MAX_VALUE) {
                throw new IllegalArgumentException("第 " + (index + 1) + " 项需在 " + MIN_VALUE + "～" + MAX_VALUE
                        + " 之间（当前 " + values[index] + "）。");
            }
        }
        return new ArrayInput(values);
    }

    public static void validateSize(int size) {
        if (size < MIN_ITEMS || size > MAX_ITEMS) {
            throw new IllegalArgumentException("需要 " + MIN_ITEMS + "～" + MAX_ITEMS + " 项，当前为 " + size + " 项。");
        }
    }

    public String normalizeEdit(String insertedText, int retainedLength) {
        if (insertedText == null || retainedLength < 0 || retainedLength > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("无效的文本更改。");
        }
        // 先比较剩余容量，不拼接完整前缀/粘贴/后缀，也不先扫描超长粘贴。
        if (insertedText.length() > MAX_TEXT_LENGTH - retainedLength) {
            throw new IllegalArgumentException("输入最多4096个字符，本次更改未应用。");
        }
        StringBuilder normalized = null;
        for (int index = 0; index < insertedText.length(); index++) {
            char character = insertedText.charAt(index);
            if (character == '\r' || character == '\f' || character == 0x0b) {
                // TextArea会删掉这些字符；先转为空格，保留分隔语义和原始长度。
                if (normalized == null) normalized = new StringBuilder(insertedText);
                normalized.setCharAt(index, ' ');
            } else if (Character.isISOControl(character) && character != '\t' && character != '\n') {
                throw new IllegalArgumentException("输入包含不支持的控制字符（"
                        + String.format("U+%04X", (int) character) + "），本次更改未应用。");
            }
        }
        return normalized == null ? insertedText : normalized.toString();
    }
}
