package io.github.aristheg201.svhub.content

/**
 * Bounded immutable draft history used by the in-game editor.
 *
 * History boundaries keep deep-cloned snapshots, while repeated mutations with
 * the same non-null coalesce key (for example typing in one EditBox) update the
 * current draft without creating one full snapshot per keystroke.
 */
class HubDraftHistory(initial: HubContent, private val capacity: Int = 64) {
    init {
        require(capacity in 2..256) { "Draft history capacity must be between 2 and 256" }
    }

    private val undo = ArrayDeque<HubContent>()
    private val redo = ArrayDeque<HubContent>()
    private var current = clone(initial)
    private var lastCoalesceKey: String? = null

    fun current(): HubContent = clone(current)
    fun canUndo(): Boolean = undo.isNotEmpty()
    fun canRedo(): Boolean = redo.isNotEmpty()

    /**
     * Replaces the current draft. A shared coalesce key means all consecutive
     * replacements belong to one undo step. Passing null always starts a new step.
     */
    fun replace(next: HubContent, coalesceKey: String? = null): HubContent {
        val cloned = clone(next)
        if (cloned == current) return current()

        if (coalesceKey == null || coalesceKey != lastCoalesceKey) {
            undo.addLast(current)
            while (undo.size > capacity) undo.removeFirst()
        }

        current = cloned
        redo.clear()
        lastCoalesceKey = coalesceKey
        return current()
    }

    fun mutate(coalesceKey: String? = null, transform: (HubContent) -> HubContent): HubContent =
        replace(transform(current()), coalesceKey)

    /** Explicitly ends an active typing/coalescing group without changing state. */
    fun breakCoalescing() {
        lastCoalesceKey = null
    }

    fun undo(): HubContent {
        if (undo.isEmpty()) return current()
        redo.addLast(current)
        current = undo.removeLast()
        lastCoalesceKey = null
        return current()
    }

    fun redo(): HubContent {
        if (redo.isEmpty()) return current()
        undo.addLast(current)
        while (undo.size > capacity) undo.removeFirst()
        current = redo.removeLast()
        lastCoalesceKey = null
        return current()
    }

    fun reset(value: HubContent): HubContent {
        undo.clear()
        redo.clear()
        current = clone(value)
        lastCoalesceKey = null
        return current()
    }

    companion object {
        fun clone(content: HubContent): HubContent = HubContentCodec.decode(HubContentCodec.encode(content))
    }
}
