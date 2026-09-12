package vn.svframe.svarcade.persistence;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import vn.svframe.svarcade.config.Values;

/** Bounded single-writer store: encoding, fsync and atomic replacement happen on I/O worker. */
public final class AtomicStore implements AutoCloseable {
    private static final int MAX_ENCODED = 8 * 1024 * 1024 + 44;
    private final Path root;
    private final ThreadPoolExecutor io;
    public AtomicStore(Path root, int queueCapacity) throws IOException {
        if (queueCapacity < 1) throw new IllegalArgumentException("Queue capacity");
        if (Files.isSymbolicLink(root)) throw new IOException("Store root may not be a symlink");
        Files.createDirectories(root); this.root = root.toRealPath();
        io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, "svarcade-state-io"); thread.setDaemon(true); return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }
    public CompletableFuture<Void> write(String key, Map<String, Object> state) {
        Path destination = destination(key);
        Map<String, Object> frozen = Values.map(state);
        return submit(() -> {
            byte[] encoded = StateCodec.encode(frozen);
            Path temporary = Files.createTempFile(root, ".pending-", ".state");
            try {
                try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    ByteBuffer bytes = ByteBuffer.wrap(encoded);
                    while (bytes.hasRemaining()) file.write(bytes);
                    file.force(true);
                }
                // Do not silently degrade to truncate/copy when atomic move is unsupported.
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                try (FileChannel directory = FileChannel.open(root, StandardOpenOption.READ)) { directory.force(true); }
                return null;
            } finally { Files.deleteIfExists(temporary); }
        });
    }
    public CompletableFuture<Optional<Map<String, Object>>> read(String key) {
        Path path = destination(key);
        return submit(() -> {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid state file");
            byte[] bytes;
            try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { bytes = in.readNBytes(MAX_ENCODED + 1); }
            return Optional.of(StateCodec.decode(bytes));
        });
    }
    private Path destination(String key) {
        if (!key.matches("[a-zA-Z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid state key");
        return root.resolve(key + ".state");
    }
    @FunctionalInterface private interface Operation<T> { T run() throws IOException; }
    private <T> CompletableFuture<T> submit(Operation<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            io.execute(() -> { try { future.complete(operation.run()); } catch (Exception e) { future.completeExceptionally(e); } });
        } catch (RejectedExecutionException e) { future.completeExceptionally(e); }
        return future;
    }
    /** Starts draining; does not block the server thread. Await termination only off-thread. */
    @Override public void close() { io.shutdown(); }
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException { return io.awaitTermination(timeout, unit); }
    public int pendingWrites() { return io.getQueue().size(); }
}
