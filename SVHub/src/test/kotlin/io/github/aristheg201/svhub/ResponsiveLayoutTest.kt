package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.ui.NativeLayout
import io.github.aristheg201.svhub.ui.UiDensity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponsiveLayoutTest {
    @Test
    fun `compact layout keeps navigation and content inside the screen`() {
        val layout = NativeLayout.resolve(320, 180)
        assertEquals(UiDensity.COMPACT, layout.density)
        assertTrue(layout.navigation.right <= 320)
        assertTrue(layout.content.right <= 320)
        assertTrue(layout.content.bottom <= 180)
        assertTrue(layout.content.width >= 280)
    }

    @Test
    fun `regular and wide layouts reserve a vertical rail`() {
        val compactWide = NativeLayout.resolve(426, 240)
        val regular = NativeLayout.resolve(640, 360)
        val wide = NativeLayout.resolve(960, 540)
        assertEquals(UiDensity.COMPACT, compactWide.density)
        assertTrue(compactWide.content.right <= 426)
        assertTrue(compactWide.content.bottom <= 240)
        assertTrue(regular.verticalNavigation)
        assertTrue(wide.verticalNavigation)
        assertTrue(regular.content.x > regular.navigation.right)
        assertTrue(wide.content.width > regular.content.width)
    }

    @Test
    fun `gui scales one through four preserve safe logical bounds`() {
        (1..4).forEach { scale ->
            val width = 1280 / scale
            val height = 720 / scale
            val layout = NativeLayout.resolve(width, height)
            assertTrue(layout.header.right <= width)
            assertTrue(layout.content.right <= width)
            assertTrue(layout.content.bottom <= height)
            assertTrue(layout.content.width > 0 && layout.content.height > 0)
            assertTrue(layout.navigation.width > 0 && layout.navigation.height > 0)
        }
    }

    @Test
    fun `grid columns respect available content width`() {
        assertEquals(1, NativeLayout.resolve(320, 180).columns(400))
        assertTrue(NativeLayout.resolve(960, 540).columns(180) >= 4)
    }
}
