package io.github.aristheg201.svhub.native.network

import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class NativeJsonPatch(val changed: JsonObject, val removed: List<String>) {
    val isEmpty: Boolean get() = changed.size() == 0 && removed.isEmpty()
}

/** Recursive object delta. Arrays and primitives are atomic; object members are patched individually. */
object NativeJsonDelta {
    fun diff(previous: JsonObject, next: JsonObject): NativeJsonPatch {
        val changed = JsonObject()
        val removed = mutableListOf<String>()
        diffObject(previous, next, "", changed, removed)
        return NativeJsonPatch(changed, removed)
    }

    fun apply(target: JsonObject, patch: NativeJsonPatch) {
        patch.removed.forEach { removePath(target, it.split('/')) }
        merge(target, patch.changed)
    }

    private fun diffObject(previous: JsonObject, next: JsonObject, prefix: String, changed: JsonObject, removed: MutableList<String>) {
        previous.keySet().filterNot(next::has).forEach { removed += path(prefix, it) }
        next.entrySet().forEach { (key, value) ->
            val old = previous.get(key)
            when {
                old == null -> changed.add(key, value.deepCopy())
                old == value -> Unit
                old.isJsonObject && value.isJsonObject -> {
                    val child = JsonObject()
                    diffObject(old.asJsonObject, value.asJsonObject, path(prefix, key), child, removed)
                    if (child.size() > 0) changed.add(key, child)
                }
                else -> changed.add(key, value.deepCopy())
            }
        }
    }

    private fun merge(target: JsonObject, changed: JsonObject) {
        changed.entrySet().forEach { (key, value) ->
            val current = target.get(key)
            if (current?.isJsonObject == true && value.isJsonObject) merge(current.asJsonObject, value.asJsonObject)
            else target.add(key, value.deepCopy())
        }
    }

    private fun removePath(target: JsonObject, parts: List<String>) {
        if (parts.isEmpty()) return
        var cursor = target
        parts.dropLast(1).forEach { cursor = cursor.getAsJsonObject(it) ?: return }
        cursor.remove(parts.last())
    }

    private fun path(prefix: String, key: String) = if (prefix.isEmpty()) key else "$prefix/$key"
}
