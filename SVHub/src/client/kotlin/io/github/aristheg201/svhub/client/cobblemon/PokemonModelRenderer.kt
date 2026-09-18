package io.github.aristheg201.svhub.client.cobblemon

import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.AnimationActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.EntityParticlesActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.ForkActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.ParallelActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.RunActionEffectKeyframe
import com.cobblemon.mod.common.api.moves.animations.keyframes.SequenceActionEffectKeyframe
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.animation.ActiveAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockParticleKeyframe
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
data class CobblemonSceneParticleCue(
    val sourceEntityId: String,
    val targetEntityId: String?,
    val effectId: String,
    val startedAtMs: Long,
    val delayMs: Long = 0L,
    val lifetimeMs: Long = 520L
)

object PokemonModelRenderer {
    private data class ModelKey(val species: String, val aspects: List<String>)
    private data class SceneModelKey(val instanceId: String, val species: String, val aspects: List<String>)

    private data class SceneAnimationRequest(
        val signalId: String,
        val serial: Long,
        val labels: LinkedHashSet<String>,
        val faint: Boolean,
        val targetEntityId: String?,
        val moveId: String?
    )

    private data class MovePresentation(
        val animationLabels: LinkedHashSet<String> = linkedSetOf(),
        val particleEffects: List<String> = emptyList()
    )

    private class LiveModel(val pokemon: RenderablePokemon) {
        val state = FloatingState()
        val seenSceneSignals = linkedMapOf<String, Long>()
        val pendingSceneAnimations = ArrayDeque<SceneAnimationRequest>()
        val nativeParticleCues = ArrayDeque<CobblemonSceneParticleCue>()
        var lastRenderNanos: Long = System.nanoTime()
    }

    private val models = ConcurrentHashMap<ModelKey, LiveModel>()
    private val sceneModels = ConcurrentHashMap<SceneModelKey, LiveModel>()
    private val movePresentationCache = ConcurrentHashMap<String, MovePresentation>()

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
        return renderInternal(
            gui = gui,
            live = live,
            removal = { models.remove(key(view), live) },
            centerX = centerX,
            centerY = centerY,
            size = size,
            yaw = yaw,
            zoom = zoom,
            pitch = pitch,
            depth = 1000.0,
            selfClip = true
        )
    }

    /**
     * Scene render path. The caller owns the shared scene scissor and depth order.
     * Each logical entity gets an independent FloatingState even when several
     * entities use the same species/aspects.
     */
    fun renderScene(
        gui: GuiGraphics,
        view: PokemonView,
        instanceId: String,
        centerX: Int,
        centerY: Int,
        size: Int,
        yaw: Float = 0f,
        zoom: Float = 1f,
        pitch: Float = 28f,
        depth: Double = 1000.0
    ): Boolean {
        val sceneKey = SceneModelKey(instanceId, view.speciesId, view.aspects.sorted())
        val live = sceneModel(sceneKey, view) ?: return false
        val rendered = renderInternal(
            gui = gui,
            live = live,
            removal = { sceneModels.remove(sceneKey, live) },
            centerX = centerX,
            centerY = centerY,
            size = size,
            yaw = yaw,
            zoom = zoom,
            pitch = pitch,
            depth = depth,
            selfClip = false
        )
        if (rendered) flushSceneAnimations(instanceId, live)
        return rendered
    }

    fun requestSceneAnimation(
        view: PokemonView,
        instanceId: String,
        signalId: String,
        serial: Long,
        labels: Set<String>,
        targetEntityId: String? = null,
        moveId: String? = null,
        faint: Boolean = false
    ) {
        if (serial <= 0L || signalId.isBlank()) return
        val key = SceneModelKey(instanceId, view.speciesId, view.aspects.sorted())
        val live = sceneModel(key, view) ?: return
        if (live.seenSceneSignals[signalId] == serial) return
        live.seenSceneSignals[signalId] = serial
        while (live.seenSceneSignals.size > 64) {
            val first = live.seenSceneSignals.entries.firstOrNull()?.key ?: break
            live.seenSceneSignals.remove(first)
        }
        live.pendingSceneAnimations.addLast(
            SceneAnimationRequest(
                signalId = signalId,
                serial = serial,
                labels = LinkedHashSet(labels.filter(String::isNotBlank)),
                faint = faint,
                targetEntityId = targetEntityId,
                moveId = moveId?.takeIf(String::isNotBlank)
            )
        )
        while (live.pendingSceneAnimations.size > 8) live.pendingSceneAnimations.removeFirst()
    }

    fun drainSceneParticleCues(activeInstanceIds: Set<String>): List<CobblemonSceneParticleCue> {
        val cues = mutableListOf<CobblemonSceneParticleCue>()
        sceneModels.forEach { (key, live) ->
            if (key.instanceId !in activeInstanceIds) return@forEach
            while (live.nativeParticleCues.isNotEmpty()) cues += live.nativeParticleCues.removeFirst()
        }
        return cues
    }

    fun pruneScene(activeInstanceIds: Set<String>) {
        if (sceneModels.size <= activeInstanceIds.size + 32) return
        sceneModels.keys.removeIf { it.instanceId !in activeInstanceIds }
    }

    private fun renderInternal(
        gui: GuiGraphics,
        live: LiveModel,
        removal: () -> Unit,
        centerX: Int,
        centerY: Int,
        size: Int,
        yaw: Float,
        zoom: Float,
        pitch: Float,
        depth: Double,
        selfClip: Boolean
    ): Boolean {
        val safeSize = size.coerceIn(28, 512)
        val safeZoom = zoom.coerceIn(0.45f, 2.25f)
        val now = System.nanoTime()
        val deltaTicks = ((now - live.lastRenderNanos).coerceAtLeast(0L) / 50_000_000.0)
            .toFloat()
            .coerceIn(0f, 1.5f)
        live.lastRenderNanos = now

        val pose = gui.pose()
        pose.pushPose()
        if (selfClip) {
            gui.enableScissor(
                centerX - safeSize / 2,
                centerY - safeSize / 2,
                centerX + safeSize / 2,
                centerY + safeSize / 2
            )
        }

        pose.translate(centerX.toDouble(), centerY - safeSize * 0.43, depth)
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
            removal()
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            pose.popPose()
            if (selfClip) gui.disableScissor()
        }
        return rendered
    }

    private fun flushSceneAnimations(instanceId: String, live: LiveModel) {
        val model = live.state.currentModel ?: return
        while (live.pendingSceneAnimations.isNotEmpty()) {
            val request = live.pendingSceneAnimations.removeFirst()
            val movePresentation = request.moveId?.let(::resolveMovePresentation) ?: MovePresentation()
            val labels = linkedSetOf<String>().apply {
                addAll(movePresentation.animationLabels)
                addAll(request.labels)
                if (!request.faint && isEmpty()) add("physical")
            }

            val animation = if (request.faint) {
                model.getFaintAnimation(live.state)
                    ?: model.getAnimation(live.state, "faint", live.state.runtime)
                    ?: model.getAnimation(live.state, "recoil", live.state.runtime)
            } else {
                labels.firstNotNullOfOrNull { label ->
                    model.getAnimation(live.state, label, live.state.runtime)
                }
            }

            if (animation != null) {
                if (animation is PrimaryAnimation) live.state.addPrimaryAnimation(animation)
                else if (request.faint) live.state.addPrimaryAnimation(PrimaryAnimation(animation))
                else live.state.addActiveAnimation(animation)

                collectAnimationParticles(animation).forEach { (effectId, delayMs) ->
                    live.nativeParticleCues += CobblemonSceneParticleCue(
                        sourceEntityId = instanceId,
                        targetEntityId = request.targetEntityId,
                        effectId = effectId,
                        startedAtMs = System.currentTimeMillis(),
                        delayMs = delayMs
                    )
                }
            }

            movePresentation.particleEffects.forEachIndexed { index, effectId ->
                live.nativeParticleCues += CobblemonSceneParticleCue(
                    sourceEntityId = instanceId,
                    targetEntityId = request.targetEntityId,
                    effectId = effectId,
                    startedAtMs = System.currentTimeMillis(),
                    delayMs = index * 45L,
                    lifetimeMs = 620L
                )
            }
            while (live.nativeParticleCues.size > 24) live.nativeParticleCues.removeFirst()
        }
    }

    private fun collectAnimationParticles(animation: ActiveAnimation): List<Pair<String, Long>> = when (animation) {
        is PrimaryAnimation -> collectAnimationParticles(animation.animation)
        is BedrockActiveAnimation -> animation.animation.effects
            .filterIsInstance<BedrockParticleKeyframe>()
            .map { keyframe -> keyframe.effect.id.toString() to (keyframe.seconds * 1_000f).toLong().coerceAtLeast(0L) }
        else -> emptyList()
    }

    private fun resolveMovePresentation(rawMoveId: String): MovePresentation {
        val parsed = ResourceLocation.tryParse(rawMoveId)
        val namespace = parsed?.namespace ?: "cobblemon"
        val rawPath = parsed?.path ?: rawMoveId.substringAfter(':')
        val normalized = rawPath.lowercase().filter(Char::isLetterOrDigit)
        if (normalized.isBlank()) return MovePresentation()
        val cacheKey = "$namespace:$normalized"
        return movePresentationCache.computeIfAbsent(cacheKey) {
            val labels = linkedSetOf<String>()
            val particles = linkedSetOf<String>()
            val candidates = listOfNotNull(
                ResourceLocation.tryParse("$namespace:moves/$normalized"),
                ResourceLocation.tryParse("$namespace:$normalized"),
                ResourceLocation.tryParse("cobblemon:moves/$normalized"),
                ResourceLocation.tryParse("cobblemon:moves/generic_move")
            ).distinct()
            candidates.firstNotNullOfOrNull(ActionEffects.actionEffects::get)?.let { timeline ->
                collectActionEffect(timeline, labels, particles, linkedSetOf(), 0)
            }
            MovePresentation(labels, particles.take(12))
        }
    }

    private fun collectActionEffect(
        timeline: ActionEffectTimeline,
        labels: LinkedHashSet<String>,
        particles: LinkedHashSet<String>,
        visited: MutableSet<String>,
        depth: Int
    ) {
        if (depth > 6) return
        timeline.timeline.forEach { frame -> collectActionFrame(frame, labels, particles, visited, depth) }
    }

    private fun collectActionFrame(
        frame: ActionEffectKeyframe,
        labels: LinkedHashSet<String>,
        particles: LinkedHashSet<String>,
        visited: MutableSet<String>,
        depth: Int
    ) {
        if (depth > 6) return
        when (frame) {
            is AnimationActionEffectKeyframe -> frame.animation.filter(String::isNotBlank).forEach(labels::add)
            is EntityParticlesActionEffectKeyframe -> {
                val raw = frame.effect?.trim()?.removeSurrounding("\"")?.removeSurrounding("'").orEmpty()
                if (raw.matches(Regex("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$"))) {
                    particles += if (':' in raw) raw else "cobblemon:$raw"
                }
            }
            is SequenceActionEffectKeyframe -> frame.keyframes.forEach { collectActionFrame(it, labels, particles, visited, depth + 1) }
            is ParallelActionEffectKeyframe -> frame.keyframes.forEach { collectActionFrame(it, labels, particles, visited, depth + 1) }
            is ForkActionEffectKeyframe -> {
                frame.ifTrue.forEach { collectActionFrame(it, labels, particles, visited, depth + 1) }
                frame.ifFalse.forEach { collectActionFrame(it, labels, particles, visited, depth + 1) }
            }
            is RunActionEffectKeyframe -> {
                val id = frame.actionEffect ?: return
                val key = id.toString()
                if (visited.add(key)) {
                    ActionEffects.actionEffects[id]?.let { nested -> collectActionEffect(nested, labels, particles, visited, depth + 1) }
                }
            }
        }
    }

    private fun model(view: PokemonView): LiveModel? {
        val key = key(view)
        models[key]?.let { return it }
        val id = ResourceLocation.tryParse(view.speciesId) ?: return null
        val species = PokemonSpecies.getByIdentifier(id) ?: return null
        val created = LiveModel(RenderablePokemon(species, view.aspects.toSet()))
        return models.putIfAbsent(key, created) ?: created
    }

    private fun sceneModel(key: SceneModelKey, view: PokemonView): LiveModel? {
        sceneModels[key]?.let { return it }
        val id = ResourceLocation.tryParse(view.speciesId) ?: return null
        val species = PokemonSpecies.getByIdentifier(id) ?: return null
        val created = LiveModel(RenderablePokemon(species, view.aspects.toSet()))
        return sceneModels.putIfAbsent(key, created) ?: created
    }

    private fun key(view: PokemonView) = ModelKey(view.speciesId, view.aspects.sorted())
    private fun degreesToRadians(value: Float): Float = (value * PI / 180.0).toFloat()
    fun clear() { models.clear(); sceneModels.clear(); movePresentationCache.clear() }
}
