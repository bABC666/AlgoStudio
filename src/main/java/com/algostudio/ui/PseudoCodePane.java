package com.algostudio.ui;

import com.algostudio.model.frame.VisualFrame;
import com.algostudio.model.pseudocode.PseudocodeCatalog;
import javafx.css.PseudoClass;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

/** 只显示当前历史帧的行ID；没有自己的计时器、算法或播放状态。 */
public final class PseudoCodePane extends TitledPane {
    private static final PseudoClass ACTIVE=PseudoClass.getPseudoClass("active");
    private final Label name=new Label("暂无伪代码"),status=new Label("请先生成算法轨迹");
    private final VBox code=new VBox(3);
    private final ScrollPane scroll=new ScrollPane(code);
    private final Map<String,Label> rows=new LinkedHashMap<>();
    private PseudocodeCatalog.Program program;
    private Label active;

    public PseudoCodePane(){
        setId("pseudocode-pane");getStyleClass().add("pseudo-pane");setText("伪代码");setAnimated(false);setExpanded(false);
        setMinWidth(0);setPrefWidth(260);setMaxWidth(Double.MAX_VALUE);
        name.setId("pseudo-program");name.setWrapText(true);name.setMinHeight(Region.USE_PREF_SIZE);
        status.setId("pseudo-status");status.getStyleClass().add("muted");
        code.getStyleClass().add("pseudo-code");code.setFillWidth(true);
        scroll.setId("pseudo-scroll");scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinHeight(120);scroll.setPrefHeight(180);scroll.setMaxHeight(180);
        var content=new VBox(5,name,status,scroll);content.getStyleClass().add("pseudo-content");setContent(content);
        expandedProperty().addListener((o,before,after)->ensureVisible());
        scroll.viewportBoundsProperty().addListener((o,before,after)->ensureVisible());
        code.heightProperty().addListener((o,before,after)->ensureVisible());
    }
    public void setProgram(String algorithmId){
        program=PseudocodeCatalog.find(algorithmId).orElse(null);active=null;rows.clear();code.getChildren().clear();
        setText("伪代码");name.setText(program==null?"暂无伪代码":program.name());status.setText("当前步骤无对应行");scroll.setVvalue(0);
        if(program==null)return;
        for(int i=0;i<program.lines().size();i++){
            var line=program.lines().get(i);var row=new Label(String.format(Locale.ROOT,"%02d  %s",i+1,line.text()));
            row.setId("pseudo-line-"+line.id());row.getStyleClass().add("pseudo-line");row.setWrapText(true);
            row.setMinWidth(0);row.setMaxWidth(Double.MAX_VALUE);row.setMinHeight(Region.USE_PREF_SIZE);
            row.boundsInParentProperty().addListener((o,before,after)->ensureVisible());
            rows.put(line.id(),row);code.getChildren().add(row);
        }
    }
    public void showFrame(VisualFrame frame){
        if(active!=null)active.pseudoClassStateChanged(ACTIVE,false);active=null;
        if(frame==null){setProgram(null);status.setText("请先生成算法轨迹");return;}
        if(program==null){status.setText("该轨迹无算法伪代码");setText("伪代码 · 无映射");return;}
        var number=program.lineNumber(frame.pseudoLineId());
        if(number.isEmpty()){status.setText("当前步骤无对应行");setText("伪代码 · 无映射");return;}
        active=rows.get(frame.pseudoLineId());active.pseudoClassStateChanged(ACTIVE,true);
        status.setText("当前第 "+number.getAsInt()+" 行");setText("伪代码 · 第 "+number.getAsInt()+" 行");ensureVisible();
    }
    private void ensureVisible(){
        if(!isExpanded()||active==null)return;
        double viewport=scroll.getViewportBounds().getHeight(),height=code.getHeight();
        if(viewport<=0||height<=viewport)return;
        var bounds=active.getBoundsInParent();
        double centered=(bounds.getMinY()+bounds.getHeight()/2-viewport/2)/(height-viewport);
        scroll.setVvalue(Math.max(0,Math.min(1,centered)));
    }
}
