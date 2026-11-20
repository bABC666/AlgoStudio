package com.algostudio.playback;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;

import java.util.Objects;

/** 生命周期内只创建一个 Timeline；stop 清除闭包，变速不重建时间轴。 */
public final class FxPlaybackClock implements PlaybackClock {
    public static final double BASE_INTERVAL_MILLIS = 600;
    private final Timeline timeline;
    private Runnable callback;
    private boolean disposed;

    public FxPlaybackClock() {
        requireFxThread();
        timeline = new Timeline(new KeyFrame(Duration.millis(BASE_INTERVAL_MILLIS), event -> {
            Runnable current = callback;
            if (current != null) current.run();
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
    }

    @Override
    public void start(Runnable tick) {
        requireFxThread();
        if (disposed) throw new IllegalStateException("播放时钟已释放");
        if (timeline.getStatus() == Animation.Status.RUNNING) return;
        callback = Objects.requireNonNull(tick, "tick");
        timeline.playFromStart();
    }

    @Override
    public void stop() {
        requireFxThread();
        timeline.stop();
        callback = null;
    }

    @Override
    public void setSpeed(double multiplier) {
        requireFxThread();
        if (multiplier != 1 && multiplier != 2 && multiplier != 4 && multiplier != 8) {
            throw new IllegalArgumentException("播放速度仅支持 1×、2×、4×、8×");
        }
        timeline.setRate(multiplier);
    }

    @Override
    public void dispose() {
        requireFxThread();
        if (disposed) return;
        stop();
        timeline.getKeyFrames().clear();
        disposed = true;
    }

    private void requireFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("播放时钟只能在 JavaFX Application Thread 操作");
        }
    }
}
