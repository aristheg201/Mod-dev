package io.github.aristheg201.svarcade.ui

enum class UiDensity { COMPACT, REGULAR, WIDE }

data class UiRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right get() = x + width
    val bottom get() = y + height
    fun contains(px: Double, py: Double): Boolean = px >= x && px < right && py >= y && py < bottom
    fun inset(amount: Int) = UiRect(x + amount, y + amount, (width - amount * 2).coerceAtLeast(0), (height - amount * 2).coerceAtLeast(0))
}

data class NativeLayout(
    val density: UiDensity,
    val header: UiRect,
    val navigation: UiRect,
    val content: UiRect,
    val verticalNavigation: Boolean
) {
    fun columns(minimumCellWidth: Int, gap: Int = 8, maximum: Int = 4): Int =
        ((content.width + gap) / (minimumCellWidth + gap)).coerceIn(1, maximum)

    companion object {
        fun resolve(width: Int, height: Int): NativeLayout {
            val safeWidth = width.coerceAtLeast(160)
            val safeHeight = height.coerceAtLeast(120)
            val header = UiRect(0, 0, safeWidth, 36)
            return when {
                safeWidth < 430 -> NativeLayout(
                    density = UiDensity.COMPACT,
                    header = header,
                    navigation = UiRect(6, 40, safeWidth - 12, 27),
                    content = UiRect(8, 73, safeWidth - 16, safeHeight - 81),
                    verticalNavigation = false
                )
                safeWidth < 720 -> NativeLayout(
                    density = UiDensity.REGULAR,
                    header = header,
                    navigation = UiRect(8, 44, 100, safeHeight - 52),
                    content = UiRect(118, 44, safeWidth - 126, safeHeight - 52),
                    verticalNavigation = true
                )
                else -> NativeLayout(
                    density = UiDensity.WIDE,
                    header = header,
                    navigation = UiRect(10, 46, 128, safeHeight - 56),
                    content = UiRect(150, 46, safeWidth - 162, safeHeight - 56),
                    verticalNavigation = true
                )
            }
        }
    }
}
