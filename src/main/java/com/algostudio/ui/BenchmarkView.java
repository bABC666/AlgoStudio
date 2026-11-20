package com.algostudio.ui;

import com.algostudio.model.benchmark.*;
import com.algostudio.service.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.Locale;
import java.util.function.Function;

/** 独立数据集的性能页；只在FX线程更新控件，后台结果通过请求编号核对。 */
public final class BenchmarkView extends VBox implements AutoCloseable {
    private final ComboBox<Integer> sizes = new ComboBox<>();
    private final Button run = new Button("开始对比");
    private final Button cancel = new Button("取消");
    private final Label status = new Label("请选择规模，开始对比。");
    private final Label metadata = wrapped("尚无测量结果。", "benchmark-metadata");
    private final TableView<BenchmarkResult> table = new TableView<>();
    private final BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private final ProgressIndicator busy = new ProgressIndicator();
    private final BenchmarkCoordinator coordinator;

    public BenchmarkView() { this(new BenchmarkService()); }

    public BenchmarkView(BenchmarkRunner runner) {
        super(9);
        setId("benchmark-view");
        setPadding(new Insets(16, 24, 16, 24));
        Label title = new Label("排序性能对比"); title.getStyleClass().add("title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        busy.setMaxSize(22, 22); busy.setMinSize(22, 22);
        HBox heading = new HBox(12, title, spacer, busy, status); heading.setAlignment(Pos.CENTER_LEFT);
        sizes.setId("benchmark-size"); sizes.getItems().setAll(100, 500, 2000, 3000);
        sizes.getSelectionModel().select(Integer.valueOf(500));
        run.setId("benchmark-run"); run.getStyleClass().add("primary-button");
        cancel.setId("benchmark-cancel"); status.setId("benchmark-status");
        HBox controls = new HBox(12, new Label("数组规模"), sizes, run, cancel,
                new Label("种子20261102 · 均匀随机整数1～10000 · 预热2次 / 测量5次"));
        controls.setAlignment(Pos.CENTER_LEFT);

        table.setId("benchmark-table");
        table.setPlaceholder(new Label("运行后展示已注册排序的中位数与单次操作计数"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().add(column("算法", r -> r.algorithm().name()));
        table.getColumns().add(column("耗时中位数 (ms)", r -> String.format(Locale.ROOT, "%.6f", r.medianMillis())));
        table.getColumns().add(column("数据比较", r -> Long.toString(r.metrics().comparisons())));
        table.getColumns().add(column("主数组写入", r -> Long.toString(r.metrics().writes())));
        table.getColumns().add(column("交换", r -> Long.toString(r.metrics().swaps())));
        table.setMinHeight(112); table.setPrefHeight(112); table.setMaxHeight(112);

        chart.setId("benchmark-chart"); chart.setAnimated(false); chart.setLegendVisible(false);
        chart.getXAxis().setLabel("排序算法"); chart.getYAxis().setLabel("耗时中位数 (ms)");
        ((NumberAxis) chart.getYAxis()).setForceZeroInRange(true);
        chart.setMinHeight(120); chart.setPrefHeight(280);
        chart.getStyleClass().add("benchmark-chart"); VBox.setVgrow(chart, Priority.ALWAYS);
        Label note = wrapped("教学参考：结果取决于当前硬件、JVM与输入，不是严格科学微基准。计时含操作计数，不含生成、复制、校验或绘图。\n比较=主数组值比较；写入只统计主数组，辅助缓冲区写入不计入；不同索引交换计1次交换、2次写入；快排自交换不计。", "benchmark-note");
        getChildren().addAll(heading, controls, table, chart, metadata, note);
        coordinator = new BenchmarkCoordinator(runner, BenchmarkCoordinator.newWorker(), Platform::runLater, this::show);
        run.setOnAction(e -> coordinator.start(new BenchmarkRequest(sizes.getValue(), BenchmarkRequest.DEFAULT_SEED)));
        cancel.setOnAction(e -> coordinator.cancel());
        sizes.valueProperty().addListener((o, before, after) -> coordinator.invalidate());
    }

    private void show(BenchmarkSnapshot snapshot) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("性能控件必须在FX线程更新。");
        run.setDisable(snapshot.running()); cancel.setDisable(!snapshot.running());
        busy.setVisible(snapshot.running()); busy.setManaged(snapshot.running());
        status.setText(switch (snapshot.state()) {
            case IDLE -> "请选择规模，开始对比。";
            case RUNNING -> "后台测量中…";
            case SUCCEEDED -> "测量完成";
            case CANCELLED -> "已取消，可重新开始。";
            case FAILED -> "失败：" + snapshot.error();
        });
        table.getItems().clear(); chart.getData().clear();
        if (snapshot.report() == null) { metadata.setText("尚无测量结果。每次运行都使用同一种子重新生成原始数组。"); return; }
        BenchmarkReport report = snapshot.report();
        table.getItems().setAll(report.results());
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        report.results().forEach(result -> series.getData().add(new XYChart.Data<>(result.algorithm().name(), result.medianMillis())));
        chart.getData().add(series);
        BenchmarkEnvironment env = report.environment();
        metadata.setText("本次：n=" + report.request().size() + " · seed=" + report.request().seed()
                + " · 2次预热 / 5次测量中位数 · 独立副本\nJava " + env.javaVersion() + " / " + env.vm()
                + " · " + env.os() + " / " + env.architecture() + " · 逻辑处理器 " + env.processors());
    }

    public void cancelActive() { coordinator.cancel(); }
    public ComboBox<Integer> sizeChoice() { return sizes; }
    public Button runButton() { return run; }
    public Button cancelButton() { return cancel; }
    public TableView<BenchmarkResult> resultTable() { return table; }
    public BarChart<String, Number> resultChart() { return chart; }
    @Override public void close() { coordinator.close(); }

    private static TableColumn<BenchmarkResult, String> column(String title, Function<BenchmarkResult, String> value) {
        TableColumn<BenchmarkResult, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setSortable(false);
        return column;
    }
    private static Label wrapped(String text, String id) {
        Label label = new Label(text); label.setId(id); label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE); label.getStyleClass().add("muted"); return label;
    }
}
