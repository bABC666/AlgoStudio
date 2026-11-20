package com.algostudio.ui.renderer;

import com.algostudio.model.frame.ArrayFrame;
import com.algostudio.model.frame.EventKind;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/** 只画当前快照，不执行排序、不推进播放、不保存算法状态。 */
public final class ArrayRenderer implements VisualRenderer<ArrayFrame> {
    private static final Color NORMAL = Color.web("#4975d1");
    private static final Color COMPARE = Color.web("#d99719");
    private static final Color SWAP = Color.web("#0f9493");
    private static final Color PIVOT = Color.web("#8b5bd6");
    private static final Color DONE = Color.web("#239a6d");

    @Override
    public void render(ArrayFrame frame, Canvas canvas) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        graphics.clearRect(0, 0, width, height);
        if (width < 80 || height < 90) return;
        int[] values = frame.values();
        if (values.length == 0) {
            graphics.setFill(Color.web("#718096"));
            graphics.fillText("空数组，无需排序", 24, height / 2);
            return;
        }
        double minimum = 0;
        double maximum = 1;
        for (int value : values) {
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        double left = 34;
        boolean divided = frame.rangeMiddle() >= 0;
        double plotWidth = width - left - 20;
        double cellWidth = plotWidth / values.length;
        // 密集数组不画柱顶数值，紧凑窗口可收回这部分留白以保留柱高。
        boolean compact = divided && cellWidth < 28 && height < 180;
        double top = divided ? (compact ? 28 : 58) : 32;
        double plotHeight = height - top - (compact ? 28 : 42);
        double span = maximum - minimum;
        double baseline = top + plotHeight * maximum / span;
        int indexStride = Math.max(1, (int) Math.ceil(28 / cellWidth));
        double barWidth = Math.min(92, cellWidth * .65);
        if (divided) {
            double splitX = left + frame.rangeMiddle() * cellWidth;
            graphics.setFill(Color.web("#edf3ff"));
            graphics.fillRect(left + frame.rangeStart() * cellWidth, top - 8,
                    (frame.rangeMiddle() - frame.rangeStart()) * cellWidth, plotHeight + 16);
            graphics.setFill(Color.web("#f3edff"));
            graphics.fillRect(splitX, top - 8,
                    (frame.rangeEnd() - frame.rangeMiddle()) * cellWidth, plotHeight + 16);
            graphics.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
            graphics.setTextBaseline(VPos.BASELINE);
            graphics.setTextAlign(TextAlignment.RIGHT);
            graphics.setFill(NORMAL);
            graphics.fillText("左 [" + frame.rangeStart() + ", " + frame.rangeMiddle() + ")", width / 2 - 10, compact ? 16 : 22);
            graphics.setTextAlign(TextAlignment.LEFT);
            graphics.setFill(PIVOT);
            graphics.fillText("右 [" + frame.rangeMiddle() + ", " + frame.rangeEnd() + ")", width / 2 + 10, compact ? 16 : 22);
            graphics.setStroke(PIVOT);
            graphics.setLineDashes(4, 4);
            graphics.strokeLine(splitX, top - 8, splitX, top + plotHeight + 8);
            graphics.setLineDashes();
        } else if (frame.eventKind() != EventKind.INITIAL && frame.eventKind() != EventKind.DONE) {
            graphics.setFill(Color.web("#4975d1", .045));
            graphics.fillRoundRect(left + frame.rangeStart() * cellWidth, top - 8,
                    (frame.rangeEnd() - frame.rangeStart()) * cellWidth, plotHeight + 16, 8, 8);
        }
        graphics.setStroke(Color.web("#edf1f7"));
        for (int line = 0; line < 4; line++) {
            double y = top + plotHeight * line / 3;
            graphics.strokeLine(left, y, width - 20, y);
        }
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.BASELINE);
        for (int index = 0; index < values.length; index++) {
            double x = left + index * cellWidth + (cellWidth - barWidth) / 2;
            double endpoint = top + plotHeight * (maximum - values[index]) / span;
            double y = Math.min(endpoint, baseline);
            double barHeight = Math.max(2, Math.abs(endpoint - baseline));
            Color color = color(frame, index);
            graphics.setFill(color);
            graphics.fillRoundRect(x, y, barWidth, barHeight, 6, 6);
            if (index == frame.pivotIndex()) {
                graphics.setStroke(PIVOT);
                graphics.setLineWidth(2);
                graphics.strokeRoundRect(x - 3, y - 3, barWidth + 6, barHeight + 6, 8, 8);
                graphics.setLineWidth(1);
            }
            if (cellWidth >= 28) {
                graphics.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
                graphics.setFill(Color.web("#24334d"));
                graphics.fillText(Integer.toString(values[index]), x + barWidth / 2,
                        values[index] >= 0 ? y - 10 : y + barHeight + 18);
            }
            if (index == values.length - 1 || (index % indexStride == 0 && index <= values.length - 1 - indexStride)) {
                graphics.setFont(Font.font("Microsoft YaHei", 11));
                graphics.setFill(Color.web("#718096"));
                graphics.fillText("[" + index + "]", x + barWidth / 2, height - 12);
            }
        }
        graphics.setStroke(Color.web("#cbd5e1"));
        graphics.strokeLine(left, baseline + 1, width - 20, baseline + 1);
    }

    private Color color(ArrayFrame frame, int index) {
        if (frame.eventKind() == EventKind.DONE) return DONE;
        if (index == frame.firstIndex() || index == frame.secondIndex()) {
            if (frame.eventKind() == EventKind.COMPARE) return COMPARE;
            if (frame.eventKind() == EventKind.SWAP || frame.eventKind() == EventKind.WRITE) return SWAP;
        }
        return index == frame.pivotIndex() ? PIVOT : NORMAL;
    }
}
