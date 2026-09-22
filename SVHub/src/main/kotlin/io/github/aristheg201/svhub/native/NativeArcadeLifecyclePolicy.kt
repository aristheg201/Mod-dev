package io.github.aristheg201.svhub.native

enum class ActiveSessionResolution { NONE, RESUME, STALE }

object NativeArcadeLifecyclePolicy {
    private const val MATCHED_MESSAGE = "gui.svhub.arcade.matched"

    /**
     * If the player is still subscribed to Arcade while a live session exists, Arcade is stale:
     * promote immediately. This deliberately does not promote a null subscription, so closing
     * TFT still keeps the UI closed until the player explicitly opens Arcade again.
     *
     * The matched message remains a compatibility signal for non-Arcade subscriptions, but
     * promotion must not depend on that one-tick message because another engine update may
     * replace it before the UI transition arrives.
     */
    fun shouldAutoOpenMatchedGame(currentModule: String?, message: String, activeViewPresent: Boolean): Boolean =
        activeViewPresent && currentModule != "game" &&
            (currentModule == "arcade" || message == MATCHED_MESSAGE)

    /**
     * NativeOpen packets normally follow the local view lineage. An active game promotion is
     * server-authoritative and may legitimately race one lobby replacement ahead of the client,
     * so it must be accepted even when replacesViewId no longer equals the visible lobby view.
     */
    fun shouldAcceptServerOpen(
        targetModule: String,
        targetHasActiveView: Boolean,
        currentViewId: String?,
        incomingViewId: String,
        replacesViewId: String,
        explicitlyClosed: Boolean
    ): Boolean {
        if (explicitlyClosed) return false
        if (targetModule == "game" && targetHasActiveView) return true
        return currentViewId == null || currentViewId == replacesViewId || currentViewId == incomingViewId
    }

    fun resolve(activeSessionId: String?, sessionPresent: Boolean, viewPresent: Boolean): ActiveSessionResolution = when {
        activeSessionId == null -> ActiveSessionResolution.NONE
        sessionPresent && viewPresent -> ActiveSessionResolution.RESUME
        else -> ActiveSessionResolution.STALE
    }
}
