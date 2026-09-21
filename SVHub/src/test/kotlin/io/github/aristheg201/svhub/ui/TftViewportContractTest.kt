package io.github.aristheg201.svhub.ui

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/** Real resolver geometry; screenshots still need independent visual review. */
class TftViewportContractTest {
    private fun verify(area: UiRect, density: UiDensity, phase: Boolean, boss: Boolean) {
        val layout = TftLayoutResolver.resolve(area, density, phase, boss)
        val rectangles = layout.allRects()
        val context = "$area density=$density phase=$phase boss=$boss"
        rectangles.forEach { rect ->
            assertTrue(rect.width > 0 && rect.height > 0, "Non-positive rectangle $rect: $context")
            assertTrue(rect.x >= area.x && rect.y >= area.y && rect.right <= area.right && rect.bottom <= area.bottom,
                "Rectangle escapes viewport $rect: $context")
        }
        rectangles.forEachIndexed { index, a ->
            rectangles.drop(index + 1).forEach { b ->
                val overlap = a.x < b.right && a.right > b.x && a.y < b.bottom && a.bottom > b.y
                assertTrue(!overlap, "Overlapping rectangles $a and $b: $context")
            }
        }
        layout.itemRail?.let { rail ->
            assertTrue(rail.x >= layout.board.right, "Item rail is not to the right of the board: $context")
            layout.players?.let { assertTrue(rail.y >= it.bottom, "Item rail overlaps players: $context") }
        }
        assertTrue(layout.board.width >= 40 && layout.board.height >= 24, "Board loses its minimum safe area: $context")
    }

    @Test fun supportedFramebufferAndGuiScaleMatrix() {
        val sizes = listOf(1280 to 720, 1366 to 768, 1920 to 1080, 2560 to 1440, 3440 to 1440)
        for ((width, height) in sizes) for (scale in 1..6) {
            val w = (width + scale - 1) / scale
            val h = (height + scale - 1) / scale
            if (w < 320 || h < 240) continue
            for (phase in listOf(false, true)) for (boss in listOf(false, true)) {
                verify(UiRect(0, 0, w, h), NativeLayout.resolve(w, h).density, phase, boss)
            }
        }
    }

    @Test fun densityTransitionsAndMinimumViewportRemainBounded() {
        for (w in listOf(160, 239, 240, 319, 320, 429, 430, 431, 719, 720, 721)) {
            for (h in listOf(120, 134, 135, 189, 190, 239, 240, 360, 540)) {
                for (density in UiDensity.entries) for (phase in listOf(false, true)) {
                    verify(UiRect(0, 0, w, h), density, phase, true)
                    verify(UiRect(17, 23, w, h), density, phase, false)
                }
            }
        }
    }

    @Test fun denseViewportSweepHasNoHudBoardFooterOrRailOverlap() {
        for (w in 160..1280 step 19) for (h in 120..720 step 17) {
            val density = NativeLayout.resolve(w, h).density
            verify(UiRect(0, 0, w, h), density, false, false)
            verify(UiRect(0, 0, w, h), density, true, true)
        }
    }
}
