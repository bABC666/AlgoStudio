package com.algostudio.ui.renderer;

import com.algostudio.model.frame.GraphFrame;
import com.algostudio.model.graph.GraphNode;
import com.algostudio.model.graph.FrontierKind;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 只根据当前不可变帧绘制，固定坐标按画布缩放；不搜索或修改图。 */
public final class GraphRenderer implements VisualRenderer<GraphFrame> {
    @Override public void render(GraphFrame frame, Canvas canvas) {
        var g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth(), h = canvas.getHeight(); g.clearRect(0, 0, w, h);
        if (w < 100 || h < 90) return;
        g.setTextAlign(TextAlignment.CENTER); g.setTextBaseline(VPos.CENTER);
        if (frame.graph().nodes().isEmpty()) {
            g.setFill(Color.web("#718096")); g.fillText("空图", w / 2, h / 2); return;
        }
        double radius = Math.min(20, Math.max(12, (h - 40) / 8));
        double pad = radius + 12, pw = w - pad * 2, ph = h - pad * 2;
        Map<String, GraphNode> nodes = frame.graph().nodes().stream().collect(Collectors.toMap(GraphNode::id, Function.identity()));
        g.setFont(Font.font("Microsoft YaHei", 12));
        for (var edge : frame.graph().edges()) {
            GraphNode a = nodes.get(edge.from()), b = nodes.get(edge.to());
            double x1 = pad + pw * a.x(), y1 = pad + ph * a.y(), x2 = pad + pw * b.x(), y2 = pad + ph * b.y();
            boolean active = edge.equals(frame.activeEdge());
            g.setStroke(Color.web(active ? "#d99719" : "#b7c5da")); g.setLineWidth(active ? 4 : 2);
            g.strokeLine(x1, y1, x2, y2);
            double length = Math.max(1, Math.hypot(x2 - x1, y2 - y1));
            double mx = (x1 + x2) / 2 + (y2 - y1) / length * 10;
            double my = (y1 + y2) / 2 - (x2 - x1) / length * 10;
            String weight = Integer.toString(edge.weight()); double labelWidth = weight.length() * 8 + 8;
            g.setFill(Color.WHITE); g.fillRoundRect(mx - labelWidth / 2, my - 9, labelWidth, 18, 5, 5);
            g.setFill(Color.web("#536783")); g.fillText(weight, mx, my);
        }
        g.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 15));
        for (var node : frame.graph().nodes()) {
            double x = pad + pw * node.x(), y = pad + ph * node.y();
            boolean current = node.id().equals(frame.currentNode()), visited = frame.visited().contains(node.id());
            boolean discovered = frame.frontierKind() == FrontierKind.QUEUE && frame.frontier().contains(node.id());
            g.setFill(Color.web(current ? "#d99719" : visited ? "#239a6d" : discovered ? "#8b5bd6" : "#4975d1"));
            g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            g.setFill(Color.WHITE); g.fillText(node.id(), x, y, radius * 1.7);
        }
        g.setLineWidth(1);
    }
}
