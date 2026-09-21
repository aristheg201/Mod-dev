package io.github.aristheg201.svhub

import io.github.aristheg201.svhub.ui.NativeLayout
import io.github.aristheg201.svhub.ui.TftLayoutResolver
import io.github.aristheg201.svhub.ui.UiRect
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TftLayoutTest {
    private fun overlaps(a:UiRect,b:UiRect)=a.x<b.right&&a.right>b.x&&a.y<b.bottom&&a.bottom>b.y

    @Test fun `phase title and board controls never intersect gameplay or HUD`() {
        listOf(320 to 180,426 to 240,640 to 360,960 to 540).forEach { (width,height) ->
            listOf(false,true).forEach { boss ->
                val area=UiRect(4,4,width-8,height-8)
                val layout=TftLayoutResolver.resolve(area,NativeLayout.resolve(width,height).density,true,boss)
                val banner=assertNotNull(layout.phaseBanner)
                assertTrue(banner.height>=30,"Phase title needs its own row at $width x $height")
                assertTrue(!overlaps(banner,layout.board))
                assertTrue(!overlaps(banner,layout.hud))
                assertTrue(layout.board.height>=24)
                val rects=layout.allRects()
                rects.forEachIndexed { i,a ->
                    assertTrue(a.x>=area.x && a.y>=area.y && a.right<=area.right && a.bottom<=area.bottom)
                    rects.drop(i+1).forEach { b -> assertTrue(!overlaps(a,b),"$a intersects $b") }
                }
            }
        }
    }

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
            val itemRail = assertNotNull(tft.itemRail, "$width x $height must expose a right-side item rail")
            assertTrue(itemRail.right == gameContent.right, "$width x $height item rail is not right-aligned: $itemRail")
            assertTrue(itemRail.y >= tft.hud.bottom, "$width x $height item rail overlaps HUD: $itemRail")
            assertTrue(itemRail.bottom <= tft.footer.y, "$width x $height item rail overlaps footer: $itemRail")
            tft.players?.let { players -> assertTrue(itemRail.y >= players.bottom, "$width x $height item rail overlaps player list") }
            assertTrue(!overlaps(tft.board,tft.hud),"$width x $height board overlaps HUD")
            assertTrue(!overlaps(tft.board,tft.footer),"$width x $height board overlaps footer")
            tft.traits?.let { assertTrue(!overlaps(tft.board,it),"$width x $height board overlaps traits") }
            tft.players?.let { assertTrue(!overlaps(tft.board,it),"$width x $height board overlaps players") }
            assertTrue(!overlaps(tft.board,itemRail),"$width x $height board overlaps item rail")
        }
    }
}
