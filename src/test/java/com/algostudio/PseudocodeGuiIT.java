package com.algostudio;

import com.algostudio.algorithm.AlgorithmRegistry;
import com.algostudio.model.*;
import com.algostudio.model.frame.*;
import com.algostudio.model.input.*;
import com.algostudio.model.pseudocode.PseudocodeCatalog;
import com.algostudio.service.*;
import com.algostudio.ui.*;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PseudocodeGuiIT {
    private MainView view;private Stage stage;private PseudoCodePane pane;
    private final Map<String,Label> labels=new HashMap<>();
    private final Set<String> captured=new HashSet<>();
    private final AtomicReference<Throwable> uncaught=new AtomicReference<>();
    @Test void followsAllEightTracesAndRestoresCodeAcrossPlaybackNavigationAndMissingMappings()throws Exception{
        // 上轮遗留文件必须被本轮首个匹配帧覆盖，不能只因文件存在就跳过采集。
        Files.createDirectories(Path.of("target","gui-smoke"));
        for(String name:List.of("pseudo-bubble-compare.png","pseudo-merge-write.png","pseudo-bfs-discover.png","pseudo-lcs-backtrack.png"))
            Files.writeString(Path.of("target","gui-smoke",name),"old-run sentinel");
        Platform.startup(()->Platform.setImplicitExit(false));
        try{
            fx(()->{
                Thread.currentThread().setUncaughtExceptionHandler((t,e)->uncaught.compareAndSet(null,e));
                stage=new Stage();new App().start(stage);view=(MainView)stage.getScene().getRoot();
                pane=assertInstanceOf(PseudoCodePane.class,view.lookup("#pseudocode-pane"));
                for(String id:List.of("algorithm-title","step-count","event-name","explanation","comparisons","writes","swaps","playback-status"))
                    labels.put(id,(Label)view.lookup("#"+id));
                assertFalse(pane.isExpanded());pane.setExpanded(true);view.arrayInput().setText("5,2,4,1,3");view.generateButton().fire();return null;
            });
            for(int algorithm=0;algorithm<3;algorithm++){
                int selected=algorithm;
                fx(()->{view.algorithmChoice().getSelectionModel().select(selected);return null;});
                verifyTrace(new TraceService().generate(AlgorithmRegistry.sortingAlgorithms().get(algorithm),new ArrayInput(new int[]{5,2,4,1,3})));
            }
            fx(()->{mode("mode-graph");assertUnmapped();view.graphControls().exampleChoice().getSelectionModel().select(1);return null;});
            for(int algorithm=0;algorithm<3;algorithm++){
                int selected=algorithm+1;
                fx(()->{view.graphControls().algorithmChoice().getSelectionModel().select(selected);return null;});
                verifyTrace(new GraphTraceService().generate(AlgorithmRegistry.graphAlgorithms().get(algorithm),new GraphTraversalInput(GraphExamples.evaluationGraph(),"A")));
            }
            fx(()->{mode("mode-table");assertUnmapped();view.tableControls().algorithmChoice().getSelectionModel().select(1);return null;});
            verifyTrace(new DpTraceService().generateKnapsack(DpExamples.knapsacks().getFirst().input()));
            fx(()->{view.tableControls().algorithmChoice().getSelectionModel().select(2);return null;});
            verifyTrace(new DpTraceService().generateLcs(DpExamples.lcs().getFirst().input()));
            fx(()->{
                pane.showFrame(new ArrayFrame(new int[]{1},EventKind.COMPARE,-1,-1,-1,0,1,AlgorithmMetrics.ZERO,"unmapped","missing-line"));
                assertUnmapped();assertTrue(status().contains("无对应"));
                pane.setProgram("unknown-program");assertUnmapped();
                mode("mode-visualization");view.algorithmChoice().getSelectionModel().select(0);view.arrayInput().setText("5,2,4,1,3");view.generateButton().fire();
                view.applyCss();view.layout();String first=codeFingerprint();int[] pixels=panePixels();
                for(int i=0;i<10;i++)view.nextButton().fire();
                for(int i=0;i<10;i++)view.previousButton().fire();
                assertEquals(first,codeFingerprint());assertArrayEquals(pixels,panePixels());
                view.speedChoice().getSelectionModel().select(2);view.playButton().fire();return null;
            });
            await(()->text("playback-status").equals("已完成"));
            fx(()->{assertEquals("DONE",activeId());view.playButton().fire();assertEquals("INITIAL",activeId());return null;});
            awaitNonzero();fx(()->{view.playButton().fire();return null;});stable();
            fx(()->{view.playButton().fire();mode("mode-benchmark");return null;});stable();
            String beforeReport=fx(this::codeFingerprint);
            fx(()->{view.benchmarkView().runButton().fire();return null;});await(()->!view.benchmarkView().runButton().isDisabled());
            fx(()->{assertEquals(3,view.benchmarkView().resultTable().getItems().size());mode("mode-visualization");assertEquals(beforeReport,codeFingerprint());return null;});
            startPlaying();fx(()->{view.algorithmChoice().getSelectionModel().select(2);assertEquals("INITIAL",activeId());assertTrue(programName().contains("归并"));return null;});stable();
            startPlaying();fx(()->{view.arrayInput().setText("1,2,a,4,5");assertUnmapped();view.generateButton().fire();assertUnmapped();assertEquals("生成失败",text("playback-status"));return null;});stable();
            fx(()->{view.arrayInput().setText("5,2,4,1,3");view.generateButton().fire();return null;});
            startPlaying();fx(()->{mode("mode-graph");assertTrue(programName().contains("Dijkstra"));assertEquals("INITIAL",activeId());return null;});stable();
            fx(()->{view.playButton().fire();return null;});await(()->!text("step-count").startsWith("第 1 /"));
            fx(()->{mode("mode-table");assertTrue(programName().contains("LCS"));assertEquals("INITIAL",activeId());return null;});stable();
            fx(()->{stage.setWidth(960);stage.setHeight(640);return null;});await(()->stage.getScene().getWidth()<=960);
            fx(()->{
                mode("mode-visualization");view.arrayInput().setText(IntStream.range(0,50).map(i->i%2==0?100:1).mapToObj(Integer::toString).collect(Collectors.joining(",")));view.generateButton().fire();
                String before=text("step-count");pane.setExpanded(false);int[] collapsed=canvasPixels();pane.setExpanded(true);assertEquals(before,text("step-count"));assertArrayEquals(collapsed,canvasPixels());
                GuiSteps.untilEvent(view,"写入");assertActiveVisible();assertFullLine();capture("pseudo-minimum-write.png");
                var navigation=(ScrollPane)view.lookup(".sidebar-scroll");navigation.setVvalue(1);view.layout();
                Bounds viewport=navigation.lookup(".viewport").localToScene(navigation.lookup(".viewport").getBoundsInLocal());
                Bounds generate=view.generateButton().localToScene(view.generateButton().getBoundsInLocal());
                assertTrue(generate.getMinY()<viewport.getMaxY()&&generate.getMaxY()>viewport.getMinY(),"原生成按钮仍可通过滚动访问");
                mode("mode-table");view.tableControls().lcsChoice().getSelectionModel().select(4);
                GuiSteps.finish(view);assertEquals("DONE",activeId());assertActiveVisible();assertFullLine();capture("pseudo-lcs-done-minimum.png");
                view.resetButton().fire();assertEquals("INITIAL",activeId());assertActiveVisible();
                view.tableControls().algorithmChoice().getSelectionModel().select(0);assertUnmapped();
                GuiSteps.finish(view);assertUnmapped();capture("pseudo-preview-unmapped.png");
                view.tableControls().algorithmChoice().getSelectionModel().select(1);view.playButton().fire();stage.close();assertUnmapped();return null;
            });
            String closed=fx(this::codeFingerprint);Thread.sleep(400);assertEquals(closed,fx(this::codeFingerprint));assertFalse(fx(stage::isShowing));assertNull(uncaught.get());
            for(String name:List.of("pseudo-bubble-compare.png","pseudo-merge-write.png","pseudo-bfs-discover.png","pseudo-lcs-backtrack.png"))
                assertNotNull(ImageIO.read(Path.of("target","gui-smoke",name).toFile()),"本轮必须覆盖旧截图："+name);
            System.out.println("Pseudocode GUI: all8 actual traces, row/background/line number, exact history, 4x/replay/pause, playing switches, benchmark return, unknown/error/preview clear, minimum open pane and visible current row, close verified.");
        }finally{if(stage!=null)fx(()->{stage.close();return null;});Platform.exit();}
    }
    private void verifyTrace(ExecutionTrace trace)throws Exception{
        fx(()->{
            var program=PseudocodeCatalog.find(trace.algorithmId()).orElseThrow();assertEquals(program.name(),programName());
            for(int i=0;i<trace.frames().size();i++){
                var frame=trace.frames().get(i);assertEquals(frame.pseudoLineId(),activeId(),trace.algorithmId()+" frame"+i);
                assertTrue(status().contains("第 "+program.lineNumber(frame.pseudoLineId()).orElseThrow()+" 行"));
                assertTrue(text("step-count").startsWith("第 "+(i+1)+" /"));assertActiveVisible();assertFullLine();
                Label row=activeRow();assertEquals(Color.web("#fff2cc"),row.getBackground().getFills().getFirst().getFill());
                if(i>0&&frame.eventKind()==EventKind.COMPARE&&trace.algorithmId().equals("bubble-sort"))captureOnce("pseudo-bubble-compare.png");
                if(frame.eventKind()==EventKind.WRITE)captureOnce("pseudo-merge-write.png");
                if(frame.eventKind()==EventKind.DISCOVER)captureOnce("pseudo-bfs-discover.png");
                if(frame.eventKind()==EventKind.BACKTRACK&&trace.algorithmId().equals("lcs"))captureOnce("pseudo-lcs-backtrack.png");
                if(i<trace.frames().size()-1)view.nextButton().fire();
            }return null;
        });
    }
    private void assertActiveVisible(){view.applyCss();view.layout();var scroll=(ScrollPane)pane.lookup("#pseudo-scroll");var viewport=scroll.lookup(".viewport");Bounds visible=viewport.localToScene(viewport.getBoundsInLocal()),row=activeRow().localToScene(activeRow().getBoundsInLocal());assertTrue(row.getMinY()>=visible.getMinY()-1&&row.getMaxY()<=visible.getMaxY()+1,"当前伪代码行应在视口内："+activeId());}
    private void assertFullLine(){Label row=activeRow();assertEquals(row.getText(),assertInstanceOf(Text.class,row.lookup(".text")).getText(),"当前行应换行显示而非省略");}
    private Label activeRow(){var rows=pane.lookupAll(".pseudo-line").stream().filter(n->n.getPseudoClassStates().contains(PseudoClass.getPseudoClass("active"))).toList();assertEquals(1,rows.size());return (Label)rows.getFirst();}
    private String activeId(){return activeRow().getId().substring("pseudo-line-".length());}
    private void assertUnmapped(){assertTrue(pane.lookupAll(".pseudo-line").stream().noneMatch(n->n.getPseudoClassStates().contains(PseudoClass.getPseudoClass("active"))));}
    private String programName(){return ((Label)pane.lookup("#pseudo-program")).getText();}
    private String status(){return ((Label)pane.lookup("#pseudo-status")).getText();}
    private String codeFingerprint(){return text("algorithm-title")+"|"+text("step-count")+"|"+programName()+"|"+status()+"|"+pane.getText()+"|"+pane.lookupAll(".pseudo-line").stream().filter(n->n.getPseudoClassStates().contains(PseudoClass.getPseudoClass("active"))).map(n->n.getId()).sorted().toList();}
    private String text(String id){return labels.get(id).getText();}
    private void mode(String id){((ToggleButton)view.lookup("#"+id)).fire();}
    private void startPlaying()throws Exception{fx(()->{view.resetButton().fire();view.playButton().fire();return null;});awaitNonzero();}
    private void awaitNonzero()throws Exception{await(()->!text("step-count").startsWith("第 1 /"));fx(()->{assertEquals("播放中",text("playback-status"));return null;});}
    private void stable()throws Exception{String before=fx(this::codeFingerprint);Thread.sleep(400);assertEquals(before,fx(this::codeFingerprint));}
    private int[] pixels(javafx.scene.Node node){view.applyCss();view.layout();var image=node.snapshot(null,null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] p=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),p,0,w);return p;}
    private int[] canvasPixels(){return pixels(view.canvas());}private int[] panePixels(){return pixels(pane);}
    private void captureOnce(String name)throws Exception{if(captured.add(name))capture(name);}
    private void capture(String name)throws Exception{view.applyCss();view.layout();var image=stage.getScene().snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] p=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),p,0,w);var png=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,w,h,p,0,w);Path path=Path.of("target","gui-smoke",name);Files.createDirectories(path.getParent());ImageIO.write(png,"png",path.toFile());}
    private static <T>T fx(Callable<T> action)throws Exception{var done=new CompletableFuture<T>();Platform.runLater(()->{try{done.complete(action.call());}catch(Throwable e){done.completeExceptionally(e);}});return done.get(15,TimeUnit.SECONDS);}
    private static void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(!fx(condition::getAsBoolean)){if(System.nanoTime()>end)fail("等待JavaFX状态超时");Thread.sleep(20);}}
}
