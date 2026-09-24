package io.github.aristheg201.svarcade.content

/** Permission decisions used by the pure scoped-editor merge engine. */
data class ScopedEditPermissions(
    val editSettings: Boolean,
    val editAssets: Boolean,
    val createPages: Boolean,
    val editPage: (String) -> Boolean
)

data class ScopedEditMergeResult(
    val content: HubContent? = null,
    val error: String? = null
) {
    val ok: Boolean get() = content != null && error == null
}

/**
 * Merges an editor's projected document back into the full authoritative
 * document without treating content omitted from the projection as deletion.
 *
 * `baseline` must be the exact projected snapshot originally sent to this
 * editor session. The merge computes edits against that baseline, authorizes
 * them, then applies only those deltas to `current`.
 */
object ScopedEditMerge {
    fun merge(
        current: HubContent,
        baseline: HubContent,
        candidate: HubContent,
        permissions: ScopedEditPermissions
    ): ScopedEditMergeResult {
        if (baseline.schema != candidate.schema || current.schema != candidate.schema) {
            return fail("Schema changed during scoped edit session.")
        }

        var merged = current

        val settingsChanged = baseline.defaultLocale != candidate.defaultLocale ||
            baseline.defaultTheme != candidate.defaultTheme ||
            baseline.cobblemonWiki != candidate.cobblemonWiki
        if (settingsChanged) {
            if (!permissions.editSettings) return fail("Bạn không có quyền sửa Hub settings/Cobblemon Wiki config.")
            merged = merged.copy(
                defaultLocale = candidate.defaultLocale,
                defaultTheme = candidate.defaultTheme,
                cobblemonWiki = candidate.cobblemonWiki
            )
        }

        val themeDelta = changedKeys(baseline.themes, candidate.themes)
        val assetDelta = changedKeys(baseline.assets, candidate.assets)
        if (themeDelta.isNotEmpty() || assetDelta.isNotEmpty()) {
            if (!permissions.editAssets) return fail("Bạn không có quyền sửa theme/assets.")
            val mergedThemes = applyMapDelta(merged.themes, baseline.themes, candidate.themes, themeDelta, "theme")
                ?: return fail("Theme mới trùng ID với theme ẩn trên server.")
            val mergedAssets = applyMapDelta(merged.assets, baseline.assets, candidate.assets, assetDelta, "asset")
                ?: return fail("Asset mới trùng ID với asset ẩn trên server.")
            merged = merged.copy(themes = mergedThemes, assets = mergedAssets)
        }

        val baselinePages = baseline.pages.associateBy { it.id }
        val candidatePages = candidate.pages.associateBy { it.id }
        val pageDelta = (baselinePages.keys + candidatePages.keys)
            .filterTo(linkedSetOf()) { baselinePages[it] != candidatePages[it] }

        for (id in pageDelta) {
            val existedInBaseline = id in baselinePages
            if (!existedInBaseline) {
                if (current.pages.any { it.id == id }) {
                    return fail("Page mới '$id' trùng với page ẩn trên server.")
                }
                if (!permissions.createPages) return fail("Bạn không có quyền tạo page '$id'.")
            }
            if (!permissions.editPage(id)) return fail("Bạn không có quyền sửa page '$id'.")
        }

        val structureChanged = baseline.pages.map { it.id } != candidate.pages.map { it.id }
        if (structureChanged && !permissions.editSettings) {
            return fail("Bạn không có quyền đổi thứ tự/cấu trúc page.")
        }

        if (pageDelta.isNotEmpty() || structureChanged) {
            merged = merged.copy(
                pages = if (structureChanged) {
                    mergePageSequence(current.pages, baseline.pages.map { it.id }.toSet(), candidate.pages)
                } else {
                    current.pages.map { candidatePages[it.id] ?: it }
                }
            )
        }

        // Never trust a client-supplied revision. The store owns revision advancement.
        merged = merged.copy(revision = current.revision)
        return ScopedEditMergeResult(content = merged)
    }

    private fun mergePageSequence(
        currentPages: List<HubPage>,
        baselineIds: Set<String>,
        candidatePages: List<HubPage>
    ): List<HubPage> {
        if (baselineIds.isEmpty()) return currentPages + candidatePages

        val edited = ArrayDeque(candidatePages)
        val result = ArrayList<HubPage>(currentPages.size + candidatePages.size)
        var lastEditableSlot = -1

        currentPages.forEach { page ->
            if (page.id in baselineIds) {
                if (edited.isNotEmpty()) {
                    result += edited.removeFirst()
                    lastEditableSlot = result.lastIndex
                }
                // No candidate left means this projected page was intentionally deleted.
            } else {
                result += page
            }
        }

        if (edited.isNotEmpty()) {
            val insertion = if (lastEditableSlot >= 0) lastEditableSlot + 1 else result.size
            result.addAll(insertion, edited)
        }
        return result
    }

    private fun <T> changedKeys(before: Map<String, T>, after: Map<String, T>): Set<String> =
        (before.keys + after.keys).filterTo(linkedSetOf()) { before[it] != after[it] }

    private fun <T> applyMapDelta(
        current: Map<String, T>,
        baseline: Map<String, T>,
        candidate: Map<String, T>,
        changed: Set<String>,
        kind: String
    ): Map<String, T>? {
        val result = current.toMutableMap()
        changed.forEach { id ->
            val existedInBaseline = id in baseline
            val next = candidate[id]
            if (!existedInBaseline && id in current) return null
            if (next == null) result.remove(id) else result[id] = next
        }
        return result
    }

    private fun fail(message: String) = ScopedEditMergeResult(error = message)
}
