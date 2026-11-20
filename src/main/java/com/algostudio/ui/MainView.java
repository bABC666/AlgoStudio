package com.algostudio.ui;

import com.algostudio.algorithm.sort.SortAlgorithm;
import com.algostudio.model.AlgorithmMetrics;
import com.algostudio.model.frame.ArrayFrame;
import com.algostudio.model.frame.GraphFrame;
import com.algostudio.model.frame.TableFrame;
import com.algostudio.model.frame.VisualFrame;
import com.algostudio.playback.PlaybackSnapshot;
import com.algostudio.playback.PlaybackState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.Node;
import javafx.css.PseudoClass;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Arrays;

public final class MainView extends BorderPane implements AutoCloseable {
    private final Canvas canvas = new Canvas();
    private final CanvasViewport canvasViewport = new CanvasViewport(canvas);
    private final ComboBox<SortAlgorithm> algorithms = new ComboBox<>();
    private final ComboBox<String> speeds = new ComboBox<>();
    private final Button generate = button("生成轨迹", "generate-trace");
    private final Button random = button("随机数组", "random-array");
    private final ComboBox<Integer> randomSizes = new ComboBox<>();
    private final TextArea input = new TextArea();
    private final Label inputError = wrapped("", "input-error", "input-error");
    private final Label inputSummary = wrapped("", "muted", "input-summary");
    private final Button play = button("播放", "play-pause");
    private final Button previous = button("上一步", "previous-step");
    private final Button next = button("下一步", "next-step");
    private final Button reset = button("重置", "reset-playback");
    private final Label title = label("排序演示", "title", "algorithm-title");
    private final Label algorithmDescription = wrapped("", "muted", "algorithm-description");
    private final Label status = label("未载入", "badge", "playback-status");
    private final Label step = label("尚未载入", "muted", "step-count");
    private final Label array = label("—", "array-text", "array-values");
    private final Label event = label("初始状态", "event-label", "event-name");
    private final Label explanation = wrapped("", "body", "explanation");
    private final Label comparisons = label("0", "metric-value", "comparisons");
    private final Label writes = label("0", "metric-value", "writes");
    private final Label swaps = label("0", "metric-value", "swaps");
    private final ProgressBar progress = new ProgressBar(0);
    private final GraphControls graphControls = new GraphControls();
    private final TableControls tableControls = new TableControls();
    private final PseudoCodePane pseudocode = new PseudoCodePane();
    private final BorderPane sidebar = new BorderPane();
    private final HBox metrics = new HBox(18);
    private final Label canvasCaption = label("当前数组", "eyebrow", null);
    private final Label comparisonsCaption = label("比较", "metric-caption", null);
    private final Label writesCaption = label("主数组写入", "metric-caption", null);
    private final Label swapsCaption = label("交换", "metric-caption", null);
    private final HBox legend = new HBox(18);
    private final MainController controller;
    private BenchmarkView benchmark;
    private final Node visualNavigation;
    private final Node visualWorkspace;
    private final ToggleButton visualMode = new ToggleButton("执行可视化");
    private final ToggleButton benchmarkMode = new ToggleButton("性能对比");
    private final ToggleButton graphMode = new ToggleButton("图结构");
    private final ToggleButton tableMode = new ToggleButton("DP表格");
    private boolean tablePresentation;
    private boolean graphPresentation;
    private boolean graphPreview = true;
    private Runnable onCanvasResize;

    public MainView() {
        getStyleClass().add("app");
        setTop(header());
        visualNavigation = navigation();
        visualWorkspace = workspace();
        setLeft(sidebarFor(visualNavigation));
        setCenter(visualWorkspace);
        controller = new MainController(this);
        benchmark = new BenchmarkView();
        visualMode.setOnAction(e -> showVisualization(false));
        graphMode.setOnAction(e -> showVisualization(true));
        tableMode.setOnAction(e -> showTable());
        benchmarkMode.setOnAction(e -> {
            benchmarkMode.setSelected(true);
            if (getCenter() == benchmark) return;
            controller.pausePlayback();
            setLeft(null); setCenter(benchmark);
        });
    }

    private void showVisualization(boolean graph) {
        (graph ? graphMode : visualMode).setSelected(true);
        boolean changed = controller.isTableMode() || controller.isGraphMode() != graph;
        if (!changed && getCenter() == visualWorkspace) return;
        benchmark.cancelActive();
        setLeft(sidebarFor(graph ? graphControls : visualNavigation)); setCenter(visualWorkspace);
        tablePresentation=false;showGraphPresentation(graph);
        if (changed) {
            if (graph) controller.showGraph();
            else controller.showSorting();
        }
    }

    private void showGraphPresentation(boolean graph) {
        graphPresentation = graph;
        for(Node metric:metrics.getChildren()){metric.setVisible(true);metric.setManaged(true);}
        boolean shortest = graphControls.algorithmChoice().getValue() == GraphRunMode.DIJKSTRA;
        canvasCaption.setText(graph ? (graphPreview ? "当前图 · 样式示意" : shortest ? "当前图 · 非负权最短路径" : "当前图 · 按ID升序遍历") : "当前数组");
        comparisonsCaption.setText(graph ? (shortest ? "确定节点" : "访问节点") : "比较");
        writesCaption.setText(graph ? "检查边" : "主数组写入");
        swapsCaption.setText(graph ? "松弛" : "交换");
        if (graph) {
            legend.getChildren().setAll(legend(shortest ? "未确定" : !graphPreview&&graphControls.algorithmChoice().getValue()==GraphRunMode.BFS ? "尚未发现" : "未访问", "normal"), legend("当前节点/活动边", "compare"), legend(graphPreview ? "已访问样式" : shortest ? "最短距离已确定" : "已访问", "done"));
            if (!graphPreview && !shortest) legend.getChildren().add(legend("已发现待访问", "discovered"));
        }
        else if (algorithms.getValue() != null && algorithms.getValue().descriptor().id().equals("merge-sort"))
            legend.getChildren().setAll(legend("左区间", "normal"), legend("右区间", "pivot"),
                    legend("比较", "compare"), legend("写入", "swap"), legend("已完成", "done"));
        else legend.getChildren().setAll(legend("普通", "normal"), legend("比较", "compare"),
                    legend("交换", "swap"), legend("枢轴", "pivot"), legend("已完成", "done"));
    }

    public void setGraphPreview(boolean preview) {
        graphPreview = preview;
        if (graphPresentation) showGraphPresentation(true);
    }

    private void showTable() {
        tableMode.setSelected(true);
        boolean changed=!controller.isTableMode();
        if(!changed&&getCenter()==visualWorkspace)return;
        benchmark.cancelActive();setLeft(sidebarFor(tableControls));setCenter(visualWorkspace);
        tablePresentation=true;graphPresentation=false;
        updateTablePresentation();comparisonsCaption.setText("更新单元格");
        for(int i=1;i<metrics.getChildren().size();i++){metrics.getChildren().get(i).setVisible(false);metrics.getChildren().get(i).setManaged(false);}
        legend.getChildren().setAll(legend("未计算 —","pending"),legend("当前格","compare"),legend("依赖格","normal"),legend("回溯高亮","done"));
        if(changed)controller.showTable();
    }
    public void updateTablePresentation(){
        if(tablePresentation)canvasCaption.setText(isTablePreview()?"当前表格 · 样式示意":"当前DP表 · 转移与回溯");
    }
    private boolean isTablePreview(){return tableControls.algorithmChoice().getValue()==TableRunMode.PREVIEW;}

    private HBox header() {
        ToggleGroup modes = new ToggleGroup();
        visualMode.setToggleGroup(modes); graphMode.setToggleGroup(modes); tableMode.setToggleGroup(modes); benchmarkMode.setToggleGroup(modes);
        visualMode.setId("mode-visualization"); benchmarkMode.setId("mode-benchmark");
        graphMode.setId("mode-graph");
        tableMode.setId("mode-table");
        visualMode.setSelected(true);
        HBox box = new HBox(16, label("AlgoStudio", "brand", null),
                label("看见算法的每一步", "muted", null), spacer(), visualMode, graphMode, tableMode, benchmarkMode);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("header");
        return box;
    }

    private Node sidebarFor(Node controls){
        sidebar.setCenter(controls);sidebar.setBottom(pseudocode);
        sidebar.setMinWidth(240);sidebar.setPrefWidth(260);
        return sidebar;
    }

    public void showPseudocode(String algorithmId){pseudocode.setProgram(algorithmId);}

    private ScrollPane navigation() {
        algorithms.setId("algorithm-choice");
        algorithms.setMaxWidth(Double.MAX_VALUE);
        input.setId("array-input");
        input.setWrapText(true);
        input.setPrefRowCount(2);
        input.setPrefHeight(76);
        input.setMinHeight(64);
        input.setPromptText("例如：5, 2 4, 1, 3");
        input.getStyleClass().add("input-editor");
        inputError.setVisible(false);
        inputError.setManaged(false);
        randomSizes.setId("random-size");
        randomSizes.getItems().setAll(5, 12, 20, 30, 50);
        randomSizes.getSelectionModel().select(Integer.valueOf(12));
        randomSizes.setPrefWidth(76);
        HBox randomControls = new HBox(8, randomSizes, random);
        randomControls.setAlignment(Pos.CENTER_LEFT);
        random.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(random, Priority.ALWAYS);
        VBox inputCard = new VBox(7, label("数组输入", "eyebrow", null), input,
                wrapped("5～50项，值1～100；逗号或空白分隔。", "muted", null), inputError);
        inputCard.getStyleClass().add("input-card");
        generate.setMaxWidth(Double.MAX_VALUE);
        generate.getStyleClass().add("primary-button");
        VBox box = new VBox(10, label("算法", "eyebrow", null), algorithms, inputCard,
                label("随机规模", "eyebrow", null), randomControls, generate, inputSummary,
                wrapped("修改输入会停止旧播放。生成轨迹后即可播放或逐步观察。", "muted", null));
        box.getStyleClass().add("sidebar");
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(260);
        scroll.setMinWidth(240);
        scroll.getStyleClass().add("sidebar-scroll");
        return scroll;
    }

    private VBox workspace() {
        HBox heading = new HBox(12, title, spacer(), status);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label description = algorithmDescription;
        canvas.setId("array-canvas");
        CanvasViewport canvasPane = canvasViewport;
        canvasViewport.setOnResize(this::resized);
        VBox.setVgrow(canvasPane, Priority.ALWAYS);
        array.setMinWidth(0);
        array.setMaxWidth(Double.MAX_VALUE);
        array.setWrapText(true);
        array.setAlignment(Pos.CENTER_LEFT);
        array.setMinHeight(Region.USE_PREF_SIZE);
        VBox chartHeading = new VBox(4, canvasCaption, array);
        chartHeading.setAlignment(Pos.CENTER_LEFT);
        showGraphPresentation(false);
        legend.setAlignment(Pos.CENTER);
        VBox chart = new VBox(8, chartHeading, canvasPane, legend);
        chart.getStyleClass().add("canvas-card");
        VBox.setVgrow(chart, Priority.ALWAYS);

        play.getStyleClass().add("primary-button");
        play.setMinWidth(106);
        speeds.setId("playback-speed");
        speeds.getItems().setAll("1×", "2×", "4×", "8×");
        speeds.getSelectionModel().selectFirst();
        HBox controls = new HBox(8, reset, previous, play, next, spacer(),
                label("速度", "muted", null), speeds, step);
        controls.setAlignment(Pos.CENTER_LEFT);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setMinHeight(6);
        progress.setPrefHeight(6);
        progress.setMaxHeight(6);
        progress.getStyleClass().add("playback-progress");

        VBox explanationBox = new VBox(6, event, explanation);
        explanation.setMinHeight(Region.USE_PREF_SIZE);
        explanationBox.setMinHeight(Region.USE_PREF_SIZE);
        explanationBox.setMinWidth(0);
        HBox.setHgrow(explanationBox, Priority.ALWAYS);
        metrics.getChildren().setAll(metric(comparisonsCaption, comparisons), metric(writesCaption, writes), metric(swapsCaption, swaps));
        metrics.setAlignment(Pos.CENTER);
        HBox result = new HBox(20, explanationBox, metrics);
        result.setAlignment(Pos.CENTER_LEFT);
        result.getStyleClass().add("result-card");
        result.setMinHeight(Region.USE_PREF_SIZE);
        VBox box = new VBox(10, heading, description, chart, controls, progress, result);
        box.setPadding(new Insets(20));
        return box;
    }

    public void showAlgorithm(String name, String description) {
        title.setText(name);
        algorithmDescription.setText(description);
        if (!graphPresentation && !tablePresentation) showGraphPresentation(false);
    }

    public void showPlayback(PlaybackSnapshot snapshot) {
        pseudocode.showFrame(snapshot.frame());
        boolean loaded = snapshot.frame() != null;
        status.setText(switch (snapshot.state()) {
            case EMPTY -> "未载入";
            case READY -> "就绪";
            case PLAYING -> "播放中";
            case PAUSED -> "已暂停";
            case FINISHED -> tablePresentation && isTablePreview() || graphPresentation && graphPreview ? "预览完成" : "已完成";
            case ERROR -> "生成失败";
        });
        play.setText(snapshot.state() == PlaybackState.PLAYING ? "暂停"
                : snapshot.state() == PlaybackState.FINISHED ? "重新播放" : "播放");
        play.setDisable(!loaded);
        previous.setDisable(!loaded || snapshot.stepIndex() == 0);
        next.setDisable(!loaded || snapshot.stepIndex() == snapshot.frameCount() - 1);
        reset.setDisable(!loaded || (snapshot.stepIndex() == 0 && snapshot.state() != PlaybackState.PLAYING));
        speeds.setDisable(!loaded);
        step.setText(loaded ? "第 " + (snapshot.stepIndex() + 1) + " / " + snapshot.frameCount() + " 帧" : "尚未载入");
        progress.setProgress(loaded ? (double) snapshot.stepIndex() / (snapshot.frameCount() - 1) : 0);
        AlgorithmMetrics metrics = loaded ? snapshot.frame().metrics() : AlgorithmMetrics.ZERO;
        comparisons.setText(tablePresentation ? (isTablePreview()?"—":Long.toString(metrics.dpCellsUpdated())) : graphPresentation ? (graphPreview ? "—" : Long.toString(metrics.nodesVisited())) : Long.toString(metrics.comparisons()));
        writes.setText(tablePresentation ? "—" : graphPresentation ? (graphPreview ? "—" : Long.toString(metrics.edgesExamined())) : Long.toString(metrics.writes()));
        swaps.setText(tablePresentation ? "—" : graphPresentation ? (graphPreview ? "—" : Long.toString(metrics.relaxations())) : Long.toString(metrics.swaps()));
        explanation.setText(loaded ? snapshot.frame().explanation() : snapshot.error());
        event.setText(loaded ? switch (snapshot.frame().eventKind()) {
            case INITIAL -> "初始状态";
            case COMPARE -> "比较";
            case SWAP -> "交换";
            case WRITE -> "写入";
            case PIVOT -> "枢轴";
            case DIVIDE -> "分治区间";
            case MERGE -> "合并区间";
            case GRAPH_PREVIEW -> "图状态示意";
            case TABLE_PREVIEW -> "表格样式示意";
            case DP_INITIALIZE -> "初始化边界";
            case DP_UPDATE -> "更新单元格";
            case DISCOVER -> "发现 / 入队";
            case VISIT -> "访问节点";
            case EXAMINE_EDGE -> "检查边";
            case BACKTRACK -> tablePresentation ? "回溯解" : "返回调用栈";
            case SETTLE -> "确定最短距离";
            case RELAX -> "松弛边";
            case DONE -> tablePresentation ? (isTablePreview()?"表格预览完成":"动态规划完成") : graphPresentation ? (graphPreview ? "预览完成" : graphControls.algorithmChoice().getValue() == GraphRunMode.DIJKSTRA ? "最短路径完成" : "遍历完成") : "排序完成";
        } : "等待轨迹");
        if (snapshot.frame() instanceof ArrayFrame frame) array.setText(Arrays.toString(frame.values()));
        else if (snapshot.frame() instanceof GraphFrame frame)
            array.setText(frame.graph().nodes().size() + "顶点 · " + frame.graph().edges().size() + "条无向边 · 固定坐标 / 非负权重");
        else if (snapshot.frame() instanceof TableFrame frame)
            array.setText(frame.rowLabels().size()+"行 × "+frame.columnLabels().size()+"列 · "+frame.rowTitle()+" / "+frame.columnTitle()+(isTablePreview()?" · 样式示意":""));
        else array.setText("—");
    }

    public void setOnCanvasResize(Runnable action) { onCanvasResize = action; }
    private void resized() { if (onCanvasResize != null) onCanvasResize.run(); }
    public Canvas canvas() { return canvas; }
    public void prepareCanvas(VisualFrame frame) { canvasViewport.showFrame(frame); }
    public ComboBox<SortAlgorithm> algorithmChoice() { return algorithms; }
    public ComboBox<String> speedChoice() { return speeds; }
    public Button generateButton() { return generate; }
    public Button playButton() { return play; }
    public Button previousButton() { return previous; }
    public Button nextButton() { return next; }
    public Button resetButton() { return reset; }
    public TextArea arrayInput() { return input; }
    public ComboBox<Integer> randomSizeChoice() { return randomSizes; }
    public Button randomButton() { return random; }
    public GraphControls graphControls() { return graphControls; }
    public TableControls tableControls() { return tableControls; }

    public void showInputError(String message) {
        boolean invalid = message != null && !message.isBlank();
        inputError.setText(invalid ? message : "");
        inputError.setVisible(invalid);
        inputError.setManaged(invalid);
        input.pseudoClassStateChanged(PseudoClass.getPseudoClass("invalid"), invalid);
    }

    public void showInputSummary(String text) { inputSummary.setText(text); }

    public void setGenerating(boolean generating) {
        generate.setDisable(generating);
        random.setDisable(generating);
        algorithms.setDisable(generating);
        graphControls.setBusy(generating);
        tableControls.setBusy(generating);
    }

    public BenchmarkView benchmarkView() { return benchmark; }
    public void cancelBenchmark() { if (benchmark != null) benchmark.cancelActive(); }

    @Override public void close() {
        controller.close();
        if (benchmark != null) benchmark.close();
    }

    private VBox metric(Label name, Label value) {
        VBox box = new VBox(4, value, name);
        box.setMinWidth(62);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Label legend(String text, String color) {
        return label("● " + text, "legend-" + color, null);
    }

    private Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private Button button(String text, String id) {
        Button button = new Button(text);
        button.setId(id);
        return button;
    }

    private Label label(String text, String style, String id) {
        Label label = new Label(text);
        label.setId(id);
        label.getStyleClass().add(style);
        return label;
    }

    private Label wrapped(String text, String style, String id) {
        Label label = label(text, style, id);
        label.setWrapText(true);
        return label;
    }
}
