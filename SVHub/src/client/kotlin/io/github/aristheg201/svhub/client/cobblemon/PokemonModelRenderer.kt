package io.github.aristheg201.svhub.client.cobblemon

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI

/**
 * Live Cobblemon model renderer for SVHub.
 *
 * Uses Cobblemon's actual profile-model pipeline, including current resource-pack
 * poser, texture, layers and profile transforms. The renderer never snapshots the
 * model through an auxiliary framebuffer, so it cannot poison the main GUI target.
 */
object PokemonModelRenderer {
    private data class ModelKey(val species: String, val aspects: List<String>)

    private class LiveModel(val pokemon: RenderablePokemon) {
        val state = FloatingState()
        var lastRenderNanos: Long = System.nanoTime()
    }

    private val models = ConcurrentHashMap<ModelKey, LiveModel>()

    fun render(
        gui: GuiGraphics,
        view: PokemonView,
        centerX: Int,
        centerY: Int,
        size: Int,
        yaw: Float = 0f,
        zoom: Float = 1f,
        pitch: Float = 13f
    ): Boolean {
        val live = model(view) ?: return false
        val safeSize = size.coerceIn(40, 512)
        val safeZoom = zoom.coerceIn(0.55f, 2.25f)
        val now = System.nanoTime()
        val deltaTicks = ((now - live.lastRenderNanos).coerceAtLeast(0L) / 50_000_000.0)
            .toFloat()
            .coerceIn(0f, 1.5f)
        live.lastRenderNanos = now

        val pose = gui.pose()
        pose.pushPose()
        gui.enableScissor(
            centerX - safeSize / 2,
            centerY - safeSize / 2,
            centerX + safeSize / 2,
            centerY + safeSize / 2
        )

        pose.translate(centerX.toDouble(), centerY - safeSize * 0.43, 1000.0)
        val guiScale = 2.0f * (safeSize / 140.0f) * safeZoom
        pose.scale(guiScale, guiScale, guiScale)

        val rotation = Quaternionf().rotationXYZ(
            degreesToRadians(pitch.coerceIn(-85f, 85f)),
            degreesToRadians(yaw),
            0f
        )

        var rendered = false
        try {
            drawProfilePokemon(
                renderablePokemon = live.pokemon,
                matrixStack = pose,
                rotation = rotation,
                state = live.state,
                partialTicks = deltaTicks,
                blockLight = 15
            )
            rendered = true
        } catch (_: Throwable) {
            models.remove(key(view), live)
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            pose.popPose()
            gui.disableScissor()
        }
        return rendered
    }

    private fun model(view: PokemonView): LiveModel? {
        val key = key(view)
        models[key]?.let { return it }
        val id = ResourceLocation.tryParse(view.speciesId) ?: return null
        val species = PokemonSpecies.getByIdentifier(id) ?: return null
        val created = LiveModel(RenderablePokemon(species, view.aspects.toSet()))
        return models.putIfAbsent(key, created) ?: created
    }

    private fun key(view: PokemonView) = ModelKey(view.speciesId, view.aspects.sorted())
    private fun degreesToRadians(value: Float): Float = (value * PI / 180.0).toFloat()
    fun clear() = models.clear()
}
