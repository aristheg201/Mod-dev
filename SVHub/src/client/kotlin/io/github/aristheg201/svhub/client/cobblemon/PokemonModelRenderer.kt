package io.github.aristheg201.svhub.client.cobblemon

import io.github.aristheg201.svhub.native.game.tft.PokemonAnimationResolver
import io.github.aristheg201.svhub.native.game.tft.PokemonAnimationSemantic

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
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import io.github.aristheg201.svhub.ui.SceneActorFit
import io.github.aristheg201.svhub.ui.SceneActorBounds
import io.github.aristheg201.svhub.ui.UiRect
import net.minecraft.client.Minecraft

/**
 * Live Cobblemon model renderer for SVHub.
 *
 * Uses Cobblemon's poser, texture, layers and animation state. HUD portraits use
 * the provider's profile pipeline; scene actors use the world-model origin and
 * SVHub's camera, transform and private render target.
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
    data class ProviderDiagnostics(val outcome:String,val model:String?,val poser:String?,val texture:String?,val layers:List<String>,val animationLabels:Set<String>,val reason:String?)

    /** Inspects the actual resolved client model. Unknown provider internals are reported, never guessed. */
    fun diagnostics(view:PokemonView):ProviderDiagnostics {
        val live=model(view)?:return ProviderDiagnostics("REJECTED",null,null,null,emptyList(),emptySet(),"species or renderable model could not be created")
        live.state.currentAspects=live.pokemon.aspects
        val poser=runCatching{VaryingModelRepository.getPoser(live.pokemon.species.resourceIdentifier,live.state)}.getOrNull()
            ?:return ProviderDiagnostics("FALLBACK",null,null,null,emptyList(),emptySet(),"provider returned no poser for effective aspects")
        live.state.currentModel=poser
        val labels=poser.poses.values.flatMap{pose->pose.animations.mapNotNull{animation->
            runCatching{animation.javaClass.methods.firstOrNull{it.parameterCount==0&&it.name in setOf("getName","getAnimation") }?.invoke(animation)?.toString()}.getOrNull()
        }}.filter(String::isNotBlank).toSortedSet()
        fun observable(vararg names:String):String?=names.firstNotNullOfOrNull{name->runCatching{poser.javaClass.methods.firstOrNull{it.parameterCount==0&&it.name.equals(name,true)}?.invoke(poser)?.toString()}.getOrNull()?.takeIf(String::isNotBlank)}
        val texture=observable("getTexture","texture");val layers=observable("getLayers","layers")?.let(::listOf).orEmpty()
        val reason=buildList{if(texture==null)add("provider poser API exposes no texture accessor");if(layers.isEmpty())add("provider poser API exposes no layer accessor");if(labels.isEmpty())add("provider poses expose no named animation labels")}.takeIf{it.isNotEmpty()}?.joinToString("; ")
        return ProviderDiagnostics(if(reason==null)"RESOLVED" else "UNOBSERVABLE",poser.javaClass.name,poser.javaClass.name,texture,layers,labels,reason)
    }
    data class AnimationPreviewResult(val outcome:String,val requested:String,val selected:String?,val available:Set<String>,val reason:String?)
    fun previewAnimation(view:PokemonView,instanceId:String,semantic:PokemonAnimationSemantic,serial:Long=System.nanoTime()):AnimationPreviewResult{
        val diagnostics=diagnostics(view)
        if(diagnostics.outcome=="REJECTED"||diagnostics.outcome=="FALLBACK")return AnimationPreviewResult(diagnostics.outcome,semantic.name,null,diagnostics.animationLabels,diagnostics.reason)
        val available=diagnostics.animationLabels
        if(available.isEmpty())return AnimationPreviewResult("UNOBSERVABLE",semantic.name,null,available,diagnostics.reason?:"provider exposes no animation labels")
        val selected=PokemonAnimationResolver.resolve(semantic,available).selectedLabel
            ?:return AnimationPreviewResult("FALLBACK",semantic.name,null,available,"no actual poser label maps to semantic")
        requestSceneAnimation(view,instanceId,"preview:${semantic.name.lowercase()}",serial,setOf(selected))
        return AnimationPreviewResult("RESOLVED",semantic.name,selected,available,null)
    }

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
        var scenePoser: PosableModel? = null
        val sceneContext = RenderContext()
        var sceneFit: SceneActorFit? = null
    }

    private val models = ConcurrentHashMap<ModelKey, LiveModel>()
    private val sceneModels = ConcurrentHashMap<SceneModelKey, LiveModel>()
    private val movePresentationCache = ConcurrentHashMap<String, MovePresentation>()
    private val sceneFits = ConcurrentHashMap<ModelKey, SceneActorFit>()
    private val previewBounds = ConcurrentHashMap<ModelKey, SceneActorBounds>()
    private val failedModels = ConcurrentHashMap.newKeySet<ModelKey>()
    private val resolvedPreviews = ConcurrentHashMap.newKeySet<String>()
    private val resolvedSceneInstances = ConcurrentHashMap.newKeySet<String>()
    private val logger = org.slf4j.LoggerFactory.getLogger("SVHub/PokemonModels")
    data class SceneSizingDiagnostic(val instanceId:String,val species:String,val aspects:List<String>,val fit:SceneActorFit)
    fun sceneSizingDiagnostics():List<SceneSizingDiagnostic> = sceneModels.mapNotNull { (key,live) ->
        live.sceneFit?.let { SceneSizingDiagnostic(key.instanceId,key.species,key.aspects,it) }
    }
    fun sceneHeight(view: PokemonView, instanceId: String): Float =
        sceneModels[SceneModelKey(instanceId,view.speciesId,view.aspects.sorted())]?.sceneFit?.height?.toFloat() ?: 1f

    /** Provider model/poser/layers only: no profile camera, GUI scale or host entity dispatcher. */
    fun renderEmbedded(view: PokemonView, instanceId: String, poses: PoseStack,
                       buffers: MultiBufferSource, moving: Boolean): Boolean {
        val key=key(view)
        val rendered = if (key in failedModels) false else try {
            renderEmbeddedModel(view,instanceId,poses,buffers,moving)
        } catch(failure:Exception) {
            if(failedModels.add(key)) logger.warn("Unable to render scene model {} with aspects {}",view.speciesId,view.aspects,failure)
            false
        }
        if (rendered) resolvedSceneInstances.add(instanceId) else resolvedSceneInstances.remove(instanceId)
        return rendered
    }

    /**
     * Premium card/skin preview. Use Cobblemon's profile pose pipeline instead of
     * the world scene poser so malformed custom aspects cannot surface as bind/T-pose.
     */
    fun renderPreview(gui:GuiGraphics,view:PokemonView,instanceId:String,rect:UiRect):Boolean {
        if(rect.width<8 || rect.height<8) {
            resolvedPreviews.remove(instanceId)
            return false
        }
        val size=minOf(rect.width,rect.height).coerceAtLeast(24)
        val rendered=render(
            gui=gui,
            view=view,
            centerX=rect.x+rect.width/2,
            centerY=rect.y+rect.height/2+size/5,
            size=size,
            yaw=165f,
            zoom=1.0f,
            pitch=10f
        )
        if(rendered) resolvedPreviews.add(instanceId) else resolvedPreviews.remove(instanceId)
        return rendered
    }

    fun previewResolved(instanceId:String):Boolean = instanceId in resolvedPreviews

    private fun renderEmbeddedModel(view: PokemonView, instanceId: String, poses: PoseStack,
                                   buffers: MultiBufferSource, moving: Boolean): Boolean {
        val live=sceneModel(SceneModelKey(instanceId,view.speciesId,view.aspects.sorted()),view) ?: return false
        val state=live.state
        val species=live.pokemon.species.resourceIdentifier
        state.currentAspects=live.pokemon.aspects
        val model=live.scenePoser ?: VaryingModelRepository.getPoser(species,state).also { live.scenePoser=it }
        state.currentModel=model
        val now=System.nanoTime()
        val delta=((now-live.lastRenderNanos).coerceAtLeast(0L)/50_000_000.0).toFloat().coerceIn(0f,1.5f)
        live.lastRenderNanos=now
        val context=live.sceneContext
        val baseScale=live.pokemon.form.baseScale
        context.put(RenderContext.SPECIES,species)
        context.put(RenderContext.ASPECTS,live.pokemon.aspects)
        context.put(RenderContext.SCALE,baseScale)
        context.put(RenderContext.TEXTURE,VaryingModelRepository.getTextureNoSubstitute(species,state))
        context.put(RenderContext.RENDER_STATE,RenderContext.RenderState.WORLD)
        context.put(RenderContext.POSABLE_STATE,state)
        context.put(RenderContext.DO_QUIRKS,true)
        model.context=context
        val scenePose = resolveScenePoseType(live,moving)
        val hasAuthoredPose = model.poses.values.any { pose ->
            runCatching { pose.isSuitable(state) }.getOrDefault(false) &&
                (pose.animations.isNotEmpty() || pose.transformedParts.isNotEmpty())
        }
        if (!hasAuthoredPose) {
            model.setDefault()
            return false
        }
        state.setPoseToFirstSuitable(scenePose)
        state.updatePartialTicks(delta)
        flushSceneAnimations(instanceId,live)
        model.applyAnimations(null,state,0f,0f,0f,0f,0f)
        val fit=live.sceneFit ?: sceneFits.getOrPut(key(view)) {
            val capture=SceneModelBounds()
            val measure=PoseStack()
            measure.mulPose(Axis.XP.rotationDegrees(90f))
            measure.scale(baseScale,-baseScale,-baseScale)
            measure.translate(0.0,-1.5,0.0)
            model.render(context,measure,capture,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,-1)
            ScenePresentationSizing.profile().fit(capture.bounds())
        }.also { live.sceneFit=it }
        poses.pushPose()
        try {
            poses.translate(fit.offset.x,fit.offset.y,fit.offset.z)
            poses.scale(fit.scale.toFloat(),fit.scale.toFloat(),fit.scale.toFloat())
            // Cobblemon model bones use Y down; scene ground is XY and scene Z is up.
            poses.mulPose(Axis.XP.rotationDegrees(90f))
            poses.scale(baseScale,-baseScale,-baseScale)
            // Living-model geometry uses a 24-pixel Y origin, as in the provider's
            // world renderer. Omitting this translation sinks small species below the floor.
            poses.translate(0.0,-1.5,0.0)
            val texture=VaryingModelRepository.getTexture(species,state)
            model.withLayerContext(buffers,state,VaryingModelRepository.getLayers(species,state)) {
                model.render(context,poses,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,-1)
            }
            return true
        } finally {
            model.setDefault()
            poses.popPose()
        }
    }

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
            selfClip = true,
            scenePose = false,
            moving = false
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
        depth: Double = 1000.0,
        moving: Boolean = false
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
            selfClip = false,
            scenePose = true,
            moving = moving
        )
        if (rendered) {
            resolvedSceneInstances.add(instanceId)
            flushSceneAnimations(instanceId, live)
        } else {
            resolvedSceneInstances.remove(instanceId)
        }
        return rendered
    }

    fun sceneResolved(instanceId:String):Boolean = instanceId in resolvedSceneInstances

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
        resolvedSceneInstances.retainAll(activeInstanceIds)
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
        selfClip: Boolean,
        scenePose: Boolean,
        moving: Boolean
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
            val poseType = if (scenePose) resolveScenePoseType(live, moving) else PoseType.PROFILE
            drawProfilePokemon(
                renderablePokemon = live.pokemon,
                matrixStack = pose,
                rotation = rotation,
                poseType = poseType,
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

    /**
     * Scene models must use world-style poses, not PROFILE. Many community/Fakemon
     * posers intentionally leave PROFILE sparse or static, which looks like a T-pose
     * when reused as a battlefield model. Prefer an authored moving/stationary pose
     * that actually contains pose animation or transforms.
     */
    private fun resolveScenePoseType(live: LiveModel, moving: Boolean): PoseType {
        live.state.currentAspects = live.pokemon.aspects
        val model = live.scenePoser ?: runCatching {
            VaryingModelRepository.getPoser(live.pokemon.species.resourceIdentifier, live.state)
        }.getOrNull()?.also { live.scenePoser=it }
        if (model != null) live.state.currentModel = model

        val suitable = model?.poses?.values.orEmpty().filter { pose ->
            runCatching { pose.isSuitable(live.state) }.getOrDefault(false)
        }
        val authored = suitable.filter { pose ->
            pose.animations.isNotEmpty() || pose.transformedParts.isNotEmpty()
        }
        val pool = authored.ifEmpty { suitable }
        val priorities = if (authored.isEmpty()) {
            listOf(PoseType.PROFILE, PoseType.PORTRAIT, PoseType.STAND, PoseType.HOVER, PoseType.FLOAT, PoseType.WALK, PoseType.FLY, PoseType.SWIM)
        } else if (moving) {
            listOf(PoseType.WALK, PoseType.FLY, PoseType.SWIM, PoseType.HOVER, PoseType.FLOAT, PoseType.STAND, PoseType.PROFILE, PoseType.PORTRAIT)
        } else {
            listOf(PoseType.STAND, PoseType.HOVER, PoseType.FLOAT, PoseType.FLY, PoseType.SWIM, PoseType.WALK, PoseType.PROFILE, PoseType.PORTRAIT)
        }
        return priorities.firstOrNull { type -> pool.any { type in it.poseTypes } }
            ?: pool.firstOrNull()?.poseTypes?.firstOrNull()
            ?: if (moving) PoseType.WALK else PoseType.STAND
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
                runCatching { model.getFaintAnimation(live.state) }.getOrNull()
                    ?: runCatching { model.getAnimation(live.state, "faint", live.state.runtime) }.getOrNull()
                    ?: runCatching { model.getAnimation(live.state, "recoil", live.state.runtime) }.getOrNull()
            } else {
                labels.firstNotNullOfOrNull { label ->
                    runCatching { model.getAnimation(live.state, label, live.state.runtime) }.getOrNull()
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

    fun canonicalSpeciesPath(value:String):String =
        value.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    fun resolveSpecies(rawId:String): com.cobblemon.mod.common.pokemon.Species? {
        val id=ResourceLocation.tryParse(rawId) ?: return null
        PokemonSpecies.getByIdentifier(id)?.let { return it }

        val canonical=canonicalSpeciesPath(id.path)
        if(canonical.isBlank()) return null
        val matches=PokemonSpecies.species.asSequence()
            .filter { it.resourceIdentifier.namespace==id.namespace }
            .filter { canonicalSpeciesPath(it.resourceIdentifier.path)==canonical }
            .take(2)
            .toList()
        if(matches.size!=1) {
            if(matches.isEmpty()) logger.warn("Unknown Pokemon species id {}",rawId)
            else logger.warn("Ambiguous Pokemon species alias {} -> {}",rawId,matches.map{it.resourceIdentifier})
            return null
        }
        val resolved=matches.single()
        logger.info("Resolved Pokemon species alias {} -> {}",rawId,resolved.resourceIdentifier)
        return resolved
    }

    private fun model(view: PokemonView): LiveModel? {
        val key = key(view)
        models[key]?.let { return it }
        val species = resolveSpecies(view.speciesId) ?: return null
        val created = LiveModel(RenderablePokemon(species, view.aspects.toSet()))
        return models.putIfAbsent(key, created) ?: created
    }

    private fun sceneModel(key: SceneModelKey, view: PokemonView): LiveModel? {
        sceneModels[key]?.let { return it }
        val species = resolveSpecies(view.speciesId) ?: return null
        val created = LiveModel(RenderablePokemon(species, view.aspects.toSet()))
        return sceneModels.putIfAbsent(key, created) ?: created
    }

    private fun key(view: PokemonView) = ModelKey(view.speciesId, view.aspects.sorted())
    private fun degreesToRadians(value: Float): Float = (value * PI / 180.0).toFloat()
    fun clear() { models.clear(); sceneModels.clear(); movePresentationCache.clear(); sceneFits.clear(); previewBounds.clear(); failedModels.clear(); resolvedPreviews.clear(); resolvedSceneInstances.clear(); ScenePresentationSizing.clear() }
}
