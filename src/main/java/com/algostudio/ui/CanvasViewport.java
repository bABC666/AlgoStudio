package com.algostudio.ui;

import com.algostudio.model.frame.*;
import com.algostudio.ui.renderer.TableRenderer;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ScrollPane;

/** 原Canvas的统一视口：数组/图随窗口缩放，表格保留可读格尺寸并提供双向滚动。 */
public final class CanvasViewport extends ScrollPane {
    private final Canvas canvas;
    private TableFrame table;
    private Runnable onResize;
    private boolean sizing;
    public CanvasViewport(Canvas canvas) {
        this.canvas=canvas;setContent(canvas);setId("visual-canvas-scroll");getStyleClass().add("visual-canvas-scroll");
        setMinSize(0,110);setPrefSize(0,240);setPannable(true);
        viewportBoundsProperty().addListener((o,before,after)->resizeCanvas(true));
        showFrame(null);
    }
    public void setOnResize(Runnable action){onResize=action;}
    public void showFrame(VisualFrame frame){
        TableFrame next=frame instanceof TableFrame value?value:null;
        boolean shapeChanged=table==null?next!=null:next==null||table.rowLabels().size()!=next.rowLabels().size()||table.columnLabels().size()!=next.columnLabels().size();
        table=next;
        setHbarPolicy(table==null?ScrollBarPolicy.NEVER:ScrollBarPolicy.AS_NEEDED);
        setVbarPolicy(table==null?ScrollBarPolicy.NEVER:ScrollBarPolicy.AS_NEEDED);
        if(shapeChanged){setHvalue(0);setVvalue(0);}
        resizeCanvas(false);
    }
    private void resizeCanvas(boolean notify){
        if(sizing)return;
        double width=Math.max(getViewportBounds().getWidth(),table==null?0:TableRenderer.minimumWidth(table));
        double height=Math.max(getViewportBounds().getHeight(),table==null?0:TableRenderer.minimumHeight(table));
        if(canvas.getWidth()==width&&canvas.getHeight()==height)return;
        sizing=true;
        try{canvas.setWidth(width);canvas.setHeight(height);}finally{sizing=false;}
        if(notify&&onResize!=null)onResize.run();
    }
}
