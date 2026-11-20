package com.algostudio.service;

import com.algostudio.model.benchmark.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkCoordinatorTest {
    private final BenchmarkRequest request = new BenchmarkRequest(100, 42);
    private final BenchmarkReport report = new BenchmarkService().run(request);

    @Test void duplicateStartIsIgnoredAndSuccessRestoresState() {
        ManualExecutor executor = new ManualExecutor();
        ArrayDeque<Runnable> ui = new ArrayDeque<>();
        try (var coordinator = new BenchmarkCoordinator(r -> report, executor, ui::add, s -> { })) {
            assertTrue(coordinator.start(request));
            assertFalse(coordinator.start(request));
            assertTrue(coordinator.snapshot().running());
            assertEquals(1, executor.tasks.size());
            executor.runNext();
            assertTrue(coordinator.snapshot().running());
            ui.remove().run();
            assertEquals(BenchmarkSnapshot.State.SUCCEEDED, coordinator.snapshot().state());
            assertSame(report, coordinator.snapshot().report());
            assertTrue(coordinator.start(request));
            assertNull(coordinator.snapshot().report());
        }
        assertTrue(executor.isShutdown());
    }

    @Test void oldSuccessAndFailureCannotReplaceNewRequestOrParameterInvalidation() {
        for (boolean fail : List.of(false, true)) {
            ManualExecutor executor = new ManualExecutor();
            ArrayDeque<Runnable> ui = new ArrayDeque<>();
            try (var c = new BenchmarkCoordinator(r -> {
                if (fail) throw new IllegalStateException("old failure");
                return report;
            }, executor, ui::add, s -> { })) {
                c.start(request);
                executor.runNext();
                Runnable old = ui.remove();
                c.invalidate();
                assertEquals(BenchmarkSnapshot.State.IDLE, c.snapshot().state());
                c.start(new BenchmarkRequest(500, 43));
                old.run();
                assertTrue(c.snapshot().running());
                assertNull(c.snapshot().report());
                c.cancel();
                assertEquals(BenchmarkSnapshot.State.CANCELLED, c.snapshot().state());
                assertTrue(c.start(request));
            }
        }
    }

    @Test void failureAndRejectedExecutionRestoreControlsAndAllowRetry() {
        ManualExecutor executor = new ManualExecutor();
        ArrayDeque<Runnable> ui = new ArrayDeque<>();
        try (var c = new BenchmarkCoordinator(r -> { throw new IllegalStateException("校验失败"); }, executor, ui::add, s -> { })) {
            c.start(request);
            executor.runNext(); ui.remove().run();
            assertEquals(BenchmarkSnapshot.State.FAILED, c.snapshot().state());
            assertTrue(c.snapshot().error().contains("校验失败"));
            assertNull(c.snapshot().report());
            executor.reject = true;
            c.start(request);
            assertEquals(BenchmarkSnapshot.State.FAILED, c.snapshot().state());
            assertFalse(c.snapshot().running());
            executor.reject = false;
            assertTrue(c.start(request));
        }
    }

    @Test void closeInvalidatesQueuedCallbackAndIsIdempotent() {
        ManualExecutor executor = new ManualExecutor();
        ArrayDeque<Runnable> ui = new ArrayDeque<>();
        var c = new BenchmarkCoordinator(r -> report, executor, ui::add, s -> { });
        c.start(request); executor.runNext();
        c.close(); c.close();
        var closedSnapshot = c.snapshot();
        ui.remove().run();
        assertSame(closedSnapshot, c.snapshot());
        assertFalse(c.start(request));
        assertTrue(executor.isShutdown());
    }

    @Test void cancellationInterruptsRealWorkerAndCloseTerminatesExecutor() throws Exception {
        var executor = BenchmarkCoordinator.newWorker();
        CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        AtomicBoolean workerWasUiThread = new AtomicBoolean(true);
        Thread uiThread = Thread.currentThread();
        var c = new BenchmarkCoordinator(r -> {
            workerWasUiThread.set(Thread.currentThread() == uiThread);
            started.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException expected) { interrupted.countDown(); throw new CancellationException(); }
            return report;
        }, executor, ignored -> { }, s -> { });
        try {
            c.start(request);
            assertTrue(started.await(3, TimeUnit.SECONDS));
            c.cancel();
            assertTrue(interrupted.await(3, TimeUnit.SECONDS));
            assertFalse(workerWasUiThread.get());
            assertFalse(c.snapshot().running());
            assertTrue(executor.getQueue().size() <= 1);
        } finally { c.close(); }
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
    }

    private static class ManualExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        boolean shutdown, reject;
        void runNext() { tasks.remove().run(); }
        public void execute(Runnable task) {
            if (shutdown || reject) throw new RejectedExecutionException("worker busy");
            tasks.add(task);
        }
        public void shutdown() { shutdown = true; }
        public List<Runnable> shutdownNow() { shutdown = true; var pending = List.copyOf(tasks); tasks.clear(); return pending; }
        public boolean isShutdown() { return shutdown; }
        public boolean isTerminated() { return shutdown; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
    }
}
