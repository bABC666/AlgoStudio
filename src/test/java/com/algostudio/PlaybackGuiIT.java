package com.algostudio;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 独立执行 -Dtest=PlaybackGuiIT test。全部操作针对真实 Scene/控件/Timeline。 */
class PlaybackGuiIT {
    private final CompletableFuture<Void> completed = new CompletableFuture<>();
    private final AtomicReference<Throwable> uncaught = new AtomicReference<>();
    private Stage stage;
    private Scene scene;
    private String paused;

    @Test
    void playsRestoresHistorySwitchesResizesAndClosesWithoutStaleTicks() throws Exception {
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> {
                uncaught.compareAndSet(null, error);
                completed.completeExceptionally(error);
            });
            check(() -> {
                stage = new Stage();
                new App().start(stage);
                scene = stage.getScene();
                initialAndHistory();
            });
        });
        try {
            completed.get(25, TimeUnit.SECONDS);
            assertNull(uncaught.get());
        } finally {
            Platform.exit();
        }
    }

    private void initialAndHistory() throws Exception {
        assertInstanceOf(TextArea.class, node("array-input")).setText("5,2,4,1,3");
        button("generate-trace").fire();
        assertFalse(button("play-pause").isDisabled());
        assertTrue(button("previous-step").isDisabled());
        assertEquals("就绪", text("playback-status"));
        assertEquals("[5, 2, 4, 1, 3]", text("array-values"));
        snapshot("playback-initial.png");
        String original = fingerprint();
        int originalPixels = Arrays.hashCode(canvasPixels());
        for (int index = 0; index < 10; index++) {
            button("next-step").fire();
            if (index == 1) {
                assertEquals("交换", text("event-name"));
                assertTrue(colorCount(0x0f9493) > 100, "交换柱应有青色高亮");
                snapshot("playback-swap.png");
            }
        }
        assertNotEquals(original, fingerprint());
        snapshot("playback-halfway.png");
        double halfwayFill = progressFill();
        assertTrue(halfwayFill > .48 && halfwayFill < .62, "半程进度条必须有对应比例的可见填充");
        for (int index = 0; index < 10; index++) button("previous-step").fire();
        assertEquals(original, fingerprint());
        assertEquals(originalPixels, Arrays.hashCode(canvasPixels()));
        System.out.println("GUI: 10 steps forward/back restored pixels, array, explanation and metrics.");
        combo("playback-speed").getSelectionModel().select(2);
        button("play-pause").fire();
        assertEquals("播放中", text("playback-status"));
        later(50, () -> pauseAfterFirstAdvance(40));
    }

    private void pauseAfterFirstAdvance(int attempts) {
        if (text("step-count").startsWith("第 1 /")) {
            assertTrue(attempts > 0, "播放必须真实推进至少一帧");
            later(50, () -> pauseAfterFirstAdvance(attempts - 1));
            return;
        }
        button("play-pause").fire();
        assertEquals("已暂停", text("playback-status"));
        paused = fingerprint();
        later(400, this::pauseAndRegenerate);
    }

    private void pauseAndRegenerate() {
        assertEquals(paused, fingerprint(), "暂停后时钟不得前进");
        button("play-pause").fire();
        button("generate-trace").fire();
        assertEquals("就绪", text("playback-status"));
        paused = fingerprint();
        later(350, () -> {
            assertEquals(paused, fingerprint(), "重新生成后旧时钟不得回写");
            button("play-pause").fire();
            combo("algorithm-choice").getSelectionModel().select(1);
            assertEquals("快速排序", text("algorithm-title"));
            assertEquals("就绪", text("playback-status"));
            paused = fingerprint();
            later(350, this::quickSortAndFinish);
        });
    }

    private void quickSortAndFinish() throws Exception {
        assertEquals(paused, fingerprint(), "切换算法后旧时钟不得回写");
        button("next-step").fire();
        assertEquals("枢轴", text("event-name"));
        assertTrue(colorCount(0x8b5bd6) > 100, "枢轴柱应有紫色高亮");
        snapshot("playback-pivot.png");
        button("next-step").fire();
        assertEquals("比较", text("event-name"));
        assertTrue(colorCount(0xd99719) > 100, "比较柱应有黄色高亮");
        snapshot("playback-compare.png");
        button("reset-playback").fire();
        button("play-pause").fire();
        awaitFinished(60);
    }

    private void awaitFinished(int attempts) throws Exception {
        if (!text("playback-status").equals("已完成")) {
            assertTrue(attempts > 0, "4×播放应能在限定时间内完整结束");
            later(100, () -> awaitFinished(attempts - 1));
            return;
        }
        assertEquals("[1, 2, 3, 4, 5]", text("array-values"));
        assertEquals("6", text("comparisons"));
        assertEquals("10", text("writes"));
        assertEquals("5", text("swaps"));
        assertTrue(button("next-step").isDisabled());
        assertEquals("重新播放", button("play-pause").getText());
        assertTrue(colorCount(0x239a6d) > 1000);
        assertTrue(progressFill() > .95, "终帧进度条必须显示满进度");
        snapshot("playback-finished.png");
        System.out.println("GUI: pause, regenerate, algorithm switch, pivot/compare colors and 4x completion verified.");
        resizeAtTheCurrentFrame();
    }

    private void resizeAtTheCurrentFrame() {
        String finished = fingerprint();
        AtomicBoolean handled = new AtomicBoolean();
        Runnable resized = () -> {
            if (scene.getWidth() >= 1180 || scene.getHeight() >= 760
                    || !handled.compareAndSet(false, true)) return;
            Platform.runLater(() -> check(() -> {
                snapshot("playback-resized.png");
                assertEquals(finished, fingerprint(), "缩放只能重绘当前帧");
                assertTrue(colorCount(0x239a6d) > 1000);
                assertTrue(progressFill() > .95, "最小窗口下必须仍能看见满进度条");
                for (String id : new String[]{"play-pause", "explanation", "comparisons", "playback-speed"}) {
                    Node node = node(id);
                    assertTrue(node.localToScene(node.getBoundsInLocal()).getMaxY() <= scene.getHeight() + 1,
                            id + " 不得被窗口底部裁切");
                }
                button("play-pause").fire();
                assertEquals("播放中", text("playback-status"));
                assertTrue(text("step-count").startsWith("第 1 /"));
                stage.close();
                assertFalse(stage.isShowing());
                String afterClose = fingerprint();
                later(400, () -> {
                    assertEquals(afterClose, fingerprint(), "关闭窗口后不得有旧回调继续更新");
                    System.out.println("GUI: resize retained current frame; replay reset to 0; close stopped playback.");
                    completed.complete(null);
                });
            }));
        };
        scene.widthProperty().addListener((observable, before, after) -> resized.run());
        scene.heightProperty().addListener((observable, before, after) -> resized.run());
        stage.setWidth(960);
        stage.setHeight(640);
    }

    private void later(int milliseconds, CheckedAction action) {
        PauseTransition delay = new PauseTransition(Duration.millis(milliseconds));
        delay.setOnFinished(event -> check(action));
        delay.play();
    }

    private void check(CheckedAction action) {
        if (completed.isDone()) return;
        try {
            action.run();
        } catch (Throwable error) {
            if (stage != null) stage.close();
            completed.completeExceptionally(error);
        }
    }

    private Node node(String id) {
        Node node = scene.lookup("#" + id);
        assertNotNull(node, "缺少业务控件 #" + id);
        return node;
    }

    private Button button(String id) { return assertInstanceOf(Button.class, node(id)); }
    private String text(String id) { return assertInstanceOf(Label.class, node(id)).getText(); }
    private ComboBox<?> combo(String id) { return (ComboBox<?>) node(id); }

    private String fingerprint() {
        return String.join("|", text("step-count"), text("array-values"), text("explanation"),
                text("comparisons"), text("writes"), text("swaps"), text("playback-status"));
    }

    private int[] canvasPixels() {
        WritableImage image = assertInstanceOf(Canvas.class, node("array-canvas")).snapshot(null, null);
        return pixels(image);
    }

    private long colorCount(int rgb) {
        return Arrays.stream(canvasPixels()).filter(pixel -> (pixel & 0xffffff) == rgb).count();
    }

    private double progressFill() {
        ProgressBar progress = assertInstanceOf(ProgressBar.class, scene.lookup(".playback-progress"));
        Region bar = assertInstanceOf(Region.class, progress.lookup(".bar"));
        WritableImage image = progress.snapshot(null, null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = pixels(image);
        int paintedColumns = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if ((pixels[y * width + x] & 0xffffff) == 0x4975d1) {
                    paintedColumns++;
                    break;
                }
            }
        }
        System.out.printf("Progress pixels: value=%.4f, size=%dx%d, barHeight=%.1f, fills=%s, paintedColumns=%d%n",
                progress.getProgress(), width, height, bar.getHeight(),
                bar.getBackground().getFills().stream().map(fill -> fill.getInsets().toString()).toList(), paintedColumns);
        return (double) paintedColumns / width;
    }

    private int[] pixels(WritableImage image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return pixels;
    }

    private void snapshot(String filename) throws Exception {
        WritableImage image = scene.snapshot(null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels(image), 0, width);
        Path output = Path.of("target", "gui-smoke", filename);
        Files.createDirectories(output.getParent());
        assertTrue(ImageIO.write(buffered, "png", output.toFile()));
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
