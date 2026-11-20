package com.algostudio;

import com.algostudio.ui.BenchmarkView;
import com.algostudio.ui.MainView;
import com.algostudio.service.BenchmarkService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** 实际Stage/控件/工作线程；受控失败仅用于状态测试，截图与结果均来自真实排序。 */
class BenchmarkGuiIT {
    @Test void realMeasurementsModeChangesCancellationFailureRecoveryAndMinimumLayout() throws Exception {
        CompletableFuture<Void> boot = new CompletableFuture<>();
        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Thread.currentThread().setUncaughtExceptionHandler((t, error) -> uncaught.compareAndSet(null, error));
            boot.complete(null);
        });
        boot.get(5, TimeUnit.SECONDS);
        AtomicReference<Stage> main = new AtomicReference<>(), controlled = new AtomicReference<>();
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch closeInterrupted = new CountDownLatch(1);
        try {
            MainView root = fx(() -> {
                Stage stage = new Stage(); main.set(stage); new App().start(stage);
                MainView view = (MainView) stage.getScene().getRoot();
                Label playbackStatus = (Label) view.lookup("#playback-status");
                view.playButton().fire();
                ((ToggleButton) view.lookup("#mode-benchmark")).fire();
                assertEquals("已暂停", playbackStatus.getText());
                return view;
            });
            BenchmarkView view = fx(root::benchmarkView);
            fx(() -> {
                assertEquals(500, view.sizeChoice().getValue());
                view.runButton().fire(); view.runButton().fire();
                assertTrue(view.runButton().isDisabled());
                assertFalse(view.cancelButton().isDisabled());
                return null;
            });
            await(() -> !view.runButton().isDisabled());
            fx(() -> {
                assertEquals(3, view.resultTable().getItems().size());
                assertEquals(3, view.resultChart().getData().getFirst().getData().size());
                assertChartMatchesTable(view);
                var point=view.resultChart().getData().getFirst().getData().get(0);
                String actualName=point.getXValue();Number actualValue=point.getYValue();
                point.setXValue("controlled-wrong-name");
                assertThrows(AssertionError.class,()->assertChartMatchesTable(view));
                point.setXValue(actualName);point.setYValue(actualValue.doubleValue()+1);
                assertThrows(AssertionError.class,()->assertChartMatchesTable(view));
                point.setYValue(actualValue);assertChartMatchesTable(view);
                System.out.println("Chart negative controls: wrong algorithm name and median both rejected.");
                assertTrue(view.resultTable().getItems().stream().allMatch(r -> r.samplesNanos().size() == 5));
                capture(main.get().getScene(), "benchmark-500.png");
                main.get().setWidth(960); main.get().setHeight(640);
                return null;
            });
            await(() -> main.get().getScene().getWidth() < 1000);
            fx(() -> {
                Scene scene = main.get().getScene(); scene.getRoot().applyCss(); scene.getRoot().layout();
                var note = view.lookup("#benchmark-note");
                assertTrue(((Label) note).getText().contains("辅助缓冲区写入不计入"));
                assertTrue(note.localToScene(note.getBoundsInLocal()).getMaxY() <= scene.getHeight());
                assertTrue(view.resultChart().getHeight() >= 100);
                capture(scene, "benchmark-minimum.png");
                view.sizeChoice().getSelectionModel().select(Integer.valueOf(3000));
                assertTrue(view.resultTable().getItems().isEmpty());
                view.runButton().fire(); view.cancelButton().fire();
                assertFalse(view.runButton().isDisabled());
                assertTrue(view.cancelButton().isDisabled());
                view.runButton().fire();
                ((ToggleButton) root.lookup("#mode-visualization")).fire();
                assertFalse(view.runButton().isDisabled());
                assertTrue(view.resultTable().getItems().isEmpty());
                assertFalse(root.playButton().isDisabled());
                return null;
            });

            AtomicInteger calls = new AtomicInteger();
            BenchmarkView injected = fx(() -> {
                BenchmarkView v = new BenchmarkView(request -> {
                    int call = calls.incrementAndGet();
                    if (call == 1 || call == 4) {
                        try { new CountDownLatch(1).await(); }
                        catch (InterruptedException expected) {
                            if (call == 1) interrupted.countDown();
                            else closeInterrupted.countDown();
                            throw new CancellationException();
                        }
                    }
                    if (call == 2) throw new IllegalStateException("受控校验失败");
                    return new BenchmarkService().run(request);
                });
                Stage stage = new Stage(); controlled.set(stage);
                stage.setScene(new Scene(v, 960, 600)); stage.setOnHidden(e -> v.close()); stage.show();
                v.runButton().fire(); return v;
            });
            await(() -> calls.get() == 1);
            fx(() -> {
                // 工作线程阻塞时FX仍可操作；参数改变取消运行并清空结果。
                injected.sizeChoice().getSelectionModel().select(Integer.valueOf(100));
                assertFalse(injected.runButton().isDisabled());
                injected.runButton().fire(); return null;
            });
            assertTrue(interrupted.await(3, TimeUnit.SECONDS));
            await(() -> !injected.runButton().isDisabled());
            fx(() -> {
                assertTrue(((Label) injected.lookup("#benchmark-status")).getText().contains("受控校验失败"));
                assertTrue(injected.resultTable().getItems().isEmpty());
                injected.runButton().fire(); return null;
            });
            await(() -> !injected.runButton().isDisabled());
            fx(() -> {
                assertEquals(3, injected.resultTable().getItems().size());
                injected.runButton().fire(); return null;
            });
            await(() -> calls.get() == 4);
            fx(() -> { controlled.get().close(); main.get().close(); return null; });
            assertTrue(closeInterrupted.await(3, TimeUnit.SECONDS), "关闭窗口必须中断第四次后台运行");
            assertNull(uncaught.get());
        } finally {
            fx(() -> {
                if (controlled.get() != null) controlled.get().close();
                if (main.get() != null) main.get().close();
                return null;
            });
            Platform.exit();
        }
    }

    private static void assertChartMatchesTable(BenchmarkView view){
        var points=view.resultChart().getData().getFirst().getData();var rows=view.resultTable().getItems();
        assertEquals(rows.size(),points.size());
        for(int i=0;i<rows.size();i++){
            assertEquals(rows.get(i).algorithm().name(),points.get(i).getXValue());
            assertEquals(rows.get(i).medianMillis(),points.get(i).getYValue().doubleValue(),1e-9);
        }
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        Platform.runLater(() -> { try { done.complete(action.call()); } catch (Throwable error) { done.completeExceptionally(error); } });
        return done.get(5, TimeUnit.SECONDS);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!fx(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) fail("等待JavaFX状态超时");
            Thread.sleep(20);
        }
    }
    private static void capture(Scene scene, String name) throws Exception {
        var image = scene.snapshot(null);
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] pixels = new int[w * h];
        image.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0, 0, w, h, pixels, 0, w);
        Path path = Path.of("target", "gui-smoke", name); Files.createDirectories(path.getParent());
        ImageIO.write(png, "png", path.toFile());
    }
}
