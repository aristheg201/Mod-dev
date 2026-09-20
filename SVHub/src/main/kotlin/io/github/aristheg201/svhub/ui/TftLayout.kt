package io.github.aristheg201.svhub.ui

/** Pure layout contract for the dedicated TFT presentation. */
data class TftResolvedLayout(
    val hud: UiRect,
    val traits: UiRect?,
    val players: UiRect?,
    val board: UiRect,
    val footer: UiRect
) {
    fun allRects(): List<UiRect> = buildList { add(hud); traits?.let(::add); players?.let(::add); add(board); add(footer) }
}

object TftLayoutResolver {
    fun resolve(area: UiRect, density: UiDensity): TftResolvedLayout {
        val gap = if (density == UiDensity.COMPACT) 2 else 6
        val hudH = when (density) { UiDensity.COMPACT -> 22; UiDensity.REGULAR -> 30; UiDensity.WIDE -> 34 }.coerceAtMost(area.height / 3)
        val footerH = when (density) {
            UiDensity.COMPACT -> when { area.height < 135 -> 36; area.height < 190 -> 52; else -> 66 }
            UiDensity.REGULAR -> (area.height * .22f).toInt().coerceIn(64,92)
            UiDensity.WIDE -> (area.height * .22f).toInt().coerceIn(78,108)
        }.coerceAtMost((area.height - hudH - gap - 36).coerceAtLeast(24))
        val leftW = when (density) { UiDensity.WIDE -> 96; UiDensity.REGULAR -> 78; UiDensity.COMPACT -> 0 }
            .coerceAtMost((area.width / 4).coerceAtLeast(0))
        val rightW = if (density == UiDensity.WIDE) 76.coerceAtMost(area.width / 5) else 0
        val centerX = area.x + gap
        val centerRight = area.right - gap
        val centerW = (centerRight - centerX).coerceAtLeast(40)
        val hud = UiRect(area.x, area.y, area.width, hudH)
        val contentTop = hud.bottom
        val footer = UiRect(centerX, (area.bottom - footerH).coerceAtLeast(contentTop + 24), centerW, footerH)
        // Arena fills the screen beneath compact HUD overlays, with space for the shop below.
        val board = UiRect(area.x,area.y,area.width,(footer.y-area.y-gap).coerceAtLeast(24))
        val sideTop = contentTop + gap
        val sideH = (footer.y - sideTop).coerceAtLeast(24)
        val traits = if (leftW > 0) UiRect(area.x, sideTop, leftW, sideH) else null
        val players = if (rightW > 0) UiRect(area.right - rightW, sideTop, rightW, sideH) else null
        return TftResolvedLayout(hud, traits, players, board, footer)
    }
}
