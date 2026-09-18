package io.github.aristheg201.svhub.native

internal enum class ActiveSessionResolution { NONE, RESUME, STALE }

internal object NativeArcadeLifecyclePolicy {
    fun resolve(activeSessionId: String?, sessionPresent: Boolean, viewPresent: Boolean): ActiveSessionResolution = when {
        activeSessionId == null -> ActiveSessionResolution.NONE
        sessionPresent && viewPresent -> ActiveSessionResolution.RESUME
        else -> ActiveSessionResolution.STALE
    }
}
