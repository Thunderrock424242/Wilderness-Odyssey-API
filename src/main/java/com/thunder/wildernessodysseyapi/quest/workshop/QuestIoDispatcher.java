package com.thunder.wildernessodysseyapi.quest.workshop;

import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Ordered bounded I/O lane. Shared rejection retains work; the game thread pumps without doing I/O. */
public final class QuestIoDispatcher implements AutoCloseable {
    public interface Backend {
        boolean enabled();
        boolean submit(Runnable work);
    }

    private final Backend backend;
    private final Thread ownerThread = Thread.currentThread();
    private final ArrayDeque<Work<?>> pending = new ArrayDeque<>();
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private ThreadPoolExecutor fallback;
    private Work<?> cleanup;
    private boolean cleanupAccepted;
    private boolean scheduled;
    private boolean closed;
    private volatile String lastWorker = "pending";

    public QuestIoDispatcher() {
        this(new Backend() {
            @Override public boolean enabled() { return AsyncTaskManager.getConfigValues().enabled(); }
            @Override public boolean submit(Runnable work) { return AsyncTaskManager.trySubmitIoWork("quest-store", work); }
        });
    }

    /** Injectable worker boundary; the backend must never run submitted work inline. */
    public QuestIoDispatcher(Backend backend) {
        this.backend = Objects.requireNonNull(backend);
    }

    public synchronized boolean trySubmit(Runnable immutableIoWork) {
        return enqueue(new Work<>(() -> { immutableIoWork.run(); return null; }, new CompletableFuture<>()));
    }

    public synchronized <T> CompletionStage<T> submit(Callable<T> immutableIoWork) {
        var result = new CompletableFuture<T>();
        if (!enqueue(new Work<>(immutableIoWork, result))) {
            result.completeExceptionally(new RejectedExecutionException("Quest I/O queue is full or closed."));
        }
        return result;
    }

    private boolean enqueue(Work<?> work) {
        if (closed || cleanupAccepted || pending.size() >= 128) return false;
        pending.addLast(work);
        pump();
        return true;
    }

    /** One terminal handle-release slot outside the bounded content queue; no new content can follow it. */
    synchronized <T> CompletionStage<T> submitCleanup(Callable<T> action) {
        var result = new CompletableFuture<T>();
        if (closed || cleanupAccepted) {
            result.completeExceptionally(new RejectedExecutionException("Quest cleanup is already scheduled or stopped."));
            return result;
        }
        cleanupAccepted = true;
        cleanup = new Work<>(action, result);
        pump();
        return result;
    }

    /** Called from the mandatory server tick path, independently of optional DataEngine time allowance. */
    public synchronized void pump() {
        if (closed || scheduled || pending.isEmpty() && cleanup == null) return;
        scheduled = true;
        if (backend.enabled()) {
            if (!backend.submit(this::drain)) scheduled = false;
        } else {
            if (fallback == null) {
                fallback = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), work -> {
                    var thread = new Thread(work, "WO-Quest-IO-" + java.util.UUID.randomUUID());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
            }
            try {
                fallback.execute(this::drain);
            } catch (RejectedExecutionException exception) {
                scheduled = false;
            }
        }
    }

    private void drain() {
        try {
            if (Thread.currentThread() == ownerThread) throw new IllegalStateException("Quest I/O backend ran on the owner thread.");
            lastWorker = Thread.currentThread().getName();
            while (true) {
                Work<?> work;
                synchronized (this) {
                    if (closed) return;
                    work = pending.pollFirst();
                    if (work == null) {
                        work = cleanup;
                        cleanup = null;
                    }
                    if (work == null) return;
                }
                work.run();
            }
        } finally {
            synchronized (this) {
                scheduled = false;
                if (closed) termination.complete(null);
                else pump();
            }
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        var failure = new RejectedExecutionException("Quest I/O stopped before this work began.");
        Work<?> work;
        while ((work = pending.pollFirst()) != null) work.result().completeExceptionally(failure);
        if (cleanup != null) {
            cleanup.result().completeExceptionally(failure);
            cleanup = null;
        }
        if (fallback != null) fallback.shutdown();
        if (!scheduled) termination.complete(null);
    }

    public CompletionStage<Void> termination() {
        return termination;
    }
    /** Operator diagnostics show the actual lane used, including explicitly disabled shared-worker fallback. */
    public String lastWorker() { return lastWorker; }

    private record Work<T>(Callable<T> action, CompletableFuture<T> result) {
        void run() {
            try {
                result.complete(action.call());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        }
    }
}
