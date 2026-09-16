package io.github.aristheg201.svhub.content

/**
 * Bounded immutable draft history used by the in-game editor.
 * Every stored state is deep-cloned through the content codec so JsonObject
 * component properties cannot alias across undo/redo snapshots.
 */
class HubDraftHistory(initial: HubContent, private val capacity: Int = 64) {
    init {
        require(capacity in 2..256) { "Draft history capacity must be between 2 and 256" }
    }

    private val undo = ArrayDeque<HubContent>()
    private val redo = ArrayDeque<HubContent>()
    private var current = clone(initial)

    fun current(): HubContent = clone(current)
    fun canUndo(): Boolean = undo.isNotEmpty()
    fun canRedo(): Boolean = redo.isNotEmpty()

    fun replace(next: HubContent): HubContent {
        val cloned = clone(next)
        if (HubContentCodec.encode(cloned) == HubContentCodec.encode(current)) return current()
        undo.addLast(current)
        while (undo.size > capacity) undo.removeFirst()
        current = cloned
        redo.clear()
        return current()
    }

    fun mutate(transform: (HubContent) -> HubContent): HubContent = replace(transform(current()))

    fun undo(): HubContent {
        if (undo.isEmpty()) return current()
        redo.addLast(current)
        current = undo.removeLast()
        return current()
    }

    fun redo(): HubContent {
        if (redo.isEmpty()) return current()
        undo.addLast(current)
        current = redo.removeLast()
        return current()
    }

    fun reset(value: HubContent): HubContent {
        undo.clear()
        redo.clear()
        current = clone(value)
        return current()
    }

    companion object {
        fun clone(content: HubContent): HubContent = HubContentCodec.decode(HubContentCodec.encode(content))
    }
}
