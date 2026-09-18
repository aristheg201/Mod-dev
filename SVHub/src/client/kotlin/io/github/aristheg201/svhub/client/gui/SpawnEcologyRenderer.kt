package io.github.aristheg201.svhub.client.gui

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.HubTheme
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import kotlin.math.max

object SpawnEcologyRenderer {
    private data class EnvSummary(
        val biomes: LinkedHashSet<String> = linkedSetOf(),
        val dimensions: LinkedHashSet<String> = linkedSetOf(),
        val structures: LinkedHashSet<String> = linkedSetOf(),
        val times: LinkedHashSet<String> = linkedSetOf(),
        val weather: LinkedHashSet<String> = linkedSetOf()
    )

    fun render(
        gui: GuiGraphics,
        font: Font,
        x: Int,
        startY: Int,
        width: Int,
        spawns: JsonArray,
        total: Int,
        theme: HubTheme
    ): Int {
        var y = startY
        val p = theme.palette
        val summary = summarize(spawns)

        if (summary.biomes.isNotEmpty()) {
            gui.drawString(font, "BIOMES", x, y, p.mutedText, true)
            y += 13
            y = renderChips(gui, font, x, y, width, summary.biomes.take(16), theme, ::environmentColor)
            if (summary.biomes.size > 16) {
                gui.drawString(font, "+ ${summary.biomes.size - 16} biome tags", x + 4, y, p.mutedText, false)
                y += 13
            }
            y += 4
        }

        val contextChips = buildList {
            summary.dimensions.take(5).forEach { add("DIM · ${pretty(it)}") }
            summary.structures.take(5).forEach { add("STRUCT · ${pretty(it)}") }
            summary.times.take(4).forEach { add("TIME · ${pretty(it)}") }
            summary.weather.take(4).forEach { add("WEATHER · ${pretty(it)}") }
        }
        if (contextChips.isNotEmpty()) {
            y = renderChips(gui, font, x, y, width, contextChips, theme) { p.panelAlt }
            y += 7
        }

        val count = minOf(8, spawns.size())
        for (i in 0 until count) {
            val spawn = spawns[i].asJsonObject
            val conditions = spawn.getAsJsonArray("conditions")?.strings().orEmpty()
            val detailConditions = conditions.filterNot(::isEnvironmentCondition).take(5)
            val form = spawn.string("form").takeIf { it.isNotBlank() && !it.equals("Normal", true) }
            val aspects = spawn.getAsJsonArray("aspects")?.strings().orEmpty()
            val formLine = buildList {
                form?.let { add(pretty(it)) }
                addAll(aspects.map(::pretty))
            }.distinct().joinToString(" · ")
            val herd = spawn.string("herd").takeIf(String::isNotBlank)
            val extraLines = detailConditions.sumOf { condition ->
                font.split(Component.literal("• ${prettyCondition(condition)}"), width - 26).take(2).size
            } + (if (formLine.isNotBlank()) 1 else 0) + (if (herd != null) 1 else 0)
            val cardHeight = 42 + extraLines * 11

            val bucket = spawn.string("bucket", "common")
            val accent = bucketColor(bucket, theme)
            PixelUi.panel(gui, x, y, width, cardHeight, p.panelAlt, accent)
            gui.fill(x, y, x + 5, y + cardHeight, accent)

            gui.drawString(font, pretty(bucket).uppercase(), x + 12, y + 9, accent, true)
            val level = spawn.string("level", "1-100")
            val context = pretty(spawn.string("context", "grounded"))
            val weight = runCatching { spawn.get("weight")?.asDouble }.getOrNull()
            val meta = buildString {
                append("Lv.").append(level).append("  ·  ").append(context)
                if (weight != null) append("  ·  W ").append(trim(weight))
            }
            gui.drawString(font, font.plainSubstrByWidth(meta, width - 116), x + 104, y + 9, p.text, false)

            var cy = y + 25
            if (formLine.isNotBlank()) {
                gui.drawString(font, "Form  $formLine", x + 12, cy, p.accent2, false)
                cy += 11
            }
            if (herd != null) {
                gui.drawString(font, font.plainSubstrByWidth("Herd  ${pretty(herd)}", width - 24), x + 12, cy, p.mutedText, false)
                cy += 11
            }
            if (detailConditions.isEmpty() && formLine.isBlank() && herd == null) {
                gui.drawString(font, "No extra spawn restrictions", x + 12, cy, p.mutedText, false)
            } else {
                detailConditions.forEach { condition ->
                    font.split(Component.literal("• ${prettyCondition(condition)}"), width - 26).take(2).forEach { line ->
                        gui.drawString(font, line, x + 12, cy, p.text, false)
                        cy += 11
                    }
                }
            }
            y += cardHeight + 7
        }

        val real = max(total, spawns.size())
        if (real > count) {
            gui.drawString(font, "+ ${real - count} spawn entries", x + 4, y, p.mutedText, false)
            y += 14
        }
        return y
    }

    private fun summarize(spawns: JsonArray): EnvSummary {
        val out = EnvSummary()
        for (i in 0 until spawns.size()) {
            val conditions = spawns[i].asJsonObject.getAsJsonArray("conditions")?.strings().orEmpty()
            conditions.forEach { condition ->
                val key = condition.substringBefore(':').trim().lowercase()
                val raw = condition.substringAfter(':', "").trim()
                val values = splitValues(raw)
                when (key) {
                    "biome" -> out.biomes.addAll(values)
                    "dimension" -> out.dimensions.addAll(values)
                    "structure" -> out.structures.addAll(values)
                    "time" -> if (raw.isNotBlank()) out.times += raw
                    "weather" -> if (raw.isNotBlank()) out.weather += raw
                }
            }
        }
        return out
    }

    private fun renderChips(
        gui: GuiGraphics,
        font: Font,
        x: Int,
        startY: Int,
        width: Int,
        values: List<String>,
        theme: HubTheme,
        color: (String) -> Int
    ): Int {
        var cx = x
        var cy = startY
        values.forEach { raw ->
            val label = pretty(raw)
            val w = (font.width(label) + 14).coerceIn(34, width)
            if (cx != x && cx + w > x + width) {
                cx = x
                cy += 20
            }
            val fill = PixelUi.withAlpha(color(raw), 72)
            gui.fill(cx, cy, cx + w, cy + 16, fill)
            gui.fill(cx, cy, cx + 3, cy + 16, color(raw))
            gui.drawString(font, font.plainSubstrByWidth(label, w - 9), cx + 6, cy + 4, theme.palette.text, false)
            cx += w + 5
        }
        return cy + 19
    }

    private fun splitValues(raw: String): List<String> = raw
        .removePrefix("[")
        .removeSuffix("]")
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

    private fun isEnvironmentCondition(value: String): Boolean {
        val key = value.substringBefore(':').trim().lowercase()
        return key in setOf("biome", "dimension", "structure", "time", "weather")
    }

    private fun prettyCondition(value: String): String {
        val key = value.substringBefore(':').trim()
        val raw = value.substringAfter(':', "").trim()
        if (raw.isBlank()) return pretty(value)
        return "${pretty(key)}: ${splitValues(raw).joinToString(", ") { pretty(it) }}"
    }

    private fun pretty(value: String): String = value
        .removePrefix("#")
        .substringAfterLast(':')
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .split(' ')
        .filter(String::isNotBlank)
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    private fun environmentColor(raw: String): Int {
        val s = raw.lowercase()
        return when {
            "nether" in s || "crimson" in s || "warped" in s -> 0xFFD05B54.toInt()
            "end" in s -> 0xFF9B78C7.toInt()
            "ocean" in s || "river" in s || "water" in s || "beach" in s -> 0xFF4E96C7.toInt()
            "desert" in s || "badlands" in s || "savanna" in s -> 0xFFC89248.toInt()
            "snow" in s || "frozen" in s || "ice" in s || "taiga" in s -> 0xFF83BFC8.toInt()
            "forest" in s || "jungle" in s || "swamp" in s || "meadow" in s || "plains" in s -> 0xFF5F9B69.toInt()
            "cave" in s || "deep" in s || "stone" in s || "mountain" in s -> 0xFF777E86.toInt()
            else -> 0xFF6D9A8E.toInt()
        }
    }

    private fun bucketColor(bucket: String, theme: HubTheme): Int = when (bucket.lowercase().replace('_', '-')) {
        "common" -> 0xFF79A987.toInt()
        "uncommon" -> 0xFF5F9FD3.toInt()
        "rare" -> 0xFFC591D8.toInt()
        "ultra-rare" -> 0xFFE2BE62.toInt()
        else -> theme.palette.accent
    }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, value)

    private fun JsonObject.string(key: String, fallback: String = "") =
        runCatching { get(key)?.asString ?: fallback }.getOrDefault(fallback)

    private fun JsonArray.strings(): List<String> =
        (0 until size()).mapNotNull { index ->
            runCatching { get(index).asString }.getOrNull()?.takeIf(String::isNotBlank)
        }
}
