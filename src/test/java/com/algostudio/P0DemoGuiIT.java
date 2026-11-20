package com.algostudio;

import com.algostudio.ui.MainView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelFormat;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** 一个真实App/Stage贯穿README的P0演示；不使用替代算法、工作线程或播放时钟。 */
class P0DemoGuiIT {
    private MainView view;
    private Stage stage;
    private String pausedFrame;
    private String originalInput;
    private int[] pausedPixels;

    @Test void inputBubblePlaybackRollbackQuickBenchmarkInvalidRecoveryAndClose() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Thread.currentThread().setUncaughtExceptionHandler((t, error) -> uncaught.compareAndSet(null, error));
            ready.complete(null);
        });
        ready.get(5, TimeUnit.SECONDS);
        try {
            fx(() -> {
                stage = new Stage(); new App().start(stage); view = (MainView) stage.getScene().getRoot();
                view.arrayInput().setText("5, 2 4, 1, 3");
                for (int i = 0; i < 8; i++) view.generateButton().fire();
                assertEquals("[5, 2, 4, 1, 3]", text("array-values"));
                assertEquals("就绪", text("playback-status"));
                String first = fingerprint();
                int[] pixels = canvasPixels();
                for (int i = 0; i < 10; i++) view.nextButton().fire();
                for (int i = 0; i < 10; i++) view.previousButton().fire();
                assertEquals(first, fingerprint()); assertArrayEquals(pixels, canvasPixels());
                assertTrue(view.previousButton().isDisabled());
                capture("p0-rollback.png");
                assertTrue(view.speedChoice().getItems().contains("8×"));
                view.speedChoice().getSelectionModel().select("8×");
                view.playButton().fire(); return null;
            });
            await(() -> text("playback-status").equals("已完成"));
            fx(() -> {
                assertEquals("[1, 2, 3, 4, 5]", text("array-values"));
                // 四轮分别4、3、2、1次比较；最后一轮用于确认无交换。
                assertEquals("10", text("comparisons")); assertEquals("14", text("writes"));
                assertEquals("7", text("swaps")); capture("p0-bubble-done.png");
                view.playButton().fire();
                view.algorithmChoice().getSelectionModel().select(1);
                assertEquals("快速排序", text("algorithm-title"));
                assertTrue(text("step-count").startsWith("第 1 /"));
                view.playButton().fire(); return null;
            });
            await(() -> text("playback-status").equals("已完成"));
            fx(() -> {
                assertEquals("[1, 2, 3, 4, 5]", text("array-values"));
                assertEquals("6", text("comparisons")); assertEquals("10", text("writes"));
                assertEquals("5", text("swaps")); capture("p0-quick-done.png");
                view.playButton().fire();
                for (int i = 0; i < 3; i++) view.nextButton().fire();
                pausedFrame = fingerprint(); pausedPixels = canvasPixels();
                originalInput = view.arrayInput().getText();
                view.playButton().fire();
                mode("mode-benchmark"); return null;
            });
            Thread.sleep(350); // 测试线程等待超过 8× 的一个播放间隔。
            fx(() -> {
                mode("mode-visualization");
                assertEquals(pausedFrame, fingerprint(), "切页必须先停止旧播放");
                assertEquals("已暂停", text("playback-status"));
                for (int i = 0; i < 8; i++) { mode("mode-benchmark"); mode("mode-visualization"); }
                mode("mode-benchmark");
                var benchmark = view.benchmarkView();
                benchmark.sizeChoice().getSelectionModel().select(Integer.valueOf(500));
                benchmark.runButton().fire(); benchmark.runButton().fire();
                assertTrue(benchmark.runButton().isDisabled()); return null;
            });
            await(() -> !view.benchmarkView().runButton().isDisabled());
            fx(() -> {
                var benchmark = view.benchmarkView();
                assertEquals(3, benchmark.resultTable().getItems().size());
                assertEquals(3, benchmark.resultChart().getData().getFirst().getData().size());
                var rows = benchmark.resultTable().getItems();
                assertEquals(124540, rows.get(0).metrics().comparisons());
                assertEquals(4933, rows.get(1).metrics().comparisons());
                capture("p0-benchmark.png");
                mode("mode-visualization"); view.applyCss(); view.layout();
                assertEquals(pausedFrame, fingerprint(), "性能完成后须保留原演示帧");
                assertEquals(originalInput, view.arrayInput().getText());
                assertArrayEquals(pausedPixels, canvasPixels());
                view.previousButton().fire(); view.nextButton().fire();
                assertEquals(pausedFrame, fingerprint(), "返回后仍可回退并恢复同一帧");
                assertArrayEquals(pausedPixels, canvasPixels());
                capture("p0-benchmark-return.png");
                mode("mode-benchmark");
                assertEquals(3, benchmark.resultTable().getItems().size());
                benchmark.sizeChoice().getSelectionModel().select(Integer.valueOf(3000));
                benchmark.runButton().fire(); mode("mode-visualization");
                assertFalse(benchmark.runButton().isDisabled());
                assertTrue(benchmark.resultTable().getItems().isEmpty());
                view.arrayInput().setText("1,2,a,4,5"); view.generateButton().fire();
                assertEquals("生成失败", text("playback-status"));
                assertTrue(view.playButton().isDisabled());
                assertEquals("—", text("array-values"));
                capture("p0-invalid.png");
                view.arrayInput().setText("5,2,4,1,3"); view.generateButton().fire();
                assertEquals("就绪", text("playback-status")); assertFalse(view.playButton().isDisabled());
                view.playButton().fire(); stage.close();
                assertFalse(stage.isShowing()); return null;
            });
            String closed = fx(this::fingerprint);
            Thread.sleep(350);
            assertEquals(closed, fx(this::fingerprint), "关闭后无旧UI回调");
            await(() -> Thread.getAllStackTraces().keySet().stream()
                    .noneMatch(t -> t.isAlive() && t.getName().equals("algostudio-benchmark")));
            assertNull(uncaught.get());
            System.out.println("P0 full Stage flow: input -> bubble -> exact 10-step rollback -> quick -> seeded benchmark -> invalid/recover -> close; no worker remains.");
        } finally {
            fx(() -> { if (stage != null) stage.close(); return null; }); Platform.exit();
        }
    }

    private void mode(String id) { ((ToggleButton) view.lookup("#" + id)).fire(); }
    private String text(String id) { return ((Label) view.lookup("#" + id)).getText(); }
    private String fingerprint() {
        return String.join("|", List.of("array-values", "step-count", "event-name", "explanation", "comparisons", "writes", "swaps")
                .stream().map(this::text).toList());
    }
    private int[] canvasPixels() {
        var image = view.canvas().snapshot(null, null);
        int w = (int) image.getWidth(), h = (int) image.getHeight(); int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w); return pixels;
    }
    private void capture(String name) throws Exception {
        Scene scene = stage.getScene(); var image = scene.snapshot(null);
        int w = (int) image.getWidth(), h = (int) image.getHeight(); int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); png.setRGB(0, 0, w, h, pixels, 0, w);
        Path path = Path.of("target", "gui-smoke", name); Files.createDirectories(path.getParent()); ImageIO.write(png, "png", path.toFile());
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        Platform.runLater(() -> { try { done.complete(action.call()); } catch (Throwable error) { done.completeExceptionally(error); } });
        return done.get(5, TimeUnit.SECONDS);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!fx(condition::getAsBoolean)) { if (System.nanoTime() > deadline) fail("P0流程等待超时"); Thread.sleep(20); }
    }
}
