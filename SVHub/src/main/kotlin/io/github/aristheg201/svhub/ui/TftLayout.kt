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
            UiDensity.REGULAR -> 92
            UiDensity.WIDE -> 108
        }.coerceAtMost((area.height - hudH - gap - 36).coerceAtLeast(24))
        val leftW = when (density) { UiDensity.WIDE -> 126; UiDensity.REGULAR -> 92; UiDensity.COMPACT -> 0 }
            .coerceAtMost((area.width / 4).coerceAtLeast(0))
        val rightW = if (density == UiDensity.WIDE) 112.coerceAtMost(area.width / 5) else 0
        val centerX = area.x + leftW + if (leftW > 0) gap else 0
        val centerRight = area.right - rightW - if (rightW > 0) gap else 0
        val centerW = (centerRight - centerX).coerceAtLeast(40)
        val hud = UiRect(area.x, area.y, area.width, hudH)
        val contentTop = hud.bottom
        val footer = UiRect(centerX, (area.bottom - footerH).coerceAtLeast(contentTop + 24), centerW, footerH)
        val board = UiRect(centerX, contentTop + gap, centerW, (footer.y - contentTop - gap * 2).coerceAtLeast(24))
        val sideTop = contentTop + gap
        val sideH = (area.bottom - sideTop).coerceAtLeast(0)
        val traits = if (leftW > 0) UiRect(area.x, sideTop, leftW, sideH) else null
        val players = if (rightW > 0) UiRect(area.right - rightW, sideTop, rightW, sideH) else null
        return TftResolvedLayout(hud, traits, players, board, footer)
    }
}
