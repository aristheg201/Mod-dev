package io.github.aristheg201.svhub.client.cobblemon

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI

/**
 * Live Cobblemon model renderer for SVHub.
 *
 * This deliberately uses Cobblemon's profile-model pipeline instead of rendering
 * PokemonItem stacks. VaryingModelRepository, poser, texture/layers, PROFILE pose
 * and animation application are all driven by Cobblemon through drawProfilePokemon.
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
        zoom: Float = 1f
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

        // Mirrors Cobblemon's own Pokédex portrait transform: the profile renderer
        // normalizes each species using its model profileScale/profileTranslation.
        pose.translate(centerX.toDouble(), centerY - safeSize * 0.43, 1000.0)
        val guiScale = 2.0f * (safeSize / 140.0f) * safeZoom
        pose.scale(guiScale, guiScale, guiScale)

        val rotation = Quaternionf().rotationXYZ(
            degreesToRadians(13f),
            degreesToRadians(yaw),
            0f
        )

        runCatching {
            drawProfilePokemon(
                renderablePokemon = live.pokemon,
                matrixStack = pose,
                rotation = rotation,
                state = live.state,
                partialTicks = deltaTicks,
                blockLight = 15
            )
        }.onFailure {
            pose.popPose()
            gui.disableScissor()
            models.remove(key(view), live)
            return false
        }

        pose.popPose()
        gui.disableScissor()
        return true
    }

    private fun model(view: PokemonView): LiveModel? {
        val key = key(view)
        return models.computeIfAbsent(key) {
            val id = ResourceLocation.tryParse(view.speciesId) ?: return@computeIfAbsent null
            val species = PokemonSpecies.getByIdentifier(id) ?: return@computeIfAbsent null
            LiveModel(RenderablePokemon(species, view.aspects.toSet()))
        }
    }

    private fun key(view: PokemonView) = ModelKey(view.speciesId, view.aspects.sorted())

    private fun degreesToRadians(value: Float): Float = (value * PI / 180.0).toFloat()

    /** Must be called when Cobblemon/resource-pack model resources are reloaded. */
    fun clear() = models.clear()
}
