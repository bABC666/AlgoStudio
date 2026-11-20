package com.algostudio;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 可选的真实桌面渲染测试：-Dtest=GuiSmokeIT test；默认测试不要求桌面。 */
class GuiSmokeIT {
    @Test
    void opensRendersResizesAndClosesTheActualJavaFxStage() throws Exception {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> {
                uncaught.compareAndSet(null, error);
                completed.completeExceptionally(error);
            });
            Stage stage = new Stage();
            try {
                new App().start(stage);
                assertTrue(stage.isShowing());
                Scene scene = stage.getScene();
                snapshot(scene, "gui-initial.png");
                assertTrue(scene.getWidth() >= 1180);
                assertFalse(assertInstanceOf(Button.class, scene.lookup("#play-pause")).isDisabled());
                assertTrue(assertInstanceOf(Button.class, scene.lookup("#previous-step")).isDisabled());
                AtomicBoolean resizeObserved = new AtomicBoolean();
                Runnable verifyResized = () -> {
                    // 原生窗口尺寸异步传回 JavaFX；等待真实 Scene 尺寸变化，不能假定 runLater 已缩放。
                    if (completed.isDone() || scene.getWidth() >= 1180 || scene.getHeight() >= 760
                            || !resizeObserved.compareAndSet(false, true)) {
                        return;
                    }
                    // 离开原生 resize 通知后再操作 Stage，防止在该回调内部销毁 Scene。
                    Platform.runLater(() -> {
                        try {
                            snapshot(scene, "gui-resized.png");
                            assertTrue(scene.getWidth() < 1180);
                            Canvas canvas = assertInstanceOf(Canvas.class, scene.lookup("#array-canvas"));
                            assertTrue(canvas.getWidth() > 300);
                            assertTrue(canvas.getHeight() >= 180);
                            stage.close();
                            assertFalse(stage.isShowing());
                            Platform.runLater(() -> completed.complete(null));
                        } catch (Throwable error) {
                            stage.close();
                            completed.completeExceptionally(error);
                        }
                    });
                };
                scene.widthProperty().addListener((observable, before, after) -> verifyResized.run());
                scene.heightProperty().addListener((observable, before, after) -> verifyResized.run());
                stage.setWidth(1000);
                stage.setHeight(680);
            } catch (Throwable error) {
                stage.close();
                completed.completeExceptionally(error);
            }
        });
        try {
            completed.get(20, TimeUnit.SECONDS);
            assertNull(uncaught.get(), "JavaFX Application Thread 不得遗留未捕获异常");
        } finally {
            Platform.exit();
        }
    }

    private void snapshot(Scene scene, String filename) throws Exception {
        WritableImage image = scene.snapshot(null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        Path output = Path.of("target", "gui-smoke", filename);
        Files.createDirectories(output.getParent());
        assertTrue(ImageIO.write(buffered, "png", output.toFile()));
    }
}
