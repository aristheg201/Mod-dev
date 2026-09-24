package io.github.aristheg201.svarcade.client.render

import io.github.aristheg201.svarcade.content.HubAsset
import net.minecraft.client.gui.GuiGraphics
import java.util.LinkedHashMap
import java.util.Random
import kotlin.math.max

/**
 * Deterministic renderer for generated pixel-background assets.
 *
 * Generator recipes are persisted in HubAsset.generator and the expensive static
 * geometry is generated once per recipe/size/palette combination. Only the
 * lightweight ambient overlay remains animated per frame in PixelUi.
 */
object GeneratedBackgroundRenderer {
    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int, val color: Int)
    private data class CacheKey(
        val assetId: String,
        val preset: String,
        val seed: Long,
        val density: Int,
        val width: Int,
        val height: Int,
        val baseColor: Int,
        val accentColor: Int
    )

    private val cache = object : LinkedHashMap<CacheKey, List<Rect>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, List<Rect>>?): Boolean = size > MAX_CACHE_ENTRIES
    }

    fun render(
        gui: GuiGraphics,
        asset: HubAsset,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        baseColor: Int,
        accentColor: Int
    ): Boolean {
        if (asset.source != "generated" || asset.type != "background" || width <= 0 || height <= 0) return false
        val generator = asset.generator
        val preset = runCatching { generator.get("preset")?.asString }.getOrNull()?.takeIf { it.isNotBlank() } ?: "pixel_sky"
        val seed = runCatching { generator.get("seed")?.asLong }.getOrNull() ?: stableSeed(asset.id, preset)
        val density = (runCatching { generator.get("density")?.asInt }.getOrNull() ?: 2).coerceIn(1, 4)
        val key = CacheKey(asset.id, preset, seed, density, width, height, baseColor, accentColor)
        val geometry = synchronized(cache) {
            cache[key] ?: generate(key).also { cache[key] = it }
        }
        geometry.forEach { rect ->
            gui.fill(x + rect.x, y + rect.y, x + rect.x + rect.width, y + rect.y + rect.height, rect.color)
        }
        return true
    }

    fun clear() = synchronized(cache) { cache.clear() }

    fun cachedSurfaceCount(): Int = synchronized(cache) { cache.size }

    private fun generate(key: CacheKey): List<Rect> {
        val out = ArrayList<Rect>(128)
        val random = Random(key.seed)
        when (key.preset) {
            "pixel_grid" -> grid(out, key, false)
            "pixel_neon" -> grid(out, key, true)
            "pixel_forest" -> forest(out, key, random)
            "pixel_cave" -> cave(out, key, random)
            "pixel_volcano" -> volcano(out, key, random)
            else -> sky(out, key, random)
        }
        return out
    }

    private fun grid(out: MutableList<Rect>, key: CacheKey, neon: Boolean) {
        val step = max(12, 30 - key.density * 4)
        val alpha = if (neon) 80 else 42
        var x = 0
        while (x < key.width) {
            out += Rect(x, 0, 1, key.height, withAlpha(key.accentColor, alpha))
            x += step
        }
        var y = 0
        while (y < key.height) {
            out += Rect(0, y, key.width, 1, withAlpha(key.accentColor, alpha))
            y += step
        }
        if (neon) {
            val block = max(4, step / 4)
            repeat(8 * key.density) { i ->
                val px = (i * 73L + key.seed).floorMod(max(1, key.width - block)).toInt()
                val py = (i * 41L + key.seed / 7L).floorMod(max(1, key.height - block)).toInt()
                out += Rect(px, py, block, block, withAlpha(key.accentColor, 34 + (i % 3) * 14))
            }
        }
    }

    private fun sky(out: MutableList<Rect>, key: CacheKey, random: Random) {
        val stars = 24 * key.density
        repeat(stars) { index ->
            val size = if (index % 9 == 0) 3 else if (index % 4 == 0) 2 else 1
            out += Rect(
                random.nextInt(max(1, key.width - size)),
                random.nextInt(max(1, key.height * 3 / 4)),
                size,
                size,
                withAlpha(key.accentColor, 45 + random.nextInt(70))
            )
        }
        repeat(2 + key.density) {
            val cloudW = 28 + random.nextInt(35)
            val cloudH = 5 + random.nextInt(7)
            val cx = random.nextInt(max(1, key.width - cloudW))
            val cy = 14 + random.nextInt(max(1, key.height / 2))
            out += Rect(cx, cy, cloudW, cloudH, withAlpha(key.baseColor, 52))
            out += Rect(cx + cloudW / 5, cy - 4, cloudW / 2, 5, withAlpha(key.baseColor, 46))
        }
    }

    private fun forest(out: MutableList<Rect>, key: CacheKey, random: Random) {
        val ground = key.height * 3 / 4
        out += Rect(0, ground, key.width, key.height - ground, withAlpha(key.baseColor, 80))
        val spacing = max(18, 40 - key.density * 6)
        var x = -spacing / 2
        while (x < key.width + spacing) {
            val trunkW = 4 + random.nextInt(4)
            val trunkH = 20 + random.nextInt(max(4, key.height / 4))
            val crownW = 20 + random.nextInt(18)
            out += Rect(x, ground - trunkH, trunkW, trunkH, withAlpha(key.baseColor, 105))
            out += Rect(x - crownW / 2, ground - trunkH - 7, crownW, 9, withAlpha(key.accentColor, 48))
            out += Rect(x - crownW / 3, ground - trunkH - 14, crownW * 2 / 3, 8, withAlpha(key.accentColor, 38))
            x += spacing
        }
    }

    private fun cave(out: MutableList<Rect>, key: CacheKey, random: Random) {
        val segments = 10 + key.density * 5
        val segmentW = max(8, key.width / segments)
        var x = 0
        while (x < key.width) {
            val topH = 8 + random.nextInt(max(4, key.height / 5))
            val bottomH = 8 + random.nextInt(max(4, key.height / 6))
            out += Rect(x, 0, segmentW + 1, topH, withAlpha(key.baseColor, 72))
            out += Rect(x, key.height - bottomH, segmentW + 1, bottomH, withAlpha(key.baseColor, 72))
            if (random.nextBoolean()) out += Rect(x + segmentW / 2, topH, 2, random.nextInt(10) + 3, withAlpha(key.accentColor, 35))
            x += segmentW
        }
    }

    private fun volcano(out: MutableList<Rect>, key: CacheKey, random: Random) {
        val baseY = key.height - max(12, key.height / 10)
        val mountains = 5 + key.density * 2
        repeat(mountains) { i ->
            val center = key.width * (i + 1) / (mountains + 1)
            val halfWidth = 18 + random.nextInt(22)
            val height = 24 + random.nextInt(max(8, key.height / 3))
            val bands = max(4, height / 7)
            repeat(bands) { band ->
                val progress = band.toFloat() / bands
                val bw = max(3, (halfWidth * (1f - progress)).toInt())
                val by = baseY - band * 7
                out += Rect(center - bw, by, bw * 2, 7, withAlpha(key.baseColor, 65 + (band % 3) * 8))
            }
        }
        repeat(6 * key.density) {
            val lx = random.nextInt(max(1, key.width))
            val lh = 3 + random.nextInt(10)
            out += Rect(lx, baseY - lh, 2, lh, withAlpha(0xFFFF8A35.toInt(), 88))
        }
    }

    private fun stableSeed(assetId: String, preset: String): Long {
        var hash = 1125899906842597L
        (assetId + ':' + preset).forEach { hash = 31L * hash + it.code }
        return hash
    }

    private fun Long.floorMod(divisor: Int): Long {
        if (divisor <= 0) return 0L
        val remainder = this % divisor
        return if (remainder < 0) remainder + divisor else remainder
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private const val MAX_CACHE_ENTRIES = 64
}
