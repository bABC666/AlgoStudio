package com.algostudio.service;

import com.algostudio.model.benchmark.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** 命令及dispatcher回调须在同一个UI线程串行执行；计算只在有界工作线程执行。 */
public final class BenchmarkCoordinator implements AutoCloseable {
    private final BenchmarkRunner runner;
    private final ExecutorService worker;
    private final Consumer<Runnable> dispatcher;
    private final Consumer<BenchmarkSnapshot> observer;
    private BenchmarkSnapshot snapshot = new BenchmarkSnapshot(BenchmarkSnapshot.State.IDLE, null, "");
    private FutureTask<Void> task;
    private long generation;
    private boolean closed;

    public BenchmarkCoordinator(BenchmarkRunner runner, ExecutorService worker, Consumer<Runnable> dispatcher,
                                Consumer<BenchmarkSnapshot> observer) {
        this.runner = Objects.requireNonNull(runner);
        this.worker = Objects.requireNonNull(worker);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.observer = Objects.requireNonNull(observer);
        observer.accept(snapshot);
    }

    public static ThreadPoolExecutor newWorker() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), runnable -> {
            Thread thread = new Thread(runnable, "algostudio-benchmark");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    public BenchmarkSnapshot snapshot() { return snapshot; }

    public boolean start(BenchmarkRequest request) {
        Objects.requireNonNull(request);
        if (closed || snapshot.running()) return false;
        long id = ++generation;
        publish(BenchmarkSnapshot.State.RUNNING, null, "");
        task = new FutureTask<>(() -> {
            try {
                BenchmarkReport report = runner.run(request);
                dispatcher.accept(() -> finish(id, BenchmarkSnapshot.State.SUCCEEDED, report, ""));
            } catch (CancellationException failure) {
                dispatcher.accept(() -> finish(id, BenchmarkSnapshot.State.CANCELLED, null, ""));
            } catch (RuntimeException failure) {
                String message = failure.getMessage();
                dispatcher.accept(() -> finish(id, BenchmarkSnapshot.State.FAILED, null,
                        message == null || message.isBlank() ? "性能对比失败，请重试。" : message));
            }
            return null;
        });
        try { worker.execute(task); }
        catch (RejectedExecutionException failure) {
            finish(id, BenchmarkSnapshot.State.FAILED, null, "后台任务暂不可用，请重试。");
        }
        return true;
    }

    public void cancel() {
        if (closed || !snapshot.running()) return;
        cancelTask();
        publish(BenchmarkSnapshot.State.CANCELLED, null, "");
    }

    /** 参数改变后连同已完成报告一起清空，避免将旧规模结果标为新规模。 */
    public void invalidate() {
        if (closed) return;
        cancelTask();
        publish(BenchmarkSnapshot.State.IDLE, null, "");
    }

    private void cancelTask() {
        generation++;
        if (task != null) {
            task.cancel(true);
            if (worker instanceof ThreadPoolExecutor pool) {
                pool.remove(task);
                pool.purge();
            }
            task = null;
        }
    }

    private void finish(long id, BenchmarkSnapshot.State state, BenchmarkReport report, String error) {
        if (closed || id != generation) return;
        task = null;
        publish(state, report, error);
    }

    private void publish(BenchmarkSnapshot.State state, BenchmarkReport report, String error) {
        snapshot = new BenchmarkSnapshot(state, report, error);
        observer.accept(snapshot);
    }

    @Override public void close() {
        if (closed) return;
        cancelTask();
        closed = true;
        worker.shutdownNow();
    }
}
