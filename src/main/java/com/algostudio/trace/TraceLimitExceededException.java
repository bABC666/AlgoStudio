package com.algostudio.trace;

public final class TraceLimitExceededException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public TraceLimitExceededException(int limit) {
        super("轨迹超过 " + limit + " 帧上限，请减少输入规模；部分轨迹已丢弃。");
    }
}
