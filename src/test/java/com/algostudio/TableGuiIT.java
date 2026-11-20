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

class TableGuiIT {
    private MainView view;
    private Stage stage;
    private String resized;
    @Test void maximumTablesScrollRollbackResizePlaybackSwitchAndClose() throws Exception {
        CompletableFuture<Void> ready = new CompletableFuture<>(); AtomicReference<Throwable> uncaught = new AtomicReference<>();
        Platform.startup(() -> { Platform.setImplicitExit(false); Thread.currentThread().setUncaughtExceptionHandler((t,e)->uncaught.compareAndSet(null,e)); ready.complete(null); }); ready.get(5,TimeUnit.SECONDS);
        try {
            fx(() -> {
                stage=new Stage(); new App().start(stage); view=(MainView)stage.getScene().getRoot();
                assertNotNull(view.lookup("#mode-table"), "T10应提供明确的表格样式预览入口"); mode("mode-table"); choose(1); view.applyCss(); view.layout(); return null;
            });
            fx(() -> {
                assertTrue(text("algorithm-title").contains("预览")); assertTrue(text("algorithm-description").contains("不执行"));
                assertTrue(text("table-shape").contains("9行 × 31列")); assertEquals("—", text("comparisons"));
                assertTrue(view.canvas().getWidth() >= 1524); assertTrue(view.canvas().getHeight() >= 368);
                String initial=fingerprint(); int[] image=pixels(); view.nextButton().fire();
                assertNotEquals(0xffeef2f7, color(0,0,9,31)); view.previousButton().fire(); assertEquals(initial,fingerprint()); assertArrayEquals(image,pixels());
                assertEquals(0xffeef2f7,color(0,0,9,31));
                view.nextButton().fire(); view.nextButton().fire();
                assertTrue(text("table-current").contains("dp[8][30]")); assertTrue(text("table-dependencies").contains("[7][30]"));
                assertEquals(0xfff9dfaa,color(8,30,9,31)); assertEquals(0xffdce8ff,color(7,30,9,31));
                String selected=fingerprint(); image=pixels();
                for(int i=0;i<3;i++)view.nextButton().fire(); assertEquals("预览完成",text("playback-status")); assertEquals(0xffd9f3e7,color(8,30,9,31));
                for(int i=0;i<3;i++)view.previousButton().fire(); assertEquals(selected,fingerprint()); assertArrayEquals(image,pixels());
                scroll().setHvalue(0); scroll().setVvalue(0); return null;
            });
            fx(() -> { assertInViewport(headerPoint(0,9,31)); scroll().setHvalue(1); scroll().setVvalue(1); return null; });
            fx(() -> { assertInViewport(cellPoint(8,30,9,31)); capture("table-knapsack-max.png"); resized=fingerprint(); stage.setWidth(960);stage.setHeight(640);return null; });
            await(() -> stage.getScene().getWidth()<1000);
            fx(() -> { view.applyCss();view.layout();assertEquals(resized,fingerprint());assertEquals(0xfff9dfaa,color(8,30,9,31));scroll().setHvalue(1);scroll().setVvalue(1);return null; });
            fx(() -> {
                assertInViewport(cellPoint(8,30,9,31)); capture("table-knapsack-minimum.png");
                view.nextButton().fire(); assertTrue(text("table-current").contains("1")); view.nextButton().fire();
                assertTrue(text("table-path").contains("[8][30]")); assertEquals(0xffd9f3e7,color(8,30,9,31));
                choose(3); view.applyCss();view.layout();return null;
            });
            fx(() -> {
                assertTrue(text("table-shape").contains("11行 × 11列")); assertTrue(text("table-axes").contains("前缀"));
                assertTrue(view.canvas().getHeight()>=436); assertTrue(text("step-count").startsWith("第 1 /"));
                finish(); scroll().setHvalue(1);scroll().setVvalue(1);return null;
            });
            fx(() -> {
                assertInViewport(cellPoint(10,10,11,11)); assertEquals(0xffd9f3e7,color(10,10,11,11));capture("table-lcs-minimum.png");
                view.resetButton().fire();view.speedChoice().getSelectionModel().select("4×");view.playButton().fire();return null;
            });
            await(() -> text("playback-status").equals("预览完成"));
            fx(() -> { view.playButton().fire();return null; }); await(() -> !text("step-count").startsWith("第 1 /"));
            fx(() -> { view.playButton().fire();assertEquals("已暂停",text("playback-status"));return null; });
            String paused=fx(this::fingerprint);Thread.sleep(350);assertEquals(paused,fx(this::fingerprint));
            fx(() -> { view.playButton().fire();choose(0);return null; });Thread.sleep(350);
            fx(() -> {
                assertTrue(text("step-count").startsWith("第 1 /"));assertTrue(text("table-shape").contains("4行 × 6列"));view.nextButton().fire();resized=fingerprint();
                mode("mode-benchmark");view.benchmarkView().runButton().fire();return null;
            }); await(() -> !view.benchmarkView().runButton().isDisabled());
            fx(() -> {
                assertEquals(3, view.benchmarkView().resultTable().getItems().size());mode("mode-table");assertEquals(resized,fingerprint());
                mode("mode-graph");view.graphControls().algorithmChoice().getSelectionModel().select(GraphRunMode.DIJKSTRA);view.applyCss();view.layout();return null;
            });
            fx(() -> {
                assertEquals("Dijkstra 最短路径",text("algorithm-title"));assertEquals("0",text("comparisons"));assertTrue(view.canvas().getWidth()<1000);
                mode("mode-visualization");view.arrayInput().setText("5,2,4,1,3");view.generateButton().fire();assertEquals("[5, 2, 4, 1, 3]",text("array-values"));
                mode("mode-table");view.playButton().fire();stage.close();return null;
            });
            String closed=fx(this::fingerprint);Thread.sleep(350);assertEquals(closed,fx(this::fingerprint));assertNull(uncaught.get());
            System.out.println("Table GUI: 9x31/11x11 headers/last-cell viewport visibility, pending/zero/current/dependency/path pixels, exact rollback, 960x640 resize, pause/4x, mode/example cancellation, benchmark return and close passed.");
        } finally { fx(() -> {if(stage!=null)stage.close();return null;});Platform.exit(); }
    }
    private void choose(int index){view.tableControls().exampleChoice().getSelectionModel().select(index);}
    private ScrollPane scroll(){return (ScrollPane)view.lookup("#visual-canvas-scroll");}
    private void mode(String id){((ToggleButton)view.lookup("#"+id)).fire();}
    private String text(String id){return ((Label)view.lookup("#"+id)).getText();}
    private String fingerprint(){return String.join("|",List.of("step-count","event-name","explanation","comparisons","writes","swaps","table-shape","table-current","table-dependencies","table-path").stream().map(this::text).toList());}
    private void finish(){for(int i=0;i<10&&!view.nextButton().isDisabled();i++)view.nextButton().fire();assertTrue(view.nextButton().isDisabled());}
    private Point2D cellPoint(int row,int col,int rows,int cols){return new Point2D(left(cols)+74+col*46+23,top(rows)+38+row*34+17);}
    private Point2D headerPoint(int col,int rows,int cols){return new Point2D(left(cols)+74+col*46+23,top(rows)+19);}
    private double left(int cols){return Math.max(12,(view.canvas().getWidth()-74-cols*46)/2);}
    private double top(int rows){return Math.max(12,(view.canvas().getHeight()-38-rows*34)/2);}
    private int color(int row,int col,int rows,int cols){var image=view.canvas().snapshot(null,null);return image.getPixelReader().getArgb((int)(left(cols)+74+col*46+5),(int)(top(rows)+38+row*34+5));}
    private void assertInViewport(Point2D point){
        var viewport=scroll().lookup(".viewport");assertNotNull(viewport);var bounds=viewport.localToScene(viewport.getBoundsInLocal());var scenePoint=view.canvas().localToScene(point);
        assertTrue(bounds.contains(scenePoint),"目标格/标题必须在实际滚动视口内："+scenePoint+" vs "+bounds);
    }
    private int[] pixels(){var image=view.canvas().snapshot(null,null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] out=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),out,0,w);return out;}
    private void capture(String name)throws Exception{var image=stage.getScene().snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] out=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),out,0,w);var png=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,w,h,out,0,w);Path file=Path.of("target","gui-smoke",name);Files.createDirectories(file.getParent());ImageIO.write(png,"png",file.toFile());}
    private static <T>T fx(Callable<T> action)throws Exception{var done=new CompletableFuture<T>();Platform.runLater(()->{try{done.complete(action.call());}catch(Throwable e){done.completeExceptionally(e);}});return done.get(5,TimeUnit.SECONDS);}
    private static void await(BooleanSupplier condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>deadline)fail("表格GUI等待超时");Thread.sleep(20);}}
}
