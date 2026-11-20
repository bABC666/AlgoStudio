package com.algostudio;

import com.algostudio.ui.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
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

class GraphTraversalGuiIT {
    private MainView view;
    private Stage stage;
    private String beforeResize, edgeBeforeResize;
    @Test void realBfsDfsStateParentsRollbackResizeSwitchBenchmarkAndClose() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>(); AtomicReference<Throwable> uncaught = new AtomicReference<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Thread.currentThread().setUncaughtExceptionHandler((t, error) -> uncaught.compareAndSet(null, error)); ready.complete(null);
        }); ready.get(5, TimeUnit.SECONDS);
        try {
            fx(() -> {
                stage = new Stage(); new App().start(stage); view = (MainView) stage.getScene().getRoot();
                mode("mode-graph"); view.graphControls().exampleChoice().getSelectionModel().select(1);
                view.graphControls().algorithmChoice().getSelectionModel().select(GraphRunMode.BFS);
                view.applyCss(); view.layout();
                assertEquals("广度优先搜索 BFS", text("algorithm-title"));
                assertFalse(text("algorithm-description").contains("示意")); assertEquals("0", text("comparisons"));
                for (int i = 0; i < 40 && !text("graph-frontier").contains("[B, C]"); i++) view.nextButton().fire();
                assertTrue(text("graph-frontier").contains("[B, C]"));
                assertEquals("访问顺序：[A]", text("graph-order")); assertTrue(text("graph-discovered").contains("[A, B, C]"));
                assertTrue(text("graph-parents").contains("C←A")); assertTrue(colorCount(0x8b5bd6) > 100);
                String frame = fingerprint(); int[] image = pixels(); capture("bfs-queue.png");
                for (int i = 0; i < 8; i++) view.nextButton().fire();
                for (int i = 0; i < 8; i++) view.previousButton().fire();
                assertEquals(frame, fingerprint()); assertArrayEquals(image, pixels());
                finish(); assertDone("C←A"); capture("bfs-done.png");
                view.speedChoice().getSelectionModel().select("4×"); view.resetButton().fire(); view.playButton().fire(); return null;
            });
            await(() -> text("playback-status").equals("已完成"));
            fx(() -> {
                assertDone("C←A"); view.playButton().fire();
                view.graphControls().algorithmChoice().getSelectionModel().select(GraphRunMode.DFS); return null;
            }); Thread.sleep(350);
            fx(() -> {
                assertTrue(text("step-count").startsWith("第 1 /")); assertEquals("深度优先搜索 DFS", text("algorithm-title"));
                for (int i = 0; i < 40 && !text("graph-frontier").contains("[A, B, C]"); i++) view.nextButton().fire();
                assertTrue(text("graph-frontier").contains("[A, B, C]")); assertTrue(text("graph-parents").contains("C←B"));
                capture("dfs-stack.png");
                for (int i = 0; i < 40 && !text("event-name").equals("返回调用栈"); i++) view.nextButton().fire();
                assertEquals("返回调用栈", text("event-name")); assertTrue(text("graph-frontier").contains("[A, B, C, D]"));
                capture("dfs-backtrack.png"); finish(); assertDone("C←B");
                view.playButton().fire(); view.graphControls().startChoice().getSelectionModel().select("F"); finish();
                assertEquals("1", text("comparisons")); assertEquals("0", text("writes"));
                assertEquals("访问顺序：[F]", text("graph-order")); assertEquals("父节点：{}", text("graph-parents"));
                assertFalse(text("graph-unvisited").contains("F")); capture("traversal-isolated-f.png");
                view.graphControls().startChoice().getSelectionModel().select("A");
                view.nextButton().fire(); view.nextButton().fire();
                beforeResize = fingerprint(); edgeBeforeResize = text("graph-edge");
                stage.setWidth(960); stage.setHeight(640); return null;
            });
            await(() -> stage.getScene().getWidth() < 1000);
            fx(() -> {
                view.applyCss(); view.layout();
                assertEquals(beforeResize, fingerprint()); assertEquals(edgeBeforeResize, text("graph-edge"));
                assertTrue(colorCount(0xd99719) > 100);
                view.graphControls().setVvalue(1); view.layout(); capture("traversal-minimum.png");
                var parents = view.lookup("#graph-parents"); var bounds = parents.localToScene(parents.getBoundsInLocal());
                assertTrue(bounds.getMaxY() <= stage.getScene().getHeight() + 1);
                mode("mode-benchmark"); view.benchmarkView().runButton().fire(); return null;
            });
            await(() -> !view.benchmarkView().runButton().isDisabled());
            fx(() -> {
                assertEquals(3, view.benchmarkView().resultTable().getItems().size()); mode("mode-graph"); view.applyCss(); view.layout();
                assertEquals(beforeResize, fingerprint()); assertEquals("深度优先搜索 DFS", text("algorithm-title"));
                for (int i = 0; i < 8; i++) view.graphControls().algorithmChoice().getSelectionModel().select(i % 2 == 0 ? GraphRunMode.BFS : GraphRunMode.DFS);
                assertTrue(text("step-count").startsWith("第 1 /"));
                view.graphControls().algorithmChoice().getSelectionModel().select(GraphRunMode.PREVIEW);
                assertEquals("—", text("comparisons")); assertTrue(text("algorithm-description").contains("示意"));
                mode("mode-visualization"); view.arrayInput().setText("5,2,4,1,3"); view.generateButton().fire();
                assertEquals("[5, 2, 4, 1, 3]", text("array-values")); view.playButton().fire(); mode("mode-graph");
                view.graphControls().algorithmChoice().getSelectionModel().select(GraphRunMode.DFS); view.playButton().fire(); stage.close(); return null;
            });
            String closed = fx(this::fingerprint); Thread.sleep(350); assertEquals(closed, fx(this::fingerprint)); assertNull(uncaught.get());
            System.out.println("BFS/DFS GUI: real queue/stack/parents/order/counts, exact rollback, isolatedF, resize retained state, mode cancellation, benchmark return and close passed.");
        } finally { fx(() -> { if (stage != null) stage.close(); return null; }); Platform.exit(); }
    }
    private void finish() { for (int i = 0; i < 200 && !view.nextButton().isDisabled(); i++) view.nextButton().fire(); assertTrue(view.nextButton().isDisabled()); }
    private void assertDone(String parent) {
        assertEquals("已完成", text("playback-status")); assertEquals("5", text("comparisons")); assertEquals("12", text("writes"));
        assertEquals("0", text("swaps")); assertEquals("访问顺序：[A, B, C, D, E]", text("graph-order"));
        assertTrue(text("graph-parents").contains(parent)); assertEquals("不可达：[F]", text("graph-unvisited"));
    }
    private void mode(String id) { ((ToggleButton) view.lookup("#" + id)).fire(); }
    private String text(String id) { return ((Label) view.lookup("#" + id)).getText(); }
    private String fingerprint() {
        return String.join("|", List.of("array-values", "step-count", "event-name", "explanation", "comparisons", "writes", "swaps", "graph-current", "graph-edge", "graph-frontier", "graph-visited", "graph-order", "graph-parents", "graph-discovered", "graph-unvisited").stream().map(this::text).toList());
    }
    private int[] pixels() {
        var image = view.canvas().snapshot(null, null); int w = (int) image.getWidth(), h = (int) image.getHeight(); int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w); return pixels;
    }
    private int colorCount(int rgb) { int count = 0; for (int pixel : pixels()) if ((pixel & 0xffffff) == rgb) count++; return count; }
    private void capture(String name) throws Exception {
        Scene scene = stage.getScene(); var image = scene.snapshot(null); int w = (int) image.getWidth(), h = (int) image.getHeight(); int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); png.setRGB(0, 0, w, h, pixels, 0, w);
        Path path = Path.of("target", "gui-smoke", name); Files.createDirectories(path.getParent()); ImageIO.write(png, "png", path.toFile());
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>(); Platform.runLater(() -> { try { done.complete(action.call()); } catch (Throwable error) { done.completeExceptionally(error); } }); return done.get(5, TimeUnit.SECONDS);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!fx(condition::getAsBoolean)) { if (System.nanoTime() > deadline) fail("图遍历等待超时"); Thread.sleep(20); }
    }
}
