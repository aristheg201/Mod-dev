package vn.svframe.svrelationships.fabric.persistence;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class AsyncRepositoryClose {
    private AsyncRepositoryClose() {}

    public static void shutdownAndAwait(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
