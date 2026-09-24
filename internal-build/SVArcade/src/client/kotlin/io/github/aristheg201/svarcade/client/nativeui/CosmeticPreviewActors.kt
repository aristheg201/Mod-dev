package io.github.aristheg201.svarcade.client.nativeui

/** Arena actors and hero portraits use independent Cobblemon render paths. */
object CosmeticPreviewActors {
    fun arena(id: String) = "store:$id"
    fun portrait(id: String) = "${arena(id)}:portrait"
    fun ready(id: String, needsPortrait: Boolean, sceneResolved: (String) -> Boolean,
              previewResolved: (String) -> Boolean): Boolean =
        sceneResolved(arena(id)) && (!needsPortrait || previewResolved(portrait(id)))
}
