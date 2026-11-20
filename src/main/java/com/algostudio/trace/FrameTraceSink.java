package com.algostudio.trace;

import com.algostudio.model.ExecutionTrace;
import com.algostudio.model.frame.VisualFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 单次算法运行独占的有界采集器；溢出清理引用并使本采集器永久失效。 */
public final class FrameTraceSink implements TraceSink {
    public static final int MAX_FRAMES = ExecutionTrace.MAX_FRAMES;
    private final int limit;
    private final List<VisualFrame> frames = new ArrayList<>();
    private boolean failed;

    public FrameTraceSink() {
        this(MAX_FRAMES);
    }

    public FrameTraceSink(int limit) {
        if (limit < 1 || limit > MAX_FRAMES) {
            throw new IllegalArgumentException("帧数上限必须在 1～" + MAX_FRAMES + " 之间");
        }
        this.limit = limit;
    }

    @Override
    public boolean enabled() {
        // 在生产者构造下一帧之前检查容量，避免创建第 limit+1 个快照。
        checkCapacity();
        return true;
    }

    @Override
    public void append(VisualFrame frame) {
        checkCapacity();
        frames.add(Objects.requireNonNull(frame, "frame"));
    }

    public List<VisualFrame> frames() {
        return List.copyOf(frames);
    }

    private void checkCapacity() {
        if (failed) {
            throw new IllegalStateException("已失败的轨迹采集器不能重复使用");
        }
        if (frames.size() >= limit) {
            frames.clear();
            failed = true;
            throw new TraceLimitExceededException(limit);
        }
    }
}
