package io.github.aristheg201.svarcade.compat

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Repairs only unusable empty animation hooks produced by resource-pack merges.
 * Valid models, textures, aspects, poses and animation expressions are untouched.
 */
object CobblemonPoseSanitizer {
    data class Result(
        val json: String,
        val changed: Boolean,
        val removedAnimations: Int,
        val removedNamedAnimations: Int,
        val removedTransitions: Int,
        val removedQuirkAnimations: Int
    )

    fun sanitize(json: String): String = sanitizeWithStats(json).json

    fun sanitizeWithStats(json: String): Result {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
            ?: return Result(json, false, 0, 0, 0, 0)
        val poses = root.getAsJsonObject("poses")
            ?: return Result(json, false, 0, 0, 0, 0)

        var removedAnimations = 0
        var removedNamedAnimations = 0
        var removedTransitions = 0
        var removedQuirkAnimations = 0

        poses.entrySet().forEach poseLoop@ { (_, rawPose) ->
            if (!rawPose.isJsonObject) return@poseLoop
            val pose = rawPose.asJsonObject

            pose.getAsJsonArray("animations")?.let { animations ->
                removedAnimations += removeInvalidAnimationEntries(animations)
            }

            pose.getAsJsonObject("namedAnimations")?.let { named ->
                removedNamedAnimations += removeInvalidMapEntries(named)
                if (named.size() == 0) pose.remove("namedAnimations")
            }

            pose.getAsJsonObject("transitions")?.let { transitions ->
                removedTransitions += removeInvalidMapEntries(transitions)
                if (transitions.size() == 0) pose.remove("transitions")
            }

            pose.getAsJsonArray("quirks")?.forEach quirkLoop@ { rawQuirk ->
                if (!rawQuirk.isJsonObject) return@quirkLoop
                val quirk = rawQuirk.asJsonObject
                normalizeSingularAnimation(quirk)
                quirk.getAsJsonArray("animations")?.let { animations ->
                    removedQuirkAnimations += removeInvalidPrimitiveEntries(animations)
                }
            }
        }

        val changed = removedAnimations + removedNamedAnimations + removedTransitions + removedQuirkAnimations > 0
        return Result(
            json = if (changed) root.toString() else json,
            changed = changed,
            removedAnimations = removedAnimations,
            removedNamedAnimations = removedNamedAnimations,
            removedTransitions = removedTransitions,
            removedQuirkAnimations = removedQuirkAnimations
        )
    }

    private fun removeInvalidAnimationEntries(array: JsonArray): Int {
        var removed = 0
        val iterator = array.iterator()
        while (iterator.hasNext()) {
            val value = iterator.next()
            val valid = when {
                value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString.isNotBlank()
                value.isJsonObject -> value.asJsonObject.get("animation")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString
                    ?.isNotBlank() == true
                else -> false
            }
            if (!valid) {
                iterator.remove()
                removed++
            }
        }
        return removed
    }

    private fun removeInvalidPrimitiveEntries(array: JsonArray): Int {
        var removed = 0
        val iterator = array.iterator()
        while (iterator.hasNext()) {
            val value = iterator.next()
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString || value.asString.isBlank()) {
                iterator.remove()
                removed++
            }
        }
        return removed
    }

    private fun removeInvalidMapEntries(map: JsonObject): Int {
        val invalidKeys = map.entrySet()
            .filter { (_, value) -> !value.isJsonPrimitive || !value.asJsonPrimitive.isString || value.asString.isBlank() }
            .map { it.key }
        invalidKeys.forEach(map::remove)
        return invalidKeys.size
    }

    private fun normalizeSingularAnimation(quirk: JsonObject) {
        if (quirk.has("animations") || !quirk.has("animation")) return
        val value = quirk.remove("animation") ?: return
        quirk.add("animations", JsonArray().apply { add(value) })
    }
}
