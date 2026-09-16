package io.github.aristheg201.svhub.client.render

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.sin

object AnimatedTextRenderer {
    fun render(gui: GuiGraphics, text: String, x: Int, y: Int, color: Int, animation: String, scale: Float, centered: Boolean, tick: Long) {
        val font = Minecraft.getInstance().font
        val visible = when (animation) {
            "typewriter" -> text.take(((tick / 2) % (text.length + 18)).toInt().coerceAtMost(text.length))
            "blink" -> if ((tick / 12) % 2L == 0L) text else ""
            "pixel_flicker" -> if (tick % 37L in 0L..1L) "" else text
            else -> text
        }
        if (visible.isEmpty()) return
        val pose = gui.pose(); pose.pushPose(); pose.translate(x.toFloat(), y.toFloat(), 20f)
        val pulse = if (animation == "glow_pulse" || animation == "pixel_pop") (1f + sin(tick / 7.0).toFloat() * 0.025f) else 1f
        pose.scale(scale * pulse, scale * pulse, 1f)
        val width = font.width(visible); val drawX = if (centered) -width / 2 else 0
        if (animation == "wave") {
            var cursor = drawX
            visible.forEachIndexed { i, c -> val dy=(sin((tick+i*3)/5.0)*2.0).toInt(); val s=c.toString(); gui.drawString(font,s,cursor,dy,color,true); cursor += font.width(s) }
        } else gui.drawString(font, Component.literal(visible), drawX, 0, if (animation == "shimmer") shimmerColor(color,tick) else color, animation != "shimmer")
        pose.popPose()
    }
    private fun shimmerColor(base: Int, tick: Long): Int { val amount=((sin(tick/8.0)+1.0)*0.16).toFloat(); val a=base ushr 24 and 0xFF; fun c(s:Int):Int { val v=base ushr s and 0xFF; return (v+(255-v)*amount).toInt().coerceIn(0,255) }; return (a shl 24) or (c(16) shl 16) or (c(8) shl 8) or c(0) }
}
