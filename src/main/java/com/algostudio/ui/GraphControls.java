package com.algostudio.ui;

import com.algostudio.model.frame.GraphFrame;
import com.algostudio.model.frame.EventKind;
import com.algostudio.model.graph.*;
import com.algostudio.model.input.GraphInput;
import com.algostudio.model.output.ShortestPathOutput;
import com.algostudio.service.GraphExamples;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.stream.Collectors;

/** 固定示例与起点选择；信息全部来自当前帧，不在UI计算遍历。 */
public final class GraphControls extends ScrollPane {
    private final ComboBox<GraphExample> examples = new ComboBox<>();
    private final ComboBox<String> starts = new ComboBox<>();
    private final ComboBox<GraphRunMode> algorithms = new ComboBox<>();
    private final Button preview = new Button("重新预览状态");
    private final Label current = wrapped("当前节点：—", "graph-current");
    private final Label edge = wrapped("活动边：—", "graph-edge");
    private final Label frontier = wrapped("工作列表：[]", "graph-frontier");
    private final Label visited = wrapped("高亮集合（示意）：[]", "graph-visited");
    private final Label order = wrapped("", "graph-order");
    private final Label discovered = wrapped("", "graph-discovered");
    private final Label parents = wrapped("", "graph-parents");
    private final Label unvisited = wrapped("", "graph-unvisited");
    private final Label note = wrapped("", "graph-preview-note");
    private final GridPane distanceTable = new GridPane();
    private final Label distanceCaption = caption("距离表 · dist / prev");
    private final Label targetCaption = caption("查看目标路径");
    private final ComboBox<String> targets = new ComboBox<>();
    private final Label path = wrapped("", "graph-path");
    private GraphFrame shownFrame;

    public GraphControls() {
        algorithms.setId("graph-algorithm"); algorithms.setMaxWidth(Double.MAX_VALUE);
        algorithms.getItems().setAll(GraphRunMode.values()); algorithms.getSelectionModel().selectFirst();
        examples.setId("graph-example"); examples.setMaxWidth(Double.MAX_VALUE);
        examples.getItems().setAll(GraphExamples.all()); examples.getSelectionModel().selectFirst();
        starts.setId("graph-start"); starts.setMaxWidth(Double.MAX_VALUE);
        setStarts(examples.getValue().graph(), "A");
        preview.setId("graph-preview"); preview.setMaxWidth(Double.MAX_VALUE); preview.getStyleClass().add("primary-button");
        distanceTable.setId("graph-distance-table"); distanceTable.setHgap(5); distanceTable.setVgap(4);
        for (double percent : new double[] { 17, 27, 20, 36 }) { var column = new ColumnConstraints(); column.setPercentWidth(percent); distanceTable.getColumnConstraints().add(column); }
        targets.setId("graph-target"); targets.setMaxWidth(Double.MAX_VALUE);
        targets.valueProperty().addListener((o, before, after) -> showPath());
        VBox content = new VBox(6, caption("图算法 / 预览"), algorithms, caption("固定示例图"), examples, caption("选择起点"), starts, preview,
                note, current, edge, distanceCaption, distanceTable, targetCaption, targets, path, frontier, discovered, visited, order, parents, unvisited);
        content.getStyleClass().addAll("sidebar", "graph-sidebar");
        setContent(content); setFitToWidth(true); setFitToHeight(true);
        setPrefWidth(260); setMinWidth(240); setHbarPolicy(ScrollBarPolicy.NEVER); getStyleClass().add("sidebar-scroll");
        showFrame(null);
    }
    public void setStarts(GraphInput graph, String preferred) {
        starts.getItems().setAll(graph.nodes().stream().map(GraphNode::id).toList());
        if (starts.getItems().contains(preferred)) starts.getSelectionModel().select(preferred);
        else starts.getSelectionModel().selectFirst();
    }
    public void showFrame(GraphFrame frame) {
        shownFrame = frame;
        boolean actual = algorithms.getValue() != null && algorithms.getValue() != GraphRunMode.PREVIEW;
        boolean shortest = algorithms.getValue() == GraphRunMode.DIJKSTRA;
        preview.setText(actual ? "重新生成轨迹" : "重新预览状态");
        note.setText(shortest ? "边权非负；同距按ID选点。橙色边为当前检查/松弛，绿色节点已确定最短距离。"
                : actual ? "邻居按ID升序；忽略边权。队首在左，栈顶在右。" : "图状态示意，不执行算法。可在上方选择图算法。");
        current.setText("当前节点：" + (frame == null || frame.currentNode() == null ? "—" : frame.currentNode()));
        edge.setText("活动边：" + (frame == null || frame.activeEdge() == null ? "—"
                : frame.activeEdge().from() + "—" + frame.activeEdge().to() + "（" + frame.activeEdge().weight() + "）"));
        frontier.setText(frame == null ? "工作列表：[]" : switch (frame.frontierKind()) {
            case NONE -> "工作列表：[]";
            case QUEUE -> "队列（队首在左）：" + frame.frontier();
            case STACK -> "栈（栈顶在右）：" + frame.frontier();
        });
        visited.setText((shortest ? "已确定：" : actual ? "已访问：" : "高亮集合（示意）：") + (frame == null ? "[]" : frame.visited().stream().sorted().toList()));
        var found = new TreeSet<String>();
        if (frame != null) { found.addAll(frame.visited()); found.addAll(frame.frontier()); }
        discovered.setText("已发现：" + found);
        order.setText((shortest ? "确定顺序：" : "访问顺序：") + (frame == null ? "[]" : frame.visitOrder()));
        parents.setText((shortest ? "prev：" : "父节点：") + (frame == null || frame.parents().isEmpty() ? "{}" : frame.parents().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).map(e -> e.getKey() + "←" + e.getValue()).collect(Collectors.joining(", "))));
        unvisited.setText((frame != null && frame.eventKind() == EventKind.DONE ? "不可达：" : shortest ? "未确定：" : "未访问：")
                + (frame == null ? "[]" : frame.graph().nodes().stream().map(GraphNode::id).filter(id -> !frame.visited().contains(id)).toList()));
        for (Label label : List.of(discovered, order, parents, unvisited)) { label.setManaged(actual); label.setVisible(actual); }
        for (var node : List.of(distanceCaption, distanceTable, targetCaption, targets, path)) { node.setManaged(shortest); node.setVisible(shortest); }
        frontier.setManaged(!shortest); frontier.setVisible(!shortest);
        discovered.setManaged(actual && !shortest); discovered.setVisible(actual && !shortest);
        parents.setManaged(actual && !shortest); parents.setVisible(actual && !shortest);
        if (shortest) showDistances(frame);
        showPath();
    }
    private void showDistances(GraphFrame frame) {
        distanceTable.getChildren().clear();
        for (int col = 0; col < 4; col++) distanceTable.add(caption(List.of("节点", "dist", "prev", "状态").get(col)), col, 0);
        if (frame == null) return;
        var ids = frame.graph().nodes().stream().map(GraphNode::id).toList();
        if (!ids.equals(List.copyOf(targets.getItems()))) {
            String selected = targets.getValue(); targets.getItems().setAll(ids);
            targets.getSelectionModel().select(ids.contains(selected) ? selected : ids.contains("E") ? "E" : ids.getFirst());
        }
        int row = 1;
        for (String id : ids) {
            long distance = frame.distances().getOrDefault(id, Long.MAX_VALUE);
            String state = frame.visited().contains(id) ? "已确定" : distance != Long.MAX_VALUE ? "暂定" : frame.eventKind() == EventKind.DONE ? "不可达" : "未发现";
            distanceTable.add(wrapped(id, "graph-node-" + id), 0, row);
            distanceTable.add(wrapped(distance == Long.MAX_VALUE ? "∞" : Long.toString(distance), "graph-dist-" + id), 1, row);
            distanceTable.add(wrapped(frame.parents().getOrDefault(id, "—"), "graph-prev-" + id), 2, row);
            distanceTable.add(wrapped(state, "graph-state-" + id), 3, row++);
        }
    }
    private void showPath() {
        if (shownFrame == null || shownFrame.distances().isEmpty() || targets.getValue() == null) { path.setText(""); return; }
        String target = targets.getValue();
        if (!shownFrame.distances().containsKey(target)) { path.setText(""); return; }
        var nodes = new ShortestPathOutput(starts.getValue(), shownFrame.distances(), shownFrame.parents()).pathTo(target);
        if (nodes.isEmpty()) path.setText(shownFrame.eventKind() == EventKind.DONE ? "目标" + target + "不可达（∞）。" : "尚未发现到" + target + "的路径（∞）。");
        else path.setText((shownFrame.visited().contains(target) ? "确定路径：" : "暂定路径：") + String.join(" → ", nodes)
                + "（距离" + shownFrame.distances().get(target) + "）");
    }
    public void setBusy(boolean busy) { algorithms.setDisable(busy); examples.setDisable(busy); starts.setDisable(busy); preview.setDisable(busy); }
    public ComboBox<GraphRunMode> algorithmChoice() { return algorithms; }
    public ComboBox<GraphExample> exampleChoice() { return examples; }
    public ComboBox<String> startChoice() { return starts; }
    public Button previewButton() { return preview; }
    private static Label caption(String text) { Label label = new Label(text); label.getStyleClass().add("eyebrow"); return label; }
    private static Label wrapped(String text, String id) {
        Label label = new Label(text); label.setId(id); label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE); label.setMinHeight(Region.USE_PREF_SIZE); label.getStyleClass().add("muted"); return label;
    }
}
