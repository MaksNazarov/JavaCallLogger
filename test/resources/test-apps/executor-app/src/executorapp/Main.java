package executorapp;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cross-thread edge check for threadpool: task is attributed to task submitter,
 * not to worker creator
 */
public class Main {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        warmUp(pool);
        submitReal(pool);
        pool.shutdown();
    }

    private static void warmUp(ExecutorService pool) throws Exception {
        pool.submit(Main::noop).get();
    }

    private static void submitReal(ExecutorService pool) throws Exception {
        pool.submit(Main::task).get();
    }

    private static void noop() {
    }

    private static void task() {
        work();
    }

    private static void work() {
    }
}
