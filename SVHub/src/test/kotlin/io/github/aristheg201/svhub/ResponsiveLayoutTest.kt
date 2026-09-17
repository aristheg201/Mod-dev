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
    fun `grid columns respect available content width`() {
        assertEquals(1, NativeLayout.resolve(320, 180).columns(400))
        assertTrue(NativeLayout.resolve(960, 540).columns(180) >= 4)
    }
}
