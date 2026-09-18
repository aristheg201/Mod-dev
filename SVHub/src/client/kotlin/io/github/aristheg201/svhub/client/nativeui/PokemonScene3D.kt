package io.github.aristheg201.svhub.client.nativeui

import io.github.aristheg201.svhub.client.cobblemon.PokemonModelRenderer
import io.github.aristheg201.svhub.client.cobblemon.PokemonView
import io.github.aristheg201.svhub.ui.SceneCameraPreset
import io.github.aristheg201.svhub.ui.SceneCameras
import io.github.aristheg201.svhub.ui.SceneProjection
import io.github.aristheg201.svhub.ui.UiRect
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ScenePoint(val x: Float, val y: Float)

enum class SceneEffectKind { PROJECTILE, CAST, HIT, HEAL, BURST }

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
    val motionFromY: Float? = null
)

class PokemonSceneState {
    private data class Motion(var fromX: Float,var fromY: Float,var toX: Float,var toY: Float,var startedAt: Long,var serial: Long)
    private data class Effect(val serial:Long,val kind:SceneEffectKind,val source:String,val target:String?,val startedAt:Long)
    private val motions = linkedMapOf<String, Motion>()
    private val effects = linkedMapOf<String, Effect>()

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

    fun prune(activeIds: Set<String>) { motions.keys.removeIf { it !in activeIds } }
    fun clear() { motions.clear(); effects.clear() }

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
    val area: UiRect,val columns: Int,val rows: Int,val originX: Float,val originY: Float,val tileWidth: Int,val tileHeight: Int
) {
    fun project(x: Float, y: Float): ScenePoint = ScenePoint(originX + (x-y)*tileWidth*0.5f, originY + (x+y)*tileHeight*0.5f)
    fun center(index: Int): ScenePoint = project((index % columns).toFloat(), (index / columns).toFloat())
    fun hitBox(index: Int): UiRect {
        val point=center(index)
        val width=max(18,(tileWidth*0.86f).roundToInt())
        val height=max(16,(tileHeight*1.20f).roundToInt())
        return UiRect((point.x-width/2f).roundToInt(),(point.y-height/2f).roundToInt(),width,height)
    }
    fun pick(mouseX:Double,mouseY:Double):Int?{
        var best:Int?=null;var bestDistance=Double.MAX_VALUE
        repeat(columns*rows){index->
            val p=center(index)
            val nx=abs(mouseX-p.x)/max(9.0,tileWidth*0.72)
            val ny=abs(mouseY-p.y)/max(8.0,tileHeight*1.10)
            val distance=nx+ny
            if(distance<=1.0&&distance<bestDistance){best=index;bestDistance=distance}
        }
        return best
    }
}

data class PokemonSceneFrame(val layout: PokemonSceneLayout,val entityCenters: Map<String, ScenePoint>)

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
        effects: List<SceneEffectSignal> = emptyList()
    ): PokemonSceneFrame {
        val metrics=SceneProjection.resolve(area,columns,rows,camera)
        val layout=PokemonSceneLayout(area,columns,rows,metrics.originX,metrics.originY,metrics.tileWidth,metrics.tileHeight)
        val activeIds=entities.mapTo(linkedSetOf()){it.id}
        state.prune(activeIds);PokemonModelRenderer.pruneScene(activeIds)
        val now=System.currentTimeMillis();state.observeEffects(effects,now)

        gui.enableScissor(area.x,area.y,area.right,area.bottom)
        for(row in 0 until rows)for(col in 0 until columns){
            val index=row*columns+col;val point=layout.project(col.toFloat(),row.toFloat())
            val fill=when{index in selectedCells->SELECTED;index in legalCells->LEGAL;teamSplitRow!=null&&row<teamSplitRow->if((row+col)and 1==0)ENEMY_A else ENEMY_B;else->if((row+col)and 1==0)ALLY_A else ALLY_B}
            drawDiamond(gui,point.x.roundToInt(),point.y.roundToInt(),layout.tileWidth,layout.tileHeight,fill,GRID_LINE)
        }

        val positioned=entities.map{entity->
            val logical=state.position(entity,now)
            Triple(entity,logical,layout.project(logical.x,logical.y))
        }.sortedWith(compareBy<Triple<PokemonSceneEntity,ScenePoint,ScenePoint>>{it.third.y}.thenBy{it.third.x}.thenBy{it.first.id})
        val centers=linkedMapOf<String,ScenePoint>()
        positioned.forEachIndexed{order,(entity,_,point)->
            centers[entity.id]=point
            val ring=if(entity.team==0)ALLY_RING else ENEMY_RING
            drawDiamond(gui,point.x.roundToInt(),(point.y+layout.tileHeight*0.20f).roundToInt(),max(10,(layout.tileWidth*0.52f).roundToInt()),max(5,(layout.tileHeight*0.30f).roundToInt()),ring,ring)
            val modelSize=max(30,(layout.tileWidth*1.18f*entity.scale*camera.modelZoom).roundToInt()).coerceAtMost(108)
            val rendered=entity.view?.let{view->
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
                    depth = camera.depthBase + order * camera.depthStride
                )
            }?:false
            if(!rendered){val label=font.plainSubstrByWidth(entity.label,max(16,layout.tileWidth-8));gui.drawCenteredString(font,label,point.x.roundToInt(),point.y.roundToInt()-4,TEXT)}
            if(entity.star>1)gui.drawCenteredString(font,"★".repeat(entity.star.coerceIn(2,3)),point.x.roundToInt(),(point.y-layout.tileHeight*0.75f).roundToInt(),GOLD)
            if(entity.maxHp>0){
                val barW=max(12,(layout.tileWidth*0.62f).roundToInt());val x=point.x.roundToInt()-barW/2;val y=(point.y+layout.tileHeight*0.55f).roundToInt()
                gui.fill(x,y,x+barW,y+3,BAR_BG)
                val hpW=(barW*entity.hp.coerceIn(0,entity.maxHp)/entity.maxHp).coerceAtLeast(if(entity.hp>0)1 else 0)
                gui.fill(x,y,x+hpW,y+2,if(entity.team==0)HP_ALLY else HP_ENEMY)
                if(entity.maxMana>0){val manaW=barW*entity.mana.coerceIn(0,entity.maxMana)/entity.maxMana;gui.fill(x,y+3,x+manaW,y+4,MANA)}
            }
        }
        renderEffects(gui,state.activeEffects(now),centers,layout.tileWidth,layout.tileHeight)
        gui.disableScissor()
        return PokemonSceneFrame(layout,centers)
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

    private fun drawDiamond(gui:GuiGraphics,cx:Int,cy:Int,width:Int,height:Int,fill:Int,border:Int){
        val halfW=max(2,width/2);val halfH=max(2,height/2);val bands=min(10,max(4,halfH*2))
        repeat(bands){band->val y0=cy-halfH+(band*halfH*2/bands);val y1=cy-halfH+((band+1)*halfH*2/bands);val midY=(y0+y1)*0.5;val ratio=1.0-abs(midY-cy)/halfH.toDouble();val half=max(1,(halfW*ratio).roundToInt());gui.fill(cx-half-1,y0,cx+half+1,max(y0+1,y1),border);if(half>1)gui.fill(cx-half,y0,cx+half,max(y0+1,y1),fill)}
    }

    private const val GRID_LINE=0xFF29403F.toInt();private const val ALLY_A=0xFF173530.toInt();private const val ALLY_B=0xFF132C29.toInt();private const val ENEMY_A=0xFF302126.toInt();private const val ENEMY_B=0xFF291B20.toInt()
    private const val SELECTED=0xFF2F786E.toInt();private const val LEGAL=0xFF365D45.toInt();private const val ALLY_RING=0xFF4CC7B2.toInt();private const val ENEMY_RING=0xFFB95E67.toInt()
    private const val TEXT=0xFFF2F6F4.toInt();private const val GOLD=0xFFE2BE62.toInt();private const val BAR_BG=0xFF10191C.toInt();private const val HP_ALLY=0xFF54C97A.toInt();private const val HP_ENEMY=0xFFD86668.toInt();private const val MANA=0xFF55A9E8.toInt()
    private const val PROJECTILE=0xFFE2BE62.toInt();private const val CAST=0xFF9A7FE3.toInt();private const val HIT=0xFFE36C5C.toInt();private const val HEAL=0xFF67C989.toInt()
}
