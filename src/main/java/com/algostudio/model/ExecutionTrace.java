package com.algostudio.model;

import com.algostudio.algorithm.AlgorithmRun;
import com.algostudio.model.frame.EventKind;
import com.algostudio.model.frame.VisualFrame;

import java.util.List;
import java.util.Objects;

public record ExecutionTrace(String algorithmId, String inputSummary,
                             List<VisualFrame> frames, AlgorithmRun run) {
    public static final int MAX_FRAMES = 10_000;

    public ExecutionTrace {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(inputSummary, "inputSummary");
        Objects.requireNonNull(run, "run");
        frames = List.copyOf(frames);
        if (frames.size() < 2 || frames.size() > MAX_FRAMES
                || frames.getFirst().eventKind() != EventKind.INITIAL
                || frames.getLast().eventKind() != EventKind.DONE) {
            throw new IllegalArgumentException("轨迹必须包含 INITIAL、DONE，且不超过 " + MAX_FRAMES + " 帧");
        }
        if (!frames.getLast().metrics().equals(run.metrics())) {
            throw new IllegalArgumentException("终帧计数与算法运行结果不一致");
        }
    }
}
