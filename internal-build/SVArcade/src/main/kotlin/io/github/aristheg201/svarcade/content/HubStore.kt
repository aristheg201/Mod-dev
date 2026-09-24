package io.github.aristheg201.svarcade.content

import io.github.aristheg201.svarcade.util.AtomicFiles
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class CommitResult(
    val ok: Boolean,
    val revision: Long,
    val message: String,
    val validation: ValidationResult? = null
)

data class HistoryEntry(
    val revision: Long,
    val savedAtEpochMs: Long,
    val fileName: String
)

/**
 * Server-authoritative content store.
 * All filesystem work is serialized through a dedicated IO executor.
 */
class HubStore(private val root: Path) : AutoCloseable {
    private val current = AtomicReference(HubSnapshot(FieldGuideContent.create()))
    private val historyIndex = AtomicReference<List<HistoryEntry>>(emptyList())
    private val ready = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val io = Executors.newSingleThreadExecutor { task ->
        Thread(task, "SVArcade-Persistence").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    private val contentFile = root.resolve("content.json")
    private val historyDir = root.resolve("history")

    fun snapshot(): HubSnapshot = current.get()
    fun isReady(): Boolean = ready.get()

    fun initializeAsync(): CompletableFuture<HubSnapshot> {
        ready.set(false)
        return supplyIo {
            Files.createDirectories(root)
            Files.createDirectories(historyDir)
            if (!Files.exists(contentFile)) {
                AtomicFiles.writeUtf8(contentFile, HubContentCodec.encode(current.get().content))
            }
            val content = readCurrentContentWithBundledMigration()
            val history = scanHistory()
            historyIndex.set(history)
            HubSnapshot(content).also(current::set)
        }.whenComplete { _, error -> ready.set(error == null) }
    }

    fun reloadAsync(): CompletableFuture<CommitResult> = supplyIo {
        runCatching {
            val content = readCurrentContentWithBundledMigration()
            val history = scanHistory()
            current.set(HubSnapshot(content))
            historyIndex.set(history)
            ready.set(true)
            CommitResult(true, content.revision, "Reloaded SVArcade revision ${content.revision}")
        }.getOrElse { error ->
            CommitResult(false, snapshot().revision, error.message ?: "Reload failed")
        }
    }

    fun commitAsync(baseRevision: Long, candidate: HubContent): CompletableFuture<CommitResult> = supplyIo {
        val now = current.get().content
        if (baseRevision != now.revision) {
            return@supplyIo CommitResult(false, now.revision, "Revision conflict: expected ${now.revision}, got $baseRevision")
        }

        val validation = HubValidator.validate(candidate)
        if (!validation.ok) {
            return@supplyIo CommitResult(false, now.revision, "Validation failed", validation)
        }

        runCatching {
            val next = candidate.copy(revision = now.revision + 1)
            persistTransition(now, next)
            CommitResult(true, next.revision, "Published revision ${next.revision}", validation)
        }.getOrElse { error ->
            CommitResult(false, now.revision, error.message ?: "Publish failed", validation)
        }
    }

    /** In-memory only; safe for command suggestions on the server thread. */
    fun history(limit: Int = 20): List<HistoryEntry> =
        historyIndex.get().take(limit.coerceIn(0, MAX_HISTORY_ENTRIES))

    fun rollbackAsync(revision: Long): CompletableFuture<CommitResult> = supplyIo {
        val entry = historyIndex.get().firstOrNull { it.revision == revision }
            ?: return@supplyIo CommitResult(false, snapshot().revision, "Revision $revision not found")
        val now = current.get().content

        runCatching {
            val archived = readValidatedContent(historyDir.resolve(entry.fileName))
            val next = archived.copy(revision = now.revision + 1)
            val validation = HubValidator.validate(next)
            if (!validation.ok) {
                return@runCatching CommitResult(false, now.revision, "Rollback validation failed", validation)
            }
            persistTransition(now, next)
            CommitResult(true, next.revision, "Rolled back content from revision $revision as revision ${next.revision}", validation)
        }.getOrElse { error ->
            CommitResult(false, now.revision, error.message ?: "Rollback failed")
        }
    }

    private fun persistTransition(previous: HubContent, next: HubContent) {
        val historyEntry = saveHistory(previous)
        AtomicFiles.writeUtf8(contentFile, HubContentCodec.encode(next))
        current.set(HubSnapshot(next))
        historyIndex.updateAndGet { existing ->
            (listOf(historyEntry) + existing)
                .distinctBy { it.fileName }
                .sortedWith(compareByDescending<HistoryEntry> { it.revision }.thenByDescending { it.savedAtEpochMs })
                .take(MAX_HISTORY_ENTRIES)
        }
        pruneHistoryFiles()
    }

    /**
     * Upgrades only exact SVArcade-owned bundled revisions. The old showcase can move
     * to the player handbook, the first handbook can receive its missing command
     * cards, and the exact bundled dark handbook can move to the light field-guide
     * presentation. One original snapshot is archived before the automatic write.
     */
    private fun readCurrentContentWithBundledMigration(): HubContent {
        val loaded = readValidatedContent(contentFile)
        val migrated = BundledContentMigration.migrate(loaded)
        val afterMigration = migrated ?: loaded
        val handbook = BundledHandbookPatch.apply(afterMigration) ?: afterMigration
        val visual = BundledVisualRefreshPatch.apply(handbook) ?: handbook
        val finalContent = visual

        if (finalContent == loaded) return loaded

        HubValidator.validate(finalContent).requireValid()
        saveHistory(loaded)
        AtomicFiles.writeUtf8(contentFile, HubContentCodec.encode(finalContent))
        return finalContent
    }

    private fun readValidatedContent(path: Path): HubContent {
        require(Files.exists(path)) { "Missing SVArcade content file: ${path.fileName}" }
        val content = HubContentCodec.decode(Files.readString(path))
        HubValidator.validate(content).requireValid()
        return content
    }

    private fun saveHistory(content: HubContent): HistoryEntry {
        Files.createDirectories(historyDir)
        val savedAt = System.currentTimeMillis()
        val fileName = "rev-${content.revision}-$savedAt.json"
        AtomicFiles.writeUtf8(historyDir.resolve(fileName), HubContentCodec.encode(content))
        return HistoryEntry(content.revision, savedAt, fileName)
    }

    private fun scanHistory(): List<HistoryEntry> {
        if (!Files.exists(historyDir)) return emptyList()
        return Files.list(historyDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .map { path -> parseHistory(path.fileName.toString()) }
                .filter { it != null }
                .map { it!! }
                .sorted(compareByDescending<HistoryEntry> { it.revision }.thenByDescending { it.savedAtEpochMs })
                .limit(MAX_HISTORY_ENTRIES.toLong())
                .toList()
        }
    }

    private fun parseHistory(fileName: String): HistoryEntry? {
        val match = HISTORY_FILE.matchEntire(fileName) ?: return null
        val revision = match.groupValues[1].toLongOrNull() ?: return null
        val savedAt = match.groupValues[2].toLongOrNull() ?: return null
        return HistoryEntry(revision, savedAt, fileName)
    }

    private fun pruneHistoryFiles() {
        if (!Files.exists(historyDir)) return
        val keep = historyIndex.get().mapTo(HashSet()) { it.fileName }
        Files.list(historyDir).use { stream ->
            stream.filter {
                Files.isRegularFile(it) && HISTORY_FILE.matches(it.fileName.toString()) && it.fileName.toString() !in keep
            }.forEach { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun <T> supplyIo(block: () -> T): CompletableFuture<T> {
        if (closed.get()) return CompletableFuture.failedFuture(IllegalStateException("SVArcade store is closed"))
        return CompletableFuture.supplyAsync(block, io)
    }

    override fun close() {
        ready.set(false)
        if (!closed.compareAndSet(false, true)) return

        io.shutdown()
        try {
            if (!io.awaitTermination(SHUTDOWN_DRAIN_SECONDS, TimeUnit.SECONDS)) {
                io.shutdownNow()
                io.awaitTermination(SHUTDOWN_FORCE_SECONDS, TimeUnit.SECONDS)
            }
        } catch (_: InterruptedException) {
            io.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private const val MAX_HISTORY_ENTRIES = 256
        private const val SHUTDOWN_DRAIN_SECONDS = 5L
        private const val SHUTDOWN_FORCE_SECONDS = 1L
        private val HISTORY_FILE = Regex("^rev-(\\d+)-(\\d+)\\.json$")
    }
}
