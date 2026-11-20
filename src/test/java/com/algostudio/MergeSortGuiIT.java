package com.algostudio;

import com.algostudio.ui.MainView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelFormat;
import javafx.stage.Stage;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** 单独JVM运行：真实Scene、Canvas、Timeline和后台三算法测量。 */
class MergeSortGuiIT {
    private MainView view;
    private Stage stage;
    private final Map<String, Label> labels = new HashMap<>();
    private final AtomicReference<Throwable> uncaught = new AtomicReference<>();

    @Test void rendersSplitAndWriteRestoresHistoryCancelsOldTicksAndComparesThreeAlgorithms() throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            fx(() -> {
                Thread.currentThread().setUncaughtExceptionHandler((t,e) -> uncaught.compareAndSet(null,e));
                stage = new Stage(); new App().start(stage); view = (MainView)stage.getScene().getRoot();
                for(String id:new String[]{"algorithm-title","algorithm-description","playback-status","step-count","array-values","event-name","explanation","comparisons","writes","swaps"})
                    labels.put(id,(Label)view.lookup("#"+id));
                assertEquals(3, view.algorithmChoice().getItems().size());
                view.arrayInput().setText("5,2,4,1,3");
                view.algorithmChoice().getSelectionModel().select(2);
                assertEquals("归并排序", text("algorithm-title"));
                assertTrue(text("algorithm-description").contains("辅助"));
                return null;
            });
            String initial = fx(this::fingerprint); int[] initialPixels = fx(this::pixels);
            fx(() -> {
                view.nextButton().fire(); assertEquals("分治区间", text("event-name"));
                assertTrue(text("explanation").contains("[0, 2)"));
                assertTrue(text("explanation").contains("[2, 5)"));
                assertTrue(colorCount(0xedf3ff) > 500); assertTrue(colorCount(0xf3edff) > 500);
                capture("merge-divide.png");
                for (int i=1;i<10;i++) view.nextButton().fire();
                for (int i=0;i<10;i++) view.previousButton().fire();
                assertEquals(initial, fingerprint()); assertArrayEquals(initialPixels, pixels());
                while (!text("event-name").equals("写入") && !view.nextButton().isDisabled()) view.nextButton().fire();
                assertEquals("写入", text("event-name")); assertEquals("1", text("writes"));
                assertEquals("0", text("swaps")); assertTrue(colorCount(0x0f9493)>100);
                assertTrue(text("explanation").contains("主数组下标")); capture("merge-write.png");
                String written = fingerprint(); int[] writtenPixels=pixels();
                view.previousButton().fire(); assertEquals("比较", text("event-name"));
                assertTrue(colorCount(0xd99719)>100);
                view.nextButton().fire(); assertEquals(written,fingerprint()); assertArrayEquals(writtenPixels,pixels());
                view.speedChoice().getSelectionModel().select(2); view.resetButton().fire(); view.playButton().fire();
                return null;
            });
            await(() -> text("playback-status").equals("已完成"));
            fx(() -> {
                assertEquals("[1, 2, 3, 4, 5]", text("array-values"));
                assertEquals("8", text("comparisons")); assertEquals("12", text("writes"));
                assertEquals("0", text("swaps")); assertTrue(colorCount(0x239a6d)>500);
                assertEquals("重新播放",view.playButton().getText()); capture("merge-done.png");
                view.playButton().fire(); assertEquals("播放中",text("playback-status")); return null;
            });
            awaitNonzero();
            fx(() -> { view.playButton().fire(); assertEquals("已暂停",text("playback-status")); return null; });
            stableAfterOldTick();
            // True playing -> performance pauses and preserves this merge frame on return.
            startPlaying();
            fx(() -> { mode("mode-benchmark"); return null; });
            stableAfterOldTick();
            fx(() -> { mode("mode-visualization"); assertEquals("已暂停",text("playback-status")); return null; });
            String paused=fx(this::fingerprint);
            fx(() -> { mode("mode-benchmark"); view.benchmarkView().runButton().fire(); return null; });
            await(() -> !view.benchmarkView().runButton().isDisabled());
            fx(() -> {
                var benchmark=view.benchmarkView();
                assertEquals(3,benchmark.resultTable().getItems().size());
                assertEquals(3,benchmark.resultChart().getData().getFirst().getData().size());
                var merge=benchmark.resultTable().getItems().get(2);
                assertEquals("merge-sort",merge.algorithm().id()); assertEquals(0,merge.metrics().swaps());
                assertTrue(merge.metrics().writes()>0);
                stage.setWidth(960);stage.setHeight(640);return null;
            });
            await(() -> stage.getScene().getWidth()<=960);
            fx(() -> {
                view.applyCss();view.layout();
                var table=view.benchmarkView().resultTable();
                var rows=table.lookupAll(".table-row-cell").stream()
                        .filter(node -> node instanceof javafx.scene.control.TableRow<?> row && row.getItem()!=null).toList();
                assertTrue(rows.size()>=3,"三条性能结果必须在窗口中可见");
                for(var row:rows) assertTrue(row.localToScene(row.getBoundsInLocal()).getMaxY()<=table.localToScene(table.getBoundsInLocal()).getMaxY()-1,
                        "第三行不能被表格底部裁切");
                capture("merge-benchmark.png");mode("mode-visualization");
                assertEquals(paused,fingerprint());return null;
            });
            // Actual nonzero merge playback -> algorithm, input and category changes.
            startPlaying();
            fx(() -> {view.algorithmChoice().getSelectionModel().select(1);assertEquals("快速排序",text("algorithm-title"));assertReady();return null;});
            stableAfterOldTick();
            fx(() -> {view.algorithmChoice().getSelectionModel().select(2);return null;}); startPlaying();
            fx(() -> {view.arrayInput().setText("3,1,3,2,1");assertEquals("未载入",text("playback-status"));view.generateButton().fire();assertReady();return null;});
            stableAfterOldTick();startPlaying();
            fx(() -> {mode("mode-graph");assertReady();return null;});stableAfterOldTick();
            fx(() -> {mode("mode-visualization");return null;});startPlaying();
            fx(() -> {mode("mode-table");assertReady();return null;});stableAfterOldTick();
            fx(() -> {
                mode("mode-visualization");
                view.arrayInput().setText(IntStream.range(0,50).map(i->i%2==0?100:1).mapToObj(Integer::toString).collect(Collectors.joining(",")));
                view.generateButton().fire();view.nextButton().fire();view.applyCss();view.layout();
                assertTrue(colorCount(0xedf3ff)>100);assertTrue(colorCount(0xf3edff)>100);
                assertTrue(view.canvas().getWidth()>500);assertTrue(view.canvas().getHeight()>90);
                assertAll("最小窗口的分治与写回均应可读",
                        () -> {capture("merge-fifty-minimum.png");assertReadableMergeFrame();},
                        () -> {
                            while(!text("event-name").equals("写入")&&!view.nextButton().isDisabled())view.nextButton().fire();
                            assertEquals("写入",text("event-name"));
                            capture("merge-fifty-write-minimum.png");assertReadableMergeFrame();
                        });
                return null;
            });
            startPlaying();
            String closed=fx(() -> {stage.close();assertEquals("未载入",text("playback-status"));assertTrue(view.playButton().isDisabled());return fingerprint();});
            Thread.sleep(400);
            assertEquals(closed,fx(this::fingerprint));assertFalse(fx(stage::isShowing));assertNull(uncaught.get());
            System.out.println("Merge GUI: split/compare/write colors, exact history, 4x completion/replay, playing input/algorithm/category/mode switches, 50-item minimum window, three-row benchmark and close verified.");
        } finally { if(stage!=null)fx(() -> {stage.close();return null;});Platform.exit(); }
    }

    private void assertReady(){assertEquals("就绪",text("playback-status"));assertTrue(text("step-count").startsWith("第 1 /"));}
    private void startPlaying() throws Exception {fx(() -> {view.resetButton().fire();view.playButton().fire();return null;});awaitNonzero();}
    private void awaitNonzero() throws Exception {await(() -> !text("comparisons").equals("0")||!text("writes").equals("0"));fx(() -> {assertEquals("播放中",text("playback-status"));return null;});}
    private void stableAfterOldTick() throws Exception {String before=fx(this::fingerprint);Thread.sleep(400);assertEquals(before,fx(this::fingerprint));}
    private void mode(String id){((ToggleButton)view.lookup("#"+id)).fire();}
    private String text(String id){return labels.get(id).getText();}
    // Catches fixed margins consuming the plot, and a full label value hiding clipped skin text.
    private void assertReadableMergeFrame(){
        int[] p=pixels();int width=(int)view.canvas().getWidth(),height=(int)view.canvas().getHeight(),longest=0;
        for(int x=0;x<width;x++){
            int run=0;
            for(int y=0;y<height;y++){
                run=(p[y*width+x]&0xffffff)==0x4975d1?run+1:0;
                longest=Math.max(longest,run);
            }
        }
        int actualBarHeight=longest;
        Label explanation=labels.get("explanation");
        Text drawn=assertInstanceOf(Text.class,explanation.lookup(".text"));
        assertAll(
                ()->assertTrue(actualBarHeight>=40,"值100的柱高应至少40px，实际"+actualBarHeight),
                ()->assertEquals(explanation.getText(),drawn.getText(),"实际绘制的步骤说明不得截断或省略"),
                ()->assertTrue(drawn.localToScene(drawn.getBoundsInLocal()).getMaxY()<=stage.getScene().getHeight(),"完整说明应在窗口内"));
    }
    private String fingerprint(){return String.join("|",text("algorithm-title"),text("step-count"),text("array-values"),text("event-name"),text("explanation"),text("comparisons"),text("writes"),text("swaps"));}
    private int[] pixels(){view.applyCss();view.layout();var image=view.canvas().snapshot(null,null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] p=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),p,0,w);return p;}
    private long colorCount(int color){return Arrays.stream(pixels()).filter(p->(p&0xffffff)==color).count();}
    private void capture(String name)throws Exception{view.applyCss();view.layout();var image=stage.getScene().snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] p=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),p,0,w);var png=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,w,h,p,0,w);var path=Path.of("target","gui-smoke",name);Files.createDirectories(path.getParent());ImageIO.write(png,"png",path.toFile());}
    private static <T>T fx(Callable<T> action)throws Exception{var done=new CompletableFuture<T>();Platform.runLater(()->{try{done.complete(action.call());}catch(Throwable e){done.completeExceptionally(e);}});return done.get(6,TimeUnit.SECONDS);}
    private static void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>end)fail("等待JavaFX状态超时");Thread.sleep(20);}}
}
