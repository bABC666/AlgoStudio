package com.algostudio;

import com.algostudio.ui.MainView;
import com.algostudio.ui.renderer.GraphRenderer;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.frame.*;
import com.algostudio.model.graph.FrontierKind;
import com.algostudio.model.input.GraphInput;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
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

class GraphGuiIT {
    private MainView view;
    private Stage stage;
    private String beforeResize, edgeBeforeResize;
    @Test void fixedGraphsHighlightRollbackIsolatedStartResizeModeChangesAndClose() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Thread.currentThread().setUncaughtExceptionHandler((t, error) -> uncaught.compareAndSet(null, error));
            ready.complete(null);
        }); ready.get(5, TimeUnit.SECONDS);
        try {
            fx(() -> {
                stage = new Stage(); new App().start(stage); view = (MainView) stage.getScene().getRoot();
                view.playButton().fire(); mode("mode-graph");
                view.applyCss(); view.layout(); // 新插入ScrollPane须完成skin/layout后再查其标签。
                assertTrue(text("algorithm-description").contains("不执行遍历"));
                assertEquals("图状态预览", text("algorithm-title"));
                assertEquals("A", view.graphControls().startChoice().getValue());
                assertTrue(text("array-values").contains("7顶点"));
                assertEquals("—", text("comparisons"));
                String initial = fingerprint(); int[] initialPixels = pixels();
                capture("graph-initial.png");
                view.nextButton().fire();
                assertTrue(text("graph-frontier").contains("队列")); assertTrue(text("graph-frontier").contains("[A]"));
                int nodeColor = colorCount(0xd99719); assertTrue(nodeColor > 100);
                view.nextButton().fire(); assertTrue(colorCount(0xd99719) > nodeColor + 50);
                capture("graph-edge.png");
                view.nextButton().fire(); assertTrue(text("graph-frontier").contains("栈"));
                assertTrue(colorCount(0x239a6d) > 100); capture("graph-stack.png");
                view.nextButton().fire(); assertEquals("预览完成", text("playback-status"));
                for (int i = 0; i < 4; i++) view.previousButton().fire();
                assertEquals(initial, fingerprint()); assertArrayEquals(initialPixels, pixels());
                for (int round = 0; round < 5; round++) {
                    for (int i = 0; i < 4; i++) view.nextButton().fire();
                    for (int i = 0; i < 4; i++) view.previousButton().fire();
                }
                assertEquals(initial, fingerprint()); assertArrayEquals(initialPixels, pixels());
                view.speedChoice().getSelectionModel().select("4×"); view.playButton().fire();
                view.graphControls().exampleChoice().getSelectionModel().select(1);
                view.graphControls().startChoice().getSelectionModel().select("F"); return null;
            });
            Thread.sleep(350);
            fx(() -> {
                assertTrue(text("step-count").startsWith("第 1 /"));
                view.nextButton().fire(); view.nextButton().fire();
                assertTrue(text("explanation").contains("孤立"));
                assertTrue(text("graph-frontier").contains("[F]")); capture("graph-isolated-f.png");
                view.graphControls().startChoice().getSelectionModel().select("A");
                view.nextButton().fire(); view.nextButton().fire();
                beforeResize = fingerprint(); edgeBeforeResize = text("graph-edge");
                stage.setWidth(960); stage.setHeight(640); return null;
            });
            await(() -> stage.getScene().getWidth() < 1000);
            String beforeBenchmark = fx(() -> {
                view.applyCss(); view.layout();
                assertEquals(beforeResize, fingerprint()); assertEquals(edgeBeforeResize, text("graph-edge"));
                assertTrue(colorCount(0xd99719) > 100);
                assertTrue(view.canvas().getHeight() >= 110);
                view.graphControls().exampleChoice().getSelectionModel().select(0);
                view.graphControls().startChoice().getSelectionModel().select("A");
                view.nextButton().fire(); view.nextButton().fire();
                capture("graph-teaching-minimum.png");
                view.graphControls().exampleChoice().getSelectionModel().select(1);
                view.graphControls().startChoice().getSelectionModel().select("A");
                view.nextButton().fire(); view.nextButton().fire();
                for (String id : List.of("graph-start", "graph-preview", "graph-frontier", "play-pause", "explanation")) {
                    var node = view.lookup("#" + id);
                    var bounds = node.localToScene(node.getBoundsInLocal());
                    assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= stage.getScene().getHeight() + 1, id);
                }
                capture("graph-minimum.png"); return fingerprint();
            });
            int[] beforePixels = fx(this::pixels);
            fx(() -> { mode("mode-benchmark"); view.benchmarkView().runButton().fire(); return null; });
            await(() -> !view.benchmarkView().runButton().isDisabled());
            fx(() -> {
                assertEquals(3, view.benchmarkView().resultTable().getItems().size());
                mode("mode-graph"); view.applyCss(); view.layout();
                assertEquals(beforeBenchmark, fingerprint()); assertArrayEquals(beforePixels, pixels());
                mode("mode-visualization");
                view.arrayInput().setText("5,2,4,1,3"); view.generateButton().fire();
                assertEquals("[5, 2, 4, 1, 3]", text("array-values")); view.playButton().fire();
                mode("mode-graph"); assertTrue(text("step-count").startsWith("第 1 /"));
                view.playButton().fire(); return null;
            });
            await(() -> text("playback-status").equals("预览完成"));
            fx(() -> {
                var empty = new GraphFrame(new GraphInput(List.of(), List.of()), Set.of(), null, null, List.of(),
                        FrontierKind.NONE, Map.of(), Map.of(), List.of(), EventKind.INITIAL, AlgorithmMetrics.ZERO, "空图", null);
                new GraphRenderer().render(empty, new Canvas(500, 300));
                new GraphRenderer().render(empty, new Canvas(0, 0));
                view.playButton().fire(); stage.close(); return null;
            });
            String closed = fx(this::fingerprint); Thread.sleep(350);
            assertEquals(closed, fx(this::fingerprint)); assertNull(uncaught.get());
            System.out.println("Graph: fixed definitions, node/edge colors, exact rollback, isolatedF, minimum layout, benchmark return, shared playback and close passed.");
        } finally { fx(() -> { if (stage != null) stage.close(); return null; }); Platform.exit(); }
    }
    private void mode(String id) { ((ToggleButton) view.lookup("#" + id)).fire(); }
    private String text(String id) { return ((Label) view.lookup("#" + id)).getText(); }
    private String fingerprint() {
        return String.join("|", List.of("array-values", "step-count", "event-name", "explanation", "comparisons", "writes", "swaps", "graph-current", "graph-frontier", "graph-visited")
                .stream().map(this::text).toList());
    }
    private int[] pixels() {
        var image = view.canvas().snapshot(null, null); int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] pixels = new int[w * h]; image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w); return pixels;
    }
    private int colorCount(int rgb) { int count = 0; for (int pixel : pixels()) if ((pixel & 0xffffff) == rgb) count++; return count; }
    private void capture(String name) throws Exception {
        Scene scene = stage.getScene(); var image = scene.snapshot(null); int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] pixels = new int[w * h]; image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); png.setRGB(0, 0, w, h, pixels, 0, w);
        Path path = Path.of("target", "gui-smoke", name); Files.createDirectories(path.getParent()); ImageIO.write(png, "png", path.toFile());
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>(); Platform.runLater(() -> {
            try { done.complete(action.call()); } catch (Throwable error) { done.completeExceptionally(error); }
        }); return done.get(5, TimeUnit.SECONDS);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!fx(condition::getAsBoolean)) { if (System.nanoTime() > deadline) fail("图预览等待超时"); Thread.sleep(20); }
    }
}
