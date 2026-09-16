package io.github.aristheg201.svhub.client.cobblemon

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.joml.Quaternionf
import java.util.ArrayDeque
import java.util.LinkedHashMap

/**
 * Bounded client-only portrait cache for Pokémon/Fakemon list cards.
 *
 * Missing portraits are queued by visible cards. [pump] materializes at most one
 * model-derived portrait per rendered frame, so grids never render dozens of live
 * Cobblemon models at once. All GL work stays on the client render thread.
 */
object PokemonPortraitCache {
    private data class Key(val species: String, val aspects: List<String>)
    private data class Entry(val location: ResourceLocation)

    private val cache = object : LinkedHashMap<Key, Entry>(32, 0.75f, true) {}
    private val queued = HashSet<Key>()
    private val queue = ArrayDeque<Pair<Key, PokemonView>>()
    private val failedUntil = HashMap<Key, Long>()
    private var target: TextureTarget? = null
    private var lastPumpFrame = Long.MIN_VALUE

    /** Returns a cached texture or queues this visible Pokémon for generation. */
    fun request(view: PokemonView): ResourceLocation? {
        val key = key(view)
        cache[key]?.let { return it.location }
        val now = System.currentTimeMillis()
        if ((failedUntil[key] ?: 0L) > now) return null
        if (queued.add(key)) queue.addLast(key to view)
        return null
    }

    /** Call once from screen rendering; hard-budgeted to one generation per frame. */
    fun pump(frameId: Long) {
        if (frameId == lastPumpFrame) return
        lastPumpFrame = frameId
        val next = queue.removeFirstOrNull() ?: return
        queued.remove(next.first)
        val location = runCatching { generate(next.second) }.getOrNull()
        if (location == null) {
            failedUntil[next.first] = System.currentTimeMillis() + FAILURE_BACKOFF_MS
            return
        }
        cache[next.first] = Entry(location)
        trim()
    }

    fun clear() {
        val minecraft = Minecraft.getInstance()
        cache.values.forEach { runCatching { minecraft.textureManager.release(it.location) } }
        cache.clear()
        queued.clear()
        queue.clear()
        failedUntil.clear()
        target?.let { runCatching { it.destroyBuffers() } }
        target = null
        lastPumpFrame = Long.MIN_VALUE
    }

    fun cachedCount(): Int = cache.size
    fun queuedCount(): Int = queue.size

    private fun generate(view: PokemonView): ResourceLocation? {
        RenderSystem.assertOnRenderThread()
        val speciesId = ResourceLocation.tryParse(view.speciesId) ?: return null
        val species = PokemonSpecies.getByIdentifier(speciesId) ?: return null
        val minecraft = Minecraft.getInstance()
        val renderTarget = ensureTarget()
        val mainTarget = minecraft.mainRenderTarget

        renderTarget.setClearColor(0f, 0f, 0f, 0f)
        renderTarget.bindWrite(true)
        renderTarget.clear(false)

        RenderSystem.backupProjectionMatrix()
        return try {
            RenderSystem.setProjectionMatrix(
                Matrix4f().setOrtho(0f, PORTRAIT_SIZE.toFloat(), PORTRAIT_SIZE.toFloat(), 0f, 1000f, 3000f),
                RenderSystem.getVertexSorting()
            )

            val pose = PoseStack()
            pose.translate(PORTRAIT_SIZE / 2.0, PORTRAIT_SIZE * 0.07, 1000.0)
            val guiScale = 2.0f * (PORTRAIT_SIZE / 140.0f)
            pose.scale(guiScale, guiScale, guiScale)

            drawProfilePokemon(
                renderablePokemon = RenderablePokemon(species, view.aspects.toSet()),
                matrixStack = pose,
                rotation = Quaternionf().rotationXYZ(radians(13f), 0f, 0f),
                state = FloatingState(),
                partialTicks = 0f,
                blockLight = 15
            )

            val image = Screenshot.takeScreenshot(renderTarget)
            val texture = DynamicTexture(image)
            minecraft.textureManager.register("svhub_portrait", texture)
        } finally {
            RenderSystem.restoreProjectionMatrix()
            mainTarget.bindWrite(true)
        }
    }

    private fun ensureTarget(): TextureTarget {
        val existing = target
        if (existing != null) return existing
        return TextureTarget(PORTRAIT_SIZE, PORTRAIT_SIZE, true, false).also { target = it }
    }

    private fun trim() {
        val minecraft = Minecraft.getInstance()
        while (cache.size > MAX_PORTRAITS) {
            val iterator = cache.entries.iterator()
            if (!iterator.hasNext()) return
            val eldest = iterator.next()
            iterator.remove()
            runCatching { minecraft.textureManager.release(eldest.value.location) }
        }
    }

    private fun key(view: PokemonView) = Key(view.speciesId, view.aspects.sorted())
    private fun radians(degrees: Float): Float = (degrees * Math.PI / 180.0).toFloat()

    private const val PORTRAIT_SIZE = 96
    private const val MAX_PORTRAITS = 128
    private const val FAILURE_BACKOFF_MS = 10_000L
}
