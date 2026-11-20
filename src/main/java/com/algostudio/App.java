package com.algostudio;

import com.algostudio.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public final class App extends Application {
    private MainView view;
    @Override
    public void start(Stage stage) {
        view = new MainView();
        Scene scene = new Scene(view, 1180, 760);
        scene.getStylesheets().add(Objects.requireNonNull(
                App.class.getResource("/style.css"), "缺少 style.css").toExternalForm());
        stage.setTitle("AlgoStudio · 算法可视化与性能分析");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.setOnHidden(event -> view.close());
        stage.show();
    }

    @Override
    public void stop() {
        if (view != null) view.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
