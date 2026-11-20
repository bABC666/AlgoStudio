package com.algostudio.ui.renderer;

import com.algostudio.model.frame.*;
import javafx.scene.canvas.Canvas;

/** 按帧类型分派，播放器不需要知道算法类别。 */
public final class RendererPipeline implements VisualRenderer<VisualFrame> {
    private final ArrayRenderer arrays = new ArrayRenderer();
    private final GraphRenderer graphs = new GraphRenderer();
    private final TableRenderer tables = new TableRenderer();
    @Override public void render(VisualFrame frame, Canvas canvas) {
        if (frame == null) { canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight()); return; }
        switch (frame) {
            case ArrayFrame array -> arrays.render(array, canvas);
            case GraphFrame graph -> graphs.render(graph, canvas);
            case TableFrame table -> tables.render(table, canvas);
        }
    }
}
