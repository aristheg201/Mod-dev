package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.ui.NativeLayout
import io.github.aristheg201.svhub.ui.TftLayoutResolver
import io.github.aristheg201.svhub.ui.UiRect
import kotlin.test.Test
import kotlin.test.assertTrue

class TftLayoutTest {
    @Test
    fun `TFT presentation stays inside required logical viewports`() {
        listOf(320 to 180, 426 to 240, 640 to 360, 960 to 540).forEach { (width, height) ->
            val base = NativeLayout.resolve(width, height)
            val gameContent = UiRect(8, 42, (width - 16).coerceAtLeast(144), (height - 50).coerceAtLeast(70)).inset(8)
            val tft = TftLayoutResolver.resolve(gameContent, base.density)
            tft.allRects().forEach { rect ->
                assertTrue(rect.x >= gameContent.x, "$width x $height: rect left overflow $rect")
                assertTrue(rect.y >= gameContent.y, "$width x $height: rect top overflow $rect")
                assertTrue(rect.right <= gameContent.right, "$width x $height: rect right overflow $rect")
                assertTrue(rect.bottom <= gameContent.bottom, "$width x $height: rect bottom overflow $rect")
                assertTrue(rect.width > 0 && rect.height > 0, "$width x $height: zero-sized rect $rect")
            }
            assertTrue(tft.board.height >= 24, "$width x $height board too short")
            assertTrue(tft.footer.height >= 24, "$width x $height footer too short")
        }
    }
}
