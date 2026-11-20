package com.algostudio;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.text.Text;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** 独立JVM执行 -Dtest=InputGuiIT test；输入始终仅作为数据。 */
class InputGuiIT {
    private final CompletableFuture<Void> completed = new CompletableFuture<>();
    private final AtomicReference<Throwable> uncaught = new AtomicReference<>();
    private Stage stage;
    private Scene scene;
    private String frozen;

    @Test
    void validatesRecoversRandomizesAndCancelsStalePlaybackAtInputBoundaries() throws Exception {
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
                assertEquals(12, sourceValues().length, "首次启动默认应为12项随机数组");
                assertEquals(Arrays.toString(sourceValues()), text("array-values"));
                assertEquals("就绪", text("playback-status"));
                assertFalse(node("input-error").isVisible());
                snapshot("input-default.png");
                validationAndRecovery();
            });
        });
        try {
            completed.get(25, TimeUnit.SECONDS);
            assertNull(uncaught.get());
        } finally {
            Platform.exit();
        }
    }

    private void validationAndRecovery() throws Exception {
        setInput("5, 2 4, 1, 3");
        assertTrue(button("play-pause").isDisabled());
        assertEquals("—", text("array-values"));
        generate();
        assertEquals("[5, 2, 4, 1, 3]", text("array-values"));
        String fiftyOne = IntStream.rangeClosed(1, 51).mapToObj(Integer::toString).collect(Collectors.joining(","));
        for (String invalid : new String[]{"", ",, ,", "1,2,a,4,5", "1,2,3.5,4,5", "1,2,2147483648,4,5",
                "1,2,0,4,5", "1,2,101,4,5", "1,2,3,4", fiftyOne, "1,2,System.exit(0),4,5"}) {
            setInput(invalid);
            generate();
            assertRejected();
            if (invalid.contains("a")) snapshot("input-error.png");
            setInput("5,2,4,1,3");
            generate();
            assertEquals("就绪", text("playback-status"));
            assertFalse(node("input-error").isVisible());
            assertEquals("[5, 2, 4, 1, 3]", text("array-values"));
        }
        String boundary = "5,2,4,1,3" + " ".repeat(4087);
        assertEquals(4096, boundary.length());
        setInput(boundary);
        generate();
        assertEquals("就绪", text("playback-status"));
        input().appendText("0");
        assertEquals(4096, input().getLength(), "4097字符编辑应被拒绝，不能静默截断或保存超长文本");
        assertRejected();
        assertTrue(text("input-error").contains("4096"));
        setInput("5,2,4,1,3");
        generate();
        snapshot("input-recovered.png");
        System.out.println("Input GUI: default12, invalid table, recovery, exact4096 and rejected4097 verified.");
        rawPasteBoundaries();
    }

    private void rawPasteBoundaries() throws Exception {
        for (int separator : new int[]{13, 11, 12}) {
            paste("1" + (char) separator + "2,3,4,5,6");
            generate();
            assertEquals("[1, 2, 3, 4, 5, 6]", text("array-values"), "粘贴分隔符不得被删除后合并数值");
        }
        paste("1\r\n2,3,4,5,6");
        generate();
        assertEquals("[1, 2, 3, 4, 5, 6]", text("array-values"));
        snapshot("input-paste-normalized.png");
        String boundary = "5,2,4,1,3" + " ".repeat(4087);
        setInput(boundary);
        input().selectRange(4090, 4096);
        input().replaceSelection("\t     ");
        assertEquals(4096, input().getLength());
        assertEquals('\t', input().getText().charAt(4090));
        generate();
        assertEquals("就绪", text("playback-status"));
        String before = input().getText();
        input().selectRange(4090, 4096);
        input().replaceSelection(" ".repeat(7));
        assertEquals(before, input().getText());
        assertRejected();
        assertTrue(text("input-error").contains("4096"));
        combo("playback-speed").getSelectionModel().select(2);
        for (int control : new int[]{0, 1, 8, 27, 127, 133}) {
            setInput("5,2,4,1,3");
            generate();
            button("play-pause").fire();
            String previousText = input().getText();
            paste("1" + (char) control + "2,3,4,5,6");
            assertEquals(previousText, input().getText());
            assertRejected();
            assertTrue(text("input-error").contains("控制字符"));
        }
        snapshot("input-paste-rejected.png");
        frozen = fingerprint();
        later(400, () -> {
            assertEquals(frozen, fingerprint(), "拒绝粘贴后旧播放不得回写");
            generate();
            assertEquals("就绪", text("playback-status"));
            System.out.println("Input GUI: raw paste CR/VT/FF normalized; illegal controls rejected atomically; selected replacement boundaries verified.");
            beginEditingCancellation();
        });
    }

    private void beginEditingCancellation() {
        combo("playback-speed").getSelectionModel().select(2);
        button("play-pause").fire();
        setInput("9,8,7,6,5");
        assertTrue(button("play-pause").isDisabled());
        assertEquals("—", text("array-values"));
        frozen = fingerprint();
        later(400, this::editedInputStopsOldPlayback);
    }

    private void editedInputStopsOldPlayback() {
        assertEquals(frozen, fingerprint());
        generate();
        assertEquals("[9, 8, 7, 6, 5]", text("array-values"));
        button("play-pause").fire();
        setInput("1,2,a,4,5");
        generate();
        assertRejected();
        frozen = fingerprint();
        later(400, () -> {
            assertEquals(frozen, fingerprint(), "错误状态不得被旧播放覆盖");
            combo("algorithm-choice").getSelectionModel().select(1);
            assertRejected();
            setInput("5,2,4,1,3");
            generate();
            for (int click = 0; click < 8; click++) generate();
            assertEquals("就绪", text("playback-status"));
            assertTrue(text("step-count").startsWith("第 1 /"));
            button("play-pause").fire();
            combo("random-size").getSelectionModel().selectFirst();
            button("random-array").fire();
            assertRandomPreview(5);
            frozen = fingerprint();
            later(350, this::randomGenerationAndMinimumWindow);
        });
    }

    private void randomGenerationAndMinimumWindow() {
        assertEquals(frozen, fingerprint(), "随机生成必须取消旧播放");
        combo("random-size").getSelectionModel().selectLast();
        button("random-array").fire();
        assertRandomPreview(50);
        AtomicBoolean resized = new AtomicBoolean();
        Runnable finish = () -> {
            if (scene.getWidth() >= 1180 || scene.getHeight() >= 760 || !resized.compareAndSet(false, true)) return;
            Platform.runLater(() -> check(() -> {
                snapshot("input-fifty-minimum.png");
                assertRandomPreview(50);
                Label values = assertInstanceOf(Label.class, node("array-values"));
                Text painted = assertInstanceOf(Text.class, values.lookup(".text"));
                assertEquals(values.getText(), painted.getText(), "50项当前数组不能以省略号隐藏数据");
                for (String id : new String[]{"generate-trace", "array-input", "random-array", "play-pause", "explanation"}) {
                    Node node = node(id);
                    assertTrue(node.localToScene(node.getBoundsInLocal()).getMaxY() <= scene.getHeight() + 1,
                            id + " 在最小窗口中应可见");
                }
                Node resultCard = scene.lookup(".result-card");
                assertTrue(resultCard.localToScene(resultCard.getBoundsInLocal()).getMaxY() <= scene.getHeight() + 1,
                        "最小窗口下整个结果卡片应可见，不能只保留文字而裁掉卡片底部");
                setInput(IntStream.generate(() -> 100).limit(50).mapToObj(Integer::toString).collect(Collectors.joining(", ")));
                generate();
                snapshot("input-fifty-hundreds.png");
                Label maximumValues = assertInstanceOf(Label.class, node("array-values"));
                assertEquals(maximumValues.getText(), assertInstanceOf(Text.class, maximumValues.lookup(".text")).getText());
                assertTrue(resultCard.localToScene(resultCard.getBoundsInLocal()).getMaxY() <= scene.getHeight() + 1);
                System.out.println("Input GUI: input edit/error/random canceled old playback; 5/50 items and minimum layout verified.");
                button("play-pause").fire();
                stage.close();
                assertFalse(stage.isShowing());
                completed.complete(null);
            }));
        };
        scene.widthProperty().addListener((observable, before, after) -> finish.run());
        scene.heightProperty().addListener((observable, before, after) -> finish.run());
        stage.setWidth(960);
        stage.setHeight(640);
    }

    private void assertRandomPreview(int expectedSize) {
        int[] values = sourceValues();
        assertEquals(expectedSize, values.length);
        assertTrue(Arrays.stream(values).allMatch(value -> value >= 1 && value <= 100));
        assertEquals(Arrays.toString(values), text("array-values"));
        assertEquals("就绪", text("playback-status"));
        assertEquals("0", text("comparisons"));
        assertFalse(button("generate-trace").isDisabled());
        assertFalse(button("random-array").isDisabled());
    }

    private void assertRejected() {
        assertEquals("生成失败", text("playback-status"));
        assertTrue(node("input-error").isVisible());
        assertFalse(text("input-error").isBlank());
        assertTrue(button("play-pause").isDisabled());
        assertTrue(button("next-step").isDisabled());
        assertEquals("—", text("array-values"));
        for (String metric : new String[]{"comparisons", "writes", "swaps"}) assertEquals("0", text(metric));
        assertFalse(button("generate-trace").isDisabled());
        assertFalse(button("random-array").isDisabled());
    }

    private int[] sourceValues() {
        return Arrays.stream(input().getText().split(",\\s*")).mapToInt(Integer::parseInt).toArray();
    }
    private void setInput(String text) { input().setText(text); }
    private void paste(String text) { input().selectAll(); input().replaceSelection(text); }
    private void generate() { button("generate-trace").fire(); }
    private TextArea input() { return assertInstanceOf(TextArea.class, node("array-input")); }
    private Button button(String id) { return assertInstanceOf(Button.class, node(id)); }
    private String text(String id) { return assertInstanceOf(Label.class, node(id)).getText(); }
    private ComboBox<?> combo(String id) { return (ComboBox<?>) node(id); }
    private Node node(String id) { return assertNotNullNode(scene.lookup("#" + id), id); }
    private Node assertNotNullNode(Node node, String id) { assertNotNull(node, "缺少控件 #" + id); return node; }
    private String fingerprint() { return text("array-values") + text("step-count") + text("comparisons") + text("playback-status"); }

    private void later(int milliseconds, Action action) {
        PauseTransition delay = new PauseTransition(Duration.millis(milliseconds));
        delay.setOnFinished(event -> check(action));
        delay.play();
    }
    private void check(Action action) {
        if (completed.isDone()) return;
        try { action.run(); }
        catch (Throwable error) { if (stage != null) stage.close(); completed.completeExceptionally(error); }
    }
    private void snapshot(String name) throws Exception {
        WritableImage image = scene.snapshot(null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        Path path = Path.of("target", "gui-smoke", name);
        Files.createDirectories(path.getParent());
        assertTrue(ImageIO.write(buffered, "png", path.toFile()));
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
}
