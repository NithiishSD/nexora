package com.nexora.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// Runs several tasks at the same moment to provoke races: every thread waits on a "ready" latch,
// then one "go" signal releases them together. Used by the concurrency tests that prove a lock works.
public final class ConcurrentRunner {

    private ConcurrentRunner() {
    }

    // The outcome of one task: its result, or the exception it threw.
    public record Outcome<T>(T value, Throwable error) {
        public boolean succeeded() {
            return error == null;
        }
    }

    public static <T> List<Outcome<T>> runTogether(List<Callable<T>> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();          // "I am at the start line"
                    go.await();                 // wait for the start signal
                    return task.call();
                }));
            }
            ready.await(30, TimeUnit.SECONDS);
            go.countDown();                     // start everyone at once
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    outcomes.add(new Outcome<>(future.get(30, TimeUnit.SECONDS), null));
                } catch (java.util.concurrent.ExecutionException e) {
                    outcomes.add(new Outcome<>(null, e.getCause()));
                } catch (java.util.concurrent.TimeoutException e) {
                    outcomes.add(new Outcome<>(null, e));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
