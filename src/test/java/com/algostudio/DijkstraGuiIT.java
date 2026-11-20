package com.algostudio;

import com.algostudio.ui.*;
import javafx.application.Platform;
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

class DijkstraGuiIT {
    private MainView view;
    private Stage stage;
    private String resizedFrame;

    @Test void distancesParentsPathsRollbackPlaybackSwitchResizeAndClose() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>(); AtomicReference<Throwable> uncaught = new AtomicReference<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Thread.currentThread().setUncaughtExceptionHandler((t, error) -> uncaught.compareAndSet(null, error)); ready.complete(null);
        }); ready.get(5, TimeUnit.SECONDS);
        try {
            fx(() -> {
                stage = new Stage(); new App().start(stage); view = (MainView) stage.getScene().getRoot(); mode("mode-graph");
                view.graphControls().exampleChoice().getSelectionModel().select(1); selectDijkstra(); view.applyCss(); view.layout();
                assertEquals("Dijkstra 最短路径", text("algorithm-title")); assertEquals("0", text("graph-dist-A")); assertEquals("∞", text("graph-dist-F"));
                assertEquals("0", text("comparisons")); assertTrue(text("graph-visited").contains("已确定"));
                target("C");
                for (int i = 0; i < 50 && !(text("event-name").equals("松弛边") && text("graph-dist-C").equals("3")); i++) view.nextButton().fire();
                assertEquals("松弛边", text("event-name")); assertEquals("3", text("graph-dist-C")); assertEquals("B", text("graph-prev-C"));
                assertEquals("暂定", text("graph-state-C")); assertTrue(text("graph-path").contains("A → B → C")); assertTrue(text("graph-path").contains("暂定"));
                String frame = fingerprint(); int[] image = pixels(); capture("dijkstra-relax.png");
                for (int i = 0; i < 6; i++) view.nextButton().fire();
                for (int i = 0; i < 6; i++) view.previousButton().fire();
                assertEquals(frame, fingerprint()); assertArrayEquals(image, pixels());
                assertActiveBcInterior(); resizedFrame = fingerprint(); stage.setWidth(960); stage.setHeight(640); return null;
            });
            await(() -> stage.getScene().getWidth() < 1000);
            fx(() -> {
                view.applyCss(); view.layout(); assertEquals(resizedFrame, fingerprint()); assertActiveBcInterior();
                view.graphControls().setVvalue(1); view.layout(); capture("dijkstra-minimum.png");
                var path = view.lookup("#graph-path"); var bounds = path.localToScene(path.getBoundsInLocal());
                assertTrue(bounds.getMinY() > 0 && bounds.getMaxY() <= stage.getScene().getHeight());
                target("E"); finish(); assertDone(); capture("dijkstra-done.png");
                target("F"); assertTrue(text("graph-path").contains("不可达"));
                view.resetButton().fire(); view.speedChoice().getSelectionModel().select("4×"); view.playButton().fire(); return null;
            });
            await(() -> text("playback-status").equals("已完成"));
            fx(() -> {
                assertDone(); view.playButton().fire(); return null;
            });
            await(() -> !text("step-count").startsWith("第 1 /"));
            fx(() -> { view.playButton().fire(); assertEquals("已暂停", text("playback-status")); return null; });
            String paused = fx(this::fingerprint); Thread.sleep(350); assertEquals(paused, fx(this::fingerprint));
            fx(() -> {
                view.playButton().fire(); view.graphControls().startChoice().getSelectionModel().select("F"); finish();
                assertEquals("0", text("graph-dist-F")); assertEquals("∞", text("graph-dist-A")); assertEquals("1", text("comparisons"));
                assertEquals("0", text("writes")); assertEquals("0", text("swaps")); capture("dijkstra-isolated.png");
                view.graphControls().startChoice().getSelectionModel().select("A"); view.nextButton().fire(); resizedFrame = fingerprint();
                mode("mode-benchmark"); view.benchmarkView().runButton().fire(); return null;
            });
            await(() -> !view.benchmarkView().runButton().isDisabled());
            fx(() -> {
                assertEquals(3, view.benchmarkView().resultTable().getItems().size()); mode("mode-graph"); assertEquals(resizedFrame, fingerprint());
                view.playButton().fire(); view.graphControls().algorithmChoice().getSelectionModel().select(GraphRunMode.BFS);
                assertTrue(text("step-count").startsWith("第 1 /")); assertFalse(view.lookup("#graph-distance-table").isVisible());
                assertFalse(view.lookup("#graph-path").isVisible()); assertEquals("0", text("swaps"));
                view.graphControls().algorithmChoice().getSelectionModel().select(GraphRunMode.DFS); selectDijkstra();
                assertTrue(text("step-count").startsWith("第 1 /")); assertEquals("∞", text("graph-dist-E"));
                mode("mode-visualization"); view.arrayInput().setText("5,2,4,1,3"); view.generateButton().fire();
                assertEquals("[5, 2, 4, 1, 3]", text("array-values")); mode("mode-graph"); selectDijkstra();
                view.playButton().fire(); stage.close(); return null;
            });
            // 关闭释放轨迹，并清空动态距离行；只比较仍存在的播放器控件，防止旧 tick 复活。
            String closed = fx(this::closedFingerprint); Thread.sleep(350); assertEquals(closed, fx(this::closedFingerprint)); assertNull(uncaught.get());
            System.out.println("Dijkstra GUI: dist/prev/state/path, infinity, exact rollback, active edge interior after resize, 4x playback/pause, switching, benchmark return, close passed.");
        } finally { fx(() -> { if (stage != null) stage.close(); return null; }); Platform.exit(); }
    }
    private void selectDijkstra() {
        var mode = Arrays.stream(GraphRunMode.values()).filter(m -> m.algorithmId().equals("dijkstra")).findFirst();
        assertTrue(mode.isPresent(), "Dijkstra 应为已实现菜单项"); view.graphControls().algorithmChoice().getSelectionModel().select(mode.orElseThrow());
    }
    private void target(String id) {
        var choice = (ComboBox<?>) view.lookup("#graph-target");
        int index = choice.getItems().indexOf(id); assertTrue(index >= 0); choice.getSelectionModel().select(index);
    }
    private void assertDone() {
        assertEquals("已完成", text("playback-status")); assertEquals("最短路径完成", text("event-name"));
        assertEquals("5", text("comparisons")); assertEquals("12", text("writes")); assertEquals("6", text("swaps"));
        assertEquals("7", text("graph-dist-E")); assertEquals("D", text("graph-prev-E")); assertEquals("已确定", text("graph-state-E"));
        assertEquals("∞", text("graph-dist-F")); assertEquals("不可达", text("graph-state-F"));
        assertEquals("不可达：[F]", text("graph-unvisited"));
        target("E"); assertTrue(text("graph-path").contains("A → B → C → D → E")); assertTrue(text("graph-path").contains("确定"));
    }
    private void finish() { for (int i = 0; i < 100 && !view.nextButton().isDisabled(); i++) view.nextButton().fire(); assertTrue(view.nextButton().isDisabled()); }
    private void mode(String id) { ((ToggleButton) view.lookup("#" + id)).fire(); }
    private String text(String id) { return ((Label) view.lookup("#" + id)).getText(); }
    private String closedFingerprint() {
        assertNull(view.lookup("#graph-dist-A"));
        return String.join("|", text("step-count"), text("playback-status"), text("explanation"), text("comparisons"), text("writes"), text("swaps"));
    }
    private String fingerprint() {
        List<String> ids = new ArrayList<>(List.of("step-count", "event-name", "explanation", "comparisons", "writes", "swaps", "graph-current", "graph-edge", "graph-visited", "graph-order", "graph-parents", "graph-unvisited", "graph-path"));
        for (String id : List.of("A", "B", "C", "D", "E", "F")) for (String field : List.of("dist", "prev", "state")) ids.add("graph-" + field + "-" + id);
        return String.join("|", ids.stream().map(this::text).toList());
    }
    private int[] pixels() {
        var image = view.canvas().snapshot(null, null); int w = (int) image.getWidth(), h = (int) image.getHeight(); int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w); return pixels;
    }
    // B-C 内部27%处，避开两个节点与边权标签，不能由节点橙色冒充活动边。
    private void assertActiveBcInterior() {
        assertTrue(text("graph-edge").contains("B—C")); var image = view.canvas().snapshot(null, null);
        double w = image.getWidth(), h = image.getHeight(), pad = Math.min(20, Math.max(12, (h - 40) / 8)) + 12;
        int x = (int) Math.round(pad + (w - 2 * pad) * .36), y = (int) Math.round(pad + (h - 2 * pad) * (.17 + (.75 - .17) * .27));
        boolean amber = false;
        for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) if ((image.getPixelReader().getArgb(x + dx, y + dy) & 0xffffff) == 0xd99719) amber = true;
        assertTrue(amber, "活动边内部应为橙色");
    }
    private void capture(String name) throws Exception {
        var image = stage.getScene().snapshot(null); int w = (int) image.getWidth(), h = (int) image.getHeight(); int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); png.setRGB(0, 0, w, h, pixels, 0, w);
        Path path = Path.of("target", "gui-smoke", name); Files.createDirectories(path.getParent()); ImageIO.write(png, "png", path.toFile());
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>(); Platform.runLater(() -> { try { done.complete(action.call()); } catch (Throwable error) { done.completeExceptionally(error); } }); return done.get(5, TimeUnit.SECONDS);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!fx(condition::getAsBoolean)) { if (System.nanoTime() > deadline) fail("最短路界面等待超时"); Thread.sleep(20); }
    }
}
