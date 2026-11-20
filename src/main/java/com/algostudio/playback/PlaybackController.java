package com.algostudio.playback;

import com.algostudio.model.ExecutionTrace;

import java.util.Objects;
import java.util.function.Consumer;

/** 不依赖 GUI 或算法类型的串行播放器。回退只移动不可变历史的索引。 */
public final class PlaybackController {
    private final PlaybackClock clock;
    private final Consumer<PlaybackSnapshot> observer;
    private ExecutionTrace trace;
    private PlaybackState state = PlaybackState.EMPTY;
    private int index = -1;
    private double speed = 1;
    private long generation;
    private String error = "";
    private boolean disposed;

    public PlaybackController(PlaybackClock clock, Consumer<PlaybackSnapshot> observer) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observer = Objects.requireNonNull(observer, "observer");
        publish();
    }

    public void load(ExecutionTrace next) {
        if (disposed) throw new IllegalStateException("播放器已释放");
        stopClock();
        trace = next;
        index = next == null ? -1 : 0;
        state = next == null ? PlaybackState.ERROR : PlaybackState.READY;
        error = next == null ? "无可播放的完整轨迹" : "";
        publish();
    }

    public void play() {
        if (!available() || state == PlaybackState.PLAYING) return;
        if (index == trace.frames().size() - 1) index = 0;
        state = PlaybackState.PLAYING;
        long currentGeneration = ++generation;
        clock.start(() -> tick(currentGeneration));
        publish();
    }

    public void pause() {
        if (!available() || state != PlaybackState.PLAYING) return;
        stopClock();
        state = PlaybackState.PAUSED;
        publish();
    }

    public void stepForward() {
        if (!available()) return;
        stopClock();
        index = Math.min(index + 1, trace.frames().size() - 1);
        state = restingState();
        publish();
    }

    public void stepBackward() {
        if (!available()) return;
        stopClock();
        index = Math.max(index - 1, 0);
        state = restingState();
        publish();
    }

    public void reset() {
        if (!available()) return;
        stopClock();
        index = 0;
        state = PlaybackState.READY;
        publish();
    }

    public void clear() {
        release(PlaybackState.EMPTY, "");
    }

    public void fail(String message) {
        release(PlaybackState.ERROR, Objects.requireNonNullElse(message, "轨迹生成失败"));
    }

    public void setSpeed(double multiplier) {
        if (disposed) return;
        if (multiplier != 1 && multiplier != 2 && multiplier != 4 && multiplier != 8) {
            throw new IllegalArgumentException("播放速度仅支持 1×、2×、4×、8×");
        }
        clock.setSpeed(multiplier);
        speed = multiplier;
        publish();
    }

    public void dispose() {
        if (disposed) return;
        release(PlaybackState.EMPTY, "");
        disposed = true;
        clock.dispose();
    }

    public PlaybackSnapshot snapshot() {
        return new PlaybackSnapshot(state, index, trace == null ? 0 : trace.frames().size(),
                speed, trace == null ? null : trace.frames().get(index), error);
    }

    private void tick(long callbackGeneration) {
        if (!available() || state != PlaybackState.PLAYING || callbackGeneration != generation) return;
        index++;
        if (index == trace.frames().size() - 1) {
            stopClock();
            state = PlaybackState.FINISHED;
        }
        publish();
    }

    private PlaybackState restingState() {
        if (index == 0) return PlaybackState.READY;
        return index == trace.frames().size() - 1 ? PlaybackState.FINISHED : PlaybackState.PAUSED;
    }

    private boolean available() {
        return !disposed && trace != null;
    }

    private void release(PlaybackState nextState, String message) {
        if (disposed) return;
        stopClock();
        trace = null;
        index = -1;
        state = nextState;
        error = message;
        publish();
    }

    private void stopClock() {
        generation++;
        clock.stop();
    }

    private void publish() {
        observer.accept(snapshot());
    }
}
