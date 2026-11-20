package com.algostudio.ui.renderer;

import com.algostudio.model.frame.VisualFrame;
import javafx.scene.canvas.Canvas;

public interface VisualRenderer<F extends VisualFrame> {
    void render(F frame, Canvas canvas);
}
