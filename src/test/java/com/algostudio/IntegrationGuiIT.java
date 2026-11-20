package com.algostudio;

import com.algostudio.model.input.LcsInput;
import com.algostudio.model.table.TableExample;
import com.algostudio.service.*;
import com.algostudio.ui.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.image.PixelFormat;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationGuiIT {
    private Stage stage;private MainView view;
    private final AtomicReference<Throwable> uncaught=new AtomicReference<>();
    @Test void preservesReadableHistoryAndReleasesRepeatedWindows()throws Exception{
        Platform.startup(()->Platform.setImplicitExit(false));
        try{
            fx(()->{
                Thread.currentThread().setUncaughtExceptionHandler((t,e)->uncaught.compareAndSet(null,e));
                stage=new Stage();new App().start(stage);view=(MainView)stage.getScene().getRoot();
                ((ToggleButton)view.lookup("#mode-table")).fire();
                view.tableControls().algorithmChoice().getSelectionModel().select(TableRunMode.LCS);
                view.applyCss();view.layout();
                for(int i=0;i<100&&!text("table-current").contains("dp[2][1]");i++)view.nextButton().fire();
                assertEquals("依赖格：[dp[1][1], dp[2][0]]",text("table-dependencies"));
                for(int i=0;i<100&&!view.nextButton().isDisabled();i++)view.nextButton().fire();
                var controls=new TableControls();controls.algorithmChoice().getSelectionModel().select(TableRunMode.LCS);
                var left=new DpTraceService().generateLcs(new LcsInput("A","AB"));
                controls.showFrame((com.algostudio.model.frame.TableFrame)left.frames().getLast(),left.run().output());
                var viewport=new CanvasViewport(new Canvas());var redraws=new AtomicInteger();viewport.setOnResize(redraws::incrementAndGet);
                viewport.showFrame(new TablePreviewService().generate(TableExample.KNAPSACK_MAX).frames().getFirst());
                assertAll(
                    ()->assertEquals("回溯路径：[dp[3][2], dp[2][1], dp[1][1], dp[0][0]]",text("table-path")),
                    ()->assertEquals("回溯路径：[dp[1][2], dp[1][1], dp[0][0]]",((Label)controls.getContent().lookup("#table-path")).getText()),
                    ()->assertEquals(0,redraws.get(),"主动prepare交给外层绘制，不能再通知一次resize重画"));
                viewport.showFrame(null);redraws.set(0);new Scene(viewport,400,260);viewport.resize(400,260);viewport.applyCss();viewport.layout();
                assertTrue(redraws.get()>0,"外部viewport尺寸变化必须仍通知重画");
                view.tableControls().setVvalue(1);view.applyCss();view.layout();capture("integration-dp-path.png");
                ((ToggleButton)view.lookup("#mode-visualization")).fire();view.arrayInput().setText("5,2,4,1,3");view.generateButton().fire();
                assertThrows(AssertionError.class,()->GuiSteps.untilEvent(view,"不存在的事件"));
                assertTrue(view.nextButton().isDisabled(),"缺失事件应在真实终点明确失败，而不是继续触发按钮");
                System.out.println("Missing-event negative control: bounded failure at actual trace end.");
                mode("mode-graph");view.graphControls().exampleChoice().getSelectionModel().select(1);view.graphControls().algorithmChoice().getSelectionModel().select(GraphRunMode.BFS);
                for(int i=0;i<100&&!text("graph-edge").contains("B—C");i++)view.nextButton().fire();
                assertEdgeInterior();stage.setWidth(960);stage.setHeight(640);return null;
            });
            await(()->stage.getScene().getWidth()<1000);
            fx(()->{
                view.applyCss();view.layout();assertEdgeInterior();capture("integration-bfs-minimum.png");
                view.graphControls().algorithmChoice().getSelectionModel().select(GraphRunMode.DIJKSTRA);GuiSteps.finish(view);
                var target=(ComboBox<?>)view.lookup("#graph-target");target.getSelectionModel().select(target.getItems().indexOf("E"));
                scrollVisible(view.graphControls(),view.lookup("#graph-dist-A"));
                scrollVisible(view.graphControls(),view.lookup("#graph-dist-F"));
                scrollVisible(view.graphControls(),view.lookup("#graph-path"));
                assertTrue(text("graph-path").contains("A → B → C → D → E"));capture("integration-dijkstra-viewport.png");
                mode("mode-visualization");view.speedChoice().getSelectionModel().select("4×");view.playButton().fire();return null;
            });
            await(()->!text("step-count").startsWith("第 1 /"));
            String switched=fx(()->{
                for(int cycle=0;cycle<12;cycle++){
                    mode("mode-graph");view.graphControls().algorithmChoice().getSelectionModel().select(cycle%2==0?GraphRunMode.BFS:GraphRunMode.DFS);
                    mode("mode-table");view.tableControls().algorithmChoice().getSelectionModel().select(cycle%2==0?TableRunMode.LCS:TableRunMode.KNAPSACK);
                    mode("mode-visualization");view.algorithmChoice().getSelectionModel().select(cycle%3);
                    assertTrue(text("step-count").startsWith("第 1 /"));
                }
                return fingerprint();
            });
            Thread.sleep(400);assertEquals(switched,fx(this::fingerprint));
            fx(()->{stage.close();return null;});
            for(int cycle=0;cycle<3;cycle++){
                Label step=fx(()->{stage=new Stage();new App().start(stage);view=(MainView)stage.getScene().getRoot();view.speedChoice().getSelectionModel().select("4×");view.playButton().fire();return (Label)view.lookup("#step-count");});
                await(()->!text("step-count").startsWith("第 1 /"));
                fx(()->{mode("mode-benchmark");view.benchmarkView().sizeChoice().getSelectionModel().select(Integer.valueOf(3000));view.benchmarkView().runButton().fire();return null;});
                await(()->benchmarkThreads()==1);
                // 演示区此时脱离Scene，用预先保存的控件引用检查关闭后的旧tick。
                Label held=step;
                fx(()->{stage.close();assertTrue(view.playButton().isDisabled());assertEquals("尚未载入",held.getText());return null;});
                await(()->benchmarkThreads()==0);Thread.sleep(350);assertEquals("尚未载入",fx(held::getText));
            }
            assertNull(uncaught.get());
            System.out.println("Integration GUI: DP order/dependencies, prepare vs external resize, active edge interiors, actual distance/path viewport,12 rapid category cycles,3 real-window playback/benchmark/close cycles; benchmark threads=0.");
        }finally{if(stage!=null)fx(()->{stage.close();return null;});Platform.exit();}
    }
    private String text(String id){return ((Label)view.lookup("#"+id)).getText();}
    private void mode(String id){((ToggleButton)view.lookup("#"+id)).fire();view.applyCss();view.layout();}
    private String fingerprint(){return text("algorithm-title")+"|"+text("step-count")+"|"+text("event-name")+"|"+text("explanation");}
    private static long benchmarkThreads(){return Thread.getAllStackTraces().keySet().stream().filter(t->t.isAlive()&&t.getName().equals("algostudio-benchmark")).count();}
    private void scrollVisible(ScrollPane scroll,Node node){
        assertNotNull(node);view.applyCss();view.layout();var content=scroll.getContent();
        Bounds bounds=content.sceneToLocal(node.localToScene(node.getBoundsInLocal()));double visible=scroll.getViewportBounds().getHeight();
        scroll.setVvalue(Math.max(0,Math.min(1,(bounds.getMinY()+bounds.getHeight()/2-visible/2)/Math.max(1,content.getLayoutBounds().getHeight()-visible))));view.layout();
        var viewport=scroll.lookup(".viewport");Bounds area=viewport.localToScene(viewport.getBoundsInLocal()),actual=node.localToScene(node.getBoundsInLocal());
        assertTrue(actual.getMinY()>=area.getMinY()-1&&actual.getMaxY()<=area.getMaxY()+1,"实际滚动视口内应显示完整内容："+node.getId());
    }
    private void assertEdgeInterior(){
        view.applyCss();view.layout();assertTrue(text("graph-edge").contains("B—C"));var image=view.canvas().snapshot(null,null);
        double w=image.getWidth(),h=image.getHeight(),pad=Math.min(20,Math.max(12,(h-40)/8))+12;
        int x=(int)Math.round(pad+(w-2*pad)*.36),y=(int)Math.round(pad+(h-2*pad)*(.17+(.75-.17)*.27));boolean amber=false;
        for(int dx=-3;dx<=3;dx++)for(int dy=-3;dy<=3;dy++)if((image.getPixelReader().getArgb(x+dx,y+dy)&0xffffff)==0xd99719)amber=true;
        assertTrue(amber,"B—C边内部避开节点和权重的像素应为橙色");
    }
    private void capture(String name)throws Exception{var image=stage.getScene().snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] p=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),p,0,w);var png=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,w,h,p,0,w);var path=Path.of("target","gui-smoke",name);Files.createDirectories(path.getParent());ImageIO.write(png,"png",path.toFile());}
    private static void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>end)fail("等待集成状态超时");Thread.sleep(20);}}
    private static <T>T fx(Callable<T> action)throws Exception{var done=new CompletableFuture<T>();Platform.runLater(()->{try{done.complete(action.call());}catch(Throwable e){done.completeExceptionally(e);}});return done.get(15,TimeUnit.SECONDS);}
}
