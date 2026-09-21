package io.github.aristheg201.svhub.client.nativeui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository
import com.cobblemon.mod.common.util.getString
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import io.github.aristheg201.svhub.client.cobblemon.CobblemonSceneParticleCue
import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.ui.SceneCameraPreset
import io.github.aristheg201.svhub.ui.SceneCameras
import io.github.aristheg201.svhub.ui.SceneProjection
import io.github.aristheg201.svhub.ui.UiRect
import io.github.aristheg201.svhub.ui.SceneTransform
import io.github.aristheg201.svhub.ui.SceneVec3
import io.github.aristheg201.svhub.ui.SceneItemModelNode
import io.github.aristheg201.svhub.ui.SceneTacticianNode
import io.github.aristheg201.svhub.ui.SceneEffectNode
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import io.github.aristheg201.svhub.ui.PerspectiveBoardTransform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ScenePoint(val x: Float, val y: Float)

/** Extra physical platforms (for example a bench) share the arena projection. */
data class ScenePlatform(val x: Float, val y: Float, val selected: Boolean = false, val hovered: Boolean = false)

enum class SceneEffectKind { PROJECTILE, CAST, HIT, HEAL, BURST }

enum class SceneNativeAnimationKind { PHYSICAL, SPECIAL, STATUS, RECOIL, FAINT, CRY }

data class SceneNativeAnimationSignal(
    val id: String,
    val serial: Long,
    val entityId: String,
    val kind: SceneNativeAnimationKind,
    val targetEntityId: String? = null,
    val moveId: String? = null
)

data class SceneEffectSignal(
    val id: String,
    val serial: Long,
    val kind: SceneEffectKind,
    val sourceEntityId: String,
    val targetEntityId: String? = null
)

internal data class ActiveSceneEffect(
    val kind: SceneEffectKind,
    val sourceEntityId: String,
    val targetEntityId: String?,
    val progress: Float
)

internal data class ActiveNativeParticle(
    val cue: CobblemonSceneParticleCue,
    val progress: Float
)

data class PokemonSceneEntity(
    val id: String,
    val view: PokemonView?,
    val label: String,
    val boardX: Float,
    val boardY: Float,
    val team: Int = 0,
    val yaw: Float = 0f,
    val scale: Float = 1f,
    val hp: Int = -1,
    val maxHp: Int = -1,
    val mana: Int = -1,
    val maxMana: Int = -1,
    val star: Int = 1,
    val motionSerial: Long = 0L,
    val motionFromX: Float? = null,
    val motionFromY: Float? = null,
    val elevation: Float = 0f
)

class PokemonSceneState {
    private data class Motion(var fromX: Float,var fromY: Float,var toX: Float,var toY: Float,var startedAt: Long,var serial: Long)
    private data class Effect(val serial:Long,val kind:SceneEffectKind,val source:String,val target:String?,val startedAt:Long)
    private val motions = linkedMapOf<String, Motion>()
    private val effects = linkedMapOf<String, Effect>()
    private val nativeParticles = ArrayDeque<CobblemonSceneParticleCue>()

    fun position(entity: PokemonSceneEntity, now: Long = System.currentTimeMillis()): ScenePoint {
        val existing = motions[entity.id]
        if (existing == null) {
            val fromX = entity.motionFromX?.takeIf { entity.motionSerial > 0 } ?: entity.boardX
            val fromY = entity.motionFromY?.takeIf { entity.motionSerial > 0 } ?: entity.boardY
            val created = Motion(fromX, fromY, entity.boardX, entity.boardY, now, entity.motionSerial)
            motions[entity.id] = created
            return sample(created, now)
        }
        val targetChanged = existing.toX != entity.boardX || existing.toY != entity.boardY
        val serialChanged = entity.motionSerial > 0 && existing.serial != entity.motionSerial
        if (targetChanged || serialChanged) {
            val current = sample(existing, now)
            existing.fromX = if (serialChanged && entity.motionFromX != null) entity.motionFromX else current.x
            existing.fromY = if (serialChanged && entity.motionFromY != null) entity.motionFromY else current.y
            existing.toX = entity.boardX
            existing.toY = entity.boardY
            existing.startedAt = now
            existing.serial = entity.motionSerial
        }
        return sample(existing, now)
    }

    fun observeEffects(signals: List<SceneEffectSignal>, now: Long = System.currentTimeMillis()) {
        signals.filter { it.serial > 0L }.forEach { signal ->
            val previous = effects[signal.id]
            if (previous == null || previous.serial != signal.serial) {
                effects[signal.id] = Effect(signal.serial, signal.kind, signal.sourceEntityId, signal.targetEntityId, now)
            }
        }
        effects.entries.removeIf { now - it.value.startedAt > EFFECT_MS }
    }

    internal fun activeEffects(now: Long = System.currentTimeMillis()): List<ActiveSceneEffect> {
        effects.entries.removeIf { now - it.value.startedAt > EFFECT_MS }
        return effects.values.map { effect ->
            ActiveSceneEffect(
                effect.kind,
                effect.source,
                effect.target,
                ((now - effect.startedAt).toFloat() / EFFECT_MS.toFloat()).coerceIn(0f, 1f)
            )
        }
    }

    fun observeNativeParticles(cues: List<CobblemonSceneParticleCue>, now: Long = System.currentTimeMillis()) {
        cues.forEach { cue ->
            nativeParticles += cue
            while (nativeParticles.size > 96) nativeParticles.removeFirst()
        }
        nativeParticles.removeIf { cue -> now > cue.startedAtMs + cue.delayMs + cue.lifetimeMs + 80L }
    }

    internal fun activeNativeParticles(now: Long = System.currentTimeMillis()): List<ActiveNativeParticle> {
        nativeParticles.removeIf { cue -> now > cue.startedAtMs + cue.delayMs + cue.lifetimeMs + 80L }
        return nativeParticles.mapNotNull { cue ->
            val elapsed = now - cue.startedAtMs - cue.delayMs
            if (elapsed < 0L || elapsed > cue.lifetimeMs) null
            else ActiveNativeParticle(cue, (elapsed.toFloat() / cue.lifetimeMs.coerceAtLeast(1L)).coerceIn(0f, 1f))
        }
    }

    fun prune(activeIds: Set<String>) { motions.keys.removeIf { it !in activeIds } }
    fun clear() { motions.clear(); effects.clear(); nativeParticles.clear() }

    private fun sample(motion: Motion, now: Long): ScenePoint {
        val t = ((now - motion.startedAt).toFloat() / MOTION_MS).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t) * (1f - t)
        return ScenePoint(motion.fromX + (motion.toX-motion.fromX)*eased, motion.fromY + (motion.toY-motion.fromY)*eased)
    }

    companion object {
        private const val MOTION_MS = 320f
        private const val EFFECT_MS = 480L
    }
}

data class PokemonSceneLayout(
    val area: UiRect,val columns: Int,val rows: Int,val originX: Float,val originY: Float,val tileWidth: Int,val tileHeight: Int,val perspective:io.github.aristheg201.svhub.ui.PerspectiveBoardTransform?=null,
    val boardSurface: io.github.aristheg201.svhub.ui.SceneInteractionSurface = io.github.aristheg201.svhub.ui.SceneInteractionSurface("board", io.github.aristheg201.svhub.ui.SceneVec3(-.5,-.5,0.0), columns.toDouble(), rows.toDouble(), columns, rows)
) {
    fun project(x: Float, y: Float, z: Float = 0f): ScenePoint? {
        if (perspective != null) return perspective.project(io.github.aristheg201.svhub.ui.SceneVec3(x.toDouble(),y.toDouble(),z.toDouble()))?.let { ScenePoint(it.x,it.y) }
        return ScenePoint(originX + (x-y)*tileWidth*0.5f, originY + (x+y)*tileHeight*0.5f - z*tileHeight)
    }
    fun center(index: Int): ScenePoint? = project(
        (boardSurface.origin.x + (index % columns + .5) * boardSurface.width / columns).toFloat(),
        (boardSurface.origin.y + (index / columns + .5) * boardSurface.height / rows).toFloat(), boardSurface.origin.z.toFloat())
    fun pick(mouseX:Double,mouseY:Double):Int?{
        if (!area.contains(mouseX,mouseY)) return null
        if (perspective != null) return boardSurface.pick(perspective,mouseX,mouseY)
        var best:Int?=null;var bestDistance=Double.MAX_VALUE
        repeat(columns*rows){index->
            val p=center(index) ?: return@repeat
            val nx=abs(mouseX-p.x)/max(9.0,tileWidth*0.72)
            val ny=abs(mouseY-p.y)/max(8.0,tileHeight*1.10)
            val distance=nx+ny
            if(distance<=1.0&&distance<bestDistance){best=index;bestDistance=distance}
        }
        return best
    }
}

data class PokemonSceneFrame(val layout: PokemonSceneLayout,val entityCenters: Map<String, ScenePoint>,val entityHeads: Map<String, ScenePoint>)

object PokemonScene3D {
    fun render(
        gui: GuiGraphics,
        font: Font,
        area: UiRect,
        columns: Int,
        rows: Int,
        entities: List<PokemonSceneEntity>,
        state: PokemonSceneState,
        selectedCells: Set<Int> = emptySet(),
        legalCells: Set<Int> = emptySet(),
        teamSplitRow: Int? = null,
        camera: SceneCameraPreset = SceneCameras.BOARD,
        effects: List<SceneEffectSignal> = emptyList(),
        nativeAnimations: List<SceneNativeAnimationSignal> = emptyList(),
        arenaId: String? = null,
        arenaSeed: String = "",
        pathCells: Set<Int> = emptySet(),
        pathRoute: List<Int> = emptyList(),
        platforms: List<ScenePlatform> = emptyList(),
        extraRows: Int = 0,
        sceneItems: List<SceneItemModelNode> = emptyList(),
        tactician: SceneTacticianNode? = null
    ): PokemonSceneFrame {
        val metrics=SceneProjection.resolve(area,columns,rows + extraRows.coerceIn(0, 4),camera)
        val arena = arenaId?.let(MinecraftArenaRegistry::definition)
        val baseLayout=PokemonSceneLayout(area,columns,rows,metrics.originX,metrics.originY,metrics.tileWidth,metrics.tileHeight,metrics.perspective)
        val layout=if(arena != null) baseLayout.copy(boardSurface=MinecraftArenaRenderer.compiledScene(baseLayout,arena).scene.interactions.first { it.id == "board" }) else baseLayout
        val activeIds=entities.mapTo(linkedSetOf()){it.id}
        tactician?.takeIf { it.pokemonSpecies.isNotBlank() }?.let { activeIds += it.id }
        state.prune(activeIds);PokemonModelRenderer.pruneScene(activeIds)
        val now=System.currentTimeMillis();state.observeEffects(effects,now)
        val embedded=arena != null && layout.perspective != null

        gui.enableScissor(area.x,area.y,area.right,area.bottom)
        val stableArenaSeed = if (arenaSeed.isNotBlank()) arenaSeed else arenaId.orEmpty()
        if(arena!=null && !embedded){
            MinecraftArenaRenderer.renderPathRoute(gui,layout,arena,pathRoute)
        }
        if(!embedded) for(row in 0 until rows)for(col in 0 until columns){
            val index=row*columns+col
            val point=layout.center(index) ?: continue
            val alternate=((row+col) and 1)==1
            val role=when{
                index in pathCells->ArenaTileRole.PATH
                teamSplitRow!=null&&row<teamSplitRow->ArenaTileRole.ENEMY
                teamSplitRow!=null->ArenaTileRole.ALLY
                else->ArenaTileRole.FLOOR
            }

            if(arena==null){
                val baseFill=when{
                    role==ArenaTileRole.ENEMY->if(alternate)ENEMY_B else ENEMY_A
                    else->if(alternate)ALLY_B else ALLY_A
                }
                val fill=when{index in selectedCells->SELECTED;index in legalCells->LEGAL;else->baseFill}
                drawDiamond(gui,point.x.roundToInt(),point.y.roundToInt(),layout.tileWidth,layout.tileHeight,fill,GRID_LINE)
            }else{
                when(arena.surfaceMode()){
                    ArenaSurfaceMode.CHECKER->MinecraftArenaRenderer.renderCheckerCell(gui,layout,arena,index,alternate)
                    ArenaSurfaceMode.TRACK->MinecraftArenaRenderer.renderTrackCell(gui,layout,arena,index,alternate)
                    ArenaSurfaceMode.GRID,ArenaSurfaceMode.TACTICAL,ArenaSurfaceMode.TERRAIN->Unit
                }
                when{
                    index in selectedCells->MinecraftArenaRenderer.renderCellHighlight(gui,layout,index,SELECTED,true)
                    index in legalCells->MinecraftArenaRenderer.renderCellHighlight(gui,layout,index,LEGAL,false)
                }
                MinecraftArenaRenderer.renderTile(gui,layout,arena,index,role,alternate,stableArenaSeed)
            }
        }
        if(arena!=null && !embedded)MinecraftArenaRenderer.renderProps(gui,layout,arena,stableArenaSeed)

        if(!embedded) platforms.take(32).forEach { platform ->
            val p = layout.project(platform.x, platform.y) ?: return@forEach
            val color = if (platform.selected) SELECTED else if (platform.hovered) LEGAL else ALLY_B
            drawDiamond(gui, p.x.roundToInt(), p.y.roundToInt() + 3, max(12, layout.tileWidth * 2 / 3), max(7, layout.tileHeight), 0xFF101719.toInt(), GRID_LINE)
            drawDiamond(gui, p.x.roundToInt(), p.y.roundToInt(), max(12, layout.tileWidth * 2 / 3), max(7, layout.tileHeight), color, if (platform.hovered || platform.selected) GOLD else GRID_LINE)
        }

        val nativeByEntity = nativeAnimations.filter { it.serial > 0L }.groupBy { it.entityId }
        entities.forEach { entity ->
            val view = entity.view ?: return@forEach
            nativeByEntity[entity.id].orEmpty().forEach { signal ->
                val labels = when (signal.kind) {
                    SceneNativeAnimationKind.PHYSICAL -> linkedSetOf("physical")
                    SceneNativeAnimationKind.SPECIAL -> linkedSetOf("special", "physical")
                    SceneNativeAnimationKind.STATUS -> linkedSetOf("status", "special")
                    SceneNativeAnimationKind.RECOIL -> linkedSetOf("recoil")
                    SceneNativeAnimationKind.FAINT -> linkedSetOf("faint", "recoil")
                    SceneNativeAnimationKind.CRY -> linkedSetOf("cry")
                }
                PokemonModelRenderer.requestSceneAnimation(
                    view = view,
                    instanceId = entity.id,
                    signalId = signal.id,
                    serial = signal.serial,
                    labels = labels,
                    targetEntityId = signal.targetEntityId,
                    moveId = signal.moveId,
                    faint = signal.kind == SceneNativeAnimationKind.FAINT
                )
            }
        }

        val positioned=entities.mapNotNull{entity->
            val logical=state.position(entity,now)
            val point=layout.project(logical.x,logical.y,entity.elevation) ?: return@mapNotNull null
            Triple(entity,logical,point)
        }.sortedWith(compareBy<Triple<PokemonSceneEntity,ScenePoint,ScenePoint>>{it.third.y}.thenBy{it.third.x}.thenBy{it.first.id})
        val centers=linkedMapOf<String,ScenePoint>()
        val heads=linkedMapOf<String,ScenePoint>()
        val renderedActors=hashSetOf<String>()
        if (embedded) {
            EmbeddedSceneRenderer.render(gui,layout,arena) { poses,buffers ->
                EmbeddedSceneRenderer.renderBoardGrid(poses,arena)
                EmbeddedSceneRenderer.renderCells(poses,arena,legalCells,0x454cc7b2)
                EmbeddedSceneRenderer.renderCells(poses,arena,selectedCells,0x99e2be62.toInt())
                positioned.forEach { (entity,logical,_) ->
                    val view=entity.view ?: return@forEach
                    poses.pushPose()
                    try {
                        EmbeddedSceneRenderer.applyTransform(poses,SceneTransform(SceneVec3(logical.x.toDouble(),logical.y.toDouble(),entity.elevation.toDouble()),SceneVec3(0.0,0.0,entity.yaw.toDouble()),SceneVec3(entity.scale.toDouble(),entity.scale.toDouble(),entity.scale.toDouble())))
                        val moving=abs(logical.x-entity.boardX)>.025f || abs(logical.y-entity.boardY)>.025f
                        if(PokemonModelRenderer.renderEmbedded(view,entity.id,poses,buffers,moving)) renderedActors+=entity.id
                    } finally { poses.popPose() }
                }
                sceneItems.forEach { item ->
                    if(item.visible) {
                        poses.pushPose()
                        try { EmbeddedSceneRenderer.applyTransform(poses,item.transform);EmbeddedSceneRenderer.renderItem(poses,item.itemId) }
                        finally { poses.popPose() }
                    }
                }
                tactician?.takeIf { it.visible }?.let { actor ->
                    poses.pushPose()
                    try {
                        EmbeddedSceneRenderer.applyTransform(poses,actor.transform)
                        val pokemonView=tacticianPokemonView(actor)
                        if(pokemonView!=null) {
                            val stateName=actor.animation.uppercase()
                            val labels=when(stateName) {
                                "WALK","CAROUSEL_MOVEMENT"->linkedSetOf("walk")
                                "RUN"->linkedSetOf("run","walk")
                                "EMOTE"->linkedSetOf("cry","status","idle")
                                "ROUND_START"->linkedSetOf("send_out","idle")
                                "VICTORY"->linkedSetOf("victory","cry","idle")
                                "DEFEAT"->linkedSetOf("faint","recoil","idle")
                                "PICKUP_REACTION"->linkedSetOf("cry","status","idle")
                                else->linkedSetOf("idle")
                            }
                            PokemonModelRenderer.requestSceneAnimation(
                                view=pokemonView,
                                instanceId=actor.id,
                                signalId="tactician:"+stateName,
                                serial=(stateName.hashCode().toLong() and 0x7fffffffL)+1L,
                                labels=labels,
                                faint=stateName=="DEFEAT"
                            )
                            PokemonModelRenderer.renderEmbedded(
                                pokemonView,
                                actor.id,
                                poses,
                                buffers,
                                stateName in setOf("WALK","RUN","CAROUSEL_MOVEMENT")
                            )
                        } else {
                            VanillaCompanionModelRenderer.renderEmbedded(actor.id,actor.entityId,actor.animation,poses,buffers)
                        }
                    } finally { poses.popPose() }
                }
                buffers.endBatch()
                val coordinates=positioned.associate { (entity,point,_) -> entity.id to SceneVec3(point.x.toDouble(),point.y.toDouble(),entity.elevation.toDouble()) }
                state.activeEffects(now).forEachIndexed { index,effect ->
                    val from=coordinates[effect.sourceEntityId] ?: return@forEachIndexed
                    val to=effect.targetEntityId?.let(coordinates::get) ?: from
                    val point=if(effect.kind==SceneEffectKind.PROJECTILE) from+(to-from)*effect.progress.toDouble()+SceneVec3(0.0,0.0,.6) else to
                    SceneEffectsRenderer.render(poses,SceneEffectNode("event:$index",SceneTransform(point),"svhub:${effect.kind.name.lowercase()}",now-(effect.progress*480).toLong()),now)
                }
                state.observeNativeParticles(PokemonModelRenderer.drainSceneParticleCues(activeIds),now)
                renderNativeSceneParticles(poses,buffers,state.activeNativeParticles(now),coordinates,checkNotNull(layout.perspective))
            }
            MinecraftArenaRenderer.renderPathRoute(gui,layout,arena,pathRoute)
        }
        positioned.forEachIndexed{order,(entity,logical,point)->
            centers[entity.id]=point
            val right=layout.perspective?.right
            val edge=right?.let { layout.project(logical.x+it.x.toFloat(),logical.y+it.y.toFloat(),entity.elevation+it.z.toFloat()) }
            val cellPixels=if(edge!=null) kotlin.math.hypot(edge.x-point.x,edge.y-point.y).coerceIn(12f,64f) else layout.tileWidth.toFloat()
            val ring=if(entity.team==0)ALLY_RING else ENEMY_RING
            if(!embedded) drawDiamond(gui,point.x.roundToInt(),(point.y+layout.tileHeight*0.20f).roundToInt(),max(9,(cellPixels*0.36f).roundToInt()),max(4,(layout.tileHeight*0.18f).roundToInt()),ring,ring)
            val modelSize=max(30,(layout.tileWidth*1.18f*entity.scale*camera.modelZoom).roundToInt()).coerceAtMost(108)
            val moving=abs(logical.x-entity.boardX)>0.025f||abs(logical.y-entity.boardY)>0.025f
            val rendered=if(embedded) entity.id in renderedActors else entity.view?.let{view->
                PokemonModelRenderer.renderScene(
                    gui = gui,
                    view = view,
                    instanceId = entity.id,
                    centerX = point.x.roundToInt(),
                    centerY = (point.y + layout.tileHeight * 0.22f).roundToInt(),
                    size = modelSize,
                    yaw = entity.yaw,
                    zoom = (entity.scale * camera.modelZoom).coerceIn(0.55f, 1.35f),
                    pitch = camera.pitch,
                    depth = camera.depthBase + order * camera.depthStride,
                    moving = moving
                )
            }?:false
            if(!rendered){val label=font.plainSubstrByWidth(entity.label,max(16,layout.tileWidth-8));gui.drawCenteredString(font,label,point.x.roundToInt(),point.y.roundToInt()-4,TEXT)}
            val height=entity.view?.let { PokemonModelRenderer.sceneHeight(it,entity.id) } ?: 1f
            val head=if(embedded) layout.project(logical.x,logical.y,entity.elevation+height*entity.scale+.12f) ?: point else ScenePoint(point.x,point.y-layout.tileHeight*.75f)
            heads[entity.id]=head
            if(entity.star>1)gui.drawCenteredString(font,"★".repeat(entity.star.coerceIn(2,3)),head.x.roundToInt(),head.y.roundToInt()-9,GOLD)
            if(entity.maxHp>0){
                val barW=max(12,(cellPixels*.8f).roundToInt());val x=head.x.roundToInt()-barW/2;val y=head.y.roundToInt()
                gui.fill(x,y,x+barW,y+3,BAR_BG)
                val hpW=(barW*entity.hp.coerceIn(0,entity.maxHp)/entity.maxHp).coerceAtLeast(if(entity.hp>0)1 else 0)
                gui.fill(x,y,x+hpW,y+2,if(entity.team==0)HP_ALLY else HP_ENEMY)
                if(entity.maxMana>0){val manaW=barW*entity.mana.coerceIn(0,entity.maxMana)/entity.maxMana;gui.fill(x,y+3,x+manaW,y+4,MANA)}
            }
        }
        val nativeParticleCues = PokemonModelRenderer.drainSceneParticleCues(activeIds)
        state.observeNativeParticles(nativeParticleCues, now)
        if(!embedded) renderEffects(gui,state.activeEffects(now),centers,layout.tileWidth,layout.tileHeight)
        if(!embedded) renderNativeParticles(gui,state.activeNativeParticles(now),centers,layout.tileWidth,layout.tileHeight)
        gui.disableScissor()
        return PokemonSceneFrame(layout,centers,heads)
    }

    private fun renderNativeSceneParticles(poses:PoseStack,buffers:MultiBufferSource,particles:List<ActiveNativeParticle>,
        positions:Map<String,SceneVec3>,camera:PerspectiveBoardTransform) {
        particles.forEach { active ->
            val cue=active.cue
            val source=positions[cue.sourceEntityId] ?: return@forEach
            val target=cue.targetEntityId?.let(positions::get) ?: source
            val id=ResourceLocation.tryParse(cue.effectId) ?: return@forEach
            val particle=BedrockParticleOptionsRepository.getEffect(id)?.particle ?: return@forEach
            val uv=particle.uvMode
            val tw=uv.textureSizeX.coerceAtLeast(1);val th=uv.textureSizeY.coerceAtLeast(1)
            val u=uv.startU.getString().toFloatOrNull()?.coerceIn(0f,tw-1f) ?: 0f
            val v=uv.startV.getString().toFloatOrNull()?.coerceIn(0f,th-1f) ?: 0f
            val w=uv.uSize.getString().toFloatOrNull()?.coerceIn(1f,tw-u) ?: min(8f,tw-u)
            val h=uv.vSize.getString().toFloatOrNull()?.coerceIn(1f,th-v) ?: min(8f,th-v)
            val buffer=buffers.getBuffer(RenderType.entityTranslucent(particle.texture))
            val pose=poses.last()
            repeat(4) { trail ->
                val t=(active.progress-trail*.065f).coerceIn(0f,1f)
                val center=source+(target-source)*t.toDouble()+SceneVec3(0.0,0.0,.6+t*.25)
                val right=camera.right*(.16-trail*.02);val up=camera.up*(.16-trail*.02)
                val corners=listOf(center-right-up,center+right-up,center+right+up,center-right+up)
                val alpha=((1f-active.progress)*220).toInt().coerceIn(0,255)
                corners.forEachIndexed { index,p ->
                    buffer.addVertex(pose.pose(),p.x.toFloat(),p.y.toFloat(),p.z.toFloat()).setColor(255,255,255,alpha)
                        .setUv((if(index==0 || index==3)u else u+w)/tw,(if(index<2)v+h else v)/th)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose,0f,0f,1f)
                }
            }
        }
    }

    private fun renderEffects(gui:GuiGraphics,effects:List<ActiveSceneEffect>,centers:Map<String,ScenePoint>,tileW:Int,tileH:Int){
        effects.forEach{effect->
            val from=centers[effect.sourceEntityId]?:return@forEach
            val target=effect.targetEntityId?.let(centers::get)
            when(effect.kind){
                SceneEffectKind.PROJECTILE->target?.let { drawProjectile(gui,from,it,effect.progress,PROJECTILE) }
                SceneEffectKind.CAST->drawPulse(gui,from,effect.progress,max(8,tileW/3),max(4,tileH/3),CAST)
                SceneEffectKind.HIT->drawPulse(gui,target?:from,effect.progress,max(7,tileW/4),max(4,tileH/4),HIT)
                SceneEffectKind.HEAL->drawPulse(gui,target?:from,effect.progress,max(8,tileW/3),max(4,tileH/3),HEAL)
                SceneEffectKind.BURST->drawPulse(gui,target?:from,effect.progress,max(10,tileW/2),max(5,tileH/2),GOLD)
            }
        }
    }

    private fun renderNativeParticles(
        gui: GuiGraphics,
        particles: List<ActiveNativeParticle>,
        centers: Map<String, ScenePoint>,
        tileW: Int,
        tileH: Int
    ) {
        if (particles.isEmpty()) return
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        particles.forEach { active ->
            val cue = active.cue
            val source = centers[cue.sourceEntityId] ?: return@forEach
            val target = cue.targetEntityId?.let(centers::get)
            val id = ResourceLocation.tryParse(cue.effectId) ?: return@forEach
            val effect = BedrockParticleOptionsRepository.getEffect(id) ?: return@forEach
            val particle = effect.particle
            val uv = particle.uvMode
            val textureWidth = uv.textureSizeX.coerceAtLeast(1)
            val textureHeight = uv.textureSizeY.coerceAtLeast(1)
            val u = uv.startU.getString().toDoubleOrNull()?.toInt()?.coerceIn(0, textureWidth - 1) ?: 0
            val v = uv.startV.getString().toDoubleOrNull()?.toInt()?.coerceIn(0, textureHeight - 1) ?: 0
            val sourceWidth = uv.uSize.getString().toDoubleOrNull()?.roundToInt()?.coerceIn(1, textureWidth - u) ?: min(8, textureWidth)
            val sourceHeight = uv.vSize.getString().toDoubleOrNull()?.roundToInt()?.coerceIn(1, textureHeight - v) ?: min(8, textureHeight)
            val baseSize = max(5, min(16, min(tileW, tileH * 2)))
            val alpha = (1f - active.progress * 0.78f).coerceIn(0.18f, 1f)
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha)

            repeat(5) { trail ->
                val t = (active.progress - trail * 0.065f).coerceIn(0f, 1f)
                val hash = cue.effectId.hashCode() * 31 + trail * 0x45d9f3b
                val wobbleX = (((hash ushr 3) and 7) - 3) * (1f - active.progress)
                val wobbleY = (((hash ushr 7) and 7) - 3) * (1f - active.progress)
                val point = if (target != null) {
                    ScenePoint(
                        source.x + (target.x - source.x) * t + wobbleX,
                        source.y + (target.y - source.y) * t + wobbleY
                    )
                } else {
                    val spread = (3 + trail * 2) * active.progress
                    ScenePoint(source.x + wobbleX * spread * 0.18f, source.y + wobbleY * spread * 0.18f)
                }
                val size = (baseSize * (1f - trail * 0.10f)).roundToInt().coerceAtLeast(4)
                gui.blit(
                    particle.texture,
                    point.x.roundToInt() - size / 2,
                    point.y.roundToInt() - size / 2,
                    size,
                    size,
                    u.toFloat(),
                    v.toFloat(),
                    sourceWidth,
                    sourceHeight,
                    textureWidth,
                    textureHeight
                )
            }
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    private fun drawProjectile(gui:GuiGraphics,from:ScenePoint,to:ScenePoint,progress:Float,color:Int){
        repeat(5){trail->
            val t=(progress-trail*0.055f).coerceIn(0f,1f)
            val x=(from.x+(to.x-from.x)*t).roundToInt();val y=(from.y+(to.y-from.y)*t).roundToInt()
            val size=if(trail==0)2 else 1;gui.fill(x-size,y-size,x+size+1,y+size+1,color)
        }
    }

    private fun drawPulse(gui:GuiGraphics,point:ScenePoint,progress:Float,width:Int,height:Int,color:Int){
        val wave=if(progress<0.5f)progress*2f else (1f-progress)*2f
        val w=(width*(0.65f+wave)).roundToInt().coerceAtLeast(4);val h=(height*(0.65f+wave)).roundToInt().coerceAtLeast(3)
        drawDiamond(gui,point.x.roundToInt(),point.y.roundToInt(),w,h,color,color)
    }

    private fun darken(color:Int,factor:Float):Int{
        val f=factor.coerceIn(0f,1f)
        val a=color ushr 24 and 0xFF
        val r=((color ushr 16 and 0xFF)*f).roundToInt()
        val g=((color ushr 8 and 0xFF)*f).roundToInt()
        val b=((color and 0xFF)*f).roundToInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun drawDiamond(gui:GuiGraphics,cx:Int,cy:Int,width:Int,height:Int,fill:Int,border:Int){
        val halfW=max(2,width/2);val halfH=max(2,height/2);val bands=min(10,max(4,halfH*2))
        repeat(bands){band->val y0=cy-halfH+(band*halfH*2/bands);val y1=cy-halfH+((band+1)*halfH*2/bands);val midY=(y0+y1)*0.5;val ratio=1.0-abs(midY-cy)/halfH.toDouble();val half=max(1,(halfW*ratio).roundToInt());gui.fill(cx-half-1,y0,cx+half+1,max(y0+1,y1),border);if(half>1)gui.fill(cx-half,y0,cx+half,max(y0+1,y1),fill)}
    }

    private const val GRID_LINE=0xFF29403F.toInt();private const val ALLY_A=0xFF173530.toInt();private const val ALLY_B=0xFF132C29.toInt();private const val ENEMY_A=0xFF302126.toInt();private const val ENEMY_B=0xFF291B20.toInt()
    private const val SELECTED=0xFF2F786E.toInt();private const val LEGAL=0xFF365D45.toInt();private const val ALLY_RING=0x664CC7B2;private const val ENEMY_RING=0x66B95E67
    private const val TEXT=0xFFF2F6F4.toInt();private const val GOLD=0xFFE2BE62.toInt();private const val BAR_BG=0xFF10191C.toInt();private const val HP_ALLY=0xFF54C97A.toInt();private const val HP_ENEMY=0xFFD86668.toInt();private const val MANA=0xFF55A9E8.toInt()
    private const val PROJECTILE=0xFFE2BE62.toInt();private const val CAST=0xFF9A7FE3.toInt();private const val HIT=0xFFE36C5C.toInt();private const val HEAL=0xFF67C989.toInt()

    private fun tacticianPokemonView(actor:SceneTacticianNode):PokemonView? {
        if(actor.pokemonSpecies.isBlank()) return null
        val id=ResourceLocation.tryParse(actor.pokemonSpecies) ?: return null
        val species=PokemonSpecies.getByIdentifier(id) ?: return null
        return PokemonView(
            key=actor.pokemonSpecies+"|"+actor.pokemonAspects.sorted().joinToString(","),
            route="",
            speciesId=actor.pokemonSpecies,
            aspects=actor.pokemonAspects,
            displayName=species.translatedName.string,
            dexNumber=species.nationalPokedexNumber,
            fakemon=species.resourceIdentifier.namespace!="cobblemon"
        )
    }

}
