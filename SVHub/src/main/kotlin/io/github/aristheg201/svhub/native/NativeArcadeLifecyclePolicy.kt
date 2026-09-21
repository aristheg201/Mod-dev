package io.github.aristheg201.svhub.native

internal enum class ActiveSessionResolution { NONE, RESUME, STALE }

internal object NativeArcadeLifecyclePolicy {
    private const val MATCHED_MESSAGE = "gui.svhub.arcade.matched"

    /**
     * PvP matchmaking completes from the server tick after the human collection window.
     * At that moment the client is normally still subscribed to the arcade lobby, so the
     * matched event is the one async event allowed to promote the UI into the active game.
     *
     * Other engine messages must not auto-open the game: TFT deliberately allows closing
     * the game view without resigning, and normal combat updates must not reopen it.
     */
    fun shouldAutoOpenMatchedGame(currentModule: String?, message: String, activeViewPresent: Boolean): Boolean =
        activeViewPresent && currentModule != "game" && message == MATCHED_MESSAGE

    fun resolve(activeSessionId: String?, sessionPresent: Boolean, viewPresent: Boolean): ActiveSessionResolution = when {
        activeSessionId == null -> ActiveSessionResolution.NONE
        sessionPresent && viewPresent -> ActiveSessionResolution.RESUME
        else -> ActiveSessionResolution.STALE
    }
}
