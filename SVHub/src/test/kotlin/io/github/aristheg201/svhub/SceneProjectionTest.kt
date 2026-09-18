package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.ui.SceneCameras
import io.github.aristheg201.svhub.ui.SceneProjection
import io.github.aristheg201.svhub.ui.ScrollbarLayout
import io.github.aristheg201.svhub.ui.UiRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneProjectionTest {
    @Test fun `camera presets fit all required logical viewports`() {
        val viewports = listOf(320 to 180, 426 to 240, 640 to 360, 960 to 540)
        val presets = listOf(SceneCameras.BOARD, SceneCameras.XIANGQI, SceneCameras.LANE, SceneCameras.LUDO, SceneCameras.TFT)
        viewports.forEach { (width, height) ->
            val area = UiRect(8, 42, width - 16, height - 50)
            presets.forEach { camera ->
                val metrics = SceneProjection.resolve(area, 8, 8, camera)
                assertTrue(metrics.tileWidth >= 10)
                assertTrue(metrics.tileHeight >= 7)
                val near = metrics.project(0f, 0f)
                val far = metrics.project(7f, 7f)
                assertTrue(near.x in (area.x - metrics.tileWidth).toFloat()..(area.right + metrics.tileWidth).toFloat())
                assertTrue(far.y <= (area.bottom + metrics.tileHeight).toFloat())
                assertTrue(metrics.depthFor(7f, 7f) > metrics.depthFor(0f, 0f))
            }
        }
    }

    @Test fun `touch scrollbar has safe hit width and clamps drag`() {
        val viewport = UiRect(8, 73, 304, 99)
        val metrics = assertNotNull(ScrollbarLayout.resolve(viewport, 420, 0))
        assertTrue(metrics.hitRect.width >= 14)
        assertTrue(metrics.visualThumb.height >= 24)
        assertEquals(0, ScrollbarLayout.scrollFromPointer(metrics, metrics.trackTop.toDouble(), 0.0))
        assertEquals(metrics.maxScroll, ScrollbarLayout.scrollFromPointer(metrics, metrics.trackBottom.toDouble(), 0.0))
    }
}
