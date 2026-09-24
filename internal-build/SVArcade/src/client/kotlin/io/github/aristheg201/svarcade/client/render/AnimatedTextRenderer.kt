package io.github.aristheg201.svarcade.client.render

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.roundToInt
import kotlin.math.sin

/** Lightweight, readability-first animated text presets for Hub UI. */
object AnimatedTextRenderer {
    fun render(
        gui: GuiGraphics,
        text: String,
        x: Int,
        y: Int,
        color: Int,
        animation: String,
        scale: Float,
        centered: Boolean,
        tick: Long
    ) {
        if (text.isEmpty()) return
        val font = Minecraft.getInstance().font
        val preset = normalize(animation)
        val rich = text.indexOf('<') >= 0
        // Character slicing can break MiniMessage tag structure. Rich text keeps its
        // complete component and still receives scale/position animation.
        val visible = if (rich && preset in CHARACTER_ANIMATIONS) text else visibleText(text, preset, tick)
        if (visible.isEmpty()) return

        val pose = gui.pose()
        pose.pushPose()

        val baseX = when (preset) {
            "marquee" -> x + (sin(tick / 18.0) * 8.0).roundToInt()
            else -> x
        }
        val baseY = when (preset) {
            "gentle_bounce" -> y + (sin(tick / 8.0) * 1.5).roundToInt()
            else -> y
        }
        pose.translate(baseX.toFloat(), baseY.toFloat(), 20f)

        val animatedScale = when (preset) {
            "pulse", "glow", "glow_pulse" -> 1f + sin(tick / 8.0).toFloat() * 0.018f
            "pixel_pop" -> if (tick < 10L) 0.84f + tick.coerceAtMost(10L) * 0.016f else 1f
            else -> 1f
        }
        pose.scale(scale * animatedScale, scale * animatedScale, 1f)

        val component = MiniMessageText.component(visible)
        val width = font.width(component)
        val drawX = if (centered) -width / 2 else 0
        val drawColor = when (preset) {
            "shimmer" -> shimmerColor(color, tick)
            "glow", "glow_pulse" -> glowColor(color, tick)
            else -> color
        }

        if (preset == "wave" && !rich) {
            var cursor = drawX
            visible.forEachIndexed { index, char ->
                val dy = (sin((tick + index * 3) / 5.5) * 1.8).roundToInt()
                val glyph = char.toString()
                gui.drawString(font, glyph, cursor, dy, drawColor, true)
                cursor += font.width(glyph)
            }
        } else {
            gui.drawString(font, component, drawX, 0, drawColor, preset !in setOf("shimmer", "glow", "glow_pulse"))
        }
        pose.popPose()
    }

    private fun visibleText(text: String, preset: String, tick: Long): String = when (preset) {
        "typewriter" -> text.take(((tick / 2L) + 1L).coerceAtMost(text.length.toLong()).toInt())
        "left_reveal" -> text.take(((tick / 1.5).toInt() + 1).coerceAtMost(text.length))
        "blink" -> if ((tick / 16L) % 4L == 3L) "" else text
        "retro_flicker", "pixel_flicker" -> if (tick % 47L in 0L..1L || tick % 89L == 0L) "" else text
        else -> text
    }

    private fun normalize(animation: String): String = when (animation.lowercase()) {
        "none", "static" -> "none"
        "typewriter" -> "typewriter"
        "blink" -> "blink"
        "pulse" -> "pulse"
        "glow", "glow_pulse" -> animation.lowercase()
        "shimmer" -> "shimmer"
        "wave" -> "wave"
        "gentle_bounce", "bounce" -> "gentle_bounce"
        "marquee" -> "marquee"
        "retro_flicker", "pixel_flicker" -> animation.lowercase()
        "left_reveal" -> "left_reveal"
        "pixel_pop", "pixel_pop_in" -> "pixel_pop"
        else -> "none"
    }

    private fun shimmerColor(base: Int, tick: Long): Int {
        val amount = ((sin(tick / 8.0) + 1.0) * 0.16).toFloat()
        return brighten(base, amount)
    }

    private fun glowColor(base: Int, tick: Long): Int {
        val amount = (0.08 + (sin(tick / 10.0) + 1.0) * 0.07).toFloat()
        return brighten(base, amount)
    }

    private fun brighten(base: Int, amount: Float): Int {
        val a = base ushr 24 and 0xFF
        fun channel(shift: Int): Int {
            val value = base ushr shift and 0xFF
            return (value + (255 - value) * amount).toInt().coerceIn(0, 255)
        }
        return (a shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private val CHARACTER_ANIMATIONS = setOf("typewriter", "left_reveal", "wave")
}
