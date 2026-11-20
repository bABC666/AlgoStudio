package com.algostudio.ui;

import com.algostudio.algorithm.sort.BubbleSort;
import com.algostudio.algorithm.sort.MergeSort;
import com.algostudio.algorithm.AlgorithmRegistry;
import com.algostudio.algorithm.sort.SortAlgorithm;
import com.algostudio.model.frame.GraphFrame;
import com.algostudio.model.ExecutionTrace;
import com.algostudio.model.frame.TableFrame;
import com.algostudio.service.TablePreviewService;
import com.algostudio.service.DpTraceService;
import com.algostudio.model.output.AlgorithmOutput;
import com.algostudio.model.input.ArrayInput;
import com.algostudio.playback.FxPlaybackClock;
import com.algostudio.playback.PlaybackController;
import com.algostudio.playback.PlaybackSnapshot;
import com.algostudio.playback.PlaybackState;
import com.algostudio.service.InputValidator;
import com.algostudio.service.RandomArrayGenerator;
import com.algostudio.service.TraceService;
import com.algostudio.service.GraphPreviewService;
import com.algostudio.service.GraphTraceService;
import com.algostudio.model.input.GraphTraversalInput;
import com.algostudio.ui.renderer.RendererPipeline;
import javafx.scene.control.TextFormatter;

import java.util.Arrays;
import java.util.stream.Collectors;

/** 统一输入→校验→轨迹→播放入口；不执行用户代码、不复制播放器。 */
public final class MainController implements AutoCloseable {
    private final MainView view;
    private final TraceService traces = new TraceService();
    private final InputValidator validator = new InputValidator();
    private final RandomArrayGenerator random = new RandomArrayGenerator();
    private final RendererPipeline renderer = new RendererPipeline();
    private final GraphPreviewService graphPreviews = new GraphPreviewService();
    private final GraphTraceService graphTraces = new GraphTraceService();
    private final TablePreviewService tablePreviews = new TablePreviewService();
    private final DpTraceService dpTraces = new DpTraceService();
    private AlgorithmOutput tableOutput;
    private final PlaybackController playback;
    private boolean generating;
    private boolean closed;
    private boolean graphMode;
    private boolean tableMode;

    public MainController(MainView view) {
        this.view = view;
        playback = new PlaybackController(new FxPlaybackClock(), this::refresh);
        view.algorithmChoice().getItems().setAll(AlgorithmRegistry.sortingAlgorithms());
        view.algorithmChoice().valueProperty().addListener((observable, before, after) -> regenerate());
        view.arrayInput().setTextFormatter(new TextFormatter<>(change -> {
            if (!change.isContentChange()) return change;
            int retainedLength = change.getControlText().length() - (change.getRangeEnd() - change.getRangeStart());
            try {
                String normalized = validator.normalizeEdit(change.getText(), retainedLength);
                if (!normalized.equals(change.getText())) change.setText(normalized);
                return change;
            } catch (IllegalArgumentException invalid) {
                reject(invalid.getMessage());
                return null;
            }
        }));
        view.arrayInput().textProperty().addListener((observable, before, after) -> inputChanged());
        view.generateButton().setOnAction(event -> regenerate());
        view.randomButton().setOnAction(event -> randomize());
        view.playButton().setOnAction(event -> {
            if (playback.snapshot().state() == PlaybackState.PLAYING) playback.pause();
            else playback.play();
        });
        view.previousButton().setOnAction(event -> playback.stepBackward());
        view.nextButton().setOnAction(event -> playback.stepForward());
        view.resetButton().setOnAction(event -> playback.reset());
        view.speedChoice().valueProperty().addListener((observable, before, after) -> {
            if (after != null) playback.setSpeed(switch (after) {
                case "2×" -> 2;
                case "4×" -> 4;
                case "8×" -> 8;
                default -> 1;
            });
        });
        view.setOnCanvasResize(this::redraw);
        view.tableControls().exampleChoice().valueProperty().addListener((o,before,after)->regenerateTable());
        view.tableControls().algorithmChoice().valueProperty().addListener((o,before,after)->regenerateTable());
        view.tableControls().knapsackChoice().valueProperty().addListener((o,before,after)->regenerateTable());
        view.tableControls().lcsChoice().valueProperty().addListener((o,before,after)->regenerateTable());
        view.tableControls().previewButton().setOnAction(event->regenerateTable());
        view.graphControls().previewButton().setOnAction(event -> regenerateGraph());
        view.graphControls().algorithmChoice().valueProperty().addListener((o, before, after) -> regenerateGraph());
        view.graphControls().startChoice().valueProperty().addListener((o, before, after) -> regenerateGraph());
        view.graphControls().exampleChoice().valueProperty().addListener((o, before, after) -> {
            if (closed || generating) return;
            generating = true;
            try { if (after != null) view.graphControls().setStarts(after.graph(), view.graphControls().startChoice().getValue()); }
            finally { generating = false; }
            regenerateGraph();
        });
        setInput(random.generate(RandomArrayGenerator.DEFAULT_SIZE));
        view.algorithmChoice().getSelectionModel().selectFirst();
    }

    private void inputChanged() {
        if (closed || graphMode || tableMode) return;
        view.cancelBenchmark();
        playback.clear();
        view.showInputError("");
        view.showInputSummary("已编辑 · " + view.arrayInput().getLength() + " / 4096字符，请生成轨迹。");
    }

    private void randomize() {
        if (closed || generating || graphMode || tableMode) return;
        try {
            Integer size = view.randomSizeChoice().getValue();
            if (size == null) throw new IllegalArgumentException("请选择随机数组的规模。");
            setInput(random.generate(size));
            regenerate();
        } catch (RuntimeException failure) {
            reject(failure.getMessage());
        }
    }

    private void setInput(ArrayInput input) {
        view.arrayInput().setText(Arrays.stream(input.values()).mapToObj(Integer::toString).collect(Collectors.joining(", ")));
    }

    private void regenerate() {
        if (closed || generating || graphMode || tableMode) return;
        view.cancelBenchmark();
        generating = true;
        view.setGenerating(true);
        playback.clear();
        view.showInputError("");
        try {
            SortAlgorithm algorithm = view.algorithmChoice().getValue();
            if (algorithm == null) return;
            view.showAlgorithm(algorithm.descriptor().name(),
                    algorithm instanceof BubbleSort ? "相邻比较，逐轮把较大值移到右侧。"
                            : algorithm instanceof MergeSort ? "递归处理左右区间，归并到辅助缓冲后逐项写回；辅助写入不计，交换为0。"
                            : "选定枢轴，分区后处理较小的子区间。");
            ArrayInput input = validator.parseArray(view.arrayInput().getText());
            load(traces.generate(algorithm, input));
            view.showInputSummary("已载入 " + input.size() + " 项 · " + view.arrayInput().getLength() + " / 4096字符");
        } catch (RuntimeException failure) {
            reject(failure.getMessage());
        } finally {
            generating = false;
            view.setGenerating(false);
        }
    }

    private void reject(String message) {
        if (closed || graphMode || tableMode) return;
        view.cancelBenchmark();
        playback.fail(message);
        view.showInputError(message);
        view.showInputSummary("校验未通过，请修正输入后生成轨迹。");
    }

    private void refresh(PlaybackSnapshot snapshot) {
        view.showPlayback(snapshot);
        view.graphControls().showFrame(snapshot.frame() instanceof GraphFrame graph ? graph : null);
        view.tableControls().showFrame(snapshot.frame() instanceof TableFrame table ? table : null,tableOutput);
        render(snapshot);
    }

    private void load(ExecutionTrace trace){
        view.showPseudocode(trace.algorithmId());
        playback.load(trace);
    }

    public boolean isGraphMode() { return graphMode; }
    public boolean isTableMode() { return tableMode; }
    public void showGraph() { graphMode = true; tableMode = false; tableOutput=null; regenerateGraph(); }
    public void showSorting() { graphMode = false; tableMode = false; tableOutput=null; regenerate(); }
    public void showTable() { graphMode = false; tableMode = true; regenerateTable(); }

    private void regenerateTable() {
        if (closed || generating || !tableMode) return;
        view.cancelBenchmark(); generating=true; view.setGenerating(true); tableOutput=null; playback.clear();
        try {
            var controls=view.tableControls();var mode=controls.algorithmChoice().getValue();
            if(mode==null)throw new IllegalArgumentException("请选择DP算法或表格预览。");
            view.updateTablePresentation();
            var trace=switch(mode){
                case PREVIEW->{
                    if(controls.exampleChoice().getValue()==null)throw new IllegalArgumentException("请选择预览布局。");
                    view.showAlgorithm("DP表格样式预览", "展示完整快照、当前格与依赖/回溯高亮；不执行背包或LCS算法。");
                    yield tablePreviews.generate(controls.exampleChoice().getValue());
                }
                case KNAPSACK->{
                    if(controls.knapsackChoice().getValue()==null)throw new IllegalArgumentException("请选择背包预设。");
                    view.showAlgorithm("0/1 背包", "逐格比较选/不选当前物品；同值不选；回溯得到最大价值和物品编号。");
                    yield dpTraces.generateKnapsack(controls.knapsackChoice().getValue().input());
                }
                case LCS->{
                    if(controls.lcsChoice().getValue()==null)throw new IllegalArgumentException("请选择LCS预设。");
                    view.showAlgorithm("最长公共子序列 LCS", "字符相同取左上+1，不同取上/左较大值；回溯平局向上。");
                    yield dpTraces.generateLcs(controls.lcsChoice().getValue().input());
                }
            };
            tableOutput=trace.run().output();load(trace);
        } catch(RuntimeException failure) { playback.fail(failure.getMessage()); }
        finally { generating=false;view.setGenerating(false); }
    }

    private void regenerateGraph() {
        if (closed || generating || !graphMode) return;
        view.cancelBenchmark();
        generating = true; view.setGenerating(true); playback.clear();
        try {
            var example = view.graphControls().exampleChoice().getValue();
            if (example == null) throw new IllegalArgumentException("请选择固定示例图。");
            GraphRunMode mode = view.graphControls().algorithmChoice().getValue();
            if (mode == null) throw new IllegalArgumentException("请选择图算法或预览。");
            view.setGraphPreview(mode == GraphRunMode.PREVIEW);
            if (mode == GraphRunMode.PREVIEW) {
                view.showAlgorithm("图状态预览", "固定无向非负加权图 · 样式示意，不执行遍历。");
                load(graphPreviews.generate(example.graph(), view.graphControls().startChoice().getValue()));
            } else {
                var algorithm = AlgorithmRegistry.graphAlgorithms().stream().filter(a -> a.descriptor().id().equals(mode.algorithmId())).findFirst().orElseThrow();
                view.showAlgorithm(algorithm.descriptor().name(), switch (mode) {
                    case BFS -> "按ID升序检查邻居；入队时发现，出队时访问；忽略边权。";
                    case DFS -> "按ID升序逐一深入；显式调用栈模拟递归，栈顶在右；忽略边权。";
                    case DIJKSTRA -> "非负边权；选择最小暂定距离并确定，严格改善时松弛；同距按ID升序。";
                    case PREVIEW -> throw new IllegalStateException("预览不执行算法。");
                });
                load(graphTraces.generate(algorithm, new GraphTraversalInput(example.graph(), view.graphControls().startChoice().getValue())));
            }
        } catch (RuntimeException failure) { playback.fail(failure.getMessage()); }
        finally { generating = false; view.setGenerating(false); }
    }

    public void pausePlayback() { playback.pause(); }

    private void redraw() {
        render(playback.snapshot());
    }

    private void render(PlaybackSnapshot snapshot) {
        view.prepareCanvas(snapshot.frame());
        renderer.render(snapshot.frame(), view.canvas());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        tableOutput=null;
        playback.dispose();
    }
}
