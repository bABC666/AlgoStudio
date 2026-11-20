package com.algostudio;

import com.algostudio.ui.*;
import javafx.application.Platform;
import javafx.geometry.Point2D;
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

class DpGuiIT {
    private MainView view;private Stage stage;private String paused;
    @Test void realDpValuesSolutionsRollbackMaxTablesModeCancellationAndClose() throws Exception {
        var ready=new CompletableFuture<Void>();var uncaught=new AtomicReference<Throwable>();
        Platform.startup(()->{Platform.setImplicitExit(false);Thread.currentThread().setUncaughtExceptionHandler((t,e)->uncaught.compareAndSet(null,e));ready.complete(null);});ready.get(5,TimeUnit.SECONDS);
        try{
            fx(()->{stage=new Stage();new App().start(stage);view=(MainView)stage.getScene().getRoot();mode("mode-table");view.applyCss();view.layout();assertNotNull(view.lookup("#table-algorithm"),"须提供真实DP算法选择");algorithm(1);view.applyCss();view.layout();return null;});
            fx(()->{
                assertEquals("0/1 背包",text("algorithm-title"));assertEquals("0",text("comparisons"));assertFalse(text("table-preview-note").contains("示意"));assertFalse(text("table-result").contains("最大价值7"));
                seekCell("dp[2][5]");assertEquals("10",text("comparisons"));assertTrue(text("table-dependencies").contains("dp[1][2]"));capture("knapsack-update.png");
                String saved=fingerprint();int[] image=pixels();for(int i=0;i<6;i++)view.nextButton().fire();for(int i=0;i<6;i++)view.previousButton().fire();assertEquals(saved,fingerprint());assertArrayEquals(image,pixels());
                finish();assertEquals("15",text("comparisons"));assertTrue(text("table-result").contains("最大价值7"));assertTrue(text("table-result").contains("[1, 2]"));assertTrue(text("table-result").contains("总重量5"));capture("knapsack-done.png");
                view.previousButton().fire();assertFalse(text("table-result").contains("最大价值7"));view.nextButton().fire();assertTrue(text("table-result").contains("最大价值7"));
                algorithm(2);view.applyCss();view.layout();assertEquals("最长公共子序列 LCS",text("algorithm-title"));assertEquals("0",text("comparisons"));
                seekCell("dp[3][2]");assertEquals("6",text("comparisons"));assertTrue(text("table-dependencies").contains("dp[2][1]"));view.nextButton().fire();assertEquals("回溯解",text("event-name"));capture("lcs-backtrack.png");
                finish();assertTrue(text("table-result").contains("长度2"));assertTrue(text("table-result").contains("AC"));capture("lcs-done.png");
                view.resetButton().fire();assertFalse(text("table-result").contains("长度2"));view.speedChoice().getSelectionModel().select("4×");view.playButton().fire();return null;
            });
            await(()->text("playback-status").equals("已完成"));
            fx(()->{view.playButton().fire();return null;});await(()->!text("step-count").startsWith("第 1 /"));
            fx(()->{paused=fingerprint();mode("mode-benchmark");return null;});Thread.sleep(350);
            fx(()->{mode("mode-table");assertEquals(paused,fingerprint());assertEquals("已暂停",text("playback-status"));view.playButton().fire();algorithm(1);return null;});Thread.sleep(350);
            fx(()->{
                assertTrue(text("step-count").startsWith("第 1 /"));assertEquals("0",text("comparisons"));preset("knapsack-example",1);finish();assertTrue(text("table-shape").contains("4行 × 1列"));assertEquals("0",text("comparisons"));assertTrue(text("table-result").contains("最大价值0"));assertTrue(text("table-result").contains("[]"));capture("knapsack-zero-capacity.png");
                preset("knapsack-example",2);finish();assertTrue(text("table-result").contains("[1]"));preset("knapsack-example",3);finish();assertEquals("240",text("comparisons"));assertTrue(text("table-result").contains("最大价值800"));assertTrue(text("table-shape").contains("9行 × 31列"));
                stage.setWidth(960);stage.setHeight(640);return null;
            });await(()->stage.getScene().getWidth()<1000);
            fx(()->{view.applyCss();view.layout();scroll().setHvalue(1);scroll().setVvalue(1);return null;});
            fx(()->{assertLastVisible(8,30,9,31);capture("dp-knapsack-maximum.png");algorithm(2);view.applyCss();view.layout();return null;});
            fx(()->{
                preset("lcs-example",1);finish();assertTrue(text("table-result").contains("长度0"));assertTrue(text("table-result").contains("∅"));capture("lcs-empty-result.png");
                preset("lcs-example",2);finish();assertTrue(text("table-result").contains("AA"));preset("lcs-example",3);finish();assertTrue(text("table-result").contains("“A”"));
                preset("lcs-example",4);finish();assertEquals("100",text("comparisons"));assertTrue(text("table-result").contains("ABCDEFGHIJ"));view.applyCss();view.layout();scroll().setHvalue(1);scroll().setVvalue(1);return null;
            });
            fx(()->{assertLastVisible(10,10,11,11);capture("dp-lcs-maximum.png");paused=fingerprint();mode("mode-benchmark");view.benchmarkView().runButton().fire();return null;});await(()->!view.benchmarkView().runButton().isDisabled());
            fx(()->{
                assertEquals(3, view.benchmarkView().resultTable().getItems().size());mode("mode-table");assertEquals(paused,fingerprint());
                return null;
            });
            verifyPlayingPresetAndCategoryChanges();
            fx(()->{
                mode("mode-graph");assertFalse(text("algorithm-title").contains("LCS"));mode("mode-visualization");view.arrayInput().setText("5,2,4,1,3");view.generateButton().fire();assertEquals("[5, 2, 4, 1, 3]",text("array-values"));
                mode("mode-table");algorithm(0);assertEquals("—",text("comparisons"));assertTrue(text("algorithm-title").contains("预览"));assertFalse(view.lookup("#table-result").isVisible());
                algorithm(2);preset("lcs-example",0);view.playButton().fire();stage.close();return null;
            });
            String closed=fx(this::fingerprint);Thread.sleep(350);assertEquals(closed,fx(this::fingerprint));assertNull(uncaught.get());
            System.out.println("DP GUI: real knapsack/LCS transitions/dependencies/solutions/counts, no future-result leak, exact rollback, 4x/finish, playing-mode and preset cancellation, zero-capacity/empty/ties, max viewport, benchmark return, preview/sort/graph regression and close passed.");
        }finally{fx(()->{if(stage!=null)stage.close();return null;});Platform.exit();}
    }
    private void verifyPlayingPresetAndCategoryChanges()throws Exception{
        startPlayingKnapsack();
        fx(()->{preset("knapsack-example",1);assertFreshCategory();assertTrue(text("table-shape").contains("4行 × 1列"));assertFalse(text("table-result").contains("最大价值0"));paused=fingerprint();return null;});
        Thread.sleep(400);
        fx(()->{assertEquals(paused,fingerprint());assertFreshCategory();return null;});
        for(String destination:List.of("mode-graph","mode-visualization")){
            startPlayingKnapsack();
            fx(()->{mode(destination);assertFreshCategory();assertFalse(text("algorithm-title").contains("背包"));paused=categoryFingerprint();return null;});
            Thread.sleep(400);
            fx(()->{assertEquals(paused,categoryFingerprint());assertFreshCategory();return null;});
        }
    }
    private void startPlayingKnapsack()throws Exception{
        fx(()->{mode("mode-table");algorithm(1);preset("knapsack-example",0);view.resetButton().fire();view.speedChoice().getSelectionModel().select("4×");view.playButton().fire();return null;});
        await(()->Integer.parseInt(text("comparisons"))>0);
        fx(()->{assertEquals("播放中",text("playback-status"));return null;});
    }
    private void assertFreshCategory(){assertEquals("就绪",text("playback-status"));assertTrue(text("step-count").startsWith("第 1 /"));}
    private String categoryFingerprint(){return String.join("|",List.of("algorithm-title","step-count","event-name","explanation","comparisons","writes","swaps","array-values").stream().map(this::text).toList());}
    private void algorithm(int index){preset("table-algorithm",index);}
    private void preset(String id,int index){((ComboBox<?>)view.lookup("#"+id)).getSelectionModel().select(index);}
    private void mode(String id){((ToggleButton)view.lookup("#"+id)).fire();}
    private String text(String id){return ((Label)view.lookup("#"+id)).getText();}
    private String fingerprint(){return String.join("|",List.of("step-count","event-name","explanation","comparisons","writes","swaps","table-current","table-dependencies","table-path","table-result").stream().map(this::text).toList());}
    private void seekCell(String target){for(int i=0;i<400&&!(text("event-name").equals("更新单元格")&&text("table-current").contains(target));i++)view.nextButton().fire();assertEquals("更新单元格",text("event-name"));assertTrue(text("table-current").contains(target));}
    private void finish(){for(int i=0;i<500&&!view.nextButton().isDisabled();i++)view.nextButton().fire();assertTrue(view.nextButton().isDisabled());assertEquals("已完成",text("playback-status"));}
    private ScrollPane scroll(){return (ScrollPane)view.lookup("#visual-canvas-scroll");}
    private void assertLastVisible(int row,int col,int rows,int cols){double left=Math.max(12,(view.canvas().getWidth()-74-cols*46)/2),top=Math.max(12,(view.canvas().getHeight()-38-rows*34)/2);var point=view.canvas().localToScene(new Point2D(left+74+col*46+23,top+38+row*34+17));var viewport=scroll().lookup(".viewport");assertTrue(viewport.localToScene(viewport.getBoundsInLocal()).contains(point));}
    private int[] pixels(){view.applyCss();view.layout();var image=view.canvas().snapshot(null,null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] out=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),out,0,w);return out;}
    private void capture(String name)throws Exception{var image=stage.getScene().snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] out=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),out,0,w);var png=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,w,h,out,0,w);Path file=Path.of("target","gui-smoke",name);Files.createDirectories(file.getParent());ImageIO.write(png,"png",file.toFile());}
    private static <T>T fx(Callable<T> action)throws Exception{var done=new CompletableFuture<T>();Platform.runLater(()->{try{done.complete(action.call());}catch(Throwable e){done.completeExceptionally(e);}});return done.get(8,TimeUnit.SECONDS);}
    private static void await(BooleanSupplier condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>deadline)fail("DP GUI等待超时");Thread.sleep(20);}}
}
