package com.algostudio.ui.renderer;

import com.algostudio.model.frame.TableFrame;
import com.algostudio.model.table.TableCell;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.text.*;

/** 只画快照；固定可读格尺寸由外层视口提供滚动，不在渲染器执行DP。 */
public final class TableRenderer implements VisualRenderer<TableFrame> {
    public static final double CELL_WIDTH=46,CELL_HEIGHT=34,ROW_HEADER=74,COLUMN_HEADER=38,PADDING=12;
    public static double minimumWidth(TableFrame frame){return PADDING*2+ROW_HEADER+frame.columnLabels().size()*CELL_WIDTH;}
    public static double minimumHeight(TableFrame frame){return PADDING*2+COLUMN_HEADER+frame.rowLabels().size()*CELL_HEIGHT;}
    @Override public void render(TableFrame frame,Canvas canvas){
        var g=canvas.getGraphicsContext2D();double w=canvas.getWidth(),h=canvas.getHeight();g.clearRect(0,0,w,h);
        double left=Math.max(PADDING,(w-ROW_HEADER-frame.columnLabels().size()*CELL_WIDTH)/2);
        double top=Math.max(PADDING,(h-COLUMN_HEADER-frame.rowLabels().size()*CELL_HEIGHT)/2);
        g.setTextAlign(TextAlignment.CENTER);g.setTextBaseline(VPos.CENTER);g.setFont(Font.font("Microsoft YaHei",13));
        g.setFill(Color.web("#536783"));g.fillText("行 / 列",left+ROW_HEADER/2,top+COLUMN_HEADER/2);
        for(int col=0;col<frame.columnLabels().size();col++){
            g.setFill(Color.web("#edf3ff"));g.fillRect(left+ROW_HEADER+col*CELL_WIDTH,top,CELL_WIDTH,COLUMN_HEADER-2);
            g.setFill(Color.web("#315bb0"));g.fillText(frame.columnLabels().get(col),left+ROW_HEADER+(col+.5)*CELL_WIDTH,top+COLUMN_HEADER/2,CELL_WIDTH-4);
        }
        for(int row=0;row<frame.rowLabels().size();row++){
            g.setFill(Color.web("#edf3ff"));g.fillRect(left,top+COLUMN_HEADER+row*CELL_HEIGHT,ROW_HEADER-2,CELL_HEIGHT);
            g.setFill(Color.web("#315bb0"));g.fillText(frame.rowLabels().get(row),left+ROW_HEADER/2,top+COLUMN_HEADER+(row+.5)*CELL_HEIGHT,ROW_HEADER-6);
            for(int col=0;col<frame.columnLabels().size();col++){
                var cell=new TableCell(row,col);int value=frame.values().get(row).get(col);
                String fill=cell.equals(frame.currentCell())?"#f9dfaa":frame.path().contains(cell)?"#d9f3e7":frame.dependencies().contains(cell)?"#dce8ff":value==TableFrame.UNCOMPUTED?"#eef2f7":"#ffffff";
                double x=left+ROW_HEADER+col*CELL_WIDTH,y=top+COLUMN_HEADER+row*CELL_HEIGHT;
                g.setFill(Color.web(fill));g.fillRect(x+1,y+1,CELL_WIDTH-2,CELL_HEIGHT-2);
                g.setStroke(Color.web("#cad5e5"));g.setLineWidth(1);g.strokeRect(x+.5,y+.5,CELL_WIDTH-1,CELL_HEIGHT-1);
                g.setFill(Color.web(value==TableFrame.UNCOMPUTED?"#718096":"#24334d"));
                g.fillText(value==TableFrame.UNCOMPUTED?"—":Integer.toString(value),x+CELL_WIDTH/2,y+CELL_HEIGHT/2,CELL_WIDTH-6);
            }
        }
    }
}
