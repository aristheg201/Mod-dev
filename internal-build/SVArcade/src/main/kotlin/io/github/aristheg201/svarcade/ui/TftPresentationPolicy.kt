package io.github.aristheg201.svarcade.ui

/** Presentation decisions cannot grant rewards or alter authoritative game state. */
class TftPresentationPolicy(private val bossIntroMillis: Long = 2_000L) {
    enum class Owner { GAMEPLAY, SCENE, RESULT, AUGMENT, CAROUSEL, BOSS_INTRO, PVE_REWARD }

    data class Input(
        val sessionId: String,
        val round: String,
        val phase: String,
        val boss: Boolean = false,
        val pve: Boolean = false,
        val hasLoot: Boolean = false,
        val lootSerial: Long = 0L,
        val hasAugments: Boolean = false,
        val finished: Boolean = false,
        val sceneOnly: Boolean = false
    )

    private var session: String? = null
    private var bossRound: String? = null
    private var bossStartedAt = 0L
    private var bossDismissed = false
    private var dismissedLoot: Pair<String, Long>? = null
    private var activeInput: Input? = null
    var owner: Owner = Owner.GAMEPLAY
        private set

    fun resolve(input: Input, nowMillis: Long): Owner {
        if (input.sceneOnly) return Owner.SCENE
        if (session != input.sessionId) {
            session = input.sessionId
            bossRound = null
            bossDismissed = false
            dismissedLoot = null
        }
        activeInput = input
        owner = when {
            input.finished -> Owner.RESULT
            input.hasAugments -> Owner.AUGMENT
            input.phase == "draft" -> Owner.CAROUSEL
            input.phase == "post" && input.hasLoot && dismissedLoot != (input.round to input.lootSerial) -> Owner.PVE_REWARD
            input.phase == "combat" && input.pve && input.boss -> {
                if (bossRound != input.round) {
                    bossRound = input.round
                    bossStartedAt = nowMillis
                    bossDismissed = false
                }
                if (!bossDismissed && (nowMillis - bossStartedAt).coerceAtLeast(0L) < bossIntroMillis.coerceAtLeast(0L))
                    Owner.BOSS_INTRO else Owner.GAMEPLAY
            }
            else -> Owner.GAMEPLAY
        }
        return owner
    }

    fun dismiss() {
        val input = activeInput ?: return
        when (owner) {
            Owner.BOSS_INTRO -> bossDismissed = true
            Owner.PVE_REWARD -> dismissedLoot = input.round to input.lootSerial
            else -> Unit
        }
        owner = Owner.GAMEPLAY
    }
}

/** Exclusive header, scene/cards and local continuation button never overlap. */
data class TftExclusiveLayout(val header: UiRect, val body: UiRect, val continueButton: UiRect) {
    companion object {
        fun resolve(area: UiRect): TftExclusiveLayout {
            require(area.width >= 80 && area.height >= 70)
            val padding = 4
            val headerHeight = minOf(26, area.height / 4)
            val buttonHeight = minOf(22, area.height / 4)
            val header = UiRect(area.x + padding, area.y + padding, area.width - padding * 2, headerHeight)
            val buttonWidth = minOf(140, (area.width - padding * 4) / 2)
            val button = UiRect(area.x + (area.width - buttonWidth) / 2, area.bottom - padding - buttonHeight, buttonWidth, buttonHeight)
            val body = UiRect(header.x, header.bottom + padding, header.width, button.y - padding - header.bottom - padding)
            return TftExclusiveLayout(header, body, button)
        }
    }
}
