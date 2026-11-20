package com.algostudio.model.benchmark;

public record BenchmarkSnapshot(State state, BenchmarkReport report, String error) {
    public enum State { IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED }
    public boolean running() { return state == State.RUNNING; }
}
