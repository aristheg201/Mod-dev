package io.github.aristheg201.svhub.content

import io.github.aristheg201.svhub.util.AtomicFiles
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class CommitResult(val ok: Boolean, val revision: Long, val message: String, val validation: ValidationResult? = null)
data class HistoryEntry(val revision: Long, val savedAtEpochMs: Long, val fileName: String)

class HubStore(private val root: Path) {
    private val lock = ReentrantLock()
    private val current = AtomicReference(HubSnapshot(DefaultContent.create()))
    private val contentFile = root.resolve("content.json")
    private val historyDir = root.resolve("history")

    fun snapshot(): HubSnapshot = current.get()

    fun load(): HubSnapshot = lock.withLock {
        Files.createDirectories(root); Files.createDirectories(historyDir)
        if (!Files.exists(contentFile)) AtomicFiles.writeUtf8(contentFile, HubContentCodec.encode(current.get().content))
        val content = HubContentCodec.decode(Files.readString(contentFile)); HubValidator.validate(content).requireValid()
        HubSnapshot(content).also(current::set)
    }

    fun reload(): CommitResult = runCatching { load(); CommitResult(true, snapshot().revision, "Reloaded SVHub") }
        .getOrElse { CommitResult(false, snapshot().revision, it.message ?: "Reload failed") }

    fun commit(baseRevision: Long, candidate: HubContent): CommitResult = lock.withLock {
        val now = current.get().content
        if (baseRevision != now.revision) return CommitResult(false, now.revision, "Revision conflict: expected ${now.revision}, got $baseRevision")
        val validation = HubValidator.validate(candidate); if (!validation.ok) return CommitResult(false, now.revision, "Validation failed", validation)
        saveHistory(now)
        val next = candidate.copy(revision = now.revision + 1)
        AtomicFiles.writeUtf8(contentFile, HubContentCodec.encode(next)); current.set(HubSnapshot(next))
        CommitResult(true, next.revision, "Published revision ${next.revision}", validation)
    }

    fun history(limit: Int = 20): List<HistoryEntry> = if (!Files.exists(historyDir)) emptyList() else Files.list(historyDir).use { s ->
        s.filter { it.fileName.toString().endsWith(".json") }.map { p ->
            val name = p.fileName.toString(); val rev = name.substringBefore('-').removePrefix("rev-").toLongOrNull() ?: -1L
            HistoryEntry(rev, Files.getLastModifiedTime(p).toMillis(), name)
        }.filter { it.revision >= 0 }.sorted(compareByDescending<HistoryEntry> { it.revision }).limit(limit.toLong()).toList()
    }

    fun rollback(revision: Long): CommitResult = lock.withLock {
        val file = history(revision.toInt().coerceAtLeast(20)).firstOrNull { it.revision == revision }?.let { historyDir.resolve(it.fileName) }
            ?: return CommitResult(false, snapshot().revision, "Revision $revision not found")
        val candidate = HubContentCodec.decode(Files.readString(file)).copy(revision = snapshot().revision)
        commit(snapshot().revision, candidate)
    }

    private fun saveHistory(content: HubContent) {
        Files.createDirectories(historyDir)
        val path = historyDir.resolve("rev-${content.revision}-${System.currentTimeMillis()}.json")
        AtomicFiles.writeUtf8(path, HubContentCodec.encode(content))
    }
}
