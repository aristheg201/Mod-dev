package io.github.aristheg201.svhub.ui

data class ScrollbarMetrics(
    val viewport: UiRect,
    val maxScroll: Int,
    val clampedScroll: Int,
    val trackTop: Int,
    val trackBottom: Int,
    val thumbTop: Int,
    val thumbBottom: Int,
    val hitRect: UiRect,
    val visualTrack: UiRect,
    val visualThumb: UiRect
)

object ScrollbarLayout {
    const val TOUCH_HIT_WIDTH = 14
    const val MIN_THUMB = 24

    fun resolve(viewport: UiRect, contentHeight: Int, scroll: Int): ScrollbarMetrics? {
        val maxScroll = (contentHeight - viewport.height).coerceAtLeast(0)
        if (maxScroll <= 0 || viewport.height <= 0 || viewport.width <= 0) return null
        val clamped = scroll.coerceIn(0, maxScroll)
        val top = viewport.y + 4
        val bottom = (viewport.bottom - 4).coerceAtLeast(top + 1)
        val trackH = (bottom - top).coerceAtLeast(1)
        val minThumb = MIN_THUMB.coerceAtMost(trackH)
        val rawThumb = (trackH.toLong() * viewport.height / contentHeight.coerceAtLeast(1)).toInt()
        val thumbH = rawThumb.coerceIn(minThumb, trackH)
        val travel = (trackH - thumbH).coerceAtLeast(0)
        val offset = if (travel == 0) 0 else (clamped.toLong() * travel / maxScroll).toInt()
        val thumbTop = top + offset
        val hitWidth = TOUCH_HIT_WIDTH.coerceAtMost(viewport.width)
        val hitRect = UiRect(viewport.right - hitWidth, top, hitWidth, trackH)
        val visualWidth = 5.coerceAtMost(viewport.width)
        val visualTrack = UiRect(viewport.right - visualWidth - 2, top, visualWidth, trackH)
        val visualThumb = UiRect(visualTrack.x, thumbTop, visualWidth, thumbH)
        return ScrollbarMetrics(viewport, maxScroll, clamped, top, bottom, thumbTop, thumbTop + thumbH, hitRect, visualTrack, visualThumb)
    }

    fun scrollFromPointer(metrics: ScrollbarMetrics, mouseY: Double, dragOffset: Double): Int {
        val thumbH = (metrics.thumbBottom - metrics.thumbTop).coerceAtLeast(1)
        val travel = ((metrics.trackBottom - metrics.trackTop) - thumbH).coerceAtLeast(0)
        if (travel == 0 || metrics.maxScroll == 0) return 0
        val offset = (mouseY - dragOffset - metrics.trackTop).coerceIn(0.0, travel.toDouble())
        return (offset / travel.toDouble() * metrics.maxScroll).toInt().coerceIn(0, metrics.maxScroll)
    }
}
