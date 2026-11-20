package com.algostudio.ui;

import com.algostudio.model.frame.TableFrame;
import com.algostudio.model.frame.EventKind;
import com.algostudio.model.output.*;
import com.algostudio.service.DpExamples;
import com.algostudio.model.table.TableCell;
import com.algostudio.model.table.TableExample;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.stream.Collectors;

/** 输入为固定预设；状态来自当前帧，最终输出仅在DONE时展示。 */
public final class TableControls extends ScrollPane {
    private final ComboBox<TableRunMode> algorithms=new ComboBox<>();
    private final ComboBox<DpExamples.KnapsackExample> knapsacks=new ComboBox<>();
    private final ComboBox<DpExamples.LcsExample> strings=new ComboBox<>();
    private final ComboBox<TableExample> examples=new ComboBox<>();
    private final Button preview=new Button("重新预览表格");
    private final Label shape=wrapped("","table-shape"),axes=wrapped("","table-axes"),current=wrapped("","table-current"),
            dependencies=wrapped("","table-dependencies"),path=wrapped("","table-path"),
            note=wrapped("","table-preview-note"),input=wrapped("","table-input"),result=wrapped("","table-result");
    public TableControls(){
        algorithms.setId("table-algorithm");algorithms.getItems().setAll(TableRunMode.values());algorithms.getSelectionModel().selectFirst();algorithms.setMaxWidth(Double.MAX_VALUE);
        knapsacks.setId("knapsack-example");knapsacks.getItems().setAll(DpExamples.knapsacks());knapsacks.getSelectionModel().selectFirst();knapsacks.setMaxWidth(Double.MAX_VALUE);
        strings.setId("lcs-example");strings.getItems().setAll(DpExamples.lcs());strings.getSelectionModel().selectFirst();strings.setMaxWidth(Double.MAX_VALUE);
        examples.setId("table-example");examples.getItems().setAll(TableExample.values());examples.getSelectionModel().selectFirst();examples.setMaxWidth(Double.MAX_VALUE);
        preview.setId("table-preview");preview.setMaxWidth(Double.MAX_VALUE);preview.getStyleClass().add("primary-button");
        var title=wrapped("动态规划 / 表格预览","table-preview-title");title.getStyleClass().add("eyebrow");
        VBox content=new VBox(10,title,algorithms,examples,knapsacks,strings,preview,note,input,
                shape,axes,current,dependencies,path,result,wrapped("—表示未计算，0是已填值。使用画布下方/右侧滚动条查看大表。","table-scroll-note"));
        content.getStyleClass().addAll("sidebar","graph-sidebar");setContent(content);setFitToWidth(true);setFitToHeight(true);
        setPrefWidth(260);setMinWidth(240);setHbarPolicy(ScrollBarPolicy.NEVER);getStyleClass().add("sidebar-scroll");showFrame(null);
    }
    public void showFrame(TableFrame frame){
        showFrame(frame,null);
    }
    public void showFrame(TableFrame frame,AlgorithmOutput output){
        TableRunMode mode=algorithms.getValue();boolean actual=mode!=null&&mode!=TableRunMode.PREVIEW;
        visible(examples,!actual);visible(knapsacks,mode==TableRunMode.KNAPSACK);visible(strings,mode==TableRunMode.LCS);visible(input,actual);visible(result,actual);
        preview.setText(actual?"重新生成轨迹":"重新预览表格");
        note.setText(mode==TableRunMode.KNAPSACK?"每件物品最多选一次；价值相等时不选当前物品。更新计数不含边界初始化。"
                :mode==TableRunMode.LCS?"区分大小写；回溯上/左相等时向上。更新计数不含边界初始化。":"仅预览表格样式，不执行背包/LCS；示意值不是算法结果。");
        if(mode==TableRunMode.KNAPSACK&&knapsacks.getValue()!=null){
            var value=knapsacks.getValue().input();var parts=new ArrayList<String>();
            for(int i=0;i<value.items().size();i++){var item=value.items().get(i);parts.add((i+1)+"：重"+item.weight()+"/值"+item.value());}
            input.setText("容量："+value.capacity()+"；物品（编号：重量/价值）："+String.join("；",parts));
        }else if(mode==TableRunMode.LCS&&strings.getValue()!=null)input.setText("X="+strings.getValue().input().x()+"；Y="+strings.getValue().input().y());
        else input.setText("");
        shape.setText(frame==null?"表格：—":"表格："+frame.rowLabels().size()+"行 × "+frame.columnLabels().size()+"列（含边界）");
        axes.setText(frame==null?"行 / 列：—":"行："+frame.rowTitle()+"；列："+frame.columnTitle());
        current.setText("当前格："+(frame==null||frame.currentCell()==null?"—":describe(frame.currentCell())+" · "+frame.rowLabels().get(frame.currentCell().row())+" / "
                +frame.columnLabels().get(frame.currentCell().column())+" = "+display(frame.values().get(frame.currentCell().row()).get(frame.currentCell().column()))));
        dependencies.setText((actual?"依赖格：":"依赖格（示意）：")+(frame==null?"[]":describe(frame.dependencies())));
        path.setText((actual?"回溯路径：":"回溯高亮（示意）：")+(frame==null?"[]":describe(frame.path(),actual)));
        result.setText("结果：完成回溯后显示。");
        if(frame!=null&&frame.eventKind()==EventKind.DONE){
            if(output instanceof KnapsackOutput value)result.setText("结果：最大价值"+value.maxValue()+"；选择物品"+value.chosenItemIds()+"；总重量"+value.totalWeight()+"。");
            else if(output instanceof LcsOutput value)result.setText("结果：长度"+value.length()+"；公共子序列"+(value.subsequence().isEmpty()?"为空串（∅）":"为“"+value.subsequence()+"”")+"。");
        }
    }
    private static void visible(javafx.scene.Node node,boolean visible){node.setVisible(visible);node.setManaged(visible);}
    private String display(int value){return value==TableFrame.UNCOMPUTED?"—":Integer.toString(value);}
    private String describe(Set<TableCell> cells){return describe(cells,false);}
    private String describe(Set<TableCell> cells,boolean reverse){
        var order=Comparator.comparingInt(TableCell::row).thenComparingInt(TableCell::column);
        // 两种真实DP回溯的行/列单调不增，降序还原从右下向边界的历史顺序。
        return cells.stream().sorted(reverse?order.reversed():order).map(this::describe).collect(Collectors.joining(", ","[","]"));
    }
    private String describe(TableCell cell){return "dp["+cell.row()+"]["+cell.column()+"]";}
    private static Label wrapped(String text,String id){var label=new Label(text);label.setId(id);label.setWrapText(true);label.setMaxWidth(Double.MAX_VALUE);label.setMinHeight(Region.USE_PREF_SIZE);label.getStyleClass().add("muted");return label;}
    public ComboBox<TableExample> exampleChoice(){return examples;} public Button previewButton(){return preview;}
    public ComboBox<TableRunMode> algorithmChoice(){return algorithms;}
    public ComboBox<DpExamples.KnapsackExample> knapsackChoice(){return knapsacks;}
    public ComboBox<DpExamples.LcsExample> lcsChoice(){return strings;}
    public void setBusy(boolean busy){algorithms.setDisable(busy);knapsacks.setDisable(busy);strings.setDisable(busy);examples.setDisable(busy);preview.setDisable(busy);}
}
